package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.net.NETWORK_ERROR_CLASS
import com.snowballons.tukiwatch.net.isNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Desktop fast-iteration engine: shells out to a local Python that has
 * Streamlink installed plus the shared `stream_engine.py`. Not shipped in the APK.
 *
 * Nothing is hardcoded to one machine. Resolution order:
 *
 * Python:
 *  1. `PYKOT_PYTHON`
 *  2. a repo venv found by walking up from the working directory
 *     (`<root>/.venv`, `<root>/tukiwatch-core/.venv`)
 *  3. `python3.11`, then `python3` / `python` on PATH — first one that can
 *     `import streamlink`.
 *
 * Script:
 *  1. `PYKOT_SCRIPT`
 *  2. `androidApp/src/main/python/stream_engine.py` under the working directory
 *     or any of its ancestors (covers `desktopApp`, `tukiwatch-kotlin`, repo root).
 *
 * @param pythonBin explicit interpreter; skips discovery/validation.
 * @param scriptFile explicit engine script; skips discovery.
 * @param processTimeoutSeconds hard cap per subprocess so a hung Streamlink
 *   call fails the resolve instead of freezing the UI.
 */
class SubprocessStreamEngine(
    pythonBin: String? = null,
    scriptFile: File? = null,
    private val processTimeoutSeconds: Long = 120,
) : StreamEngine {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Resolved lazily: construction must stay cheap (Compose/UI tests). */
    private val resolvedPython: String by lazy {
        val explicit = pythonBin ?: System.getenv("PYKOT_PYTHON")
        explicit ?: discoverPython()
    }

    private val resolvedScript: File? by lazy {
        val explicit = scriptFile ?: System.getenv("PYKOT_SCRIPT")?.let(::File)
        explicit ?: discoverScript()
    }

    @Volatile
    private var cachedVersion: String? = null

    /** Interpreter actually used, for the harness status line. */
    val pythonPath: String get() = resolvedPython

    /** Engine script actually used, or null when it could not be found. */
    val scriptPath: String? get() = resolvedScript?.absolutePath

    override fun version(): String? = cachedVersion ?: runCatching {
        val out = runProcess(
            listOf(resolvedPython, "-c", "import streamlink; print(streamlink.__version__)"),
            timeoutSeconds = 30,
        )
        if (out.exitCode != 0) return@runCatching null
        val raw = out.text.trim().substringAfterLast('\n').trim()
        if (raw.isEmpty()) null else "Streamlink $raw"
    }.getOrNull()?.also { cachedVersion = it }

    override suspend fun warmup(url: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isNullOrBlank()) {
                runPython("warmup")
            } else {
                runPython("warmup", url)
            }
        }.isSuccess
    }

    private suspend fun runPython(attr: String, vararg args: String): String =
        withContext(Dispatchers.IO) {
            val script = resolvedScript ?: throw IllegalStateException(
                "stream_engine.py not found. Run from the repo or set PYKOT_SCRIPT."
            )
            val out = runProcess(
                listOf(resolvedPython, script.absolutePath, attr) + args,
                processTimeoutSeconds,
            )
            val payload = extractJsonObject(out.text)
                ?: throw IllegalStateException(
                    "stream_engine $attr failed (exit=${out.exitCode}): " +
                        out.text.take(800)
                )
            payload
        }

    override suspend fun listStreams(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = resolveDetails(url, optionsJson, wanted)

    override suspend fun resolveDetails(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = withContext(Dispatchers.IO) {
        try {
            json.decodeFromString(
                EngineResult.serializer(),
                runPython("resolve_details", url, optionsJson, wanted)
            )
        } catch (e: Exception) {
            EngineResult(
                status = "error",
                error = "unexpected",
                message = "${e.javaClass.simpleName}: ${e.message}",
                errorDetails = ErrorDetails(
                    type = "unexpected",
                    error = "Unexpected error: ${e.message}",
                    message = "${e.javaClass.simpleName}: ${e.message}",
                    reason = "Desktop subprocess failure",
                    alternative = "Check PYKOT_PYTHON / PYKOT_SCRIPT and that Streamlink is installed.",
                    errorClass = if (isNetworkError(e)) NETWORK_ERROR_CLASS else "unexpected"
                )
            )
        }
    }

    override suspend fun checkStatus(url: String, optionsJson: String): StreamStatus =
        withContext(Dispatchers.IO) {
            try {
                json.decodeFromString(
                    StreamStatus.serializer(),
                    runPython("check_status", url, optionsJson)
                )
            } catch (e: Exception) {
                StreamStatus(
                    url = url,
                    status = "error",
                    error = "${e.javaClass.simpleName}: ${e.message}",
                    errorDetails = ErrorDetails(
                        type = "unexpected",
                        error = "Unexpected error: ${e.message}",
                        message = "${e.javaClass.simpleName}: ${e.message}",
                        reason = "Desktop subprocess failure",
                        alternative = "Check PYKOT_PYTHON / PYKOT_SCRIPT and that Streamlink is installed.",
                        errorClass = if (isNetworkError(e)) NETWORK_ERROR_CLASS else "unexpected"
                    )
                )
            }
        }

    private data class ProcessOutput(val exitCode: Int, val text: String)

    private fun runProcess(cmd: List<String>, timeoutSeconds: Long): ProcessOutput {
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .apply { environment()["PYTHONUNBUFFERED"] = "1" }
            .start()

        // Drain the pipe on a side thread: waiting before reading can deadlock
        // when Streamlink logs more than the OS pipe buffer (~64 KiB).
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
        }.apply {
            isDaemon = true
            start()
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(1_000)
            throw IllegalStateException(
                "${cmd.joinToString(" ")} timed out after ${timeoutSeconds}s"
            )
        }
        // The process is gone; EOF follows immediately. Bounded join just in
        // case a grandchild holds the inherited stdout open.
        reader.join(2_000)
        return ProcessOutput(process.exitValue(), output.toString())
    }

    /** First JSON object in mixed stdout (Streamlink log lines may precede it). */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun discoverScript(): File? {
        val candidates = buildList {
            add(File("androidApp/src/main/python/stream_engine.py"))
            add(File("tukiwatch-kotlin/androidApp/src/main/python/stream_engine.py"))
            for (base in searchRoots()) {
                add(File(base, "androidApp/src/main/python/stream_engine.py"))
                add(File(base, "tukiwatch-kotlin/androidApp/src/main/python/stream_engine.py"))
            }
        }
        return candidates.firstOrNull { it.isFile }?.absoluteFile
    }

    private fun discoverPython(): String {
        val candidates = buildList {
            if (isWindows) {
                add("python.exe")
                for (base in searchRoots()) {
                    add(File(base, ".venv/Scripts/python.exe").path)
                    add(File(base, "tukiwatch-core/.venv/Scripts/python.exe").path)
                }
            } else {
                for (base in searchRoots()) {
                    add(File(base, ".venv/bin/python").path)
                    add(File(base, "tukiwatch-core/.venv/bin/python").path)
                }
                add("python3.11")
                add("python3")
                add("python")
            }
        }
        return candidates.firstOrNull(::hasStreamlink) ?: "python3.11"
    }

    /** Working dir, source location, and every ancestor — enough for Gradle/IDE runs. */
    private fun searchRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            roots += dir
            dir = dir.parentFile
        }
        runCatching {
            val code = SubprocessStreamEngine::class.java.protectionDomain?.codeSource?.location
            var fromCode: File? = code?.toURI()?.let(::File)
            while (fromCode != null) {
                roots += if (fromCode.isFile) fromCode.parentFile else fromCode
                fromCode = fromCode.parentFile
            }
        }
        return roots.toList()
    }

    private fun hasStreamlink(bin: String): Boolean = runCatching {
        runProcess(listOf(bin, "-c", "import streamlink"), timeoutSeconds = 20).exitCode == 0
    }.getOrDefault(false)

    private val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}

actual fun createStreamEngine(): StreamEngine = SubprocessStreamEngine()

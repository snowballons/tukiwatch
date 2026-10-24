package com.snowballons.tukiwatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// Desktop fast-iteration engine: shells out to local Python 3.11 + stream_engine.py.
// Override with PYKOT_SCRIPT / PYKOT_PYTHON. Not shipped in the APK.
class SubprocessStreamEngine(
    private val pythonBin: String = System.getenv("PYKOT_PYTHON") ?: "python3.11",
    private val scriptFile: File = File(
        System.getenv("PYKOT_SCRIPT")
            ?: "/home/jonnie/Projects/tukiwatch-org/tukiwatch-mobile/tukiwatch/tukiwatch-kotlin/androidApp/src/main/python/stream_engine.py"
    )
) : StreamEngine {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override fun version(): String? = "desktop-subprocess"

    override suspend fun warmup(url: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = mutableListOf(pythonBin, "-c", "import sys; sys.path.insert(0, '${scriptFile.parent}'); import stream_engine as se; print(se.warmup())")
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun runPython(attr: String, vararg args: String): String =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder(
                listOf(pythonBin, scriptFile.absolutePath, attr) + args
            ).redirectErrorStream(true).start()
            val raw = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val jsonStart = raw.indexOf('{')
            if (jsonStart < 0) {
                throw IllegalStateException("no JSON in output: ${raw.take(500)}")
            }
            raw.substring(jsonStart)
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
                    alternative = null
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
                        alternative = null
                    )
                )
            }
        }
}

actual fun createStreamEngine(): StreamEngine = SubprocessStreamEngine()
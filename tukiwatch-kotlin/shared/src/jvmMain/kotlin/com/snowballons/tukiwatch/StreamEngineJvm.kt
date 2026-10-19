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
            ?: "/home/jonnie/Projects/tukiwatch-org/tukiwatch-kotlin/androidApp/src/main/python/stream_engine.py"
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

    override suspend fun listStreams(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = withContext(Dispatchers.IO) {
        try {
            // Note: each call spawns a fresh process (no session reuse on desktop
            // fast-path); Android in-process reuses the singleton session.
            val proc = ProcessBuilder(pythonBin, scriptFile.absolutePath, url, wanted)
                .redirectErrorStream(true)
                .start()
            val raw = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val jsonStart = raw.indexOf('{')
            if (jsonStart < 0) {
                return@withContext EngineResult(
                    error = "unexpected",
                    message = raw.take(2000)
                )
            }
            json.decodeFromString(
                EngineResult.serializer(),
                raw.substring(jsonStart)
            )
        } catch (e: Exception) {
            EngineResult(error = "unexpected", message = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

actual fun createStreamEngine(): StreamEngine = SubprocessStreamEngine()

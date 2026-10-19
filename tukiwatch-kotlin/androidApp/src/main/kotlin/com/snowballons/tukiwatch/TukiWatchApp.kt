package com.snowballons.tukiwatch

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Starts Chaquopy once at process start and warms the Streamlink session
// (import + singleton) on a background thread. Extract taps then hit a
// ready session instead of paying cold-start cost.
class TukiWatchApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        appScope.launch {
            try {
                val module = Python.getInstance().getModule("stream_engine")
                module.callAttr("warmup")
            } catch (_: Exception) {
                // Warmup is best-effort; first Extract will cold-start instead.
            }
        }
    }
}

package com.snowballons.tukiwatch

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import com.snowballons.tukiwatch.liveness.LivenessRepository
import com.snowballons.tukiwatch.liveness.StreamlinkLivenessSource
import com.snowballons.tukiwatch.prefs.PrefsRepository
import com.snowballons.tukiwatch.prefs.createPrefsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Starts Chaquopy once at process start and warms the Streamlink session
// (import + singleton) on a background thread. Extract taps then hit a
// ready session instead of paying cold-start cost.
//
// Owns the Phase 2 singletons: one SQLDelight driver, one DataStore, and one
// StreamEngine shared by the player and the liveness ticker (Chaquopy's Python
// session is process-global, so a second engine would buy nothing).
class TukiWatchApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val engine: ChaquopyStreamEngine by lazy { ChaquopyStreamEngine(this) }

    val favorites: FavoritesRepository by lazy {
        FavoritesRepository(createFavoritesDriver(this), Dispatchers.IO)
    }

    val prefs: PrefsRepository by lazy { PrefsRepository(createPrefsDataStore(this)) }

    private val connectivity: AndroidDeviceConnectivity by lazy { AndroidDeviceConnectivity(this) }

    val liveness: LivenessRepository by lazy {
        LivenessRepository(
            favorites = favorites,
            source = StreamlinkLivenessSource(engine),
            nowMs = { System.currentTimeMillis() },
            connectivity = connectivity,
            dispatcher = Dispatchers.IO,
        )
    }

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

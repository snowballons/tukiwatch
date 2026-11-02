package com.snowballons.tukiwatch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.LivenessRepository
import com.snowballons.tukiwatch.liveness.StreamlinkLivenessSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State holder for the desktop harness.
 *
 * Deliberately not a ViewModel: desktop windows are not recreated on rotation,
 * so composition-scoped state is enough, and a plain class keeps the UI tests
 * free of an Android/JVM lifecycle owner. Everything slow (Streamlink resolve,
 * SQLite, liveness checks) runs on [scope] and off the UI thread.
 *
 * The harness reuses the production shared layer untouched — [SubprocessStreamEngine],
 * [FavoritesRepository], [LivenessRepository] and [StreamlinkLivenessSource] — so a
 * green desktop run is evidence about the same code the APK ships.
 */
class DesktopHarness(
    val engine: StreamEngine,
    val favorites: FavoritesRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    constructor() : this(
        engine = createStreamEngine(),
        favorites = FavoritesRepository(
            createFavoritesDriver(
                File(System.getProperty("user.home"), ".tukiwatch/harness.db")
            ),
            Dispatchers.IO,
        ),
    )

    private val liveness = LivenessRepository(
        favorites = favorites,
        source = StreamlinkLivenessSource(engine),
        nowMs = { System.currentTimeMillis() },
        dispatcher = Dispatchers.IO,
    )

    val streams: StateFlow<List<LiveStream>> = liveness.streams
    val loading: StateFlow<Boolean> = liveness.loading
    val degraded: StateFlow<Boolean> = liveness.tickDegraded

    var url by mutableStateOf("https://www.twitch.tv/lofigirl")
    var resolving by mutableStateOf(false)
    var result by mutableStateOf<EngineResult?>(null)
    var resolvedUrl by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var elapsedMs by mutableStateOf<Long?>(null)
    var selectedQuality by mutableStateOf<String?>(null)
    var isFavorite by mutableStateOf<Boolean?>(null)
    var busy by mutableStateOf(false)
    var engineVersion by mutableStateOf<String?>(null)
    var copiedQuality by mutableStateOf<String?>(null)

    /** Interpreter/script actually resolved by [SubprocessStreamEngine]. */
    val engineDetail: String?
        get() = (engine as? SubprocessStreamEngine)?.let { sub ->
            sub.scriptPath?.let { "${sub.pythonPath}  ·  ${File(it).name}" }
                ?: "script not found — set PYKOT_SCRIPT (${sub.pythonPath})"
        }

    init {
        scope.launch { engineVersion = withContext(Dispatchers.IO) { engine.version() } }
        scope.launch { liveness.refresh() }
        // The field starts on lofigirl; show Remove rather than Add when that
        // URL is already saved.
        scope.launch {
            isFavorite = runCatching { favorites.isFavorite(url.trim()) }.getOrNull()
        }
    }

    fun onUrlChanged(value: String) {
        url = value
        // Membership is only known for the URL we checked. A different URL
        // starts unknown rather than showing a stale Add/Remove label.
        if (value.trim() != resolvedUrl) isFavorite = null
    }

    fun resolve() {
        if (resolving) return
        scope.launch { resolveOnce() }
    }

    /** Open a My List row (or a sample chip) without going through the field. */
    fun resolveUrl(target: String) {
        onUrlChanged(target)
        resolve()
    }

    fun refreshLiveness() {
        scope.launch { liveness.refresh() }
    }

    fun toggleFavorite() {
        scope.launch { toggleFavoriteOnce() }
    }

    fun removeFavorite(id: Long) {
        scope.launch {
            busy = true
            try {
                favorites.removeFavorite(id)
                if (resolvedUrl.isNotBlank()) isFavorite = favorites.isFavorite(resolvedUrl)
                message = "Removed from My List."
                liveness.refresh()
            } catch (t: Throwable) {
                message = "Could not update My List: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    /** Deterministic entry point for tests; [resolve] just wraps it in [scope]. */
    suspend fun resolveOnce(): EngineResult? {
        val target = url.trim()
        if (target.isEmpty()) {
            message = "Enter a stream URL first."
            return null
        }
        resolving = true
        message = null
        val started = System.nanoTime()
        try {
            val resolved = try {
                engine.resolveDetails(target, "{}", "all")
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                EngineResult(
                    status = "error",
                    error = "unexpected",
                    message = "${t::class.simpleName}: ${t.message}",
                    errorDetails = ErrorDetails(
                        type = "unexpected",
                        error = "Unexpected error",
                        message = "${t::class.simpleName}: ${t.message}",
                        reason = "Desktop harness failure",
                    ),
                )
            }
            elapsedMs = (System.nanoTime() - started) / 1_000_000
            result = resolved
            resolvedUrl = target
            selectedQuality = resolved.directPlayable.firstOrNull()?.quality
            isFavorite = runCatching { favorites.isFavorite(target) }.getOrNull()
            message = when (resolved.status) {
                "online" ->
                    "Resolved ${resolved.directPlayable.size} directly playable " +
                        "quality(s) in ${elapsedMs}ms."
                "offline" -> "Stream is offline (${resolved.author ?: "unknown"})."
                else ->
                    resolved.errorDetails?.error ?: resolved.error ?: "Resolve failed."
            }
            return resolved
        } finally {
            resolving = false
        }
    }

    /** Deterministic entry point for tests; [toggleFavorite] wraps it. */
    suspend fun toggleFavoriteOnce() {
        val target = resolvedUrl.ifBlank { url.trim() }
        if (target.isEmpty()) {
            message = "Enter a stream URL first."
            return
        }
        busy = true
        try {
            val already = isFavorite ?: favorites.isFavorite(target)
            if (already) {
                favorites.idForUrl(target)?.let { favorites.removeFavorite(it) }
                isFavorite = false
                message = "Removed from My List."
            } else {
                val name = result?.author?.takeIf { it.isNotBlank() }
                    ?: result?.title?.takeIf { it.isNotBlank() }
                    ?: deriveName(target)
                val added = favorites.addFavorite(name, target)
                isFavorite = true
                message = if (added) "Saved to My List." else "Already in My List."
            }
            liveness.refresh()
            if (target == resolvedUrl) isFavorite = favorites.isFavorite(target)
        } catch (t: Throwable) {
            message = "Could not update My List: ${t.message}"
        } finally {
            busy = false
        }
    }

    fun showCopied(quality: String) {
        copiedQuality = quality
    }

    private fun deriveName(target: String): String =
        target.substringBefore('?').substringBefore('#').trimEnd('/')
            .substringAfterLast('/').ifEmpty { target }
}

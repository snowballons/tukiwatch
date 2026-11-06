package com.snowballons.tukiwatch.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snowballons.tukiwatch.EngineResult
import com.snowballons.tukiwatch.ErrorDetails
import com.snowballons.tukiwatch.StreamEngine
import com.snowballons.tukiwatch.StreamItem
import com.snowballons.tukiwatch.ui.StreamUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The playback surface a [StreamController] drives.
 *
 * Android implements this over ExoPlayer. Desktop has no video surface yet, so
 * the shared player screen can be previewed with a no-op implementation.
 * Failures are reported back through [StreamController.onPlayerFailure] rather
 * than thrown, because players deliver them asynchronously.
 */
interface StreamPlayer {
    /** Start [item]. Throws when the item has no URL or the surface rejects it. */
    fun play(item: StreamItem)

    fun stop()

    /** @return true when playback was actually paused, so a resume is meaningful. */
    fun pause(): Boolean

    /** @return false when there is no prepared media left to resume. */
    fun resume(): Boolean

    /** Seek back to the live edge after falling behind. */
    fun seekToLiveEdge()
}

/** Player errors the controller reacts to; everything else is just reported. */
enum class PlayerFailure {
    BEHIND_LIVE_WINDOW,
    CODEC_UNSUPPORTED,
    OTHER,
}

/**
 * Resolve-and-play state for the player screen.
 *
 * Plain class rather than a ViewModel: Android wraps it in one to survive
 * rotation, and the desktop harness can own it from a composition scope. All
 * UI state is Compose snapshot state, so both hosts render it the same way.
 *
 * The cache TTL is 45 minutes because Twitch tokenized URLs are short-lived.
 */
class StreamController(
    private val engine: StreamEngine,
    private val player: StreamPlayer,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    defaultUrl: String = "https://www.twitch.tv/lofigirl",
) {
    companion object {
        const val CACHE_TTL_MS = 45 * 60 * 1000L
        const val MAX_BEHIND_LIVE_RETRIES = 3
        const val BEHIND_LIVE_WINDOW_MS = 30 * 1000L
    }

    var url by mutableStateOf(defaultUrl)
    var status by mutableStateOf("Idle. Tap Resolve & Play.")
    var extracting by mutableStateOf(false)
    var result by mutableStateOf(EngineResult())
    var metadata by mutableStateOf<EngineResult?>(null)
    var errorDetails by mutableStateOf<ErrorDetails?>(null)
    var selected by mutableStateOf<StreamItem?>(null)
    var playerError by mutableStateOf<String?>(null)

    private val cache = mutableMapOf<String, Pair<EngineResult, Long>>()
    private var job: Job? = null
    private var behindLiveRetries = 0
    private var lastBehindLiveMs = 0L
    private var backgroundPaused = false
    private var codecFallbackDone = false

    /**
     * Preferred rendition keyword from settings ("auto", "source", "1080", …).
     * "auto" keeps the h264-first default; anything else is matched by name
     * first, then falls back to the default when no rendition matches.
     */
    var preferredQuality: String = "auto"

    /**
     * h264-first: HEVC-in-FLV (TikTok h265_*) is unplayable via ExoPlayer's FLV
     * extractor on most devices, so prefer h264 whenever offered.
     */
    private fun preferredStream(items: List<StreamItem>): StreamItem? {
        val want = preferredQuality.lowercase()
        if (want != "auto" && want.isNotBlank()) {
            val match = items.firstOrNull { it.quality.lowercase().contains(want) }
            if (match != null) return match
        }
        return items.firstOrNull { it.quality.startsWith("h264") }
            ?: items.firstOrNull { it.quality == "best" }
            ?: items.firstOrNull()
    }

    /** Drops the resolve cache; the next play resolves fresh. */
    fun clearCache() {
        cache.clear()
    }

    /** Quality row ordering: h264 first, then the rest alphabetically. */
    fun orderedQualities(): List<StreamItem> =
        result.directPlayable.sortedWith(
            compareBy({ !it.quality.startsWith("h264") }, { it.quality })
        )

    fun play(item: StreamItem) {
        selected = item
        playerError = null
        behindLiveRetries = 0
        codecFallbackDone = false
        try {
            player.play(item)
            status = "Playing ${item.quality} (${item.kind})"
        } catch (e: Exception) {
            playerError = "Play failed: ${e.message}"
        }
    }

    /** Pause without clearing media items so resume is instant. */
    fun onPauseBackground() {
        if (player.pause()) {
            backgroundPaused = true
            status = "Paused (background)"
        }
    }

    /** Resume only if we paused for background; keeps manual Stop stopped. */
    fun onResumeForeground() {
        if (!backgroundPaused) return
        backgroundPaused = false
        if (!player.resume()) return
        playerError = null
        status = selected?.let { "Playing ${it.quality} (${it.kind})" } ?: "Resumed"
    }

    /**
     * Entry point for asynchronous player failures. [description] is the
     * platform's error name plus message, already formatted.
     */
    fun onPlayerFailure(kind: PlayerFailure, description: String) {
        when (kind) {
            PlayerFailure.BEHIND_LIVE_WINDOW -> recoverBehindLiveWindow()
            PlayerFailure.CODEC_UNSUPPORTED -> fallbackToH264(description)
            PlayerFailure.OTHER -> playerError = "Player error $description"
        }
    }

    /** Official live-edge recovery: rejoin at the default (live-edge) position. */
    private fun recoverBehindLiveWindow() {
        val now = nowMs()
        if (now - lastBehindLiveMs > BEHIND_LIVE_WINDOW_MS) behindLiveRetries = 0
        lastBehindLiveMs = now
        behindLiveRetries++
        if (behindLiveRetries <= MAX_BEHIND_LIVE_RETRIES) {
            playerError = null
            try {
                player.seekToLiveEdge()
                status = "Reconnected to live edge (attempt $behindLiveRetries)"
            } catch (e: Exception) {
                playerError = "Reconnect failed: ${e.message}"
            }
            return
        }
        // Retries exhausted: tokenized URL likely expired — fresh extract.
        behindLiveRetries = 0
        playerError = null
        status = "Stream URL expired — re-extracting..."
        // Bypass the cache: the cached entry is exactly the expired URL.
        resolveAndPlay(url, forceRefresh = true)
    }

    /** Codec/container fallback: switch to an h264 rendition once per incident. */
    private fun fallbackToH264(description: String) {
        val current = selected
        val alt = result.directPlayable.firstOrNull {
            it.quality.startsWith("h264") && it.quality != current?.quality
        }
        if (codecFallbackDone || alt == null) {
            playerError = "Player error $description"
            return
        }
        playerError = null
        play(alt)
        codecFallbackDone = true
        status = "Codec unsupported here — switched to ${alt.quality} (${alt.kind})"
    }

    fun stop() {
        backgroundPaused = false
        player.stop()
        status = "Stopped"
    }

    fun resolveAndPlay(
        targetUrl: String = url,
        forceRefresh: Boolean = false,
        placeholderTitle: String? = null,
        placeholderAuthor: String? = null,
        placeholderPlatform: String? = null,
    ) {
        job?.cancel()

        val isDifferentUrl = (targetUrl != url)
        if (isDifferentUrl) {
            player.stop()
            selected = null
        }
        url = targetUrl
        extracting = true
        playerError = null
        errorDetails = null

        val fallbackAuthor = placeholderAuthor?.takeIf { it.isNotBlank() }
            ?: StreamUtils.deriveStreamerName(targetUrl)
        val fallbackPlatform = placeholderPlatform ?: StreamUtils.detectPlatform(targetUrl)

        // Pre-populate metadata immediately so UI never flashes stale stream information
        if (isDifferentUrl || metadata == null) {
            val placeholder = EngineResult(
                originalUrl = targetUrl,
                author = fallbackAuthor,
                title = placeholderTitle ?: "Loading stream…",
                platform = fallbackPlatform,
                status = "loading",
            )
            metadata = placeholder
            result = placeholder
        }

        job = scope.launch {
            // 1. Fresh cache → instant play, then silent background refresh.
            val cached = cache[targetUrl]
            if (!forceRefresh && cached != null && nowMs() - cached.second < CACHE_TTL_MS) {
                val (cr, _) = cached
                result = cr
                metadata = cr
                errorDetails = cr.errorDetails
                val best = preferredStream(cr.directPlayable)
                if (best != null) {
                    play(best)
                    status = "Playing from cache (${cr.directPlayable.size} qualities)"
                }
                launch { refreshFull(targetUrl, silent = true) }
                extracting = false
                return@launch
            }

            status = "Resolving..."
            val rawFull = engine.resolveDetails(targetUrl, "{}", "all")
            val full = if (rawFull.author.isNullOrBlank() || rawFull.author.equals("live", ignoreCase = true)) {
                rawFull.copy(author = fallbackAuthor)
            } else {
                rawFull
            }
            errorDetails = full.errorDetails
            metadata = full
            result = full

            if (full.status == "error" || full.error != null) {
                status = "Error ${full.error ?: "unknown"}: ${full.message}"
                extracting = false
                return@launch
            }
            if (full.status == "offline") {
                status = "Stream is offline (${full.author ?: "unknown"})"
                extracting = false
                return@launch
            }

            val best = preferredStream(full.directPlayable)
            if (best == null) {
                status = "No direct-playable streams found"
                extracting = false
                return@launch
            }

            cache[targetUrl] = full to nowMs()
            result = full
            play(best)
            extracting = false
        }
    }

    fun extractAndPlay(targetUrl: String = url) {
        resolveAndPlay(targetUrl)
    }

    private suspend fun refreshFull(targetUrl: String, silent: Boolean) {
        try {
            val full = engine.resolveDetails(targetUrl, "{}", "all")
            if (full.status == "online" && full.directPlayable.isNotEmpty()) {
                cache[targetUrl] = full to nowMs()
                result = full
                metadata = full
                if (!silent) status = "Found ${full.streams.size} qualities"
            }
        } catch (_: Exception) {
        }
    }
}

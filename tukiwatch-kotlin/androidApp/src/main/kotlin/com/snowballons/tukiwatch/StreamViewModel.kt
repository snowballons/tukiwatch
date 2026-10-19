package com.snowballons.tukiwatch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Rotation-safe holder: ExoPlayer + result cache survive config changes.
// Cache TTL 45 min (Twitch tokenized URLs are short-lived).
class StreamViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val CACHE_TTL_MS = 45 * 60 * 1000L
        const val MAX_BEHIND_LIVE_RETRIES = 3
        const val BEHIND_LIVE_WINDOW_MS = 30 * 1000L
    }

    val player: ExoPlayer = ExoPlayer.Builder(application.applicationContext).build().apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    recoverBehindLiveWindow()
                } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
                    fallbackToH264(error)
                } else {
                    this@StreamViewModel.playerError =
                        "Player error ${error.errorCodeName}: ${error.message}"
                }
            }
        })
    }

    var url by mutableStateOf("https://www.twitch.tv/lofigirl")
    var status by mutableStateOf("Idle. Tap Extract, then Play.")
    var extracting by mutableStateOf(false)
    var result by mutableStateOf(EngineResult())
    var selected by mutableStateOf<StreamItem?>(null)
    var playerError by mutableStateOf<String?>(null)
    var lastTimings by mutableStateOf<String?>(null)

    private val cache = mutableMapOf<String, Pair<EngineResult, Long>>()
    private var job: Job? = null
    private var lastEngine: ChaquopyStreamEngine? = null
    private var behindLiveRetries = 0
    private var lastBehindLiveMs = 0L
    private var backgroundPaused = false
    private var codecFallbackDone = false

    /** h264-first: HEVC-in-FLV (TikTok h265_*) is unplayable via ExoPlayer's
     * FLV extractor on most devices, so prefer h264 whenever offered. */
    private fun preferredStream(items: List<StreamItem>): StreamItem? =
        items.firstOrNull { it.quality.startsWith("h264") }
            ?: items.firstOrNull { it.quality == "best" }
            ?: items.firstOrNull()

    /** Quality row ordering: h264 first, then the rest alphabetically. */
    fun orderedQualities(): List<StreamItem> =
        result.directPlayable.sortedWith(
            compareBy({ !it.quality.startsWith("h264") }, { it.quality })
        )

    private fun timingsText(prefix: String, r: EngineResult): String? {
        val t = r.timings ?: return null
        return "$prefix py=${t.totalMs}ms (streams=${t.streamsMs}ms reused=${t.sessionReused})"
    }

    fun play(item: StreamItem) {
        selected = item
        playerError = null
        behindLiveRetries = 0
        codecFallbackDone = false
        try {
            playStream(player, item)
            status = "Playing ${item.quality} (${item.kind})"
        } catch (e: Exception) {
            playerError = "Play failed: ${e.message}"
        }
    }

    /** Pause without clearing media items so resume is instant. */
    fun onPauseBackground() {
        if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
            backgroundPaused = true
            player.playWhenReady = false
            status = "Paused (background)"
        }
    }

    /** Resume only if we paused for background; keeps manual Stop stopped. */
    fun onResumeForeground() {
        if (!backgroundPaused) return
        backgroundPaused = false
        if (player.playbackState == Player.STATE_IDLE) return
        playerError = null
        player.playWhenReady = true
        status = selected?.let { "Playing ${it.quality} (${it.kind})" } ?: "Resumed"
    }

    /** Official live-edge recovery: rejoin at the default (live-edge) position. */
    private fun recoverBehindLiveWindow() {
        val now = System.currentTimeMillis()
        if (now - lastBehindLiveMs > BEHIND_LIVE_WINDOW_MS) behindLiveRetries = 0
        lastBehindLiveMs = now
        behindLiveRetries++
        if (behindLiveRetries <= MAX_BEHIND_LIVE_RETRIES) {
            playerError = null
            try {
                player.seekToDefaultPosition()
                player.prepare()
                player.playWhenReady = true
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
        lastEngine?.let { extractAndPlay(it, url) }
    }

    /** Codec/container fallback: switch to an h264 rendition once per incident. */
    private fun fallbackToH264(error: PlaybackException) {
        val current = selected
        val alt = result.directPlayable.firstOrNull {
            it.quality.startsWith("h264") && it.quality != current?.quality
        }
        if (codecFallbackDone || alt == null) {
            playerError = "Player error ${error.errorCodeName}: ${error.message}"
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
        player.clearMediaItems()
        status = "Stopped"
    }

    fun extractAndPlay(engine: ChaquopyStreamEngine, targetUrl: String = url) {
        lastEngine = engine
        job?.cancel()
        job = viewModelScope.launch {
            extracting = true
            playerError = null
            url = targetUrl

            // 1. Fresh cache → instant play, then silent background refresh.
            val cached = cache[targetUrl]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_TTL_MS) {
                val (cr, _) = cached
                result = cr
                val best = preferredStream(cr.directPlayable)
                if (best != null) {
                    play(best)
                    status = "Playing from cache (${cr.directPlayable.size} qualities)"
                    lastTimings = timingsText("cache", cr)
                }
                // Refresh in background without blocking playback.
                launch { refreshFull(engine, targetUrl, silent = true) }
                extracting = false
                return@launch
            }

            // Single full extract: streams() resolves everything anyway, so one
            // call both populates the quality row and lets us prefer h264.
            status = "Extracting..."
            val full = engine.listStreams(targetUrl, "{}", "all")
            if (full.error != null) {
                status = "Error ${full.error}: ${full.message}"
                extracting = false
                return@launch
            }
            val best = preferredStream(full.directPlayable)
            if (best == null) {
                status = "No direct-playable streams"
                extracting = false
                return@launch
            }
            cache[targetUrl] = full to System.currentTimeMillis()
            result = full
            play(best)
            lastTimings = timingsText("full", full)
            extracting = false
        }
    }

    private suspend fun refreshFull(
        engine: ChaquopyStreamEngine,
        targetUrl: String,
        silent: Boolean
    ) {
        try {
            val full = engine.listStreams(targetUrl, "{}", "all")
            if (full.error == null && full.directPlayable.isNotEmpty()) {
                cache[targetUrl] = full to System.currentTimeMillis()
                result = full
                // Keep current selection if still present; else keep playing item.
                lastTimings = timingsText("full", full)
                if (!silent) status = "Found ${full.streams.size} qualities"
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}

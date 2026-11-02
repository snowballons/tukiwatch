package com.snowballons.tukiwatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.snowballons.tukiwatch.ui.state.PlayerFailure
import com.snowballons.tukiwatch.ui.state.StreamController
import com.snowballons.tukiwatch.ui.state.StreamPlayer

/**
 * Android wrapper around the shared [StreamController].
 *
 * Only the things that cannot live in common code stay here: the ExoPlayer
 * instance (needs a `Context`), its release on clear, and the mapping from
 * Media3 error codes to the shared [PlayerFailure] vocabulary. Resolve,
 * caching, quality ordering, background pause and codec fallback all run in
 * the shared controller, so the desktop harness can exercise the same logic.
 *
 * The ViewModel is rotation-safe: ExoPlayer and the controller (with its
 * result cache) survive config changes.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TukiWatchApp

    val player: ExoPlayer = run {
        // Configure audio attributes for proper audio focus management and headphone
        // disconnect handling (AudioBecomingNoisy = pause on unplug).
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(application.applicationContext)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }.apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                controller.onPlayerFailure(
                    kind = error.toPlayerFailure(),
                    description = "${error.errorCodeName}: ${error.message}",
                )
            }
        })
    }

    val controller = StreamController(
        engine = app.engine,
        player = object : StreamPlayer {
            override fun play(item: StreamItem) = playStream(player, item)

            override fun stop() {
                player.stop()
                player.clearMediaItems()
            }

            override fun pause(): Boolean {
                if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                    player.playWhenReady = false
                    return true
                }
                return false
            }

            override fun resume(): Boolean {
                if (player.playbackState == Player.STATE_IDLE) return false
                player.playWhenReady = true
                return true
            }

            override fun seekToLiveEdge() {
                player.seekToDefaultPosition()
                player.prepare()
                player.playWhenReady = true
            }
        },
        scope = viewModelScope,
        nowMs = { System.currentTimeMillis() },
    )

    fun onPauseBackground() = controller.onPauseBackground()

    fun onResumeForeground() = controller.onResumeForeground()

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}

/** Media3 error codes collapse into the three cases the controller reacts to. */
private fun PlaybackException.toPlayerFailure(): PlayerFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlayerFailure.BEHIND_LIVE_WINDOW
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED -> PlayerFailure.CODEC_UNSUPPORTED
    else -> PlayerFailure.OTHER
}

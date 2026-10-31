package com.snowballons.tukiwatch.ui.state

import com.snowballons.tukiwatch.EngineResult
import com.snowballons.tukiwatch.StreamEngine
import com.snowballons.tukiwatch.StreamItem
import com.snowballons.tukiwatch.StreamStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The controller is the Android player's logic, moved out of the ViewModel so
 * it can be tested without an emulator. An unconfined scope makes the resolve
 * coroutine finish synchronously against the fake engine.
 */
class StreamControllerTest {
    private val h264 = StreamItem(
        quality = "h264_720",
        kind = "HLS",
        playableDirect = true,
        url = "https://cdn/h264.m3u8",
    )
    private val h265 = StreamItem(
        quality = "h265_1080",
        kind = "HLS",
        playableDirect = true,
        url = "https://cdn/h265.m3u8",
    )

    private fun controller(
        engine: StreamEngine,
        player: FakePlayer = FakePlayer(),
        now: () -> Long = { 0L },
    ) = StreamController(
        engine = engine,
        player = player,
        scope = CoroutineScope(Dispatchers.Unconfined),
        nowMs = now,
    )

    @Test
    fun resolvePlaysH264FirstAndRecordsTimings() {
        val engine = FakeEngine(online(h265, h264))
        val player = FakePlayer()
        val c = controller(engine, player)

        c.resolveAndPlay("https://www.twitch.tv/x")

        assertEquals("h264_720", player.played.single().quality)
        assertEquals("Playing h264_720 (HLS)", c.status)
        assertFalse(c.extracting)
        assertNotNull(c.lastTimings)
        assertNull(c.errorDetails)
    }

    @Test
    fun orderedQualitiesPutH264FirstThenAlphabetical() {
        val engine = FakeEngine(online(h265, h264, h265.copy(quality = "best")))
        val c = controller(engine)

        c.resolveAndPlay("https://www.twitch.tv/x")

        assertEquals(listOf("h264_720", "best", "h265_1080"), c.orderedQualities().map { it.quality })
    }

    @Test
    fun offlineStreamDoesNotTouchThePlayer() {
        val engine = FakeEngine(
            EngineResult(status = "offline", author = "lofigirl"),
        )
        val player = FakePlayer()
        val c = controller(engine, player)

        c.resolveAndPlay("https://www.twitch.tv/lofigirl")

        assertEquals("Stream is offline (lofigirl)", c.status)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun structuredErrorSurfacesInState() {
        val engine = FakeEngine(
            EngineResult(
                status = "error",
                error = "no_plugin",
                message = "no plugin for host",
                errorDetails = com.snowballons.tukiwatch.ErrorDetails(
                    type = "no_plugin",
                    error = "No plugin",
                    message = "no plugin for host",
                ),
            ),
        )
        val c = controller(engine)

        c.resolveAndPlay("https://example.com/stream")

        assertTrue(c.status.startsWith("Error no_plugin"))
        assertNotNull(c.errorDetails)
        assertNull(c.selected)
    }

    @Test
    fun freshCachePlaysImmediatelyAndRefreshesSilently() {
        val engine = FakeEngine(online(h264))
        val player = FakePlayer()
        val c = controller(engine, player)

        c.resolveAndPlay("https://www.twitch.tv/x")
        c.resolveAndPlay("https://www.twitch.tv/x")

        // First resolve + the cache-hit silent refresh; the cache hit itself
        // does not wait on the engine.
        assertEquals(2, engine.resolveCalls)
        assertEquals(2, player.played.size)
        assertEquals("Playing from cache (1 qualities)", c.status)
    }

    @Test
    fun staleCacheRefreshesBeforePlaying() {
        var now = 0L
        val engine = FakeEngine(online(h264))
        val player = FakePlayer()
        val c = controller(engine, player, now = { now })

        c.resolveAndPlay("https://www.twitch.tv/x")
        now = StreamController.CACHE_TTL_MS + 1
        c.resolveAndPlay("https://www.twitch.tv/x")

        assertEquals(2, engine.resolveCalls)
        assertEquals("Playing h264_720 (HLS)", c.status)
    }

    @Test
    fun backgroundPauseOnlyResumesWhenItPaused() {
        val player = FakePlayer()
        val c = controller(FakeEngine(online(h264)), player)
        c.resolveAndPlay("https://www.twitch.tv/x")

        c.onPauseBackground()
        assertEquals("Paused (background)", c.status)
        assertFalse(player.playWhenReady)

        c.onResumeForeground()
        assertEquals("Playing h264_720 (HLS)", c.status)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun manualStopStaysStoppedAfterResume() {
        val player = FakePlayer()
        val c = controller(FakeEngine(online(h264)), player)
        c.resolveAndPlay("https://www.twitch.tv/x")

        c.stop()
        c.onResumeForeground()

        assertEquals("Stopped", c.status)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun codecFailureFallsBackToH264Once() {
        val engine = FakeEngine(online(h264, h265))
        val player = FakePlayer()
        val c = controller(engine, player)
        c.resolveAndPlay("https://www.twitch.tv/x")
        c.play(h265)

        c.onPlayerFailure(PlayerFailure.CODEC_UNSUPPORTED, "DECODER_INIT_FAILED: hevc")

        assertEquals("h264_720", c.selected?.quality)
        assertTrue(c.status.startsWith("Codec unsupported here"))
        assertNull(c.playerError)
    }

    @Test
    fun codecFailureWithoutAlternativeReportsThePlayerError() {
        val c = controller(FakeEngine(online(h264)))
        c.resolveAndPlay("https://www.twitch.tv/x")

        c.onPlayerFailure(PlayerFailure.CODEC_UNSUPPORTED, "DECODER_INIT_FAILED: hevc")

        assertEquals("Player error DECODER_INIT_FAILED: hevc", c.playerError)
    }

    @Test
    fun behindLiveWindowSeeksThenReResolvesAfterRetries() {
        val engine = FakeEngine(online(h264))
        val player = FakePlayer()
        val c = controller(engine, player)
        c.resolveAndPlay("https://www.twitch.tv/x")

        repeat(3) {
            c.onPlayerFailure(PlayerFailure.BEHIND_LIVE_WINDOW, "behind")
        }
        assertEquals(3, player.seekToLiveEdgeCalls)
        assertTrue(c.status.startsWith("Reconnected to live edge"))

        c.onPlayerFailure(PlayerFailure.BEHIND_LIVE_WINDOW, "behind")
        assertEquals(2, engine.resolveCalls)
        assertEquals("Playing h264_720 (HLS)", c.status)
    }

    private fun online(vararg items: StreamItem) =
        EngineResult(
            status = "online",
            streams = items.toList(),
            timings = com.snowballons.tukiwatch.EngineTimings(
                totalMs = 12.0,
                streamsMs = 3.0,
            ),
        )

    private class FakeEngine(var result: EngineResult) : StreamEngine {
        var resolveCalls = 0

        override suspend fun listStreams(
            url: String,
            optionsJson: String,
            wanted: String,
        ): EngineResult = result

        override suspend fun resolveDetails(
            url: String,
            optionsJson: String,
            wanted: String,
        ): EngineResult {
            resolveCalls++
            return result
        }

        override suspend fun checkStatus(url: String, optionsJson: String): StreamStatus =
            StreamStatus(url = url)

        override fun version(): String = "fake"
    }

    private class FakePlayer : StreamPlayer {
        val played = mutableListOf<StreamItem>()
        var playWhenReady = false
        var seekToLiveEdgeCalls = 0
        private var prepared = false

        override fun play(item: StreamItem) {
            played += item
            prepared = true
            playWhenReady = true
        }

        override fun stop() {
            playWhenReady = false
            prepared = false
        }

        override fun pause(): Boolean {
            if (!playWhenReady || !prepared) return false
            playWhenReady = false
            return true
        }

        override fun resume(): Boolean {
            if (!prepared) return false
            playWhenReady = true
            return true
        }

        override fun seekToLiveEdge() {
            seekToLiveEdgeCalls++
        }
    }
}

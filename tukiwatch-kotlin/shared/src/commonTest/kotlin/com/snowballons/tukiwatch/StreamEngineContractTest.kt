package com.snowballons.tukiwatch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamEngineContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun engineResult_roundTrip_preserves_metadata_and_error_details() {
        val original = EngineResult(
            status = "online",
            error = null,
            message = null,
            streams = listOf(
                StreamItem(
                    quality = "720p30",
                    alias = false,
                    kind = "TwitchHLSStream",
                    playableDirect = true,
                    url = "https://example.com/720p.m3u8",
                    headers = mapOf("User-Agent" to "Mozilla/5.0")
                )
            ),
            wanted = "all",
            qualityIndex = listOf("720p30", "best"),
            originalUrl = "https://www.twitch.tv/lofigirl",
            platform = "twitch",
            title = "lofi hip hop radio",
            author = "LofiGirl",
            thumbnail = "https://ui-avatars.com/api/?name=LofiGirl&background=9146FF&color=FFFFFF&format=png",
            category = "Music",
            streamId = "318064971747",
            viewerCount = 1234,
            streamTypes = listOf("TWITCHHLS"),
            bestQuality = "https://example.com/1080p.m3u8",
            errorDetails = null
        )

        val encoded = json.encodeToString(EngineResult.serializer(), original)
        val decoded = json.decodeFromString(EngineResult.serializer(), encoded)

        assertEquals(original.status, decoded.status)
        assertEquals(original.title, decoded.title)
        assertEquals(original.author, decoded.author)
        assertEquals(original.category, decoded.category)
        assertEquals(original.streamId, decoded.streamId)
        assertEquals(original.viewerCount, decoded.viewerCount)
        assertEquals(original.streamTypes, decoded.streamTypes)
        assertEquals(original.bestQuality, decoded.bestQuality)
        assertEquals(original.platform, decoded.platform)
        assertEquals(original.streams.size, decoded.streams.size)
        assertEquals(original.streams[0].url, decoded.streams[0].url)
        assertEquals(original.streams[0].headers["User-Agent"], decoded.streams[0].headers["User-Agent"])
    }

    @Test
    fun engineResult_error_roundTrip_preserves_error_details() {
        val original = EngineResult(
            status = "error",
            error = "browser_required",
            message = "Kick requires a browser to play.",
            streams = emptyList(),
            originalUrl = "https://kick.com/x",
            platform = "kick",
            title = null,
            author = null,
            thumbnail = null,
            category = null,
            streamId = null,
            viewerCount = null,
            streamTypes = null,
            bestQuality = null,
            errorDetails = ErrorDetails(
                type = "browser_required",
                error = "Kick requires a browser to play.",
                message = "Browser automation required for kick: https://kick.com/x",
                reason = "Platform uses anti-bot protection",
                alternative = "Open this stream in the official app or website."
            )
        )

        val encoded = json.encodeToString(EngineResult.serializer(), original)
        val decoded = json.decodeFromString(EngineResult.serializer(), encoded)

        assertEquals("browser_required", decoded.error)
        assertEquals("kick", decoded.platform)
        assertEquals("browser_required", decoded.errorDetails?.type)
        assertEquals("Platform uses anti-bot protection", decoded.errorDetails?.reason)
        assertEquals("Open this stream in the official app or website.", decoded.errorDetails?.alternative)
    }

    @Test
    fun streamStatus_roundTrip_preserves_liveness() {
        val original = StreamStatus(
            url = "https://example.com/nope",
            status = "error",
            title = null,
            author = null,
            thumbnail = null,
            category = null,
            streamId = null,
            platform = "example",
            viewerCount = null,
            error = "No plugin available for this URL",
            errorDetails = ErrorDetails(
                type = "no_plugin",
                error = "No plugin available for this URL",
                message = "No plugin available for URL: https://example.com/nope",
                reason = "Streamlink does not support this domain",
                alternative = "Try opening in official app"
            )
        )

        val encoded = json.encodeToString(StreamStatus.serializer(), original)
        val decoded = json.decodeFromString(StreamStatus.serializer(), encoded)

        assertEquals("error", decoded.status)
        assertEquals("example", decoded.platform)
        assertEquals("No plugin available for this URL", decoded.error)
        assertEquals("no_plugin", decoded.errorDetails?.type)
    }

    @Test
    fun directPlayable_filters_non_direct() {
        val result = EngineResult(
            streams = listOf(
                StreamItem(quality = "720p", playableDirect = true, url = "u1"),
                StreamItem(quality = "1080p", playableDirect = false, url = null),
                StreamItem(quality = "480p", playableDirect = true, url = null)
            )
        )
        assertEquals(1, result.directPlayable.size)
        assertEquals("720p", result.directPlayable[0].quality)
    }

    @Test
    fun defaultNulls_do_not_break_decode() {
        val encoded = """{"status":"offline","original_url":"https://x","platform":"twitch","author":"A","title":"Offline","thumbnail":"t"}"""
        val decoded = json.decodeFromString(EngineResult.serializer(), encoded)
        assertEquals("offline", decoded.status)
        assertNull(decoded.error)
        assertTrue(decoded.streams.isEmpty())
        assertEquals("twitch", decoded.platform)
    }
}
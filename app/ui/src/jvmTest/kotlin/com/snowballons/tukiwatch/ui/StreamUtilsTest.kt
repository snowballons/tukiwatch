package com.snowballons.tukiwatch.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamUtilsTest {

    @Test
    fun derivesTikTokHandleWithLivePath() {
        val name = StreamUtils.deriveStreamerName("https://www.tiktok.com/@cukurayanairobibackup/live")
        assertEquals("cukurayanairobibackup", name)
    }

    @Test
    fun derivesTikTokHandleWithoutLive() {
        val name = StreamUtils.deriveStreamerName("https://www.tiktok.com/@creator")
        assertEquals("creator", name)
    }

    @Test
    fun derivesYouTubeHandle() {
        val name = StreamUtils.deriveStreamerName("https://www.youtube.com/@mkbhd/live")
        assertEquals("mkbhd", name)
    }

    @Test
    fun derivesYouTubeVideoId() {
        val name = StreamUtils.deriveStreamerName("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", name)
    }

    @Test
    fun derivesTwitchChannel() {
        val name = StreamUtils.deriveStreamerName("https://www.twitch.tv/ninja")
        assertEquals("ninja", name)
    }

    @Test
    fun derivesKickChannel() {
        val name = StreamUtils.deriveStreamerName("https://kick.com/xqc")
        assertEquals("xqc", name)
    }

    @Test
    fun detectsPlatforms() {
        assertEquals("TikTok", StreamUtils.detectPlatform("https://www.tiktok.com/@user/live"))
        assertEquals("Twitch", StreamUtils.detectPlatform("https://www.twitch.tv/ninja"))
        assertEquals("YouTube", StreamUtils.detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("Kick", StreamUtils.detectPlatform("https://kick.com/xqc"))
    }
}

package com.snowballons.tukiwatch.ui

/**
 * Common utilities for stream URL parsing, channel name derivation,
 * and platform identification across Compose Multiplatform UI.
 */
object StreamUtils {

    private val ACTION_SEGMENTS = setOf(
        "live", "stream", "streams", "watch", "video", "videos", "channel", "user", "c"
    )

    private val YT_V_REGEX = Regex("""[?&]v=([a-zA-Z0-9_-]{11})""")
    private val YOUTU_BE_REGEX = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""")

    /**
     * Derives a clean display name / streamer handle from a channel or stream URL.
     *
     * Handles:
     * - TikTok handles: `https://www.tiktok.com/@cukurayanairobibackup/live` -> `cukurayanairobibackup`
     * - YouTube handles: `https://www.youtube.com/@mkbhd/live` -> `mkbhd`
     * - YouTube videos: `https://www.youtube.com/watch?v=dQw4w9WgXcQ` -> `dQw4w9WgXcQ`
     * - Twitch/Kick channels: `https://www.twitch.tv/ninja` -> `ninja`
     * - Strips trailing action paths like `/live`, query strings, and leading `@`.
     */
    fun deriveStreamerName(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return "Stream"

        // YouTube video query or short-link
        if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
            val vMatch = YT_V_REGEX.find(trimmed)
            if (vMatch != null) return vMatch.groupValues[1]
            val shortMatch = YOUTU_BE_REGEX.find(trimmed)
            if (shortMatch != null) return shortMatch.groupValues[1]
        }

        val clean = trimmed
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')

        val withoutProto = clean.substringAfter("://", clean)
        val segments = withoutProto.split('/')
            .drop(1) // drop hostname
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) {
            return withoutProto.substringBefore('/')
        }

        // Look for explicit @handle segment first (e.g. TikTok, YouTube)
        val handleSegment = segments.firstOrNull { it.startsWith('@') && it.length > 1 }
        if (handleSegment != null) {
            return handleSegment.removePrefix("@")
        }

        // Drop trailing non-identifying segments like /live, /watch, /videos
        val candidates = segments.filterNot { it.lowercase() in ACTION_SEGMENTS }
        val chosen = candidates.lastOrNull() ?: segments.first()
        return chosen.removePrefix("@").ifBlank { "Stream" }
    }

    /**
     * Detects well-known streaming platforms from URL hostname.
     */
    fun detectPlatform(url: String): String? {
        val lower = url.lowercase()
        return when {
            "twitch.tv" in lower -> "Twitch"
            "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
            "kick.com" in lower -> "Kick"
            "tiktok.com" in lower -> "TikTok"
            "afreecatv.com" in lower || "sooplive.co.kr" in lower -> "SOOP"
            "chzzk.naver.com" in lower -> "CHZZK"
            "bilibili.com" in lower -> "Bilibili"
            "facebook.com" in lower || "fb.gg" in lower -> "Facebook"
            "instagram.com" in lower -> "Instagram"
            else -> null
        }
    }
}

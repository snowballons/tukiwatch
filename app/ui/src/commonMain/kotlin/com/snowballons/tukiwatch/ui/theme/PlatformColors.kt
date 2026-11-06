package com.snowballons.tukiwatch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Streaming-platform brand colours. These are semantic, not themed: Twitch is
 * purple and YouTube is red in every palette, so they live outside
 * [TukiPalette] on purpose.
 */
object PlatformColors {
    val Twitch = Color(0xFF9146FF)
    val YouTube = Color(0xFFFF0000)
    val Kick = Color(0xFF53FC18)
    val TikTok = Color(0xFFEE1D52)
    val Facebook = Color(0xFF1877F2)
    val Instagram = Color(0xFFE4405F)
    val Unknown = Color(0xFFB18CFF)

    fun platformColor(platform: String?): Color = when (platform?.lowercase()) {
        "twitch" -> Twitch
        "youtube", "youtu-be" -> YouTube
        "kick" -> Kick
        "tiktok" -> TikTok
        "facebook" -> Facebook
        "instagram" -> Instagram
        else -> Unknown
    }
}

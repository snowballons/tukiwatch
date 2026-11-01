package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One palette for every surface. Android follows the system light/dark setting;
 * the desktop harness pins [TukiWatchTheme] to dark because it is a tool window.
 */
object TukiWatchPalette {
    val Purple = Color(0xFF9146FF)
    val PurpleSoft = Color(0xFFB18CFF)
    val Live = Color(0xFF2ED573)
    val LiveOnLight = Color(0xFF0B7A3E)
    val Error = Color(0xFFFF5C5C)

    val SurfaceDark = Color(0xFF18181B)
    val SurfaceRaised = Color(0xFF26262C)
    val BackgroundDark = Color(0xFF0E0E10)
    val TextOnDark = Color(0xFFEFEFF1)
    val TextMutedOnDark = Color(0xFFB5B5BE)

    // Platform brand colors
    val Twitch = Color(0xFF9146FF)
    val YouTube = Color(0xFFFF0000)
    val Kick = Color(0xFF53FC18)
    val TikTok = Color(0xFFEE1D52)
    val Facebook = Color(0xFF1877F2)
    val Instagram = Color(0xFFE4405F)

    fun platformColor(platform: String?): Color = when (platform?.lowercase()) {
        "twitch" -> Twitch
        "youtube", "youtu-be" -> YouTube
        "kick" -> Kick
        "tiktok" -> TikTok
        "facebook" -> Facebook
        "instagram" -> Instagram
        else -> PurpleSoft
    }
}

private val LightColors = lightColorScheme(
    primary = TukiWatchPalette.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2E0066),
    secondary = TukiWatchPalette.PurpleSoft,
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFEEEEF2),
    onSurfaceVariant = Color(0xFF5A5A66),
    error = TukiWatchPalette.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = TukiWatchPalette.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF381B73),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = TukiWatchPalette.PurpleSoft,
    onSecondary = Color.White,
    background = TukiWatchPalette.BackgroundDark,
    onBackground = TukiWatchPalette.TextOnDark,
    surface = TukiWatchPalette.SurfaceDark,
    onSurface = TukiWatchPalette.TextOnDark,
    surfaceVariant = TukiWatchPalette.SurfaceRaised,
    onSurfaceVariant = TukiWatchPalette.TextMutedOnDark,
    error = TukiWatchPalette.Error,
    onError = Color.White,
    errorContainer = Color(0xFF3A1418),
    onErrorContainer = Color(0xFFFFD5D8),
)

@Composable
fun TukiWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

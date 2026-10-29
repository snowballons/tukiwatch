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
}

private val LightColors = lightColorScheme(
    primary = TukiWatchPalette.Purple,
    onPrimary = Color.White,
    secondary = TukiWatchPalette.PurpleSoft,
    error = TukiWatchPalette.Error,
)

private val DarkColors = darkColorScheme(
    primary = TukiWatchPalette.Purple,
    onPrimary = Color.White,
    secondary = TukiWatchPalette.PurpleSoft,
    background = TukiWatchPalette.BackgroundDark,
    onBackground = TukiWatchPalette.TextOnDark,
    surface = TukiWatchPalette.SurfaceDark,
    onSurface = TukiWatchPalette.TextOnDark,
    surfaceVariant = TukiWatchPalette.SurfaceRaised,
    onSurfaceVariant = TukiWatchPalette.TextMutedOnDark,
    error = TukiWatchPalette.Error,
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

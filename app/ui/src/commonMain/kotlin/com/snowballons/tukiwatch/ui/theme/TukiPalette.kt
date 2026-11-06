package com.snowballons.tukiwatch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A theme is a filled-out contract, not a colour scheme: the app names a role
 * and every palette supplies all of them. Swapping the palette is therefore
 * total rather than cosmetic.
 *
 * Platform brand colours (Twitch, YouTube, …) are deliberately absent — they
 * are semantic, not themed. See [PlatformColors].
 */
@Immutable
data class TukiPalette(
    val dark: Boolean,
    // grounds
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    // ink
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    // accent
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    // semantic
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    // live / status
    val live: Color,
    val onLive: Color,
    val liveWash: Color,
)

val LocalTukiPalette = staticCompositionLocalOf { TukiWatchDark }

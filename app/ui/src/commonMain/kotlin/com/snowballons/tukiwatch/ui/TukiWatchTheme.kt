package com.snowballons.tukiwatch.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import com.snowballons.tukiwatch.ui.theme.TukiPalette
import com.snowballons.tukiwatch.ui.theme.TukiWatchDark

/** Read by every haptic call site so one setting silences the whole app. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Bridges a palette into the Material3 roles the screens read. */
fun TukiPalette.toColorScheme(): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
        )
    }

/**
 * Applies a [TukiPalette] to the tree. The palette is resolved by the caller
 * (see `tukiPaletteFor`) so the same object can also drive system-bar and
 * splash colours; screens read roles via `MaterialTheme.colorScheme` or, for
 * the extras Material has no slot for, `LocalTukiPalette.current`.
 */
@Composable
fun TukiWatchTheme(
    palette: TukiPalette = TukiWatchDark,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTukiPalette provides palette,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}

package com.snowballons.tukiwatch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * TukiWatch's own identity: the Twitch-purple dark scheme the app shipped
 * with, expressed as roles. The default, and the pair `"system"` resolves to.
 */
val TukiWatchDark = TukiPalette(
    dark = true,
    background = Color(0xFF0E0E10),
    surface = Color(0xFF18181B),
    surfaceRaised = Color(0xFF26262C),
    outline = Color(0xFF3F3F48),
    onBackground = Color(0xFFEFEFF1),
    onSurface = Color(0xFFEFEFF1),
    onSurfaceVariant = Color(0xFFB5B5BE),
    primary = Color(0xFF9146FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF381B73),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = Color(0xFFB18CFF),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A1418),
    onErrorContainer = Color(0xFFFFD5D8),
    live = Color(0xFF2ED573),
    onLive = Color(0xFFFFFFFF),
    liveWash = Color(0x262ED573),
)

val TukiWatchLight = TukiPalette(
    dark = false,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEEEEF2),
    outline = Color(0xFFC9C9D2),
    onBackground = Color(0xFF1A1A1E),
    onSurface = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFF5A5A66),
    primary = Color(0xFF9146FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2E0066),
    secondary = Color(0xFFB18CFF),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828),
    live = Color(0xFF0B7A3E),
    onLive = Color(0xFFFFFFFF),
    liveWash = Color(0x260B7A3E),
)

/** Catppuccin Mocha — mauve accent, teal secondary, green live. */
val CatppuccinMocha = TukiPalette(
    dark = true,
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF181825),
    surfaceRaised = Color(0xFF313244),
    outline = Color(0xFF6C7086),
    onBackground = Color(0xFFCDD6F4),
    onSurface = Color(0xFFCDD6F4),
    onSurfaceVariant = Color(0xFFA6ADC8),
    primary = Color(0xFFCBA6F7),
    onPrimary = Color(0xFF1E1E2E),
    primaryContainer = Color(0xFF45475A),
    onPrimaryContainer = Color(0xFFCDD6F4),
    secondary = Color(0xFF94E2D5),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF1E1E2E),
    errorContainer = Color(0xFF3B1F2A),
    onErrorContainer = Color(0xFFF5C2D0),
    live = Color(0xFFA6E3A1),
    onLive = Color(0xFF1E1E2E),
    liveWash = Color(0x26A6E3A1),
)

/** Catppuccin Latte — the same scheme on paper. */
val CatppuccinLatte = TukiPalette(
    dark = false,
    background = Color(0xFFEFF1F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE6E9EF),
    outline = Color(0xFF9CA0B0),
    onBackground = Color(0xFF4C4F69),
    onSurface = Color(0xFF4C4F69),
    onSurfaceVariant = Color(0xFF6C6F85),
    primary = Color(0xFF8839EF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4D4FB),
    onPrimaryContainer = Color(0xFF3B1D6E),
    secondary = Color(0xFF179299),
    error = Color(0xFFD20F39),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D7DE),
    onErrorContainer = Color(0xFF9B0929),
    live = Color(0xFF40A02B),
    onLive = Color(0xFFFFFFFF),
    liveWash = Color(0x2640A02B),
)

/** Nord — frost-blue accent, aurora green live. */
val Nord = TukiPalette(
    dark = true,
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    surfaceRaised = Color(0xFF434C5E),
    outline = Color(0xFF4C566A),
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFE5E9F0),
    onSurfaceVariant = Color(0xFFD8DEE9),
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF5E81AC),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF81A1C1),
    error = Color(0xFFBF616A),
    onError = Color(0xFF2E3440),
    errorContainer = Color(0xFF4A2A2E),
    onErrorContainer = Color(0xFFF3C0C4),
    live = Color(0xFFA3BE8C),
    onLive = Color(0xFF2E3440),
    liveWash = Color(0x26A3BE8C),
)

/** Tokyo Night — night-blue accent, purple secondary, green live. */
val TokyoNight = TukiPalette(
    dark = true,
    background = Color(0xFF1A1B26),
    surface = Color(0xFF16161E),
    surfaceRaised = Color(0xFF292E42),
    outline = Color(0xFF414868),
    onBackground = Color(0xFFC0CAF5),
    onSurface = Color(0xFFC0CAF5),
    onSurfaceVariant = Color(0xFF9AA5CE),
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF24283B),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7),
    error = Color(0xFFF7768E),
    onError = Color(0xFF1A1B26),
    errorContainer = Color(0xFF3A2230),
    onErrorContainer = Color(0xFFF7B8C6),
    live = Color(0xFF9ECE6A),
    onLive = Color(0xFF1A1B26),
    liveWash = Color(0x269ECE6A),
)

/** Gruvbox Dark — warm orange accent, aqua secondary, green live. */
val GruvboxDark = TukiPalette(
    dark = true,
    background = Color(0xFF282828),
    surface = Color(0xFF3C3836),
    surfaceRaised = Color(0xFF504945),
    outline = Color(0xFF665C54),
    onBackground = Color(0xFFFBF1C7),
    onSurface = Color(0xFFEBDBB2),
    onSurfaceVariant = Color(0xFFBDAE93),
    primary = Color(0xFFFE8019),
    onPrimary = Color(0xFF282828),
    primaryContainer = Color(0xFF504945),
    onPrimaryContainer = Color(0xFFFBF1C7),
    secondary = Color(0xFF8EC07C),
    error = Color(0xFFFB4934),
    onError = Color(0xFF282828),
    errorContainer = Color(0xFF3F2626),
    onErrorContainer = Color(0xFFFBC2B8),
    live = Color(0xFFB8BB26),
    onLive = Color(0xFF282828),
    liveWash = Color(0x26B8BB26),
)

/** Rosé Pine — iris accent, foam secondary, foam live. */
val RosePine = TukiPalette(
    dark = true,
    background = Color(0xFF191724),
    surface = Color(0xFF1F1D2E),
    surfaceRaised = Color(0xFF26233A),
    outline = Color(0xFF6E6A86),
    onBackground = Color(0xFFE0DEF4),
    onSurface = Color(0xFFE0DEF4),
    onSurfaceVariant = Color(0xFF908CAA),
    primary = Color(0xFFC4A7E7),
    onPrimary = Color(0xFF191724),
    primaryContainer = Color(0xFF26233A),
    onPrimaryContainer = Color(0xFFE0DEF4),
    secondary = Color(0xFF9CCFD8),
    error = Color(0xFFEB6F92),
    onError = Color(0xFF191724),
    errorContainer = Color(0xFF3A2230),
    onErrorContainer = Color(0xFFF5C0CE),
    live = Color(0xFF9CCFD8),
    onLive = Color(0xFF191724),
    liveWash = Color(0x269CCFD8),
)

/** Rosé Pine Dawn — Rosé Pine on paper. */
val RosePineDawn = TukiPalette(
    dark = false,
    background = Color(0xFFFAF4ED),
    surface = Color(0xFFFFFAF3),
    surfaceRaised = Color(0xFFF2E9E1),
    outline = Color(0xFF9893A5),
    onBackground = Color(0xFF575279),
    onSurface = Color(0xFF575279),
    onSurfaceVariant = Color(0xFF797593),
    primary = Color(0xFF907AA9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DEF0),
    onPrimaryContainer = Color(0xFF4A3B66),
    secondary = Color(0xFF56949F),
    error = Color(0xFFB4637A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6E0E6),
    onErrorContainer = Color(0xFF8C3A50),
    live = Color(0xFF286983),
    onLive = Color(0xFFFFFFFF),
    liveWash = Color(0x26286983),
)

/** One selectable entry in the theme picker. */
data class ThemeOption(
    val key: String,
    val label: String,
    val palette: TukiPalette,
) {
    val dark: Boolean get() = palette.dark
}

/** Picker order: TukiWatch first, then the curated third-party schemes. */
val TukiThemes: List<ThemeOption> = listOf(
    ThemeOption("tukiwatch-dark", "TukiWatch Dark", TukiWatchDark),
    ThemeOption("tukiwatch-light", "TukiWatch Light", TukiWatchLight),
    ThemeOption("catppuccin-mocha", "Catppuccin Mocha", CatppuccinMocha),
    ThemeOption("catppuccin-latte", "Catppuccin Latte", CatppuccinLatte),
    ThemeOption("nord", "Nord", Nord),
    ThemeOption("tokyo-night", "Tokyo Night", TokyoNight),
    ThemeOption("gruvbox-dark", "Gruvbox Dark", GruvboxDark),
    ThemeOption("rose-pine", "Rosé Pine", RosePine),
    ThemeOption("rose-pine-dawn", "Rosé Pine Dawn", RosePineDawn),
)

private val themesByKey: Map<String, TukiPalette> = TukiThemes.associate { it.key to it.palette }

/**
 * Resolves a stored theme id to a palette. `"system"` follows the device
 * light/dark setting; an unknown id falls back to TukiWatch rather than
 * throwing, so a removed theme cannot brick the app.
 */
fun tukiPaletteFor(key: String, systemDark: Boolean): TukiPalette =
    when (key) {
        "system" -> if (systemDark) TukiWatchDark else TukiWatchLight
        else -> themesByKey[key] ?: if (systemDark) TukiWatchDark else TukiWatchLight
    }

package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import com.snowballons.tukiwatch.prefs.Prefs
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import com.snowballons.tukiwatch.ui.theme.ThemeOption
import com.snowballons.tukiwatch.ui.theme.TukiShapes
import com.snowballons.tukiwatch.ui.theme.TukiThemes

/**
 * User-facing preferences. Every control applies to [prefs] immediately; there
 * is no Save button, so the screen never owns pending state of its own.
 */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    onThemeChange: (String) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onPreferredQualityChange: (String) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckIntervalChange: (Int) -> Unit,
    onUpdateRemindersChange: (Boolean) -> Unit,
    onShowOfflineChange: (Boolean) -> Unit,
    onMyListSortChange: (String) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearCache: () -> Unit,
    onReplayIntro: () -> Unit,
    onSaveTwitchCredentials: (clientId: String?, accessToken: String?) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
    onOpenGitHub: () -> Unit,
    onReportProblem: () -> Unit,
    onSupport: () -> Unit,
    appVersionName: String,
    appVersionCode: Int,
    streams: List<LiveStream> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header()

        // Stats Dashboard Card
        TrackingStatsCard(streams = streams)

        // ---- Appearance ----
        SettingsGroup("Appearance") {
            ThemeDropdown(
                currentThemeId = prefs.themeId,
                onThemeChange = onThemeChange,
            )
        }

        // ---- Playback ----
        SettingsGroup("Playback") {
            SettingsSwitchRow(
                title = "Background playback",
                subtitle = "Keep the stream playing when you leave the app",
                badge = "New",
                checked = prefs.backgroundPlayback,
                onCheckedChange = onBackgroundPlaybackChange,
                divider = false,
            )
            SettingsSwitchRow(
                title = "Auto picture-in-picture",
                subtitle = "Float the player when you switch apps",
                checked = prefs.autoPip,
                onCheckedChange = onAutoPipChange,
            )
            SettingsSegmentedRow(
                title = "Preferred quality",
                subtitle = "What the player picks first when a stream loads",
            ) {
                Segmented(
                    options = listOf(
                        Prefs.QUALITY_AUTO to "Auto",
                        "source" to "Source",
                        "1080" to "1080p",
                        "720" to "720p",
                        "480" to "480p",
                    ),
                    selected = prefs.preferredQuality,
                    onSelect = onPreferredQualityChange,
                )
            }
            SettingsSwitchRow(
                title = "Keep screen awake while watching",
                subtitle = "Stop the display from sleeping during playback",
                checked = prefs.keepScreenAwake,
                onCheckedChange = onKeepScreenAwakeChange,
            )
        }

        // ---- Live updates ----
        SettingsGroup("Live updates") {
            SettingsSwitchRow(
                title = "Auto-check favorites",
                subtitle = "Refresh live status for My List while the app is open",
                checked = prefs.autoCheckFavorites,
                onCheckedChange = onAutoCheckChange,
                divider = false,
            )
            SettingsSegmentedRow(
                title = "Check interval",
                subtitle = "How often favorites are refreshed",
            ) {
                Segmented(
                    options = listOf(2 to "2 min", 5 to "5 min", 10 to "10 min", 0 to "Manual"),
                    selected = prefs.checkIntervalMinutes,
                    onSelect = onCheckIntervalChange,
                )
            }
        }

        // ---- Notifications ----
        SettingsGroup("Notifications") {
            SettingsSwitchRow(
                title = "Update reminders",
                subtitle = "Tell me when a new version is available",
                checked = prefs.updateReminders,
                onCheckedChange = onUpdateRemindersChange,
                divider = false,
            )
        }

        // ---- My List ----
        SettingsGroup("My List") {
            SettingsSwitchRow(
                title = "Show offline channels",
                subtitle = "List favorite channels even when they are not live",
                checked = prefs.showOfflineChannels,
                onCheckedChange = onShowOfflineChange,
                divider = false,
            )
            SettingsSegmentedRow(title = "Sort order") {
                Segmented(
                    options = listOf(
                        Prefs.SORT_LIVE to "Live first",
                        Prefs.SORT_RECENT to "Recent",
                        Prefs.SORT_NAME to "Name",
                    ),
                    selected = prefs.myListSort,
                    onSelect = onMyListSortChange,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text("Export JSON")
                }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                    Text("Import JSON")
                }
            }
        }

        // ---- Interaction ----
        SettingsGroup("Interaction") {
            SettingsSwitchRow(
                title = "Haptic feedback",
                subtitle = "A light buzz on taps, refreshes and long-presses",
                checked = prefs.hapticsEnabled,
                onCheckedChange = onHapticsChange,
                divider = false,
            )
            SettingsSwitchRow(
                title = "Reduce motion",
                subtitle = "Prefer simple fades over animated transitions",
                checked = prefs.reduceMotion,
                onCheckedChange = onReduceMotionChange,
            )
        }

        // ---- Twitch ----
        var showTwitchDialog by remember { mutableStateOf(false) }
        SettingsGroup("Twitch account · optional") {
            SettingsActionRow(
                title = "Add API credentials",
                subtitle = "Your own Client ID + token sharpens live data for Twitch " +
                    "channels. Stored on this device only.",
                value = if (prefs.hasHelixCredentials) "Connected" else "Add",
                divider = false,
                onClick = { showTwitchDialog = true },
            )
        }
        if (showTwitchDialog) {
            TwitchCredentialsDialog(
                initialClientId = prefs.helixClientId.orEmpty(),
                initialToken = prefs.helixAccessToken.orEmpty(),
                onDismiss = { showTwitchDialog = false },
                onSave = { id, token ->
                    onSaveTwitchCredentials(id, token)
                    showTwitchDialog = false
                },
            )
        }

        // ---- Data ----
        SettingsGroup("Data & storage") {
            SettingsActionRow(
                title = "Clear stream cache",
                subtitle = "Drop remembered resolutions and thumbnails",
                value = "Clear",
                divider = false,
                onClick = onClearCache,
            )
            SettingsActionRow(
                title = "Replay intro",
                subtitle = "Show the welcome tour again on next launch",
                onClick = onReplayIntro,
            )
        }

        // ---- Support ----
        SettingsGroup("Support & about") {
            Box(Modifier.padding(14.dp)) {
                SupportButton(onClick = onSupport)
            }
        }
        SettingsGroup {
            SettingsActionRow(title = "Report a problem", divider = false, onClick = onReportProblem)
            SettingsActionRow(title = "GitHub repository", onClick = onOpenGitHub)
            SettingsActionRow(title = "Privacy Policy", onClick = onOpenPrivacyPolicy)
            SettingsActionRow(title = "Terms of Service", onClick = onOpenTermsOfService)
        }

        Footer(appVersionName, appVersionCode)
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "TukiWatch · on-device, no account",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SettingsGroup(
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TukiShapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        if (divider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        BadgeChip(badge)
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        badge = badge,
        divider = divider,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSegmentedRow(
    title: String,
    subtitle: String? = null,
    divider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column {
        if (divider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(title = title, subtitle = subtitle, divider = divider, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val palette = LocalTukiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TukiShapes.medium)
            .background(palette.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(TukiShapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(primary.copy(alpha = 0.16f))
            .border(1.dp, primary.copy(alpha = 0.34f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = primary,
        )
    }
}

@Composable
private fun SupportButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TukiShapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFF5C8A), MaterialTheme.colorScheme.primary),
                ),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "♥  Support the project",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
    }
}

@Composable
private fun Footer(versionName: String, versionCode: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TukiWatch $versionName ($versionCode)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Every stream. One watchlist. · No servers, no tracking.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TrackingStatsCard(
    streams: List<LiveStream>,
    modifier: Modifier = Modifier,
) {
    val trackedCount = streams.size
    val platformsCount = streams.mapNotNull { it.platform }.distinct().size
    val liveCount = streams.count { it.status == STATUS_ONLINE }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TukiShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatColumn(label = "Tracked", value = "$trackedCount")
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
            )
            StatColumn(label = "Platforms", value = "$platformsCount")
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
            )
            StatColumn(
                label = "Live Now",
                value = "$liveCount",
                valueColor = if (liveCount > 0) LocalTukiPalette.current.live else null,
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    valueColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeSwatch(
    swatch: ThemeOption?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTukiPalette.current
    val chipBackground = swatch?.palette?.background ?: palette.surface
    val chipBorder = swatch?.palette?.outline ?: palette.outline
    val dots = swatch?.palette?.let { listOf(it.primary, it.live, it.onBackground) }
        ?: listOf(palette.primary, palette.onBackground, palette.onSurfaceVariant)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(chipBackground)
            .border(1.dp, chipBorder, RoundedCornerShape(6.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        dots.forEach { dot ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dot),
            )
        }
    }
}

@Composable
private fun ThemeDropdown(
    currentThemeId: String,
    onThemeChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalTukiPalette.current
    val currentTheme = TukiThemes.find { it.key == currentThemeId }
    val currentLabel = if (currentThemeId == "system") "Follow system" else (currentTheme?.label ?: "Follow system")
    val currentSubtitle = if (currentThemeId == "system") "matches your device" else (if (currentTheme?.dark == true) "dark" else "light")

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeSwatch(swatch = currentTheme)

            Column(Modifier.weight(1f)) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(palette.surfaceRaised)
                .border(1.dp, palette.outline.copy(alpha = 0.5f), TukiShapes.medium),
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Follow system",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (currentThemeId == "system") FontWeight.Bold else FontWeight.Medium,
                            color = if (currentThemeId == "system") palette.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "matches your device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = {
                    ThemeSwatch(swatch = null)
                },
                trailingIcon = if (currentThemeId == "system") {
                    {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary,
                        )
                    }
                } else null,
                onClick = {
                    onThemeChange("system")
                    expanded = false
                },
            )

            TukiThemes.forEach { theme ->
                val isSelected = currentThemeId == theme.key
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) palette.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (theme.dark) "dark" else "light",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        ThemeSwatch(swatch = theme)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary,
                            )
                        }
                    } else null,
                    onClick = {
                        onThemeChange(theme.key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TwitchCredentialsDialog(
    initialClientId: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (clientId: String?, accessToken: String?) -> Unit,
) {
    var clientId by remember { mutableStateOf(initialClientId) }
    var token by remember { mutableStateOf(initialToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Twitch API credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Optional. Used only to improve live data for Twitch " +
                        "channels, and kept on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Access token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(clientId.trim(), token.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

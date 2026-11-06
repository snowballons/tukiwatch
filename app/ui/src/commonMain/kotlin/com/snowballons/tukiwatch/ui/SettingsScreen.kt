package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import com.snowballons.tukiwatch.ui.theme.ThemeOption
import com.snowballons.tukiwatch.ui.theme.TukiThemes

@Composable
fun SettingsScreen(
    currentThemeId: String,
    onThemeChange: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
    onOpenGitHub: () -> Unit,
    appVersionName: String,
    appVersionCode: Int,
    streams: List<LiveStream> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val trackedCount = streams.size
    val platformsCount = streams.mapNotNull { it.platform }.distinct().size
    val liveCount = streams.count { it.status == STATUS_ONLINE }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Header
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Preferences and application info",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Stats Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatColumn(label = "Tracked", value = "$trackedCount")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )
                StatColumn(label = "Platforms", value = "$platformsCount")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )
                StatColumn(
                    label = "Live Now",
                    value = "$liveCount",
                    valueColor = if (liveCount > 0) LocalTukiPalette.current.live else null
                )
            }
        }

        // Appearance — theme picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick a theme. System matches your device's dark mode setting.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                ThemeRow(
                    label = "Follow system",
                    subtitle = "matches your device",
                    swatch = null,
                    selected = currentThemeId == "system",
                    onClick = { onThemeChange("system") },
                )

                TukiThemes.forEach { theme ->
                    ThemeRow(
                        label = theme.label,
                        subtitle = if (theme.dark) "dark" else "light",
                        swatch = theme,
                        selected = currentThemeId == theme.key,
                        onClick = { onThemeChange(theme.key) },
                    )
                }
            }
        }

        // Data Management (Backups)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Data & Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Export or restore your My List channels using the standard JSON backup format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export JSON")
                    }

                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import JSON")
                    }
                }
            }
        }

        // Engine & Build Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Engine & Build",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Version", style = MaterialTheme.typography.bodyMedium)
                    Text("$appVersionName ($appVersionCode)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Streamlink", style = MaterialTheme.typography.bodyMedium)
                    Text("8.6.2", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Python Engine", style = MaterialTheme.typography.bodyMedium)
                    Text("3.11 (Chaquopy Local)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Architecture", style = MaterialTheme.typography.bodyMedium)
                    Text("On-Device Serverless", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Legal & Links
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "About & Legal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPrivacyPolicy)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privacy Policy", style = MaterialTheme.typography.bodyMedium)
                    Text("›", style = MaterialTheme.typography.titleMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenTermsOfService)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Terms of Service", style = MaterialTheme.typography.bodyMedium)
                    Text("›", style = MaterialTheme.typography.titleMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenGitHub)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GitHub Repository", style = MaterialTheme.typography.bodyMedium)
                    Text("›", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
        horizontalAlignment = Alignment.CenterHorizontally
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

/**
 * Shows a theme rather than naming it: the candidate's own background chip
 * with its accent, live and ink as swatches, plus a selected marker. A list of
 * twenty names tells you nothing; a few squares tell you what matters at a
 * glance. Pass [swatch] = null for the "follow system" row.
 */
@Composable
private fun ThemeRow(
    label: String,
    subtitle: String,
    swatch: ThemeOption?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalTukiPalette.current
    val chipBackground = swatch?.palette?.background ?: palette.surface
    val chipBorder = swatch?.palette?.outline ?: palette.outline
    val dots = swatch?.palette?.let { listOf(it.primary, it.live, it.onBackground) }
        ?: listOf(palette.primary, palette.onBackground, palette.onSurfaceVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
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

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) palette.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
            )
        }
    }
}

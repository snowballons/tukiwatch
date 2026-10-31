package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
    onOpenGitHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
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

        // Appearance
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { onThemeChange(ThemeMode.SYSTEM) },
                        label = { Text("System") },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        label = { Text("Dark") },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        label = { Text("Light") },
                    )
                }
            }
        }

        // Data Management (Backups)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Text("1.1.5 (13)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                    Text("Python", style = MaterialTheme.typography.bodyMedium)
                    Text("3.11 (Chaquopy)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Architecture", style = MaterialTheme.typography.bodyMedium)
                    Text("On-device / Serverless", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Legal & Links
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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

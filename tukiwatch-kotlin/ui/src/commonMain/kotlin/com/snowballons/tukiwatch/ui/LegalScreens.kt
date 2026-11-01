package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = TukiIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Last Updated: October 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("1. Information We Collect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "TukiWatch runs entirely on-device without a backend server. We do not operate user accounts, databases, or analytics tracking on external servers. Favorites and settings are stored locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("2. Network & Third-Party Streaming Requests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "When you resolve or play a live stream, your device connects directly to the relevant third-party platform (such as Twitch or YouTube) or content delivery network to retrieve publicly accessible stream playlists and video chunks. Those third-party platforms are governed by their respective privacy policies.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("3. Storage & Backups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Your My List channels and configuration are saved in local device storage. Exporting backups produces a JSON file stored only in the directory or cloud location you choose via the Android Storage Access Framework.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("4. Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Because no user data is collected by snowballons, your data remains under your exclusive custody on your device.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun TermsOfServiceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = TukiIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Last Updated: October 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("1. Acceptance of Terms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "By using TukiWatch (\"the App\"), you agree to these Terms of Service. If you do not agree, please do not use the App.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("2. Description of Service", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "TukiWatch is a free and open-source media player and stream aggregator client that uses on-device extraction algorithms to resolve publicly available live video streams.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("3. Acceptable Use", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "You agree to use TukiWatch solely for lawful purposes. You agree not to attempt to circumvent DRM, paywalls, or copyright protections, or violate the terms of service of any third-party streaming service.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("4. Disclaimer of Warranty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "The App is provided \"as is\" without warranty of any kind. Compatibility with third-party platforms may change at any time without notice based on upstream service changes.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

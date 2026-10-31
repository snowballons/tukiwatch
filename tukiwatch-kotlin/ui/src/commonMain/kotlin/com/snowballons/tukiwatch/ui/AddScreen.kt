package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.EngineResult

data class PlatformPreset(
    val key: String,
    val name: String,
    val urlTemplate: String,
    val placeholder: String,
)

val PRESET_PLATFORMS = listOf(
    PlatformPreset("twitch", "Twitch", "https://www.twitch.tv/{identifier}", "Enter channel name (e.g. shroud)"),
    PlatformPreset("youtube", "YouTube", "https://www.youtube.com/{identifier}", "Enter @channel or video URL"),
    PlatformPreset("tiktok", "TikTok", "https://www.tiktok.com/@{identifier}", "Enter @username or stream URL"),
    PlatformPreset("kick", "Kick", "https://kick.com/{identifier}", "Enter channel name"),
    PlatformPreset("facebook", "Facebook", "https://www.facebook.com/{identifier}", "Enter page name or video URL"),
    PlatformPreset("instagram", "Instagram", "https://www.instagram.com/{identifier}", "Enter username"),
    PlatformPreset("custom", "Custom URL", "{identifier}", "https://example.com/live/stream.m3u8"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onPreview: (String) -> Unit,
    onSaveFavorite: (streamerName: String, url: String) -> Unit,
    onPlayUrl: (String) -> Unit,
    resolving: Boolean,
    previewResult: EngineResult?,
    statusMessage: String?,
    isFavorite: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    var selectedPreset by remember { mutableStateOf(PRESET_PLATFORMS.first()) }
    var inputIdentifier by remember { mutableStateOf("") }

    val resolvedUrl = remember(selectedPreset, inputIdentifier) {
        val trimmed = inputIdentifier.trim()
        if (trimmed.isEmpty()) ""
        else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            selectedPreset.urlTemplate.replace("{identifier}", trimmed.removePrefix("@"))
        }
    }

    val derivedStreamerName = remember(inputIdentifier, previewResult, resolvedUrl) {
        previewResult?.author?.takeIf { it.isNotBlank() && it.lowercase() != "live" && it.lowercase() != "video" }
            ?: StreamUtils.deriveStreamerName(resolvedUrl)
    }

    val isSaved = remember(resolvedUrl, previewResult, isFavorite) {
        if (resolvedUrl.isBlank()) false else isFavorite(resolvedUrl)
    }

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
                text = "Add Stream",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Track a channel or test stream URL on-device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Platform selector chips
        Text(
            text = "Choose Platform",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESET_PLATFORMS.forEach { preset ->
                FilterChip(
                    selected = selectedPreset.key == preset.key,
                    onClick = { selectedPreset = preset },
                    label = { Text(preset.name) },
                )
            }
        }

        // Input field
        OutlinedTextField(
            value = inputIdentifier,
            onValueChange = { inputIdentifier = it },
            placeholder = { Text(selectedPreset.placeholder) },
            label = { Text(if (selectedPreset.key == "custom") "Stream URL" else "Channel / Identifier") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )

        if (resolvedUrl.isNotEmpty() && selectedPreset.key != "custom") {
            Text(
                text = "Resolved target: $resolvedUrl",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Preview & Resolve Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onPreview(resolvedUrl) },
                enabled = resolvedUrl.isNotBlank() && !resolving,
                modifier = Modifier.weight(1.2f),
            ) {
                if (resolving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Resolving…")
                } else {
                    Text("Preview Stream")
                }
            }

            OutlinedButton(
                onClick = { onSaveFavorite(derivedStreamerName, resolvedUrl) },
                enabled = resolvedUrl.isNotBlank() && !resolving,
                modifier = Modifier.weight(0.9f),
            ) {
                val label = if (isSaved) "★ Saved" else "+ Add"
                Text(label)
            }
        }

        // Status message if any
        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Live preview area
        previewResult?.let { result ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Preview Result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val errorDetails = result.errorDetails
            if (result.status == "error" && errorDetails != null) {
                StreamErrorCard(details = errorDetails)
            } else {
                StreamMetadataCard(result = result)
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveFavorite(derivedStreamerName, resolvedUrl)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    val label = if (isSaved) "★ Saved in My List" else "+ Save to My List"
                    Text(label)
                }

                if (result.status == "online" && result.directPlayable.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onPlayUrl(resolvedUrl) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Play Now")
                    }
                }
            }
        }
    }
}

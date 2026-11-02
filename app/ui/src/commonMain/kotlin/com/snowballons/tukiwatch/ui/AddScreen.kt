package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.EngineResult

data class PlatformPreset(
    val key: String,
    val name: String,
    val urlTemplate: String,
    val placeholder: String,
)

val FULL_PRESET_PLATFORMS = listOf(
    PlatformPreset("twitch", "Twitch", "https://www.twitch.tv/{identifier}", "Enter channel name (e.g. ninja)"),
    PlatformPreset("youtube", "YouTube", "https://www.youtube.com/{identifier}", "Enter @username, channel ID, or video path"),
    PlatformPreset("youtu-be", "YouTube (youtu.be)", "https://youtu.be/{identifier}", "Enter video ID"),
    PlatformPreset("tiktok", "TikTok", "https://www.tiktok.com/@{identifier}", "Enter @username or stream URL"),
    PlatformPreset("kick", "Kick", "https://kick.com/{identifier}", "Enter channel name"),
    PlatformPreset("facebook", "Facebook", "https://www.facebook.com/{identifier}", "Enter page name or video URL"),
    PlatformPreset("instagram", "Instagram", "https://www.instagram.com/{identifier}", "Enter username or post path"),
    PlatformPreset("bigo", "Bigo Live", "https://www.bigo.tv/{identifier}", "Enter streamer name"),
    PlatformPreset("dailymotion", "Dailymotion", "https://www.dailymotion.com/{identifier}", "Enter video or user path"),
    PlatformPreset("vimeo", "Vimeo", "https://vimeo.com/{identifier}", "Enter video ID or user path"),
    PlatformPreset("steam", "Steam Community", "https://steamcommunity.com/{identifier}", "Enter broadcast path"),
    PlatformPreset("bilibili", "Bilibili", "https://live.bilibili.com/{identifier}", "Enter channel ID or video path"),
    PlatformPreset("huya", "Huya", "https://www.huya.com/{identifier}", "Enter streamer name"),
    PlatformPreset("picarto", "Picarto", "https://picarto.tv/{identifier}", "Enter channel name"),
    PlatformPreset("trovo", "Trovo", "https://trovo.live/{identifier}", "Enter channel name"),
    PlatformPreset("vk", "VK", "https://vk.com/{identifier}", "Enter video or user path"),
    PlatformPreset("dlive", "DLive", "https://dlive.tv/{identifier}", "Enter channel name"),
    PlatformPreset("goodgame", "GoodGame", "https://goodgame.ru/{identifier}", "Enter channel name"),
    PlatformPreset("abematv", "AbemaTV", "https://abema.tv/{identifier}", "Enter channel or program path"),
    PlatformPreset("aloula", "Aloula", "https://www.aloula.sa/{identifier}", "Enter channel or video path"),
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
    var selectedPreset by remember { mutableStateOf(FULL_PRESET_PLATFORMS.first()) }
    var inputIdentifier by remember { mutableStateOf("") }
    var showPlatformPickerModal by remember { mutableStateOf(false) }

    val resolvedUrl = remember(selectedPreset, inputIdentifier) {
        val trimmed = inputIdentifier.trim()
        if (trimmed.isEmpty()) ""
        else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (selectedPreset.key == "youtube" && !trimmed.startsWith("@") && !trimmed.contains("/") && !trimmed.contains("?")) {
            selectedPreset.urlTemplate.replace("{identifier}", "@$trimmed")
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

    val platformColor = TukiWatchPalette.platformColor(selectedPreset.key)

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
                text = "Add Stream",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Select from 20 supported platforms or enter a direct URL",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Platform Selector Card (triggers modal)
        OutlinedCard(
            onClick = { showPlatformPickerModal = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(platformColor)
                    )
                    Column {
                        Text(
                            text = "Platform",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedPreset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Change (20) ▾",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Input field with Clear (X) icon
        OutlinedTextField(
            value = inputIdentifier,
            onValueChange = { inputIdentifier = it },
            placeholder = { Text(selectedPreset.placeholder) },
            label = { Text(if (selectedPreset.key == "custom") "Stream URL" else "${selectedPreset.name} Channel / Identifier") },
            trailingIcon = {
                if (inputIdentifier.isNotEmpty()) {
                    IconButton(onClick = { inputIdentifier = "" }) {
                        Icon(
                            imageVector = TukiIcons.Close,
                            contentDescription = "Clear input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        // URL Target preview card
        if (resolvedUrl.isNotEmpty() && selectedPreset.key != "custom") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Target URL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = resolvedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Preview & Save Action Buttons
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

        // Status message banner
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

            // Action Buttons after Preview
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

    // Platform Picker Modal Dialog (full 20 platforms)
    if (showPlatformPickerModal) {
        PlatformPickerModal(
            currentPreset = selectedPreset,
            onSelect = { preset ->
                selectedPreset = preset
                showPlatformPickerModal = false
            },
            onDismiss = { showPlatformPickerModal = false }
        )
    }
}

@Composable
fun PlatformPickerModal(
    currentPreset: PlatformPreset,
    onSelect: (PlatformPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPlatforms = remember(searchQuery) {
        if (searchQuery.isBlank()) FULL_PRESET_PLATFORMS
        else FULL_PRESET_PLATFORMS.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.key.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Select Platform",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "20 supported platforms & custom stream URLs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search field inside modal
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter platforms…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = TukiIcons.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Scrollable list of platforms
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredPlatforms, key = { it.key }) { preset ->
                        val isSelected = preset.key == currentPreset.key
                        val brandColor = TukiWatchPalette.platformColor(preset.key)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(preset) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(brandColor)
                                )
                                Column {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = preset.placeholder,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

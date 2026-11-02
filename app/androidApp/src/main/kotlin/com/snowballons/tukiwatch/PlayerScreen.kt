package com.snowballons.tukiwatch

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.snowballons.tukiwatch.ui.StatusPill
import com.snowballons.tukiwatch.ui.StreamErrorCard
import com.snowballons.tukiwatch.ui.StreamMetadataCard
import com.snowballons.tukiwatch.ui.StreamUtils
import com.snowballons.tukiwatch.ui.TukiWatchPalette

/**
 * Dedicated player screen for Phase 3.
 *
 * Renders 16:9 ExoPlayer surface, live stream details, quality switcher,
 * error handling, and intent launching for official apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    vm: StreamViewModel = viewModel(),
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = vm.controller
    var showQualitySheet by remember { mutableStateOf(false) }

    // Keep the screen on while the player is active — prevents dim/sleep during streams.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Fullscreen immersion state
    var isFullscreen by remember { mutableStateOf(false) }
    val activity = context as? Activity

    /** Hides system bars and locks landscape for a true fullscreen experience. */
    fun enterFullscreen() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.let { win ->
            WindowInsetsControllerCompat(win, win.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /** Restores portrait/unspecified orientation and shows system bars. */
    fun exitFullscreen() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.let { win ->
            WindowInsetsControllerCompat(win, win.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore system bars/orientation when the player screen is dismissed
    DisposableEffect(Unit) {
        onDispose { if (isFullscreen) exitFullscreen() }
    }

    // Lifecycle observer to pause ExoPlayer in background and resume on return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    vm.onPauseBackground()
                    playerView?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    playerView?.onResume()
                    vm.onResumeForeground()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val streamMetadata = controller.metadata
    val streamerName = streamMetadata?.author
        ?.takeIf { it.isNotBlank() && it.lowercase() != "live" && it.lowercase() != "video" }
        ?: StreamUtils.deriveStreamerName(controller.url)
    val platform = streamMetadata?.platform ?: StreamUtils.detectPlatform(controller.url)
    val platformColor = TukiWatchPalette.platformColor(platform)

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with Back button, streamer title, platform tag, and favorite star
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = com.snowballons.tukiwatch.ui.TukiIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = streamerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            platform?.let {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(platformColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = platformColor,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite == true) com.snowballons.tukiwatch.ui.TukiIcons.Star else com.snowballons.tukiwatch.ui.TukiIcons.StarOutline,
                    contentDescription = if (isFavorite == true) "Favorited" else "Favorite",
                    tint = if (isFavorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Fullscreen toggle — ⤢ expand / ⤡ shrink
            IconButton(onClick = {
                isFullscreen = !isFullscreen
                if (isFullscreen) enterFullscreen() else exitFullscreen()
            }) {
                Icon(
                    imageVector = if (isFullscreen) com.snowballons.tukiwatch.ui.TukiIcons.FullscreenExit else com.snowballons.tukiwatch.ui.TukiIcons.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 16:9 Video Player View Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = vm.player
                        useController = true
                    }.also { playerView = it }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Spinner overlay while resolving or switching qualities
            if (controller.extracting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Loading stream…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Scrollable stream information & quality selection
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stream title
            val displayTitle = streamMetadata?.title
                ?.takeIf { it.isNotBlank() && it.lowercase() != "live" }
                ?: if (controller.extracting) "Loading stream…" else "Live Stream"
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Author & Live status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(streamMetadata?.status ?: "live")
                Text(
                    text = streamerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Category & Viewer count
            val metaDetails = listOfNotNull(
                streamMetadata?.category?.takeIf { it.isNotBlank() },
                streamMetadata?.viewerCount?.let { "$it viewers" }
            )
            if (metaDetails.isNotEmpty()) {
                Text(
                    text = metaDetails.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Player error or structured bridge error
            controller.playerError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            controller.errorDetails?.let { details ->
                StreamErrorCard(details = details)
            }

            // Quality switcher with bottom-sheet trigger
            val directQualities = controller.orderedQualities()
            if (directQualities.isNotEmpty()) {
                val currentQualityName = controller.selected?.quality ?: directQualities.firstOrNull()?.quality ?: "Auto"

                // Clickable Stream Quality card that triggers the modal bottom sheet
                OutlinedCard(
                    onClick = { showQualitySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Stream Quality",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = currentQualityName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Change (${directQualities.size}) ▾",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Action row: Save to My List + Open in Official App
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                ) {
                    val favText = when (isFavorite) {
                        true -> "★ Saved"
                        false -> "+ Save"
                        null -> "Save"
                    }
                    Text(favText, maxLines = 1)
                }

                val targetPlatformName = platform?.replaceFirstChar { it.uppercase() } ?: "App"
                OutlinedButton(
                    onClick = {
                        PlatformLauncher.openInOfficialApp(
                            context = context,
                            platform = platform,
                            originalUrl = controller.url
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = platformColor
                    ),
                    border = BorderStroke(1.dp, platformColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "Open in $targetPlatformName ↗",
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }

    // Quality Picker Bottom-Sheet Modal
    if (showQualitySheet) {
        val directQualities = controller.orderedQualities()
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Stream Quality",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showQualitySheet = false }) {
                        Text("Done")
                    }
                }
                Text(
                    text = "Direct video stream renditions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(directQualities, key = { it.quality }) { item ->
                        val isSelected = item.quality == controller.selected?.quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    controller.play(item)
                                    showQualitySheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.quality,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.quality.contains("source", ignoreCase = true) || item.quality.contains("best", ignoreCase = true)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TukiWatchPalette.Live.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "HD / SOURCE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TukiWatchPalette.Live
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (item.url?.contains(".m3u8") == true) "HLS Live Stream" else "Direct Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Backward-compatible harness screen kept for JVM desktop testing.
 */
@Composable
fun AndroidStreamScreen(
    vm: StreamViewModel = viewModel(),
) {
    FullPlayerScreen(
        onClose = { vm.controller.stop() },
        vm = vm
    )
}

package com.snowballons.tukiwatch

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.snowballons.tukiwatch.ui.QualityChips
import com.snowballons.tukiwatch.ui.StatusPill
import com.snowballons.tukiwatch.ui.StreamErrorCard
import com.snowballons.tukiwatch.ui.StreamMetadataCard
import com.snowballons.tukiwatch.ui.TukiWatchPalette

/**
 * Dedicated player screen for Phase 3.
 *
 * Renders 16:9 ExoPlayer surface, live stream details, quality switcher,
 * error handling, and intent launching for official apps.
 */
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    vm: StreamViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = vm.controller

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
        ?: controller.url.trim().substringAfterLast("/").takeIf { it.isNotBlank() }
        ?: "Stream"
    val platform = streamMetadata?.platform
    val platformColor = TukiWatchPalette.platformColor(platform)

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with Back button and streamer title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("‹ Back", style = MaterialTheme.typography.titleMedium)
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
                Spacer(Modifier.width(8.dp))
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
            Text(
                text = streamMetadata?.title?.takeIf { it.isNotBlank() } ?: "Live Stream",
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

            // Quality switcher
            val directQualities = controller.orderedQualities()
            if (directQualities.isNotEmpty()) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                QualityChips(
                    items = directQualities,
                    selectedQuality = controller.selected?.quality,
                    onSelect = controller::play,
                )
            }

            // Action row: Open in Official App + Stop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        PlatformLauncher.openInOfficialApp(
                            context = context,
                            platform = platform,
                            originalUrl = controller.url
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open in App")
                }

                Button(
                    onClick = { controller.stop() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }

            // Diagnostic Timings (subtle)
            controller.lastTimings?.let { timings ->
                Text(
                    text = timings,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
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

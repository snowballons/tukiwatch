package com.snowballons.tukiwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.snowballons.tukiwatch.ui.QualityChips
import com.snowballons.tukiwatch.ui.StreamErrorCard
import com.snowballons.tukiwatch.ui.StreamMetadataCard

@Composable
fun AndroidStreamScreen(
    vm: StreamViewModel = viewModel(),
) {
    val controller = vm.controller

    // Pause playback when backgrounded (keeps media items for instant resume),
    // resume at the still-valid position on return. PlayerView surface follows.
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

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Streamlink player (8.6.2 / Python 3.11)",
            style = MaterialTheme.typography.titleMedium
        )

        // Player surface - 16:9, always visible so user sees buffering/playback.
        // Player instance lives in the ViewModel → survives rotation.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = vm.player
                    useController = true
                }.also { playerView = it }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(vertical = 8.dp)
        )

        controller.playerError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        controller.errorDetails?.let { details ->
            StreamErrorCard(
                details = details,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        OutlinedTextField(
            value = controller.url,
            onValueChange = { controller.url = it },
            label = { Text("Stream URL") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true
        )

        Row {
            Button(
                onClick = { controller.resolveAndPlay() },
                enabled = !controller.extracting
            ) {
                Text(if (controller.extracting) "Resolving..." else "Resolve & Play")
            }
            Button(
                onClick = { controller.stop() },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Stop")
            }
        }

        Text(controller.status, modifier = Modifier.padding(vertical = 8.dp))
        controller.lastTimings?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        controller.metadata?.let { meta ->
            StreamMetadataCard(
                result = meta,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (controller.result.directPlayable.isNotEmpty()) {
            val qualities = controller.orderedQualities()
            Text(
                "Qualities (${qualities.size}):",
                style = MaterialTheme.typography.titleSmall
            )
            QualityChips(
                items = qualities,
                selectedQuality = controller.selected?.quality,
                onSelect = controller::play,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        controller.selected?.let {
            Text("Selected: ${it.quality} (${it.kind})", style = MaterialTheme.typography.titleSmall)
            Text(it.url ?: "no url", style = MaterialTheme.typography.bodySmall)
        }
    }
}

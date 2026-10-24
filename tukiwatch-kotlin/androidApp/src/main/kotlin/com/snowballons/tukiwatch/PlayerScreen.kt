package com.snowballons.tukiwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
fun AndroidStreamScreen(
    engine: ChaquopyStreamEngine,
    vm: StreamViewModel = viewModel()
) {
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

    MaterialTheme {
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

            vm.playerError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            vm.errorDetails?.let { details ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            details.error ?: details.type ?: "Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        details.reason?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        details.alternative?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                label = { Text("Stream URL") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            Row {
                Button(
                    onClick = { vm.resolveAndPlay(engine) },
                    enabled = !vm.extracting
                ) {
                    Text(if (vm.extracting) "Resolving..." else "Resolve & Play")
                }
                Button(
                    onClick = { vm.stop() },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Stop")
                }
            }

            Text(vm.status, modifier = Modifier.padding(vertical = 8.dp))
            vm.lastTimings?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            vm.metadata?.let { meta ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            meta.title ?: "Live Stream",
                            style = MaterialTheme.typography.titleMedium
                        )
                        meta.author?.let { Text("by $it", style = MaterialTheme.typography.bodyMedium) }
                        Row {
                            meta.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            meta.streamTypes?.let {
                                Text(
                                    it.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        meta.viewerCount?.let {
                            Text("$it viewers", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (vm.result.directPlayable.isNotEmpty()) {
                val qualities = vm.orderedQualities()
                Text(
                    "Qualities (${qualities.size}):",
                    style = MaterialTheme.typography.titleSmall
                )
                LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                    items(qualities) { item ->
                        Button(
                            onClick = { vm.play(item) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(item.quality)
                        }
                    }
                }
            }

            vm.selected?.let {
                Text("Selected: ${it.quality} (${it.kind})", style = MaterialTheme.typography.titleSmall)
                Text(it.url ?: "no url", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
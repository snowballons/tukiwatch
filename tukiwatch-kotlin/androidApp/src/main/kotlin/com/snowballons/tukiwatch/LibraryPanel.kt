package com.snowballons.tukiwatch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snowballons.tukiwatch.liveness.STATUS_OFFLINE
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import com.snowballons.tukiwatch.liveness.LiveStream

/**
 * My List surface for Phase 2: favorites, their live status, and the JSON v1
 * export/import migration vehicle. Deliberately not the full bottom-tab
 * Library screen — that lands with navigation in Phase 3.
 */
@Composable
fun LibraryPanel(
    currentUrl: String,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = viewModel(),
) {
    LaunchedEffect(currentUrl) { vm.setCurrentUrl(currentUrl) }

    val streams by vm.streams.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val degraded by vm.degraded.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importFrom) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My List", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::refresh, enabled = !loading) { Text("Refresh") }
                TextButton(
                    enabled = !vm.busy,
                    onClick = {
                        exportLauncher.launch("tukiwatch-favorites-${System.currentTimeMillis()}.json")
                    },
                ) { Text("Export") }
                TextButton(
                    enabled = !vm.busy,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                ) { Text("Import") }
            }

            OutlinedButton(
                onClick = { vm.toggleFavorite() },
                enabled = !vm.busy && vm.isFavorite != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (vm.isFavorite) {
                        true -> "Remove from My List"
                        false -> "Add to My List"
                        null -> "Checking My List…"
                    },
                )
            }

            if (degraded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Offline or unreachable — showing the last known status.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            vm.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            when {
                streams.isEmpty() && loading -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
                streams.isEmpty() -> Text("No favorites yet.", style = MaterialTheme.typography.bodySmall)
                else -> streams.forEach { row -> StreamRow(row) }
            }
        }
    }
}

@Composable
private fun StreamRow(row: LiveStream) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (row.status) {
                STATUS_ONLINE -> "LIVE"
                STATUS_OFFLINE -> "OFF"
                else -> "ERR"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (row.status) {
                STATUS_ONLINE -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
        )
        Column(Modifier.weight(1f)) {
            Text(row.streamerName, style = MaterialTheme.typography.bodyMedium)
            val subtitle = row.title.ifBlank { row.error.orEmpty() }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

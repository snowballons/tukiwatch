package com.snowballons.tukiwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.ui.FavoriteRow
import com.snowballons.tukiwatch.ui.StreamErrorCard
import com.snowballons.tukiwatch.ui.StreamMetadataCard
import com.snowballons.tukiwatch.ui.TukiWatchTheme
import com.snowballons.tukiwatch.ui.theme.TukiWatchDark
import com.snowballons.tukiwatch.ui.theme.tukiPaletteFor
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt

/** Stable handles for the Compose UI tests. */
object DesktopTestTags {
    const val URL = "url-field"
    const val RESOLVE = "resolve-button"
    const val FAVORITE = "favorite-button"
    const val REFRESH = "refresh-button"
    const val FAVORITES = "favorites-list"
}

private val TwitchPurple = TukiWatchDark.primary
private val LiveGreen = TukiWatchDark.live

@Composable
fun DesktopHarnessApp(
    harness: DesktopHarness,
    modifier: Modifier = Modifier,
) {
    TukiWatchTheme(palette = tukiPaletteFor("tukiwatch-dark", systemDark = true)) {
        Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Header(harness)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ResolvePane(harness, Modifier.weight(1.55f).fillMaxHeight())
                MyListPane(harness, Modifier.weight(1f).fillMaxHeight())
            }
            Footer(harness)
        }
    }
}

@Composable
private fun Header(harness: DesktopHarness) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TwitchPurple),
            contentAlignment = Alignment.Center,
        ) {
            Text("TW", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("TukiWatch", style = MaterialTheme.typography.titleLarge)
            Text(
                "Desktop harness · real Streamlink engine + My List",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                harness.engineVersion ?: "engine starting…",
                style = MaterialTheme.typography.labelMedium,
            )
            harness.engineDetail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }
}

@Composable
private fun ResolvePane(harness: DesktopHarness, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResolveCard(harness)
        val result = harness.result
        if (result != null) {
            StreamMetadataCard(
                result = result,
                showStreamId = true,
                showThumbnailUrl = true,
            )
            result.errorDetails?.let {
                StreamErrorCard(it, elapsedMs = harness.elapsedMs)
            }
            if (result.streams.isNotEmpty()) QualitiesCard(harness, result)
            TimingsCard(harness, result)
        }
    }
}

@Composable
private fun ResolveCard(harness: DesktopHarness) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resolve a stream", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = harness.url,
                    onValueChange = harness::onUrlChanged,
                    label = { Text("Stream URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag(DesktopTestTags.URL),
                )
                Button(
                    onClick = harness::resolve,
                    enabled = !harness.resolving,
                    modifier = Modifier.testTag(DesktopTestTags.RESOLVE),
                ) {
                    if (harness.resolving) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Resolve")
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Try",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SampleChip("lofigirl") { harness.resolveUrl("https://www.twitch.tv/lofigirl") }
                SampleChip("HLS test") {
                    harness.resolveUrl("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                }
                SampleChip("Kick") { harness.resolveUrl("https://kick.com/xqc") }
                SampleChip("YouTube") {
                    harness.resolveUrl("https://www.youtube.com/watch?v=jfKfPfyJRdk")
                }
            }
            harness.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (harness.result?.status == STATUS_ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SampleChip(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QualitiesCard(harness: DesktopHarness, result: EngineResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Qualities", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${result.directPlayable.size} directly playable · ${result.streams.size} listed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.streams.forEach { item ->
                QualityRow(harness, item)
            }
            val selected = result.streams.firstOrNull {
                it.quality == harness.selectedQuality
            }
            selected?.url?.let {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Selected: ${selected.quality} (${selected.kind})",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QualityRow(harness: DesktopHarness, item: StreamItem) {
    val selected = harness.selectedQuality == item.quality
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clickable { harness.selectedQuality = item.quality }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (item.playableDirect && item.url != null) LiveGreen
                    else MaterialTheme.colorScheme.outline
                )
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.quality, style = MaterialTheme.typography.bodyMedium)
                if (item.alias) {
                    Text(
                        "alias",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                listOfNotNull(
                    item.kind.takeIf { it.isNotBlank() },
                    if (item.playableDirect) "direct" else "needs a player",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.url?.let { url ->
            Text(
                url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(190.dp),
            )
            TextButton(onClick = { copyToClipboard(url, harness, item.quality) }) {
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { openInBrowser(url) }) {
                Text("Open", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TimingsCard(harness: DesktopHarness, result: EngineResult) {
    val timings = result.timings ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Engine timings", style = MaterialTheme.typography.labelMedium)
            Text(
                "python total ${timings.totalMs.round1()}ms  ·  " +
                    "session ${timings.sessionMs.round1()}ms (reused=${timings.sessionReused})  ·  " +
                    "streams ${timings.streamsMs.round1()}ms  ·  " +
                    "build ${timings.buildMs.round1()}ms  ·  ${timings.count} qualities",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            harness.elapsedMs?.let {
                Text(
                    "bridge round-trip ${it}ms (process start + JSON)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MyListPane(harness: DesktopHarness, modifier: Modifier) {
    val streams by harness.streams.collectAsState()
    val loading by harness.loading.collectAsState()
    val degraded by harness.degraded.collectAsState()

    Card(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My List", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${streams.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = harness::refreshLiveness,
                    enabled = !loading,
                    modifier = Modifier.testTag(DesktopTestTags.REFRESH),
                ) {
                    Text(if (loading) "Checking…" else "Refresh")
                }
            }

            OutlinedButton(
                onClick = harness::toggleFavorite,
                enabled = !harness.busy && harness.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag(DesktopTestTags.FAVORITE),
            ) {
                Text(
                    when (harness.isFavorite) {
                        true -> "Remove from My List"
                        false -> "Add to My List"
                        null -> "Add to My List"
                    }
                )
            }

            if (degraded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Offline or unreachable — showing the last known status.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            when {
                streams.isEmpty() && loading ->
                    Text("Checking…", style = MaterialTheme.typography.bodySmall)
                streams.isEmpty() ->
                    Text(
                        "No favorites yet. Resolve a stream, then add it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag(DesktopTestTags.FAVORITES),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(streams, key = { it.id }) { row ->
                        FavoriteRow(
                            row = row,
                            onClick = { harness.resolveUrl(row.url) },
                            trailing = {
                                TextButton(
                                    onClick = { harness.removeFavorite(row.id) },
                                    modifier = Modifier.testTag("remove-${row.id}"),
                                ) {
                                    Text("✕", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer(harness: DesktopHarness) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Liveness refreshes on demand here; the 5-minute loop is Android-foreground only. " +
                "PYKOT_PYTHON / PYKOT_SCRIPT override discovery.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        harness.copiedQuality?.let {
            Text(
                "Copied $it URL",
                style = MaterialTheme.typography.labelSmall,
                color = LiveGreen,
            )
        }
    }
}

private fun copyToClipboard(url: String, harness: DesktopHarness, quality: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        harness.showCopied(quality)
    }
}

private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}

private fun Double.round1(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString()
    else "${tenths / 10}.${tenths % 10}"
}

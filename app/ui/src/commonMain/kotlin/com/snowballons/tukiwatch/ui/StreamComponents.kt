package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.EngineResult
import com.snowballons.tukiwatch.ErrorDetails
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.liveness.STATUS_OFFLINE
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE

/** Small status chip shared by the player, My List and the desktop harness. */
@Composable
fun StatusPill(
    status: String,
    modifier: Modifier = Modifier,
) {
    val liveColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        TukiWatchPalette.LiveOnLight
    } else {
        TukiWatchPalette.Live
    }
    val (label, color) = when (status) {
        STATUS_ONLINE -> "LIVE" to liveColor
        STATUS_OFFLINE -> "OFF" to MaterialTheme.colorScheme.onSurfaceVariant
        STATUS_ERROR -> "ERR" to MaterialTheme.colorScheme.error
        else -> status.take(3).uppercase().ifEmpty { "?" } to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * Metadata for a resolved stream.
 *
 * [showStreamId] and [showThumbnailUrl] are diagnostics the desktop harness
 * turns on; the product surface leaves them off.
 */
@Composable
fun StreamMetadataCard(
    result: EngineResult,
    modifier: Modifier = Modifier,
    showStreamId: Boolean = false,
    showThumbnailUrl: Boolean = false,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                result.status?.takeIf { it.isNotBlank() }?.let { StatusPill(it) }
                Spacer(Modifier.weight(1f))
                result.platform?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                result.title ?: "Untitled stream",
                style = MaterialTheme.typography.titleMedium,
            )
            result.author?.let {
                Text("by $it", style = MaterialTheme.typography.bodyMedium)
            }
            val details = listOfNotNull(
                result.category,
                result.streamTypes?.takeIf { it.isNotEmpty() }?.joinToString(" + "),
                result.viewerCount?.let { "$it viewers" },
            )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showStreamId) {
                result.streamId?.let {
                    Text(
                        "stream id $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showThumbnailUrl) {
                result.thumbnail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Structured error payload. [elapsedMs] is optional so the product surface can
 * omit timing while the harness shows it.
 */
@Composable
fun StreamErrorCard(
    details: ErrorDetails,
    modifier: Modifier = Modifier,
    elapsedMs: Long? = null,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                details.error ?: details.type ?: "Error",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val tags = listOfNotNull(details.type, details.errorClass)
                .joinToString(" · ")
            if (tags.isNotBlank()) {
                Text(
                    tags,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            details.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            details.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            details.alternative?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
            elapsedMs?.let {
                Text("Failed after ${it}ms", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * One My List row. [onClick] is optional (Android's Phase 2 panel is display
 * only); [trailing] lets the desktop harness add its remove button.
 */
@Composable
fun FavoriteRow(
    row: LiveStream,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(row.status)
        Column(Modifier.weight(1f)) {
            Text(row.streamerName, style = MaterialTheme.typography.bodyMedium)
            // `error` is only meaningful for STATUS_ERROR; an offline row must
            // not read like a failure.
            val subtitle = row.title.ifBlank {
                if (row.status == STATUS_ERROR) row.error.orEmpty() else ""
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                row.platform,
                row.category,
                row.viewerCount?.let { "$it viewers" },
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

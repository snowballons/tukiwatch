package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE

/**
 * Visual card for streams matching the original mobile design.
 * Shows status pill, platform chip, streamer name, title, category, and viewer count.
 */
@Composable
fun StreamCard(
    stream: LiveStream,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val platformColor = TukiWatchPalette.platformColor(stream.platform)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top row: Status pill + Platform badge + Trailing action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(stream.status)

                stream.platform?.let { platform ->
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(platformColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = platform.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = platformColor,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                trailing?.invoke()
            }

            Spacer(Modifier.height(8.dp))

            // Streamer name
            Text(
                text = stream.streamerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Title or error message
            val titleText = if (stream.status == STATUS_ONLINE && stream.title.isNotBlank()) {
                stream.title
            } else if (stream.status == STATUS_ERROR) {
                stream.error ?: stream.errorDetails?.message ?: "Stream error"
            } else {
                "Offline"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (stream.status == STATUS_ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Meta row: category + viewer count
            val metaParts = listOfNotNull(
                stream.category?.takeIf { it.isNotBlank() },
                stream.viewerCount?.let { "$it viewers" }
            )

            if (metaParts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = metaParts.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

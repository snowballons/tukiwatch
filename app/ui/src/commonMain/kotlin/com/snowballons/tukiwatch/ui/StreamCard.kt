package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import com.snowballons.tukiwatch.ui.theme.PlatformColors

/**
 * Visual card for streams matching the original mobile design.
 * Features a 16:9 thumbnail preview with floating live badge, platform pill,
 * cache freshness tag, streamer name, title, category, and viewer count.
 */
@Composable
fun StreamCard(
    stream: LiveStream,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val platformColor = PlatformColors.platformColor(stream.platform)
    val liveColor = LocalTukiPalette.current.live

    val titleText = if (stream.status == STATUS_ONLINE && stream.title.isNotBlank()) {
        stream.title
    } else if (stream.status == STATUS_ERROR) {
        stream.error ?: stream.errorDetails?.message ?: "Stream error"
    } else {
        "Offline"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Thumbnail section with floating status & platform overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black)
            ) {
                val thumbnailUri = stream.thumbnail.takeIf { it.isNotBlank() }
                    ?: "https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&h=338&fit=crop"

                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.85f),
                )

                // Top-Left: Live / Offline Status Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    StatusPill(stream.status)
                }

                // Top-Right: Platform Badge
                stream.platform?.let { platform ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(platformColor.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = platform.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Bottom-Right: Quick Play badge on live streams
                if (stream.status == STATUS_ONLINE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(liveColor.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "▶ PLAY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // Card content: Streamer, title, category & viewer details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stream.streamerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stream.status == STATUS_ERROR) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    trailing?.let {
                        Spacer(Modifier.width(8.dp))
                        it()
                    }
                }

                // Meta row: category + viewer count
                val metaParts = listOfNotNull(
                    stream.category?.takeIf { it.isNotBlank() },
                    stream.viewerCount?.let { "$it viewers" }
                )

                if (metaParts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = metaParts.joinToString("  •  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

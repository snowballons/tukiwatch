package com.snowballons.tukiwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class OnboardingSlide(
    val title: String,
    val description: String,
    val badge: String,
)

private val SLIDES = listOf(
    OnboardingSlide(
        title = "All Streams, Zero Servers",
        description = "Monitor and play streams from Twitch, YouTube, TikTok, and more directly on your device. No cloud backend, no tracking, completely free and open.",
        badge = "ON-DEVICE",
    ),
    OnboardingSlide(
        title = "Smart Liveness Ticker",
        description = "Your favorites are refreshed live with instant status, stream titles, viewer counts, and categories, completely scoped to active usage.",
        badge = "BATTERY FRIENDLY",
    ),
    OnboardingSlide(
        title = "Private & Portable",
        description = "Your favorite channels and preferences stay on your device. Seamlessly export and import JSON backups whenever you switch devices.",
        badge = "YOUR DATA",
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableStateOf(0) }
    val currentSlide = SLIDES[currentIndex]
    val isLast = currentIndex == SLIDES.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Skip Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLast) {
                TextButton(onClick = onComplete) {
                    Text("Skip")
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }

        // Center Content Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSlide.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = currentSlide.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = currentSlide.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Bottom Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                SLIDES.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (isLast) onComplete() else currentIndex++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isLast) "Get Started" else "Continue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

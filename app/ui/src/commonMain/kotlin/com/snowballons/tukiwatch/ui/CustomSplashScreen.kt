package com.snowballons.tukiwatch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-run brand moment: the TukiWatch mark, a quiet glow in the active
 * theme's accent, and the "Every stream. One watchlist." promise, followed by
 * a fade into the app.
 */
@Composable
fun CustomSplashScreen(
    logo: Painter,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    minDisplayTimeMs: Long = 2500L,
) {
    val palette = LocalTukiPalette.current
    var isVisible by remember { mutableStateOf(true) }

    val logoScale = remember { Animatable(0.84f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "Splash")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoGlow",
    )
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(550))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        launch {
            delay(260)
            textAlpha.animateTo(1f, animationSpec = tween(600))
        }

        delay(minDisplayTimeMs)
        isVisible = false
        delay(400)
        onFinish()
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.background,
                            palette.surface,
                            palette.background,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 560.dp
            val glowSize = if (compact) 210.dp else 248.dp
            val logoSize = if (compact) 132.dp else 156.dp
            val bottomGap = if (compact) 34.dp else 52.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(glowSize)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        palette.primary.copy(alpha = glowAlpha),
                                        Color.Transparent,
                                    ),
                                ),
                                CircleShape,
                            ),
                    )
                    Image(
                        painter = logo,
                        contentDescription = "TukiWatch logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(logoSize)
                            .scale(logoScale.value)
                            .alpha(logoAlpha.value),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(textAlpha.value),
                ) {
                    Text(
                        text = "TukiWatch",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.onBackground,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(2.dp)
                            .background(palette.primary),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "EVERY STREAM.\nONE WATCHLIST.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.4.sp,
                        lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "ON-DEVICE  ·  NO ACCOUNT",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                    )
                }

                Spacer(Modifier.height(bottomGap))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(palette.primary.copy(alpha = dot1Alpha), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(palette.primary.copy(alpha = dot2Alpha), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(palette.primary.copy(alpha = dot3Alpha), CircleShape),
                    )
                }
            }
        }
    }
}

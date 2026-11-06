package com.snowballons.tukiwatch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.snowballons.tukiwatch.ui.StatusPill
import com.snowballons.tukiwatch.ui.StreamErrorCard
import com.snowballons.tukiwatch.ui.StreamUtils
import com.snowballons.tukiwatch.ui.TukiIcons
import com.snowballons.tukiwatch.ui.state.StreamController
import com.snowballons.tukiwatch.ui.theme.LocalTukiPalette
import com.snowballons.tukiwatch.ui.theme.PlatformColors
import kotlin.math.roundToInt

/**
 * Dedicated player screen for Phase 3.
 *
 * Renders 16:9 ExoPlayer surface, live stream details, quality switcher,
 * error handling, and intent launching for official apps.
 *
 * The video surface is a single stable composable slot: fullscreen and
 * picture-in-picture only change the slot's size and hide the surrounding
 * chrome, so the PlayerView (and its surface) is never recreated when the
 * layout mode changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    onClose: () -> Unit,
    vm: StreamViewModel = viewModel(),
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundPlayback: Boolean = false,
    keepScreenAwake: Boolean = true,
) {
    val context = LocalContext.current
    val controller = vm.controller
    var showQualitySheet by remember { mutableStateOf(false) }

    // Keep the screen on while the player is active — unless the user opted out.
    val view = LocalView.current
    DisposableEffect(keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }

    // Fullscreen immersion state
    var isFullscreen by remember { mutableStateOf(false) }
    var showFullscreenInfo by remember { mutableStateOf(false) }
    val activity = context as? Activity
    val mainActivity = context as? MainActivity
    val isInPip = mainActivity?.isInPip == true
    // Full-bleed covers both immersive fullscreen and the PiP window: no
    // system-bar padding, no header, no info column.
    val fullBleed = isFullscreen || isInPip

    /** Hides system bars and locks landscape for a true fullscreen experience. */
    fun enterFullscreen() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                win.attributes = win.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(win, win.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /** Restores portrait/unspecified orientation and shows system bars. */
    fun exitFullscreen() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                win.attributes = win.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            WindowInsetsControllerCompat(win, win.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun leaveFullscreen() {
        isFullscreen = false
        showFullscreenInfo = false
        exitFullscreen()
    }

    // Restore system bars/orientation when the player screen is dismissed
    DisposableEffect(Unit) {
        onDispose { if (isFullscreen) exitFullscreen() }
    }

    // Back exits fullscreen first; the app shell's handler closes the player.
    BackHandler(enabled = isFullscreen) { leaveFullscreen() }

    // Lifecycle observer to pause ExoPlayer in background and resume on return.
    // With background playback on, only the surface is released on pause so the
    // audio keeps going (best-effort; no foreground service yet).
    //
    // Entering PiP pauses the activity, so PiP must count as "still on screen"
    // or the floating window would freeze on the last frame.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(isInPip) {
        // The pause may land before onPictureInPictureModeChanged; resuming here
        // covers that ordering without resuming a stream the user paused.
        if (isInPip) vm.onResumeForeground()
    }

    DisposableEffect(lifecycle, vm, backgroundPlayback, isInPip) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val inPip = isInPip || activity?.isInPictureInPictureMode == true
                    if (!backgroundPlayback && !inPip) vm.onPauseBackground()
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

    // Keep the PiP window shaped like the actual stream instead of a fixed 16:9.
    DisposableEffect(vm.player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    mainActivity?.updatePipAspectRatio(videoSize.width, videoSize.height)
                }
            }
        }
        vm.player.addListener(listener)
        onDispose { vm.player.removeListener(listener) }
    }

    val streamMetadata = controller.metadata
    val streamerName = streamMetadata?.author
        ?.takeIf { it.isNotBlank() && it.lowercase() != "live" && it.lowercase() != "video" }
        ?: StreamUtils.deriveStreamerName(controller.url)
    val platform = streamMetadata?.platform ?: StreamUtils.detectPlatform(controller.url)
    val platformColor = PlatformColors.platformColor(platform)
    val palette = LocalTukiPalette.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (fullBleed) Modifier else Modifier.safeContentPadding())
            .background(if (fullBleed) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // The header is a fixed 56dp in the normal layout; in fullscreen it is
        // an overlay and takes no space away from the video.
        val headerHeight = if (fullBleed) 0.dp else 56.dp
        val idealVideoHeight = maxWidth * 9f / 16f
        // Clamp so a landscape viewport never pushes the video past the bottom.
        val videoHeight = if (fullBleed) {
            maxHeight
        } else {
            minOf(idealVideoHeight, (maxHeight - headerHeight).coerceAtLeast(0.dp))
        }
        val infoTop = headerHeight + videoHeight
        val infoHeight = (maxHeight - infoTop).coerceAtLeast(0.dp)

        // Single video slot — one AndroidView call site for every layout mode.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = headerHeight)
                .then(
                    if (fullBleed) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().height(videoHeight)
                    }
                )
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    // Source rect hint drives the PiP enter animation; it must
                    // be the video bounds in window pixels, not the whole screen.
                    if (!isInPip) {
                        val bounds = coords.boundsInWindow()
                        if (!bounds.isEmpty) {
                            mainActivity?.updatePipSourceRect(
                                Rect(
                                    bounds.left.roundToInt(),
                                    bounds.top.roundToInt(),
                                    bounds.right.roundToInt(),
                                    bounds.bottom.roundToInt(),
                                )
                            )
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = vm.player
                        useController = true
                    }.also { playerView = it }
                },
                update = { view -> view.useController = !isInPip },
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

        if (!fullBleed) {
            // Header with Back button, streamer title, platform tag, and favorite star
            PlayerHeader(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(headerHeight),
                streamerName = streamerName,
                platform = platform,
                platformColor = platformColor,
                isFavorite = isFavorite,
                isFullscreen = false,
                showInfo = false,
                onBack = onClose,
                onToggleFavorite = onToggleFavorite,
                onToggleFullscreen = {
                    isFullscreen = true
                    enterFullscreen()
                },
                onToggleInfo = {},
            )

            // Scrollable stream information & quality selection
            StreamInfoContent(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = infoTop)
                    .fillMaxWidth()
                    .height(infoHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                controller = controller,
                streamMetadata = streamMetadata,
                streamerName = streamerName,
                platform = platform,
                platformColor = platformColor,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenQuality = { showQualitySheet = true },
            )
        }

        if (isFullscreen && !isInPip) {
            // Immersive chrome floats over the video instead of taking layout space.
            PlayerHeader(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    ),
                streamerName = streamerName,
                platform = platform,
                platformColor = platformColor,
                isFavorite = isFavorite,
                isFullscreen = true,
                showInfo = showFullscreenInfo,
                infoHasError = controller.playerError != null || controller.errorDetails != null,
                onBack = { leaveFullscreen() },
                onToggleFavorite = onToggleFavorite,
                onToggleFullscreen = { leaveFullscreen() },
                onToggleInfo = { showFullscreenInfo = !showFullscreenInfo },
                contentColor = Color.White,
            )
        }

        if (isFullscreen && !isInPip && showFullscreenInfo) {
            // Scrim: tap anywhere outside the sheet to dismiss.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close stream info",
                    ) { showFullscreenInfo = false }
            )
            StreamInfoContent(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) { detectTapGestures { /* consume */ } }
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                controller = controller,
                streamMetadata = streamMetadata,
                streamerName = streamerName,
                platform = platform,
                platformColor = platformColor,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenQuality = { showQualitySheet = true },
            )
        }
    }

    // Quality Picker Bottom-Sheet Modal
    if (showQualitySheet) {
        val directQualities = controller.orderedQualities()
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Stream Quality",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showQualitySheet = false }) {
                        Text("Done")
                    }
                }
                Text(
                    text = "Direct video stream renditions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(directQualities, key = { it.quality }) { item ->
                        val isSelected = item.quality == controller.selected?.quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    controller.play(item)
                                    showQualitySheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.quality,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.quality.contains("source", ignoreCase = true) || item.quality.contains("best", ignoreCase = true)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(palette.liveWash)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "HD / SOURCE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.live
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (item.url?.contains(".m3u8") == true) "HLS Live Stream" else "Direct Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Header row shared by the normal and immersive layouts. */
@Composable
private fun PlayerHeader(
    streamerName: String,
    platform: String?,
    platformColor: Color,
    isFavorite: Boolean?,
    isFullscreen: Boolean,
    showInfo: Boolean,
    infoHasError: Boolean = false,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = TukiIcons.ArrowBack,
                contentDescription = "Back",
                tint = contentColor,
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = streamerName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
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
            Spacer(Modifier.width(4.dp))
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite == true) TukiIcons.Star else TukiIcons.StarOutline,
                contentDescription = if (isFavorite == true) "Favorited" else "Favorite",
                tint = if (isFavorite == true) MaterialTheme.colorScheme.primary else contentColor,
            )
        }

        if (isFullscreen) {
            IconButton(onClick = onToggleInfo) {
                Icon(
                    imageVector = TukiIcons.Info,
                    contentDescription = if (showInfo) "Hide stream info" else "Show stream info",
                    tint = when {
                        infoHasError -> MaterialTheme.colorScheme.error
                        showInfo -> MaterialTheme.colorScheme.primary
                        else -> contentColor
                    },
                )
            }
        }

        IconButton(onClick = onToggleFullscreen) {
            Icon(
                imageVector = if (isFullscreen) TukiIcons.FullscreenExit else TukiIcons.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                tint = contentColor,
            )
        }
    }
}

/** Title, status, metadata, errors, quality switcher and actions. */
@Composable
private fun StreamInfoContent(
    controller: StreamController,
    streamMetadata: EngineResult?,
    streamerName: String,
    platform: String?,
    platformColor: Color,
    isFavorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onOpenQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stream title
        val displayTitle = streamMetadata?.title
            ?.takeIf { it.isNotBlank() && it.lowercase() != "live" }
            ?: if (controller.extracting) "Loading stream…" else "Live Stream"
        Text(
            text = displayTitle,
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

        // Quality switcher with bottom-sheet trigger
        val directQualities = controller.orderedQualities()
        if (directQualities.isNotEmpty()) {
            val currentQualityName = controller.selected?.quality ?: directQualities.firstOrNull()?.quality ?: "Auto"

            // Clickable Stream Quality card that triggers the modal bottom sheet
            OutlinedCard(
                onClick = onOpenQuality,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Stream Quality",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = currentQualityName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Change (${directQualities.size}) ▾",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Action row: Save to My List + Open in Official App
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier.weight(1f)
            ) {
                val favText = when (isFavorite) {
                    true -> "★ Saved"
                    false -> "+ Save"
                    null -> "Save"
                }
                Text(favText, maxLines = 1)
            }

            val targetPlatformName = platform?.replaceFirstChar { it.uppercase() } ?: "App"
            OutlinedButton(
                onClick = {
                    PlatformLauncher.openInOfficialApp(
                        context = context,
                        platform = platform,
                        originalUrl = controller.url
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = platformColor
                ),
                border = BorderStroke(1.dp, platformColor.copy(alpha = 0.7f))
            ) {
                Text(
                    text = "Open in $targetPlatformName ↗",
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis
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

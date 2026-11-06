package com.snowballons.tukiwatch

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snowballons.tukiwatch.liveness.autoRefreshLoop
import com.snowballons.tukiwatch.prefs.Prefs
import com.snowballons.tukiwatch.ui.AddScreen
import com.snowballons.tukiwatch.ui.CustomSplashScreen
import com.snowballons.tukiwatch.ui.HomeScreen
import com.snowballons.tukiwatch.ui.LibraryScreen
import com.snowballons.tukiwatch.ui.OnboardingScreen
import com.snowballons.tukiwatch.ui.PrivacyPolicyScreen
import com.snowballons.tukiwatch.ui.SettingsScreen
import com.snowballons.tukiwatch.ui.TermsOfServiceScreen
import com.snowballons.tukiwatch.ui.TukiIcons
import com.snowballons.tukiwatch.ui.TukiWatchTheme
import com.snowballons.tukiwatch.ui.rememberHaptics
import com.snowballons.tukiwatch.ui.theme.tukiPaletteFor
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", TukiIcons.Home),
    MY_LIST("My List", TukiIcons.Star),
    ADD("Add", TukiIcons.Add),
    SETTINGS("Settings", TukiIcons.Settings),
}

enum class OverlayScreen {
    PLAYER,
    PRIVACY,
    TERMS,
}

class MainActivity : ComponentActivity() {
    /**
     * Tracks whether the full-screen player overlay is currently visible.
     * Set by [AppContent] via [LaunchedEffect]; changing it also refreshes the
     * PiP params so auto-enter is only armed while a stream is on screen.
     */
    var isPlayerActive = false
        set(value) {
            if (field == value) return
            field = value
            applyPipParams()
        }

    /** Auto PiP preference; mirrored from settings by [AppContent]. */
    var autoPipEnabled = true
        set(value) {
            if (field == value) return
            field = value
            applyPipParams()
        }

    /** True while the activity is hosted in a PiP window. Compose-observable. */
    var isInPip by mutableStateOf(false)
        private set

    /** Video bounds in window pixels; animates the PiP enter transition. */
    private var pipSourceRect: Rect? = null

    /** Actual stream aspect ratio, so the PiP window is not hard-coded 16:9. */
    private var pipAspectRatio: Rational = Rational(16, 9)

    private companion object {
        /** Widest ratio the platform accepts for a PiP window. */
        const val MAX_PIP_ASPECT = 2.39f
    }

    /**
     * URL delivered via share-sheet (ACTION_SEND) or deep link (tukiwatch:// /
     * https stream URL) that [AppContent] should open in the player.
     * Wrapped in state so Compose recomposes when a new intent arrives while
     * the activity is already running (singleTop onNewIntent path).
     */
    private val _pendingUrl = mutableStateOf<String?>(null)
    val pendingUrl: androidx.compose.runtime.State<String?> get() = _pendingUrl

    /** Called by [AppContent] after consuming the pending URL to prevent re-delivery. */
    fun consumePendingUrl() { _pendingUrl.value = null }

    /** Called by the player as its video surface moves or resizes. */
    fun updatePipSourceRect(rect: Rect) {
        if (rect == pipSourceRect) return
        pipSourceRect = rect
        applyPipParams()
    }

    /** Called by the player when Media3 reports a new video size. */
    fun updatePipAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        // Android only accepts ratios between 1:2.39 and 2.39:1 for PiP windows.
        val ratio = width.toFloat() / height
        val rational = when {
            ratio > MAX_PIP_ASPECT -> Rational(239, 100)
            ratio < 1f / MAX_PIP_ASPECT -> Rational(100, 239)
            else -> Rational(width, height)
        }
        if (rational == pipAspectRatio) return
        pipAspectRatio = rational
        applyPipParams()
    }

    private fun pipEligible(): Boolean = isPlayerActive && autoPipEnabled && !isInPip

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio)
        pipSourceRect?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(pipEligible())
        }
        return builder.build()
    }

    /**
     * Pushes the current PiP state to the system. Auto-enter is sticky per
     * activity, so it must be explicitly disarmed when the player closes.
     */
    private fun applyPipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        applyPipParams()
    }

    /**
     * Legacy PiP entry for Android 8–11. Android 12+ uses auto-enter instead,
     * which follows the system gesture rather than relying on this callback.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipEligible()) {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    /**
     * Handle re-delivery of share/deep-link intent when the activity is already
     * running (singleTop — the OS calls onNewIntent instead of recreating).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _pendingUrl.value = extractStreamUrl(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse any intent that cold-started the app via share / deep link
        _pendingUrl.value = extractStreamUrl(intent)

        val app = application as TukiWatchApp
        setContent {
            val scope = rememberCoroutineScope()
            val prefs by app.prefs.prefs.collectAsStateWithLifecycle(
                initialValue = Prefs(themeId = "system", onboardingCompleted = true)
            )

            val palette = tukiPaletteFor(prefs.themeId, isSystemInDarkTheme())

            var showSplash by rememberSaveable { mutableStateOf(true) }
            val systemBarsDark = palette.dark

            // Keep phone status bar and navigation bar in sync with app theme.
            // The splash is palette-painted too, so both follow the palette.
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    window.statusBarColor = palette.background.toArgb()
                    window.navigationBarColor = palette.surface.toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !systemBarsDark
                        isAppearanceLightNavigationBars = !systemBarsDark
                    }
                }
            }

            TukiWatchTheme(palette = palette, hapticsEnabled = prefs.hapticsEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (!prefs.onboardingCompleted) {
                        OnboardingScreen(
                            onComplete = {
                                scope.launch { app.prefs.setOnboardingCompleted(true) }
                            }
                        )
                    } else {
                        AppContent(app = app, prefs = prefs, activity = this@MainActivity)
                    }

                    if (showSplash) {
                        CustomSplashScreen(
                            logo = painterResource(R.drawable.tukiwatch_logo),
                            onFinish = { showSplash = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun getTimeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

/**
 * Extracts a stream URL from an incoming intent (share-sheet or deep link).
 *
 * Handles three cases:
 * 1. **Share sheet** (`ACTION_SEND`, `text/plain`) — the shared text may be a
 *    plain URL or a sentence containing a URL; we extract the first http/https
 *    substring.
 * 2. **Custom scheme** (`tukiwatch://<encoded-url>`) — the path+host portion
 *    of the URI is the stream URL, URL-decoded automatically by [Uri.parse].
 * 3. **Web URL interception** (`ACTION_VIEW`, http/https) — the data URI is
 *    the stream page URL (e.g. https://www.twitch.tv/ninja).
 *
 * Returns `null` if no valid URL can be extracted.
 */
private fun extractStreamUrl(intent: Intent?): String? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            // Share sheet: extract the first http/https URL from shared text
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            // Grab the first token that looks like a URL
            val urlRegex = Regex("""https?://\S+""")
            urlRegex.find(text)?.value
        }
        Intent.ACTION_VIEW -> {
            val uri = intent.data ?: return null
            when (uri.scheme) {
                "tukiwatch" -> {
                    // tukiwatch://<encoded-url>
                    // The URI host+path together form the encoded stream URL.
                    // Uri.parse decodes percent-encoded characters automatically.
                    val raw = uri.schemeSpecificPart.trimStart('/')
                    raw.takeIf { it.startsWith("http") }
                        ?: "https://$raw"
                }
                "http", "https" -> uri.toString()
                else -> null
            }
        }
        else -> null
    }
}

@Composable
fun AppContent(
    app: TukiWatchApp,
    prefs: Prefs,
    activity: MainActivity? = null,
    streamVm: StreamViewModel = viewModel(),
    libraryVm: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()

    // Animated Snackbar host — surfaces all libraryVm feedback messages
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryMessage = libraryVm.controller.message
    LaunchedEffect(libraryMessage) {
        libraryMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
            libraryVm.controller.clearMessage()
        }
    }

    // Foreground liveness cadence, driven by the Live updates settings. Manual
    // (interval 0) or auto-check off means a single check, no loop.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, prefs.autoCheckFavorites, prefs.checkIntervalMinutes) {
        if (!prefs.autoCheckFavorites || prefs.checkIntervalMinutes <= 0) {
            app.liveness.refresh()
            return@LaunchedEffect
        }
        val intervalMs = prefs.checkIntervalMinutes * 60_000L
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.liveness.autoRefreshLoop(intervalMs)
        }
    }

    // Preferred rendition selection mirrored from settings.
    LaunchedEffect(prefs.preferredQuality) {
        streamVm.controller.preferredQuality = prefs.preferredQuality
    }

    // External links (GitHub, legal, support).
    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        } catch (_: Exception) {
        }
    }

    // Keep URL in sync between player and library controller
    LaunchedEffect(streamVm.controller.url) {
        libraryVm.onCurrentUrlChanged(streamVm.controller.url)
    }

    var activeTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var activeOverlay by rememberSaveable { mutableStateOf<OverlayScreen?>(null) }

    // Inform MainActivity whether the player overlay is active so it can enter PiP on Home press
    val mainActivity = context as? MainActivity
    LaunchedEffect(activeOverlay) {
        mainActivity?.isPlayerActive = (activeOverlay == OverlayScreen.PLAYER)
    }
    LaunchedEffect(prefs.autoPip) {
        mainActivity?.autoPipEnabled = prefs.autoPip
    }

    // Consume any URL delivered via share-sheet or deep link and open the player.
    // pendingUrl is a Compose State held on MainActivity, so recomposition triggers when
    // a new intent arrives while the app is already running (singleTop onNewIntent path).
    val pendingUrl by (activity?.pendingUrl ?: remember { mutableStateOf(null) })
    LaunchedEffect(pendingUrl) {
        pendingUrl?.let { url ->
            streamVm.controller.resolveAndPlay(
                targetUrl = url,
                placeholderTitle = null,
                placeholderAuthor = null,
                placeholderPlatform = null,
            )
            activeOverlay = OverlayScreen.PLAYER
            // Clear so the same URL is not re-played on recomposition
            activity?.consumePendingUrl()
        }
    }

    // SAF Document Launchers for JSON backup migration
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(libraryVm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(libraryVm::importFrom) }

    // Streams & liveness state
    val streams by libraryVm.controller.streams.collectAsStateWithLifecycle()
    val loading by libraryVm.controller.loading.collectAsStateWithLifecycle()
    val degraded by libraryVm.controller.degraded.collectAsStateWithLifecycle()

    // Add screen preview state
    var resolvingPreview by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<EngineResult?>(null) }

    val onPlayStream: (String) -> Unit = { targetUrl ->
        val author = previewResult?.takeIf { it.originalUrl == targetUrl }?.author
        val title = previewResult?.takeIf { it.originalUrl == targetUrl }?.title
        val platform = previewResult?.takeIf { it.originalUrl == targetUrl }?.platform
        streamVm.controller.resolveAndPlay(
            targetUrl = targetUrl,
            placeholderTitle = title,
            placeholderAuthor = author,
            placeholderPlatform = platform,
        )
        activeOverlay = OverlayScreen.PLAYER
    }

    val onPlayLiveStream: (com.snowballons.tukiwatch.liveness.LiveStream) -> Unit = { stream ->
        streamVm.controller.resolveAndPlay(
            targetUrl = stream.url,
            placeholderTitle = stream.title,
            placeholderAuthor = stream.streamerName.takeIf { it.isNotBlank() } ?: stream.author,
            placeholderPlatform = stream.platform,
        )
        activeOverlay = OverlayScreen.PLAYER
    }

    // Back handling: returns from overlay -> returns to HOME tab -> exits app
    BackHandler(enabled = activeOverlay != null || activeTab != MainTab.HOME) {
        if (activeOverlay != null) {
            if (activeOverlay == OverlayScreen.PLAYER) {
                streamVm.controller.stop()
            }
            activeOverlay = null
        } else if (activeTab != MainTab.HOME) {
            activeTab = MainTab.HOME
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = {
            if (activeOverlay == null) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = if (tab == MainTab.ADD) null else { { Text(tab.label) } },
                            icon = {
                                if (tab == MainTab.ADD) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = TukiIcons.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (activeTab == tab) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeOverlay) {
                OverlayScreen.PLAYER -> {
                    val currentPlayingUrl = streamVm.controller.url
                    val isFav = streams.any {
                        it.url.trimEnd('/') == currentPlayingUrl.trimEnd('/')
                    }
                    FullPlayerScreen(
                        onClose = {
                            streamVm.controller.stop()
                            activeOverlay = null
                        },
                        isFavorite = isFav,
                        onToggleFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val author = streamVm.controller.metadata?.author
                            libraryVm.controller.toggleFavorite(author)
                        },
                        vm = streamVm,
                        backgroundPlayback = prefs.backgroundPlayback,
                        keepScreenAwake = prefs.keepScreenAwake,
                    )
                }
                OverlayScreen.PRIVACY -> {
                    PrivacyPolicyScreen(
                        onBack = { activeOverlay = null }
                    )
                }
                OverlayScreen.TERMS -> {
                    TermsOfServiceScreen(
                        onBack = { activeOverlay = null }
                    )
                }
                null -> {
                    when (activeTab) {
                        MainTab.HOME -> {
                            HomeScreen(
                                streams = streams,
                                loading = loading,
                                degraded = degraded,
                                greeting = getTimeGreeting(),
                                onRefresh = { libraryVm.refresh() },
                                onStreamClick = { stream -> onPlayLiveStream(stream) },
                            )
                        }
                        MainTab.MY_LIST -> {
                            LibraryScreen(
                                streams = streams,
                                loading = loading,
                                degraded = degraded,
                                busy = libraryVm.controller.busy,
                                showOffline = prefs.showOfflineChannels,
                                sort = prefs.myListSort,
                                onRefresh = { libraryVm.refresh() },
                                onDeleteFavorite = { id -> libraryVm.removeFavorite(id) },
                                onStreamClick = { stream -> onPlayLiveStream(stream) },
                            )
                        }
                        MainTab.ADD -> {
                            AddScreen(
                                onPreview = { targetUrl ->
                                    scope.launch {
                                        resolvingPreview = true
                                        previewResult = withContext(Dispatchers.IO) {
                                            runCatching {
                                                app.engine.resolveDetails(targetUrl, "{}", "all")
                                            }.getOrNull()
                                        }
                                        resolvingPreview = false
                                    }
                                },
                                onSaveFavorite = { name, url ->
                                    libraryVm.addFavorite(name, url)
                                },
                                onPlayUrl = { targetUrl ->
                                    onPlayStream(targetUrl)
                                },
                                resolving = resolvingPreview,
                                previewResult = previewResult,
                                statusMessage = libraryVm.controller.message,
                                isFavorite = { url ->
                                    streams.any { it.url.trimEnd('/') == url.trimEnd('/') }
                                },
                            )
                        }
                        MainTab.SETTINGS -> {
                            SettingsScreen(
                                prefs = prefs,
                                onThemeChange = { id -> scope.launch { app.prefs.setThemeId(id) } },
                                onBackgroundPlaybackChange = { on ->
                                    scope.launch { app.prefs.setBackgroundPlayback(on) }
                                },
                                onAutoPipChange = { on ->
                                    scope.launch { app.prefs.setAutoPip(on) }
                                },
                                onPreferredQualityChange = { quality ->
                                    scope.launch { app.prefs.setPreferredQuality(quality) }
                                },
                                onKeepScreenAwakeChange = { on ->
                                    scope.launch { app.prefs.setKeepScreenAwake(on) }
                                },
                                onAutoCheckChange = { on ->
                                    scope.launch { app.prefs.setAutoCheckFavorites(on) }
                                },
                                onCheckIntervalChange = { minutes ->
                                    scope.launch { app.prefs.setCheckIntervalMinutes(minutes) }
                                },
                                onUpdateRemindersChange = { on ->
                                    scope.launch { app.prefs.setUpdateReminders(on) }
                                },
                                onShowOfflineChange = { on ->
                                    scope.launch { app.prefs.setShowOfflineChannels(on) }
                                },
                                onMyListSortChange = { sort ->
                                    scope.launch { app.prefs.setMyListSort(sort) }
                                },
                                onHapticsChange = { on ->
                                    scope.launch { app.prefs.setHapticsEnabled(on) }
                                },
                                onReduceMotionChange = { on ->
                                    scope.launch { app.prefs.setReduceMotion(on) }
                                },
                                onExportBackup = {
                                    exportLauncher.launch(
                                        "tukiwatch-favorites-${System.currentTimeMillis()}.json"
                                    )
                                },
                                onImportBackup = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onClearCache = { streamVm.controller.clearCache() },
                                onReplayIntro = {
                                    scope.launch { app.prefs.setOnboardingCompleted(false) }
                                },
                                onSaveTwitchCredentials = { id, token ->
                                    scope.launch { app.prefs.setHelixCredentials(id, token) }
                                },
                                onOpenPrivacyPolicy = { activeOverlay = OverlayScreen.PRIVACY },
                                onOpenTermsOfService = { activeOverlay = OverlayScreen.TERMS },
                                onOpenGitHub = { openUrl("https://github.com/snowballons/tukiwatch") },
                                onReportProblem = {
                                    openUrl("https://github.com/snowballons/tukiwatch/issues/new")
                                },
                                onSupport = { openUrl("https://buymeacoffee.com/snowballons") },
                                appVersionName = BuildConfig.VERSION_NAME,
                                appVersionCode = BuildConfig.VERSION_CODE,
                            )
                        }
                    }
                }
            }
        }
    }
}

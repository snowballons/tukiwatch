package com.snowballons.tukiwatch

import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.snowballons.tukiwatch.prefs.ThemeMode
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
     * Set by [AppContent] via [LaunchedEffect] so that [onUserLeaveHint] knows
     * whether to enter Picture-in-Picture mode when the user presses Home.
     */
    var isPlayerActive = false

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

    /** Automatically enter PiP when the user leaves the app while a stream is playing. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
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
                initialValue = Prefs(themeMode = ThemeMode.SYSTEM, onboardingCompleted = true)
            )

            val darkTheme = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            var showSplash by rememberSaveable { mutableStateOf(true) }
            val systemBarsDark = darkTheme || showSplash

            // Keep phone status bar and navigation bar in sync with app theme.
            // The splash is always dark, so keep the bars dark until it finishes.
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    window.statusBarColor = if (systemBarsDark) {
                        android.graphics.Color.parseColor("#0E0E10")
                    } else {
                        android.graphics.Color.parseColor("#F8F9FA")
                    }
                    window.navigationBarColor = if (systemBarsDark) {
                        android.graphics.Color.parseColor("#18181B")
                    } else {
                        android.graphics.Color.parseColor("#FFFFFF")
                    }
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !systemBarsDark
                        isAppearanceLightNavigationBars = !systemBarsDark
                    }
                }
            }

            TukiWatchTheme(darkTheme = darkTheme) {
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
    val haptic = LocalHapticFeedback.current

    // Animated Snackbar host — surfaces all libraryVm feedback messages
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryMessage = libraryVm.controller.message
    LaunchedEffect(libraryMessage) {
        libraryMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
            libraryVm.controller.clearMessage()
        }
    }

    // 5-minute foreground liveness refresh cadence
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.liveness.autoRefreshLoop()
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
                                streams = streams,
                                currentTheme = prefs.themeMode,
                                onThemeChange = { mode ->
                                    scope.launch { app.prefs.setThemeMode(mode) }
                                },
                                onExportBackup = {
                                    exportLauncher.launch("tukiwatch-favorites-${System.currentTimeMillis()}.json")
                                },
                                onImportBackup = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onOpenPrivacyPolicy = {
                                    activeOverlay = OverlayScreen.PRIVACY
                                },
                                onOpenTermsOfService = {
                                    activeOverlay = OverlayScreen.TERMS
                                },
                                onOpenGitHub = {
                                    try {
                                        val gitHubIntent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/snowballons/tukiwatch")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        context.startActivity(gitHubIntent)
                                    } catch (_: Exception) {}
                                },
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

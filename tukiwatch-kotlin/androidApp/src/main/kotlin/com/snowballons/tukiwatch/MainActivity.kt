package com.snowballons.tukiwatch

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.snowballons.tukiwatch.ui.TukiWatchTheme
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val label: String, val glyph: String) {
    HOME("Home", "●"),
    MY_LIST("My List", "★"),
    ADD("Add", "+"),
    SETTINGS("Settings", "⚙"),
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

            TukiWatchTheme(darkTheme = darkTheme) {
                var showSplash by rememberSaveable { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!prefs.onboardingCompleted) {
                        OnboardingScreen(
                            onComplete = {
                                scope.launch { app.prefs.setOnboardingCompleted(true) }
                            }
                        )
                    } else {
                        AppContent(app = app, prefs = prefs)
                    }

                    if (showSplash) {
                        CustomSplashScreen(
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

@Composable
fun AppContent(
    app: TukiWatchApp,
    prefs: Prefs,
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
                                        Text(
                                            text = "+",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = tab.glyph,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (activeTab == tab) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                                onExport = {
                                    exportLauncher.launch("tukiwatch-favorites-${System.currentTimeMillis()}.json")
                                },
                                onImport = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
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
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.snowballons.tukiwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snowballons.tukiwatch.liveness.autoRefreshLoop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as TukiWatchApp
        setContent { AppContent(app) }
    }
}

@Composable
fun AppContent(app: TukiWatchApp) {
    val streamVm: StreamViewModel = viewModel()
    val libraryVm: LibraryViewModel = viewModel()

    // 5-min cadence, foreground-only. A plain LaunchedEffect is only
    // composition-scoped: it keeps ticking while the activity is STOPPED.
    // repeatOnLifecycle cancels the loop on background and restarts it on
    // return, which is §3's "only while app is foregrounded".
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.liveness.autoRefreshLoop()
        }
    }

    // Sync URL from player to library panel
    LaunchedEffect(streamVm.url) {
        libraryVm.onCurrentUrlChanged(streamVm.url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeContentPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            AndroidStreamScreen(engine = app.engine, vm = streamVm)
        }
        LibraryPanel(vm = libraryVm)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

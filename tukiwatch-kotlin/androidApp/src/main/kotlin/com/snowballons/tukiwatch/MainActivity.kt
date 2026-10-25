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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snowballons.tukiwatch.liveness.autoRefreshLoop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as TukiWatchApp
        setContent {
            // Both viewModel() calls resolve to the same activity-scoped
            // StreamViewModel, so the panel sees the URL the player is using.
            val streamVm: StreamViewModel = viewModel()

            // 5-min cadence, foreground-only: LaunchedEffect cancels with the
            // composition, which is exactly §3's "while app is foregrounded".
            LaunchedEffect(Unit) { app.liveness.autoRefreshLoop() }

            Column(
                Modifier
                    .fillMaxSize()
                    .safeContentPadding(),
            ) {
                Box(Modifier.weight(1f)) {
                    AndroidStreamScreen(engine = app.engine, vm = streamVm)
                }
                LibraryPanel(currentUrl = streamVm.url)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

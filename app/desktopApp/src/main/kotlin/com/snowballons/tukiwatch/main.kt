package com.snowballons.tukiwatch

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Desktop entry point. This is the harness window, not the shared template
 * `App()` — it resolves through the same engine and repositories the APK uses.
 */
fun main() = application {
    val harness = remember { DesktopHarness() }
    Window(
        onCloseRequest = {
            harness.close()
            exitApplication()
        },
        title = "TukiWatch · Desktop Harness",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        DesktopHarnessApp(harness)
    }
}

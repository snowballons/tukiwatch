package com.snowballons.tukiwatch

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/**
 * End-to-end UI tests over the real Compose tree with a fake engine and an
 * in-memory favorites DB. Run with a display:
 *
 *   DISPLAY=:0 ./gradlew :desktopApp:test
 *   xvfb-run -a ./gradlew :desktopApp:test
 *
 * The class skips itself when no X/Wayland display is available.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppUiTest {
    /**
     * AWT is deliberately non-headless for these tests, so `isHeadless()` alone
     * cannot detect a missing X server: touching the graphics environment is
     * what throws when there is no display.
     */
    private fun hasDisplay(): Boolean = runCatching {
        !java.awt.GraphicsEnvironment.isHeadless() &&
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .screenDevices.isNotEmpty()
    }.getOrDefault(false)

    @Test
    fun resolvingShowsMetadataQualitiesAndStatus() {
        assumeTrue("no display; run with DISPLAY or xvfb-run", hasDisplay())
        val (harness, _) = newHarness()
        try {
            runComposeUiTest {
                setContent { DesktopHarnessApp(harness) }

                onNodeWithTag(DesktopTestTags.URL).performTextClearance()
                onNodeWithTag(DesktopTestTags.URL)
                    .performTextInput("https://www.twitch.tv/fixture")
                onNodeWithTag(DesktopTestTags.RESOLVE).performClick()

                waitUntil(timeoutMillis = 5_000) { harness.result != null }
                waitForIdle()

                onNodeWithText("Fixture stream").assertExists()
                onNodeWithText("by Fixture author").assertExists()
                onNodeWithText("Qualities").assertExists()
                onNodeWithText("best").assertExists()
                onNodeWithText("audio_only").assertExists()
                onNodeWithText("Engine timings", substring = true).assertDoesNotExist()
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun favoriteButtonTracksTheSavedState() {
        assumeTrue("no display; run with DISPLAY or xvfb-run", hasDisplay())
        val (harness, _) = newHarness()
        try {
            runComposeUiTest {
                setContent { DesktopHarnessApp(harness) }

                onNodeWithTag(DesktopTestTags.URL).performTextClearance()
                onNodeWithTag(DesktopTestTags.URL)
                    .performTextInput("https://www.twitch.tv/fixture")
                onNodeWithTag(DesktopTestTags.FAVORITE).performClick()

                waitUntil(timeoutMillis = 5_000) { harness.isFavorite == true }
                waitForIdle()

                onNodeWithText("Remove from My List").assertExists()
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun errorDetailsRenderInTheErrorCard() {
        assumeTrue("no display; run with DISPLAY or xvfb-run", hasDisplay())
        val engine = FakeEngine(
            result = EngineResult(
                status = "error",
                error = "plugin_error",
                errorDetails = ErrorDetails(
                    type = "plugin_error",
                    error = "Failed to extract stream",
                    reason = "Plugin reported an error during extraction",
                    alternative = "Open the stream in the official app.",
                ),
            )
        )
        val (harness, _) = newHarness(engine)
        try {
            runComposeUiTest {
                setContent { DesktopHarnessApp(harness) }

                onNodeWithTag(DesktopTestTags.URL).performTextClearance()
                onNodeWithTag(DesktopTestTags.URL)
                    .performTextInput("https://example.invalid/live")
                onNodeWithTag(DesktopTestTags.RESOLVE).performClick()

                waitUntil(timeoutMillis = 5_000) { harness.result != null }
                waitForIdle()

                // Once in the status line and once in the structured error card.
                onAllNodesWithText("Failed to extract stream").assertCountEquals(2)
                onNodeWithText("Open the stream in the official app.").assertExists()
            }
        } finally {
            harness.close()
        }
    }
}

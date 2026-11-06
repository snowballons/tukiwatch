package com.snowballons.tukiwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic feedback that honours [LocalHapticsEnabled]. When haptics are off the
 * returned object is a no-op, so call sites stay unchanged.
 */
@Composable
fun rememberHaptics(): HapticFeedback {
    val enabled = LocalHapticsEnabled.current
    val current = LocalHapticFeedback.current
    return remember(enabled, current) {
        if (enabled) current else NoopHaptics
    }
}

private object NoopHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

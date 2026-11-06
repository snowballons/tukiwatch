package com.snowballons.tukiwatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The app's corner-radius scale, so radii are named rather than scattered as
 * literals. [small] for chips and pills, [medium] for inputs and rows,
 * [large] for panels, [card] for the primary cards.
 */
object TukiShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(16.dp)
}

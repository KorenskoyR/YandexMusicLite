package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Its content at its own height when that fits, otherwise evenly smaller, from the top, centred across: never cut
 * at the bottom, never over what lies below (on a head unit whose height runs short, the car screen's timeline
 * went under the source dock, or the dock was squeezed to nothing). Touches follow the smaller picture.
 */
@Composable
fun ShrinkToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val scale = shrinkFor(placeable.height, height)
        layout(constraints.maxWidth, height) {
            placeable.placeWithLayer(((constraints.maxWidth - placeable.width) / 2f).roundToInt(), 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}

/** How much smaller content of [contentHeight] must be to fit [available]: 1 when it fits. Pure. */
internal fun shrinkFor(contentHeight: Int, available: Int): Float =
    if (contentHeight <= available || contentHeight <= 0) 1f else available.toFloat() / contentHeight

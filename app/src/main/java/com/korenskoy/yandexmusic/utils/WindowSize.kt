package com.korenskoy.yandexmusic.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize

/** Size of the app window in dp (multi-window and foldable aware, unlike Configuration.screenWidthDp). */
data class WindowSizeDp(val widthDp: Int, val heightDp: Int)

/**
 * The size the app really draws in, in pixels, measured at its root (MainActivity); [IntSize.Zero] where there is
 * no such root (a test of one screen). What the system says of the window can be stale: a VW Golf unit (Q8MAX,
 * Android 15) showing the app in its launcher's small standing card reported that card again a second after the
 * app was opened full screen, and every page took the standing layout on the lying screen (1.15, left-hand drive).
 */
val LocalAppWindowSize = compositionLocalOf { IntSize.Zero }

/** The app's window in pixels: as drawn where known, else as the system says. */
@Composable
fun currentWindowSizePx(): IntSize {
    val drawn = LocalAppWindowSize.current
    if (drawn.width > 0 && drawn.height > 0) return drawn
    return LocalWindowInfo.current.containerSize
}

@Composable
fun currentWindowSizeDp(): WindowSizeDp {
    val size = currentWindowSizePx()
    val density = LocalDensity.current.density
    return WindowSizeDp((size.width / density).toInt(), (size.height / density).toInt())
}

/**
 * Lying or standing by the window itself, not by the system's configuration: a head unit that turns a third of the
 * screen into the whole of it does not always send the new configuration, and the car screen stayed in its standing
 * layout on a lying screen (1.14.10, a VW Golf unit). The configuration only before the window has a size.
 */
@Composable
fun isLandscapeWindow(): Boolean {
    val size = currentWindowSizePx()
    return if (size.width > 0 && size.height > 0) size.width > size.height
    else LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}

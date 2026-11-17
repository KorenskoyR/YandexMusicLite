package com.korenskoy.yandexmusic.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Size of the app window in dp (multi-window and foldable aware, unlike Configuration.screenWidthDp). */
data class WindowSizeDp(val widthDp: Int, val heightDp: Int)

@Composable
fun currentWindowSizeDp(): WindowSizeDp {
    val size = LocalWindowInfo.current.containerSize
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
    val size = LocalWindowInfo.current.containerSize
    return if (size.width > 0 && size.height > 0) size.width > size.height
    else LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}

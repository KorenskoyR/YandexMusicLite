package com.korenskoy.yandexmusic.utils

import androidx.compose.runtime.Composable
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

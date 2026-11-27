package com.korenskoy.yandexmusic.presentation.hifi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

val LocalFullscreenMode = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Applies system bar visibility, cutout handling, and light/dark appearance consistently across
 * Activity and dialog windows (API 25-37).
 */
@Suppress("DEPRECATION")
fun applyWindowSystemBars(
    window: Window,
    darkTheme: Boolean,
    fullscreen: Boolean,
    inMultiWindow: Boolean = false
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val desiredCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val attrs = window.attributes
        if (attrs.layoutInDisplayCutoutMode != desiredCutoutMode) {
            attrs.layoutInDisplayCutoutMode = desiredCutoutMode
            window.attributes = attrs
        }
    }

    val hideBars = fullscreen && !inMultiWindow
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = if (hideBars) {
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
    controller.isAppearanceLightStatusBars = !darkTheme
    controller.isAppearanceLightNavigationBars = !darkTheme

    if (hideBars) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !inMultiWindow) {
        val hideFlags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        var flags = window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        flags = if (hideBars) flags or hideFlags else flags and hideFlags.inv()
        flags = if (!darkTheme) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = if (!darkTheme) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
        if (window.decorView.systemUiVisibility != flags) {
            window.decorView.systemUiVisibility = flags
        }
    }

    ViewCompat.requestApplyInsets(window.decorView)
}

/** Keeps an overlay dialog/sheet window synchronized with [LocalFullscreenMode] and active theme. */
@Composable
fun SyncDialogWindowSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return
    val darkTheme = HiFi.palette.isDark
    val fullscreen = LocalFullscreenMode.current
    val inMultiWindow = view.context.findActivity()?.isInMultiWindowMode == true

    LaunchedEffect(dialogWindow, darkTheme, fullscreen, inMultiWindow) {
        if (!inMultiWindow || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        }
        applyWindowSystemBars(dialogWindow, darkTheme, fullscreen, inMultiWindow)
        dialogWindow.decorView.post {
            applyWindowSystemBars(dialogWindow, darkTheme, fullscreen, inMultiWindow)
        }
    }
}

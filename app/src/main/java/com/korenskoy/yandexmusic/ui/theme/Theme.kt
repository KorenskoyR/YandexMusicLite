package com.korenskoy.yandexmusic.ui.theme

import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Redesign 2.0: the single blue accent of the Hi-Fi language on a near-black ground
private val DarkColorScheme = darkColorScheme(
    primary = HiFiAccent,
    secondary = HiFiAccent,
    tertiary = HiFiAccent,
    background = HiFiGround,
    surface = DarkSurface,
    onPrimary = LightText,
    onSecondary = LightText,
    onTertiary = LightText,
    onBackground = LightText,
    onSurface = LightText,
)

@Composable
fun YandexMusicTheme(
    darkTheme: Boolean = true, // Force Dark Theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            var activity: Activity? = null
            while (context is ContextWrapper) {
                if (context is Activity) {
                    activity = context
                    break
                }
                context = context.baseContext
            }
            val window = activity?.window
            if (window != null) {
                // Always dark bars: a light navigation bar on a head unit is painted light by the vendor
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
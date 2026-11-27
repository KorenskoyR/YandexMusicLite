package com.korenskoy.yandexmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.korenskoy.yandexmusic.presentation.hifi.HiFiPalette
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiPalette

// Monochrome Hi-Fi Material fallback schemes matching dark and light palettes
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE6EAF2),
    secondary = Color(0xFFC9CED8),
    tertiary = Color(0xFFB4BAC6),
    background = HiFiGround,
    surface = DarkSurface,
    onPrimary = Color(0xFF11131A),
    onSecondary = Color(0xFF11131A),
    onTertiary = Color(0xFF11131A),
    onBackground = LightText,
    onSurface = LightText,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF181B24),
    secondary = Color(0xFF3A4152),
    tertiary = Color(0xFF525A6C),
    background = Color(0xFFE6E8EE),
    surface = Color(0xFFF3F5FA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF11131A),
    onSurface = Color(0xFF11131A),
)

@Composable
fun YandexMusicTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) HiFiPalette.Dark else HiFiPalette.Light
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalHiFiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
package com.korenskoy.yandexmusic.presentation.splash

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.korenskoy.yandexmusic.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val minSide = minOf(windowSize.widthDp, windowSize.heightDp)
    val scaleFactor = (minSide / 400f).coerceIn(0.7f, 1.4f)

    val infiniteTransition = rememberInfiniteTransition(label = stringResource(R.string.splash_resonance))

    // 1. Subwoofer breath / pulse effect for the main sphere
    val sphereScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = stringResource(R.string.splash_spherepulse)
    )

    // 2. Continuous expanding soundwaves (Golden / Neon)
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = stringResource(R.string.auth_waveprogress)
    )

    // 3. Typography fade-in and overall fade-out transition
    val textAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Short brand moment (~0.8 s): the cached library is already loading behind it
        textAlpha.animateTo(1f, animationSpec = tween(300, delayMillis = 100))
        delay(250)
        screenAlpha.animateTo(0f, animationSpec = tween(200))
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .alpha(screenAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        // Acoustic resonance rings
        Canvas(modifier = Modifier.size((280 * scaleFactor).dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f
            
            // First expanding ring
            val radius1 = maxRadius * waveProgress
            val alpha1 = 1f - waveProgress
            val brush1 = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFFFFB703).copy(alpha = alpha1 * 0.4f), Color.Transparent),
                center = center,
                radius = if (radius1 > 0) radius1 else 1f
            )
            drawCircle(brush = brush1, radius = radius1, center = center)

            // Second delayed ring
            val waveProgress2 = (waveProgress + 0.5f) % 1f
            val radius2 = maxRadius * waveProgress2
            val alpha2 = 1f - waveProgress2
            val brush2 = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFFE11D48).copy(alpha = alpha2 * 0.3f), Color.Transparent),
                center = center,
                radius = if (radius2 > 0) radius2 else 1f
            )
            drawCircle(brush = brush2, radius = radius2, center = center)
        }

        // The glowing Audio-Sphere (matches the system splash screen icon)
        Image(
            painter = painterResource(id = R.drawable.splash_icon),
            contentDescription = stringResource(R.string.cd_logotip_prilozheniya),
            modifier = Modifier
                .size((160 * scaleFactor).dp)
                .scale(sphereScale)
        )

        // Typography block
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 24.dp else (48 * scaleFactor).dp)
                .alpha(textAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.splash_yandex_music_lite),
                style = TextStyle(
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (3 * scaleFactor).sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_lossless_audio),
                style = TextStyle(
                    color = Color(0xFFFFB703),
                    fontSize = (12 * scaleFactor).sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (2 * scaleFactor).sp
                )
            )
        }
    }
}

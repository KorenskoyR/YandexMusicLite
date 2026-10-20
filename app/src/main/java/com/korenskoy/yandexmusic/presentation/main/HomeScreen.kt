package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    isPlaying: Boolean,
    isWavePlaying: Boolean,
    onPlayPauseVibe: () -> Unit,
    isLoading: Boolean = false
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val scaleFactor = (minOf(windowSize.widthDp, windowSize.heightDp) / 400f).coerceIn(0.8f, 2.0f)

    val infiniteTransition = rememberInfiniteTransition(label = "wave_spheres")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Make the spheres pulse slightly more aggressively if playing the wave
    val scaleTarget = if (isPlaying && isWavePlaying) 1.25f else 1.1f
    val scaleSpecDuration = if (isPlaying && isWavePlaying) 800 else 4000
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = scaleTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(scaleSpecDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E22),
                        Color(0xFF121214)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated Spheres Background
        Box(
            modifier = Modifier
                .size((300 * scaleFactor).dp)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x66FF3366),
                            Color(0x00FF3366)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size((400 * scaleFactor).dp)
                .graphicsLayer {
                    rotationZ = -rotation
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x443366FF),
                            Color(0x003366FF)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 0.dp else 40.dp, start = if (isLandscape) 60.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_moya_volna),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (36 * scaleFactor).sp
            )
            Spacer(modifier = Modifier.height((32 * scaleFactor).dp))

            // Play / Pause Button with interaction bounce & Liquid Glass Glow
            val buttonScale by animateFloatAsState(
                targetValue = if (isPlaying && isWavePlaying) 1.06f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "button_scale"
            )

            Box(
                modifier = Modifier
                    .size((96 * scaleFactor).dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .shadow(
                        elevation = if (isPlaying && isWavePlaying) 28.dp else 12.dp,
                        shape = CircleShape,
                        spotColor = Color(0xFFFF3366).copy(alpha = if (isPlaying && isWavePlaying) 0.85f else 0.4f),
                        ambientColor = Color(0xFFFF8833).copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF3366),
                                Color(0xFFFF7722),
                                Color(0xFFFF9900)
                            )
                        )
                    )
                    .border(
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.25f))
                            )
                        ),
                        CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPauseVibe
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    // The wave is loading or the first track is buffering
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size((40 * scaleFactor).dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying && isWavePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying && isWavePlaying) "Pause" else "Play My Vibe",
                        modifier = Modifier.size((46 * scaleFactor).dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

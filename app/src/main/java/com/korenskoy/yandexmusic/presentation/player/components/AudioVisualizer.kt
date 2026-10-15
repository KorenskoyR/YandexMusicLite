package com.korenskoy.yandexmusic.presentation.player.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val barCount = if (isCompact) 4 else 6
    val barWidth = if (isCompact) 3.dp else 4.dp
    val spacing = if (isCompact) 2.dp else 4.dp
    val maxHeight = if (isCompact) 16.dp else 32.dp

    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            // Calculate a pseudo-random phase offset for each bar
            val phaseOffset = i * (Math.PI.toFloat() / (barCount / 2f)) + (i * 0.5f)
            val sineValue = sin(phase + phaseOffset) // range [-1, 1]
            val normalizedScale = (sineValue + 1f) / 2f // range [0, 1]
            val targetScale = 0.3f + (normalizedScale * 0.7f) // range [0.3, 1.0]

            val finalScale = if (isPlaying) targetScale else 0.2f

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f) // Scale from center vertically
                        scaleY = finalScale
                    }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

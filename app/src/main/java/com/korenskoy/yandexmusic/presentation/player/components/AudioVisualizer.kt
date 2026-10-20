package com.korenskoy.yandexmusic.presentation.player.components

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
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
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val barCount = if (isCompact) 4 else 6
    val barWidth = if (isCompact) 3.dp else 4.dp
    val spacing = if (isCompact) 2.dp else 4.dp
    val maxHeight = if (isCompact) 16.dp else 32.dp

    // The animation runs only while playing; paused bars are static and cost nothing per frame
    val phaseState: State<Float> = if (isPlaying) {
        rememberInfiniteTransition(label = "visualizer_transition").animateFloat(
            initialValue = 0f,
            targetValue = 2 * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            // Calculate a pseudo-random phase offset for each bar
            val phaseOffset = i * (Math.PI.toFloat() / (barCount / 2f)) + (i * 0.5f)

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f) // Scale from center vertically
                        // Read in the draw phase so the Row is not recomposed every frame
                        scaleY = if (isPlaying) {
                            val normalizedScale = (sin(phaseState.value + phaseOffset) + 1f) / 2f // range [0, 1]
                            0.3f + (normalizedScale * 0.7f) // range [0.3, 1.0]
                        } else {
                            0.2f
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

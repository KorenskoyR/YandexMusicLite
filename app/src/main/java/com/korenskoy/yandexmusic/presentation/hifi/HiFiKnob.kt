package com.korenskoy.yandexmusic.presentation.hifi

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The "knob": a black anodized disc with a metal rim and fine concentric grooves, like the controls of
 * premium audio hardware. Presses in to 0.96, springs back and gives a short haptic tick.
 * Everything static is cached (drawWithCache); the press only changes a layer scale — no redraw.
 */
@Composable
fun HiFiKnob(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) tween(80, easing = HiFiMotion.Easing) else HiFiMotion.spring(),
        label = "knob_press"
    )
    val view = LocalView.current
    val grooves = size >= 56.dp
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val rimWidth = max(1.5.dp.toPx(), r * 0.032f)
                val faceR = r - rimWidth
                val shadow = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    center = c + Offset(0f, r * 0.22f),
                    radius = r * 1.32f
                )
                val rim = Brush.sweepGradient(HiFi.MetalSweep, center = c)
                val face = Brush.radialGradient(
                    colors = HiFi.KnobFace,
                    center = c - Offset(0f, faceR * 0.44f),
                    radius = faceR * 1.4f
                )
                val bottomShade = Brush.verticalGradient(
                    0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f),
                    startY = c.y - faceR, endY = c.y + faceR
                )
                val grooveStep = 3.dp.toPx()
                val grooveStroke = Stroke(width = 1f)
                onDrawBehind {
                    drawCircle(shadow, radius = r * 1.32f, center = c + Offset(0f, r * 0.22f))
                    drawCircle(rim, radius = r, center = c)
                    drawCircle(face, radius = faceR, center = c)
                    if (grooves) {
                        var gr = faceR * 0.2f
                        while (gr < faceR) {
                            drawCircle(Color.White.copy(alpha = 0.03f), radius = gr, center = c, style = grooveStroke)
                            gr += grooveStep
                        }
                    }
                    drawCircle(bottomShade, radius = faceR, center = c)
                    drawArc(
                        color = Color.White.copy(alpha = 0.16f),
                        startAngle = 200f, sweepAngle = 140f, useCenter = false,
                        topLeft = c - Offset(faceR - 1f, faceR - 1f),
                        size = androidx.compose.ui.geometry.Size((faceR - 1f) * 2, (faceR - 1f) * 2),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Play ↔ Pause glyph that cross-fades (120 ms) instead of jumping; play is optically nudged right. */
@Composable
fun PlayPauseGlyph(isPlaying: Boolean, size: Dp, tint: Color = Color(0xFFF4F6FA)) {
    Crossfade(targetState = isPlaying, animationSpec = HiFiMotion.quick(), label = "play_pause") { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .offset(x = if (playing) 0.dp else size * 0.05f)
        )
    }
}

enum class RingMode { Playing, Paused, Loading }

/** Bar lengths of the spectrum ring. Pure and deterministic so the look is unit-tested. */
object SpectrumMath {
    /** Length 0..1 of bar [i] of [n] at time [t] seconds. Paused bars collapse to dots (0). */
    fun level(i: Int, n: Int, t: Float, mode: RingMode): Float = when (mode) {
        RingMode.Paused -> 0f
        RingMode.Playing -> {
            val base = 0.5f + 0.5f * sin(i * 0.42f + t * 2.2f)
            val envelope = 0.55f + 0.45f * sin(i * 0.13f + t * 0.9f + 2.1f)
            val jitter = abs(sin(i * 1.7f + t * 3.1f)) * 0.18f
            (base * base * envelope * 0.82f + jitter).coerceIn(0.08f, 1f)
        }
        RingMode.Loading -> {
            // A bright wave running around the ring
            val head = (t * 0.6f) % 1f
            var d = abs(i.toFloat() / n - head)
            if (d > 0.5f) d = 1f - d
            (0.06f + 0.94f * exp(-(d * d) / 0.006f)).coerceIn(0f, 1f)
        }
    }

    /** Whether bar [i] of [n] lies in the already played arc (clockwise from the top). */
    fun isPlayed(i: Int, n: Int, progress: Float): Boolean = i.toFloat() / n < progress
}

/**
 * A ring of [bars] strokes around a knob: an equalizer and the track progress in one. One Canvas draw
 * per frame; the clock only runs while playing or loading (and never in light mode).
 */
@Composable
fun SpectrumRing(
    mode: RingMode,
    progress: () -> Float,
    bars: Int,
    innerRadius: Dp,
    maxLength: Dp,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    playedColor: Color = Color.White.copy(alpha = 0.95f),
    restColor: Color = Color.White.copy(alpha = 0.26f)
) {
    val lite = LocalHiFiLite.current
    val animate = !lite && mode != RingMode.Paused
    val clock: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "spectrum")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3600f,
            animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing)),
            label = "spectrum_clock"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier = modifier) {
        val t = clock.value
        val c = Offset(size.width / 2f, size.height / 2f)
        val inner = innerRadius.toPx()
        val maxLen = maxLength.toPx()
        val stroke = barWidth.toPx()
        val played = progress().coerceIn(0f, 1f)
        for (i in 0 until bars) {
            val angle = (i.toFloat() / bars) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val dx = cos(angle)
            val dy = sin(angle)
            val len = SpectrumMath.level(i, bars, t, mode) * maxLen
            val start = Offset(c.x + dx * inner, c.y + dy * inner)
            val end = Offset(c.x + dx * (inner + len), c.y + dy * (inner + len))
            drawLine(
                color = if (SpectrumMath.isPlayed(i, bars, played)) playedColor else restColor,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

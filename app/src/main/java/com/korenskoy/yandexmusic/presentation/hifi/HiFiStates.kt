package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.R

/** What a screen (or a part of it) shows. One vocabulary for every list in the app. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Failed : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>

    companion object {
        /** Data wins over a stale error: cached tracks stay visible while a refresh fails. Pure; unit-tested. */
        fun <T> of(data: T, isEmpty: Boolean, loading: Boolean, failed: Boolean): LoadState<T> = when {
            !isEmpty -> Ready(data)
            loading -> Loading
            failed -> Failed
            else -> Empty
        }
    }
}

/**
 * Empty or failed content, the same everywhere: an icon plaque, a short title, one line of explanation
 * and at most one action (a metal capsule).
 */
@Composable
fun HiFiEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconPlaque(icon, size = 56.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = HiFiType.CardTitle.copy(fontSize = HiFiType.RowTitle.fontSize), textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = HiFiType.Caption, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            MetalPillButton(actionLabel, onAction, height = 44.dp)
        }
    }
}

/** "Could not load" with a retry; the app also retries by itself when the network comes back. */
@Composable
fun HiFiErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, message: String? = null) {
    HiFiEmptyState(
        icon = Icons.Rounded.CloudOff,
        title = stringResource(R.string.state_error_title),
        message = message ?: stringResource(R.string.state_error_message),
        actionLabel = stringResource(R.string.state_retry),
        onAction = onRetry,
        modifier = modifier
    )
}

/** Placeholders in the shape of track rows while a list loads. The pulse is read in the draw phase. */
@Composable
fun HiFiSkeletonRows(modifier: Modifier = Modifier, count: Int = 6, coverSize: Dp = 52.dp) {
    val lite = LocalHiFiLite.current
    val pulse: State<Float>? = if (!lite) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.05f,
            targetValue = 0.12f,
            animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
            label = "skeleton_alpha"
        )
    } else null
    val loading = stringResource(R.string.state_loading)
    Column(modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(pulse, Modifier.size(coverSize), 10.dp)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Slightly different widths so the rows do not look like a grid
                    SkeletonBlock(pulse, Modifier.width((170 - (i % 3) * 30).dp).height(12.dp), 6.dp)
                    SkeletonBlock(pulse, Modifier.width((110 - (i % 2) * 20).dp).height(10.dp), 5.dp)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(pulse: State<Float>?, modifier: Modifier, radius: Dp) {
    Box(
        modifier.drawBehind {
            drawRoundRect(Color.White.copy(alpha = pulse?.value ?: 0.08f), cornerRadius = CornerRadius(radius.toPx()))
        }
    )
}

/** The Hi-Fi activity mark: a thin white arc turning in place, drawn and rotated in the draw phase. */
@Composable
fun HiFiActivityIndicator(modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = Color.White) {
    val turn = rememberInfiniteTransition(label = "activity").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "activity_turn"
    )
    val loading = stringResource(R.string.state_loading)
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = loading }
            .drawBehind {
                val stroke = (size.toPx() / 11f).coerceAtLeast(1.5f)
                drawCircle(color.copy(alpha = 0.18f), radius = this.size.minDimension / 2f - stroke / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                rotate(turn.value) {
                    drawArc(
                        color = color,
                        startAngle = 0f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                        size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
    )
}

/** The one message plate of the app: dark glass with a hairline rim, white text — above the mini-player. */
@Composable
fun HiFiSnackbar(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .hifiSoftShadow(androidx.compose.foundation.shape.RoundedCornerShape(18.dp), 20.dp)
            .hifiGlass(androidx.compose.foundation.shape.RoundedCornerShape(18.dp), fill = Color(0xF21A1D26), border = HiFi.GlassBorder)
            .padding(start = 18.dp, end = if (actionLabel != null) 6.dp else 18.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, style = HiFiType.Body, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                actionLabel,
                color = Color(0xFF8FB6FF),
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * The Hi-Fi progress bar: a recessed groove with a white fill. [progress] null means "working, amount
 * unknown": a short white segment sweeps along the groove (still in lite mode).
 */
@Composable
fun HiFiProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val sweep: State<Float>? = if (progress == null && !LocalHiFiLite.current) {
        rememberInfiniteTransition(label = "progress_sweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing)),
            label = "progress_sweep_phase"
        )
    } else null
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(this.size.height / 2f)
                drawRoundRect(Color.Black.copy(alpha = 0.45f), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                if (progress != null) {
                    val w = this.size.width * progress().coerceIn(0f, 1f)
                    if (w > 0f) drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(this.size.height), this.size.height), cornerRadius = r)
                } else {
                    val segment = this.size.width * 0.28f
                    val x = -segment + (sweep?.value ?: 0.5f) * (this.size.width + segment)
                    clipRect {
                        drawRoundRect(
                            Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(segment, this.size.height),
                            cornerRadius = r
                        )
                    }
                }
            }
    )
}

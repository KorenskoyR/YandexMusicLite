package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.runtime.getValue
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph
import com.korenskoy.yandexmusic.utils.browserUserAgent
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val MiniShape = RoundedCornerShape(31.dp)

/** What a drag on the mini-player does. Pure; unit-tested. */
object MiniGesture {
    /** [LOCKED]: a sideways drag while swiping away is off; it moves nothing. */
    enum class Axis { NONE, HORIZONTAL, VERTICAL, LOCKED }
    enum class Outcome { NONE, DISMISS, OPEN }

    /** Sideways past 100 dp dismisses; up past 48 dp opens the player. */
    const val DISMISS_DP = 100f
    const val OPEN_DP = 48f

    fun axisOf(dx: Float, dy: Float): Axis = when {
        dx == 0f && dy == 0f -> Axis.NONE
        abs(dx) >= abs(dy) -> Axis.HORIZONTAL
        else -> Axis.VERTICAL
    }

    fun outcome(axis: Axis, offsetX: Float, offsetY: Float, density: Float): Outcome = when {
        axis == Axis.HORIZONTAL && abs(offsetX) > DISMISS_DP * density -> Outcome.DISMISS
        axis == Axis.VERTICAL && -offsetY > OPEN_DP * density * 0.6f -> Outcome.OPEN
        else -> Outcome.NONE
    }
}

/**
 * The floating mini-player: a glass capsule above the tab bar with the cover, the track, a metal
 * play/pause knob and "next". Swipe sideways to dismiss; pull it up and it rises after the finger and
 * opens the full player. [progress] is read only while drawing the
 * hairline at the bottom, so the capsule itself is not recomposed on every progress tick.
 */
@Composable
fun MiniPlayer(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    progress: () -> Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    swipeToDismiss: Boolean = true,
    coverModifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(track.idString) { offsetX.snapTo(0f) }
    val open by androidx.compose.runtime.rememberUpdatedState(onClick)
    val dismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val swipeToDismissNow by androidx.compose.runtime.rememberUpdatedState(swipeToDismiss)
    val durationMs = (track.durationMs ?: 0L).coerceAtLeast(0L)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                alpha = 1f - (abs(offsetX.value) / (300f * density.density)).coerceIn(0f, 0.8f)
                // Pulled up, the capsule grows a little towards the player it opens
                val lift = (-offsetY.value / (80f * density.density)).coerceIn(0f, 1f)
                scaleX = 1f + 0.04f * lift
                scaleY = 1f + 0.04f * lift
            }
            .hifiSoftShadow(MiniShape, 18.dp)
            .clip(MiniShape)
            .background(HiFi.BarFill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), MiniShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_razvernut_pleer), onClick = onClick)
            .pointerInput(Unit) {
                // The first clear movement decides: sideways dismisses, upwards opens the player
                var axis = MiniGesture.Axis.NONE
                detectDragGestures(
                    onDragStart = { axis = MiniGesture.Axis.NONE },
                    onDragEnd = {
                        when (MiniGesture.outcome(axis, offsetX.value, offsetY.value, density.density)) {
                            MiniGesture.Outcome.DISMISS -> scope.launch {
                                offsetX.animateTo(if (offsetX.value > 0) 1000f else -1000f)
                                dismiss()
                            }
                            MiniGesture.Outcome.OPEN -> {
                                open()
                                scope.launch { offsetY.animateTo(0f, HiFiMotion.spring()) }
                            }
                            MiniGesture.Outcome.NONE -> scope.launch {
                                launch { offsetX.animateTo(0f, HiFiMotion.spring()) }
                                offsetY.animateTo(0f, HiFiMotion.spring())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { offsetX.animateTo(0f) }
                            offsetY.animateTo(0f)
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    if (axis == MiniGesture.Axis.NONE) {
                        axis = MiniGesture.axisOf(drag.x, drag.y)
                        // Sideways does nothing when swiping away is off: a bump in the car must not stop the music
                        if (axis == MiniGesture.Axis.HORIZONTAL && !swipeToDismissNow) axis = MiniGesture.Axis.LOCKED
                    }
                    scope.launch {
                        when (axis) {
                            MiniGesture.Axis.HORIZONTAL -> offsetX.snapTo(offsetX.value + drag.x)
                            // Up follows the finger with resistance, down hardly moves
                            MiniGesture.Axis.VERTICAL -> offsetY.snapTo(
                                (offsetY.value + drag.y * if (offsetY.value + drag.y < 0) 0.6f else 0.15f)
                                    .coerceIn(-120f * density.density, 12f * density.density)
                            )
                            MiniGesture.Axis.NONE, MiniGesture.Axis.LOCKED -> Unit
                        }
                    }
                }
            }
            .drawBehind {
                // Progress hairline along the bottom edge
                if (durationMs > 0 && !track.isRadio) {
                    val fraction = (progress().toFloat() / durationMs).coerceIn(0f, 1f)
                    val inset = 24.dp.toPx()
                    val y = size.height - 1.dp.toPx()
                    val w = size.width - inset * 2
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(inset, y), Offset(inset + w, y), 2.dp.toPx())
                    if (fraction > 0f) drawLine(Color.White.copy(alpha = 0.9f), Offset(inset, y), Offset(inset + w * fraction, y), 2.dp.toPx())
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 9.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            Box(
                modifier = coverModifier
                    .size(44.dp)
                    .hifiSoftShadow(RoundedCornerShape(12.dp), 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (track.isRadio) Color.White else HiFi.Placeholder),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .cover(track.coverUri, CoverSize.Small)
                        .crossfade(true)
                        .apply { if (track.isRadio) browserUserAgent() }
                        .build(),
                    contentDescription = stringResource(R.string.cd_oblozhka),
                    contentScale = if (track.isRadio) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (track.isRadio) 4.dp else 0.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: stringResource(R.string.bez_nazvaniya),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.isRadio) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HiFi.Live))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mini_player_live), color = HiFi.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                } else {
                    Text(
                        text = track.artists.joinToString { it.name ?: "" }.ifEmpty { stringResource(R.string.player_unknown_artist) },
                        color = HiFi.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            HiFiKnob(
                onClick = onPlayPauseClick,
                contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza),
                size = 44.dp
            ) {
                PlayPauseGlyph(isPlaying = isPlaying, size = 22.dp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onNextClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = stringResource(if (track.isRadio) R.string.cd_next_station else R.string.cd_sleduyuschiy_trek),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

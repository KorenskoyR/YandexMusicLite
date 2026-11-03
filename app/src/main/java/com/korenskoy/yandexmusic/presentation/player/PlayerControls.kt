package com.korenskoy.yandexmusic.presentation.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph

// --- Controls ---------------------------------------------------------------------------------------

/** Glass dislike · metal previous · large metal play · metal next · glass like. Shrinks to fit narrow phones. */
@Composable
internal fun PlayerControls(
    isPlaying: Boolean,
    isLiked: Boolean,
    isRadio: Boolean,
    compact: Boolean,
    onDislike: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit
) {
    // Wide rows (landscape, tablets) keep the cluster together instead of spreading it to the edges
    BoxWithConstraints(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 420.dp)) {
        // 52 + 68 + 96 + 68 + 52 = 336 dp of buttons; narrower rows scale them down
        val fit = (maxWidth / 344.dp).coerceIn(0.7f, 1f)
        val k = if (compact) fit * 0.8f else fit
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DislikeButton(onDislikeClick = onDislike, size = 52.dp * k)
            HiFiKnob(
                onClick = onPrev,
                contentDescription = stringResource(if (isRadio) R.string.cd_prev_station else R.string.cd_predyduschiy_trek),
                size = 68.dp * k
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(30.dp * k))
            }
            HiFiKnob(onClick = onPlayPause, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = 96.dp * k) {
                PlayPauseGlyph(isPlaying = isPlaying, size = 40.dp * k)
            }
            HiFiKnob(
                onClick = onNext,
                contentDescription = stringResource(if (isRadio) R.string.cd_next_station else R.string.cd_sleduyuschiy_trek),
                size = 68.dp * k
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(30.dp * k))
            }
            LikeHeartButton(isLiked = isLiked, onToggleLike = onLike, size = 52.dp * k)
        }
    }
}

/** A round glass button that sinks a little under the finger (no glow, no shadow band). */
@Composable
private fun GlassPressButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, HiFiMotion.spring(), label = "glass_press")
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun LikeHeartButton(isLiked: Boolean, onToggleLike: () -> Unit, size: Dp = 52.dp) {
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    GlassPressButton(
        onClick = {
            if (!isLiked) scope.launch {
                pop.snapTo(1.3f)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            onToggleLike()
        },
        contentDescription = stringResource(R.string.cd_nravitsya),
        size = size
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.86f),
            modifier = Modifier
                .size(size * 0.43f)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        )
    }
}

@Composable
fun DislikeButton(onDislikeClick: () -> Unit, size: Dp = 52.dp) {
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    GlassPressButton(
        onClick = {
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(450))
            }
            onDislikeClick()
        },
        contentDescription = stringResource(R.string.cd_ne_nravitsya),
        size = size
    ) {
        val base = Color.White.copy(alpha = 0.86f)
        Icon(
            imageVector = Icons.Outlined.HeartBroken,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.lerp(base, HiFi.Danger, flash.value),
            modifier = Modifier
                .size(size * 0.43f)
                .graphicsLayer {
                    val s = 1f + 0.25f * flash.value
                    scaleX = s
                    scaleY = s
                }
        )
    }
}

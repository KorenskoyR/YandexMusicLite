package com.korenskoy.yandexmusic.presentation.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.AppDialog
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.utils.currentWindowSizeDp

/** The sizes of the window: large on a head unit, compact on a phone or a low screen. */
private data class Sizes(
    val knob: Dp,
    val ring: Dp,
    val title: TextUnit,
    val body: TextUnit,
    val bodyLine: TextUnit,
    val button: Dp,
    val gap: Dp,
    val footer: Boolean
)

private val Large = Sizes(132.dp, 168.dp, 34.sp, 21.sp, 29.sp, 64.dp, 18.dp, true)
private val Compact = Sizes(92.dp, 116.dp, 24.sp, 16.sp, 22.sp, 52.dp, 12.dp, true)
private val Low = Sizes(72.dp, 88.dp, 22.sp, 15.sp, 20.sp, 48.dp, 8.dp, false)

/**
 * The app's own listening window, where the device has no voice input window of its own: the microphone with
 * how loud it hears, the words as they come, then what was heard. On a phone it rises from the bottom; on a
 * head unit or a wide screen it stands in the middle, large enough to read while driving.
 */
@Composable
fun VoiceListeningWindow(state: Listening, onRetry: () -> Unit, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    AppDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = currentWindowSizeDp()
        val wide = window.widthDp >= WIDE_DP
        val sizes = when {
            window.heightDp < LOW_DP -> Low
            wide && window.heightDp >= TALL_DP -> Large
            else -> Compact
        }
        Box(
            Modifier.fillMaxSize().padding(if (wide) 24.dp else 0.dp),
            contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter
        ) {
            val shape = if (wide) RoundedCornerShape(32.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier
                    .then(if (wide) Modifier.widthIn(max = 700.dp) else Modifier)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(HiFi.SheetFill)
                    .border(1.dp, HiFi.GlassBorder, shape)
                    .then(if (wide) Modifier else Modifier.navigationBarsPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = sizes.gap + 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Content(state, sizes, onRetry, onOpenSettings, onClose)
            }
        }
    }
}

@Composable
private fun Content(state: Listening, sizes: Sizes, onRetry: () -> Unit, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    Text(
        stringResource(R.string.voice_search).uppercase(),
        color = HiFi.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp
    )
    Spacer(Modifier.height(sizes.gap))
    Microphone(state, sizes, onTap = if (state is Listening.Hearing) onClose else onRetry)
    Spacer(Modifier.height(sizes.gap))
    val (title, body) = words(state)
    Text(
        title,
        color = Color.White, fontSize = sizes.title, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (body.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(body, color = HiFi.TextSecondary, fontSize = sizes.body, lineHeight = sizes.bodyLine, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    if (state !is Listening.Heard) {
        Spacer(Modifier.height(sizes.gap + 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            when (state) {
                is Listening.Missed, is Listening.Failed ->
                    MetalPillButton(stringResource(R.string.voice_again), onRetry, Modifier.weight(1f, fill = false).widthIn(min = 150.dp), height = sizes.button)
                is Listening.NoMic ->
                    MetalPillButton(stringResource(R.string.voice_open_settings), onOpenSettings, Modifier.weight(1f, fill = false).widthIn(min = 180.dp), height = sizes.button)
                else -> Unit
            }
            GlassPillButton(stringResource(R.string.voice_cancel), onClose, Modifier.widthIn(min = 140.dp), height = sizes.button)
        }
    }
    if (sizes.footer) {
        Spacer(Modifier.height(sizes.gap))
        Text(stringResource(R.string.voice_by_service), color = HiFi.TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** The metal microphone; while it listens, a ring around it breathes with the voice. */
@Composable
private fun Microphone(state: Listening, sizes: Sizes, onTap: () -> Unit) {
    val level = (state as? Listening.Hearing)?.level ?: 0f
    val grow by animateFloatAsState(if (state is Listening.Hearing) 1f + 0.12f * level else 1f, tween(90), label = "voice_level")
    val noMic = state is Listening.NoMic
    Box(Modifier.size(sizes.ring), contentAlignment = Alignment.Center) {
        if (state is Listening.Hearing) {
            Box(
                Modifier
                    .size(sizes.ring)
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                        alpha = 0.45f + 0.55f * level
                    }
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            )
        }
        HiFiKnob(onClick = onTap, contentDescription = stringResource(R.string.voice_search), size = sizes.knob) {
            Icon(
                if (noMic) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = null,
                tint = if (noMic) HiFi.Warning else HiFi.OnMetal,
                modifier = Modifier.size(sizes.knob * 0.4f).align(Alignment.Center)
            )
        }
    }
}

/** The title and the line under it, for each state. */
@Composable
private fun words(state: Listening): Pair<String, String> = when (state) {
    is Listening.Hearing -> stringResource(R.string.voice_speak) to state.partial.ifEmpty { stringResource(R.string.voice_speak_hint) }
    is Listening.Heard -> stringResource(R.string.voice_heard, state.text) to stringResource(R.string.voice_heard_hint)
    Listening.Missed -> stringResource(R.string.voice_missed) to stringResource(R.string.voice_missed_hint)
    Listening.NoMic -> stringResource(R.string.voice_no_mic) to stringResource(R.string.voice_no_mic_hint)
    is Listening.Failed -> stringResource(R.string.voice_failed_title) to stringResource(
        when (state.why) {
            Listening.Why.NETWORK -> R.string.voice_failed_network
            Listening.Why.LANGUAGE -> R.string.voice_failed_language
            Listening.Why.OTHER -> R.string.voice_failed_other
        }
    )
}

private const val WIDE_DP = 600
private const val TALL_DP = 560
private const val LOW_DP = 420

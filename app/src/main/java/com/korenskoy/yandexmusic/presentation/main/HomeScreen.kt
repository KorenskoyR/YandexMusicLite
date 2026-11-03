package com.korenskoy.yandexmusic.presentation.main

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLargeTitle
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTextAction
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph
import com.korenskoy.yandexmusic.presentation.hifi.RingMode
import com.korenskoy.yandexmusic.presentation.hifi.SpectrumRing
import com.korenskoy.yandexmusic.utils.toCoverUrl

/**
 * "Моя волна" — the face of the app: a metal knob inside a spectrum ring (equalizer and track progress
 * in one), the playing track and what comes next. Nothing else on the screen.
 *
 * [waveTrack] is the current track only while the wave is the playing source; otherwise the page shows
 * just the knob, which starts the wave.
 */
@Composable
fun HomeScreen(
    waveTrack: TrackDetailsDto?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isLiked: Boolean,
    progress: () -> Long,
    upNext: List<TrackDetailsDto>,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenQueue: () -> Unit,
    onUpNextClick: (TrackDetailsDto) -> Unit,
    contentBottom: Dp = 100.dp
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val minDim = minOf(windowSize.widthDp, windowSize.heightDp).toFloat()
    val knobSize = (minDim * if (isLandscape) 0.34f else 0.36f).dp.coerceIn(120.dp, 200.dp)

    val duration = (waveTrack?.durationMs ?: 0L).coerceAtLeast(0L)
    val progressFraction = { if (duration > 0) progress().toFloat() / duration else 0f }
    val mode = when {
        isLoading -> RingMode.Loading
        waveTrack != null && isPlaying -> RingMode.Playing
        else -> RingMode.Paused
    }

    val instrument: @Composable () -> Unit = {
        WaveInstrument(
            knobSize = knobSize,
            mode = mode,
            isPlaying = waveTrack != null && isPlaying,
            progress = progressFraction,
            onPlayPause = onPlayPause
        )
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 140.dp, end = 24.dp, top = 16.dp, bottom = contentBottom),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { instrument() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                HiFiLargeTitle(stringResource(R.string.wave_title))
                Spacer(Modifier.height(20.dp))
                WaveTrackInfo(waveTrack, isLiked, onLike, onDislike)
                WaveUpNext(upNext, onOpenQueue, onUpNextClick, horizontalPadding = 0.dp)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentBottom)
        ) {
            HiFiLargeTitle(
                stringResource(R.string.wave_title),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { instrument() }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.padding(horizontal = 20.dp)) { WaveTrackInfo(waveTrack, isLiked, onLike, onDislike) }
            WaveUpNext(upNext, onOpenQueue, onUpNextClick, horizontalPadding = 20.dp)
        }
    }
}

/** Knob + ring. The ring is ~1.7× the knob: room for the longest bars. */
@Composable
private fun WaveInstrument(knobSize: Dp, mode: RingMode, isPlaying: Boolean, progress: () -> Float, onPlayPause: () -> Unit) {
    val ringSize = knobSize * 1.72f
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        SpectrumRing(
            mode = mode,
            progress = progress,
            bars = 72,
            innerRadius = knobSize / 2 + knobSize * 0.08f,
            maxLength = knobSize * 0.24f,
            modifier = Modifier.size(ringSize)
        )
        HiFiKnob(
            onClick = onPlayPause,
            contentDescription = stringResource(if (isPlaying) R.string.wave_pause else R.string.wave_play),
            size = knobSize
        ) {
            PlayPauseGlyph(isPlaying = isPlaying, size = knobSize * 0.3f)
        }
    }
}

@Composable
private fun WaveTrackInfo(track: TrackDetailsDto?, isLiked: Boolean, onLike: () -> Unit, onDislike: () -> Unit) {
    AnimatedContent(
        targetState = track,
        contentKey = { it?.idString },
        transitionSpec = {
            (fadeIn(HiFiMotion.large()) + slideInVertically(HiFiMotion.large()) { it / 8 }) togetherWith fadeOut(HiFiMotion.quick())
        },
        label = "wave_track"
    ) { shown ->
        if (shown == null) {
            Spacer(Modifier.fillMaxWidth().height(1.dp))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(onClick = onDislike, contentDescription = stringResource(R.string.cd_ne_nravitsya), size = 48.dp) {
                    Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = shown.title ?: "Без названия",
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = shown.artists.joinToString { it.name ?: "" },
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                GlassCircleButton(onClick = onLike, contentDescription = stringResource(R.string.cd_nravitsya), size = 48.dp) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) HiFi.Accent else Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveUpNext(
    upNext: List<TrackDetailsDto>,
    onOpenQueue: () -> Unit,
    onClick: (TrackDetailsDto) -> Unit,
    horizontalPadding: Dp
) {
    AnimatedVisibility(visible = upNext.isNotEmpty(), enter = fadeIn(HiFiMotion.large()), exit = fadeOut(HiFiMotion.quick())) {
        Column {
            Spacer(Modifier.height(22.dp))
            HiFiSectionHeader(
                text = stringResource(R.string.wave_next_up),
                modifier = Modifier.padding(start = horizontalPadding, end = (horizontalPadding - 8.dp).coerceAtLeast(0.dp))
            ) {
                HiFiTextAction(text = stringResource(R.string.wave_queue), onClick = onOpenQueue)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(upNext, key = { it.idString }, contentType = { "up_next" }) { track ->
                    UpNextCard(track) { onClick(track) }
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(track: TrackDetailsDto, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(track.coverUri.toCoverUrl("300x300")).crossfade(true).size(320).build(),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(104.dp)
                .shadow(10.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1F28))
        )
        Spacer(Modifier.height(7.dp))
        Text(track.title ?: "Без названия", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artists.joinToString { it.name ?: "" }, color = HiFi.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

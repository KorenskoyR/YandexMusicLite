package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLiveLine
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTimeline
import com.korenskoy.yandexmusic.presentation.player.TimeReadout

/** The car timeline: the shared Hi-Fi timeline at the head unit's scale, elapsed time on the left, what is left on the right. */
@Composable
internal fun CarTimeline(
    u: Scale,
    progress: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrub by remember(durationMs) { mutableStateOf<Float?>(null) }
    Column(modifier.fillMaxWidth()) {
        HiFiTimeline(progress, durationMs, isPlaying, onSeek, unit = u(1f), height = 40f, onScrub = { scrub = it })
        TimeReadout(progress, durationMs, scrub, fontSize = u.sp(20f), lineHeight = u.sp(24f), lift = u(2f))
    }
}

/** Radio has no timeline: a quiet line with the on-air mark in the middle. */
@Composable
internal fun CarLiveLine(u: Scale, modifier: Modifier = Modifier) = HiFiLiveLine(modifier, unit = u(1f))

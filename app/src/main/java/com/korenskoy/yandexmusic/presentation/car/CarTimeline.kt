package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLiveLine
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTimeline

/** The car timeline: the shared Hi-Fi timeline at the head unit's scale, no digits. */
@Composable
internal fun CarTimeline(
    u: Scale,
    progress: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) = HiFiTimeline(progress, durationMs, isPlaying, onSeek, modifier, unit = u(1f))

/** Radio has no timeline: a quiet line with the on-air mark in the middle. */
@Composable
internal fun CarLiveLine(u: Scale, modifier: Modifier = Modifier) = HiFiLiveLine(modifier, unit = u(1f))

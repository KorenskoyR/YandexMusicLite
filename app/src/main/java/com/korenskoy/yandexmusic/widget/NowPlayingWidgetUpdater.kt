package com.korenskoy.yandexmusic.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.korenskoy.yandexmusic.data.player.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws the widget when the track or play state changes. A running widget session follows the
 * player by itself; this covers the time when the system has stopped that session.
 */
@Singleton
class NowPlayingWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackController: PlaybackController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        combine(playbackController.currentTrack, playbackController.isPlaying) { track, playing -> track?.idString to playing }
            .distinctUntilChanged()
            .debounce(300)
            .onEach { runCatching { NowPlayingWidget().updateAll(context) } }
            .launchIn(scope)
    }
}

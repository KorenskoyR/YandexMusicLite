package com.korenskoy.yandexmusic.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.TrackReactions
import com.korenskoy.yandexmusic.domain.model.AppThemeMode
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws every widget when the track, play state, theme, or like/favourite reaction changes.
 * Also ticks the progress bar every half-minute while music plays, and checks theme schedule transitions.
 */
@Singleton
class NowPlayingWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val trackReactions: TrackReactions,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        val playbackState = combine(
            playbackController.currentTrack,
            playbackController.isPlaying,
            trackReactions.likedTrackIds,
            trackReactions.favoriteStationIds
        ) { track, playing, likedIds, favStations ->
            val liked = track != null && (
                if (track.isRadio) track.idString.removePrefix("radio_") in favStations
                else track.idString in likedIds
            )
            Triple(track?.idString, playing, liked)
        }
        val themeState = combine(
            settingsRepository.themeMode,
            settingsRepository.themeScheduleType,
            settingsRepository.themeLightMinutes,
            settingsRepository.themeDarkMinutes
        ) { mode, scheduleType, lightMin, darkMin ->
            listOf(mode, scheduleType, lightMin, darkMin)
        }
        combine(playbackState, themeState) { p, t -> p to t }
            .distinctUntilChanged()
            .debounce(300)
            .onEach {
                WidgetClock.advance()
                updateAll()
            }
            .launchIn(scope)
        // The progress moves every half-minute while music plays, never faster: a widget redrawn every second costs
        // the battery
        scope.launch {
            playbackController.isPlaying.collectLatest { playing ->
                while (playing) {
                    delay(TICK_MS)
                    WidgetClock.advance()
                    updateAll()
                }
            }
        }
        scope.launch {
            settingsRepository.themeMode.collectLatest { mode ->
                if (mode == AppThemeMode.SCHEDULE) {
                    while (true) {
                        delay(SCHEDULE_TICK_MS)
                        WidgetClock.advance()
                        updateAll()
                    }
                }
            }
        }
    }

    private suspend fun updateAll() {
        runCatching { NowPlayingWidget().updateAll(context) }
        runCatching { PlayerWidget().updateAll(context) }
        runCatching { CoverWidget().updateAll(context) }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val SCHEDULE_TICK_MS = 60_000L
    }
}

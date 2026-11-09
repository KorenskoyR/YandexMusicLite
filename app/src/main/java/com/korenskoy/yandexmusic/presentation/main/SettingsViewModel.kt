package com.korenskoy.yandexmusic.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.download.DownloadScheduler
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.SoundMode
import com.korenskoy.yandexmusic.data.reserve.RoadReserve
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.StartPage
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings: one [state] for the sheet and one function per change. The account, update and crash
 * lines come from their own view models and are added where the sheet is shown.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val soundMode: SoundMode,
    private val playbackController: PlaybackController,
    private val roadReserve: RoadReserve,
    private val downloadManager: TrackDownloadManager,
    private val downloadScheduler: DownloadScheduler,
    private val storage: com.korenskoy.yandexmusic.data.download.DownloadStorage,
    private val equalizer: com.korenskoy.yandexmusic.data.player.SystemEqualizer
) : ViewModel() {

    private data class Sound(val compatible: Boolean, val crossfade: Int, val normalize: Boolean, val startMusic: StartMusic, val bluetooth: Boolean, val explicit: Boolean)
    private data class Look(val startPage: StartPage, val expand: Boolean, val swipe: Boolean, val left: Boolean, val clock: Boolean)
    private data class Scale(val auto: Boolean, val manual: Float, val animations: AnimationMode)
    private data class Library(val reserveMb: Int, val reserveUsed: Long, val autoDownload: Boolean, val cacheBytes: Long)

    private val sound: Flow<Sound> = combine(
        combine(soundMode.compatible, settings.crossfadeSeconds, settings.normalizeVolume, ::Triple),
        combine(settings.startMusic, settings.bluetoothAutoplay, settings.skipExplicit, ::Triple)
    ) { (compatible, crossfade, normalize), (start, bluetooth, explicit) -> Sound(compatible, crossfade, normalize, start, bluetooth, explicit) }

    private val look: Flow<Look> = combine(
        settings.startPage, settings.expandPlayerOnPlay, settings.swipeToCloseMiniPlayer, settings.steeringWheelOnLeft, settings.carClock, ::Look
    )

    private val scale: Flow<Scale> = combine(settings.uiScaleAuto, settings.uiScaleManual, settings.animationMode, ::Scale)

    private val library: Flow<Library> = combine(
        settings.roadReserveMb, roadReserve.usedBytes, settings.autoDownloadLikedTracks, downloadManager.cacheSizeBytes, ::Library
    )

    val state: StateFlow<SettingsUiState> = combine(sound, look, scale, library, storage.state) { s, l, sc, lib, card ->
        SettingsUiState(
            compatibleSound = s.compatible,
            crossfadeSeconds = s.crossfade,
            normalizeVolume = s.normalize,
            equalizerAvailable = equalizer.available,
            startMusic = s.startMusic,
            bluetoothAutoplay = s.bluetooth,
            skipExplicit = s.explicit,
            reserveMb = lib.reserveMb,
            reserveUsedBytes = lib.reserveUsed,
            autoDownload = lib.autoDownload,
            cacheSizeBytes = lib.cacheBytes,
            storage = card,
            startPage = l.startPage,
            expandPlayerOnPlay = l.expand,
            swipeToCloseMiniPlayer = l.swipe,
            steeringWheelOnLeft = l.left,
            carClock = l.clock,
            uiScaleAuto = sc.auto,
            uiScaleManual = sc.manual,
            animationMode = sc.animations
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun save(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settings.block() }
    }

    fun setCompatibleSound(enabled: Boolean) {
        viewModelScope.launch {
            soundMode.choose(enabled)
            playbackController.onSoundModeChanged(enabled)
        }
    }

    fun setCrossfade(seconds: Int) = save { setCrossfadeSeconds(seconds) }
    fun setNormalizeVolume(enabled: Boolean) = save { setNormalizeVolume(enabled) }
    fun openEqualizer() = equalizer.open()
    fun setStartMusic(value: StartMusic) = save { setStartMusic(value) }
    fun setBluetoothAutoplay(enabled: Boolean) = save { setBluetoothAutoplay(enabled) }
    fun setSkipExplicit(enabled: Boolean) = save { setSkipExplicit(enabled) }
    fun setReserveMb(megabytes: Int) = save { setRoadReserveMb(megabytes) }
    fun clearReserve() {
        viewModelScope.launch { roadReserve.clear() }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoDownloadLikedTracks(enabled)
            if (enabled) downloadScheduler.downloadAllLiked()
        }
    }

    fun upgradeDownloads() {
        viewModelScope.launch { downloadScheduler.upgradeDownloadedQuality() }
    }

    fun setDownloadsOnCard(onCard: Boolean) = storage.moveTo(onCard)
    fun setStartPage(value: StartPage) = save { setStartPage(value) }
    fun setExpandPlayer(enabled: Boolean) = save { setExpandPlayerOnPlay(enabled) }
    fun setSwipeToClose(enabled: Boolean) = save { setSwipeToCloseMiniPlayer(enabled) }
    fun setSteeringWheelOnLeft(onLeft: Boolean) = save { setSteeringWheelOnLeft(onLeft) }
    fun setCarClock(enabled: Boolean) = save { setCarClock(enabled) }
    fun setUiScaleAuto(enabled: Boolean) = save { setUiScaleAuto(enabled) }
    fun setUiScaleManual(scale: Float) = save { setUiScaleManual(scale) }
    fun setAnimationMode(value: AnimationMode) = save { setAnimationMode(value) }
}

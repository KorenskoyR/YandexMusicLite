package com.korenskoy.yandexmusic.presentation.main

import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import android.content.Intent
import android.content.Context
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import android.net.Uri
import com.korenskoy.yandexmusic.data.download.DownloadStorage
import com.korenskoy.yandexmusic.data.player.SystemEqualizer
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
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
    private val storage: DownloadStorage,
    private val equalizer: SystemEqualizer,
    private val journal: PlaybackJournal,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private data class Sound(val compatible: Boolean, val crossfade: Int, val normalize: Boolean, val startMusic: StartMusic, val bluetooth: Boolean, val explicit: Boolean, val equalizer: Boolean)

    /** Whether an equalizer is on the device; asked again each time the sound settings open. */
    private val equalizerFound = MutableStateFlow(false)
    private data class Look(val startPage: StartPage, val expand: Boolean, val swipe: Boolean, val left: Boolean, val clock: Boolean, val under: CarUnderCover = CarUnderCover.QUEUE)
    private data class Scale(val auto: Boolean, val manual: Float, val animations: AnimationMode)
    private data class Library(
        val reserveMb: Int,
        val reserveUsed: Long,
        val autoDownload: Boolean,
        val cacheBytes: Long,
        val smooth: Boolean = true,
        val journal: PlaybackJournal.Snapshot = PlaybackJournal.Snapshot()
    )

    private val sound: Flow<Sound> = combine(
        combine(soundMode.compatible, settings.crossfadeSeconds, settings.normalizeVolume, ::Triple),
        combine(settings.startMusic, settings.bluetoothAutoplay, settings.skipExplicit, ::Triple),
        equalizerFound
    ) { (compatible, crossfade, normalize), (start, bluetooth, explicit), eq -> Sound(compatible, crossfade, normalize, start, bluetooth, explicit, eq) }

    private val look: Flow<Look> = combine(
        combine(settings.startPage, settings.expandPlayerOnPlay, settings.swipeToCloseMiniPlayer, settings.steeringWheelOnLeft, settings.carClock, ::Look),
        settings.carUnderCover
    ) { look, under -> look.copy(under = under) }

    private val scale: Flow<Scale> = combine(settings.uiScaleAuto, settings.uiScaleManual, settings.animationMode, ::Scale)

    private val library: Flow<Library> = combine(
        combine(settings.roadReserveMb, roadReserve.usedBytes, settings.autoDownloadLikedTracks, downloadManager.cacheSizeBytes) { mb, used, auto, cache ->
            Library(mb, used, auto, cache)
        },
        settings.smoothPlayback,
        journal.state
    ) { lib, smooth, journalState -> lib.copy(smooth = smooth, journal = journalState) }

    val state: StateFlow<SettingsUiState> = combine(sound, look, scale, library, storage.state) { s, l, sc, lib, card ->
        SettingsUiState(
            compatibleSound = s.compatible,
            crossfadeSeconds = s.crossfade,
            normalizeVolume = s.normalize,
            equalizerAvailable = s.equalizer,
            startMusic = s.startMusic,
            bluetoothAutoplay = s.bluetooth,
            skipExplicit = s.explicit,
            reserveMb = lib.reserveMb,
            reserveUsedBytes = lib.reserveUsed,
            autoDownload = lib.autoDownload,
            cacheSizeBytes = lib.cacheBytes,
            smoothPlayback = lib.smooth,
            journal = lib.journal,
            storage = card,
            startPage = l.startPage,
            expandPlayerOnPlay = l.expand,
            swipeToCloseMiniPlayer = l.swipe,
            steeringWheelOnLeft = l.left,
            carClock = l.clock,
            carUnderCover = l.under,
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

    fun setSmoothPlayback(enabled: Boolean) = save { setSmoothPlayback(enabled) }

    fun clearJournal() = journal.clear()

    /** The journal as text, to a messenger or mail of the user's choice. */
    fun shareJournal(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w("SettingsViewModel", "Journal not shared", it) }
    }

    fun refreshEqualizer() {
        viewModelScope.launch(Dispatchers.Default) { equalizerFound.value = equalizer.isAvailable() }
    }

    init {
        refreshEqualizer()
    }
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
    fun setStoragePlace(place: TrackDownloadManager.Place) = storage.moveTo(place)
    fun useUsbFolder(tree: Uri) = storage.useUsbFolder(tree)
    fun setStartPage(value: StartPage) = save { setStartPage(value) }
    fun setExpandPlayer(enabled: Boolean) = save { setExpandPlayerOnPlay(enabled) }
    fun setSwipeToClose(enabled: Boolean) = save { setSwipeToCloseMiniPlayer(enabled) }
    fun setSteeringWheelOnLeft(onLeft: Boolean) = save { setSteeringWheelOnLeft(onLeft) }
    fun setCarClock(enabled: Boolean) = save { setCarClock(enabled) }
    fun setCarUnderCover(value: CarUnderCover) = save { setCarUnderCover(value) }
    fun setUiScaleAuto(enabled: Boolean) = save { setUiScaleAuto(enabled) }
    fun setUiScaleManual(scale: Float) = save { setUiScaleManual(scale) }
    fun setAnimationMode(value: AnimationMode) = save { setAnimationMode(value) }
}

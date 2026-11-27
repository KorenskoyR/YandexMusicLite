package com.korenskoy.yandexmusic.presentation.main

import android.provider.Settings
import com.korenskoy.yandexmusic.data.start.DeviceStartReceiver
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.download.AutoDownloadBaseline
import com.korenskoy.yandexmusic.presentation.main.components.EqualizerActions
import com.korenskoy.yandexmusic.data.player.AppEqualizer
import com.korenskoy.yandexmusic.data.player.PlaybackEnvironment
import com.korenskoy.yandexmusic.domain.model.CarLayout
import com.korenskoy.yandexmusic.domain.model.MiniOnWave
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import android.content.Intent
import android.content.Context
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import android.net.Uri
import androidx.core.net.toUri
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
import com.korenskoy.yandexmusic.domain.model.AppThemeMode
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.StartPage
import com.korenskoy.yandexmusic.domain.model.ThemePolicy
import com.korenskoy.yandexmusic.domain.model.ThemeScheduleType
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
    private val appEqualizer: AppEqualizer,
    private val journal: PlaybackJournal,
    private val libraryRepository: LibraryRepository,
    private val autoDownloadBaseline: AutoDownloadBaseline,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private data class Sound(
        val compatible: Boolean,
        val crossfade: Int,
        val normalize: Boolean,
        val startMusic: StartMusic,
        val bluetooth: Boolean,
        val explicit: Boolean,
        val equalizer: SystemEqualizer.Target?,
        val ai: Boolean = false,
        val appEqualizer: AppEqualizer.State = AppEqualizer.State(),
        val autoStart: Boolean = false
    )

    /** Whether an equalizer is on the device; asked again each time the sound settings open. */
    private val equalizerFound = MutableStateFlow<SystemEqualizer.Target?>(null)
    private data class Look(val startPage: StartPage, val expand: Boolean, val swipe: Boolean, val left: Boolean, val clock: Boolean, val under: CarUnderCover = CarUnderCover.QUEUE, val mini: MiniOnWave = MiniOnWave.LANDSCAPE, val carLayout: CarLayout = CarLayout.AUTO)
    private data class ThemePref(
        val mode: AppThemeMode = AppThemeMode.DARK,
        val scheduleType: ThemeScheduleType = ThemeScheduleType.CUSTOM,
        val lightMinutes: Int = ThemePolicy.DEFAULT_LIGHT_MINUTES,
        val darkMinutes: Int = ThemePolicy.DEFAULT_DARK_MINUTES,
        val fullscreen: Boolean = false
    )
    private data class Scale(
        val auto: Boolean,
        val manual: Float,
        val animations: AnimationMode,
        val autoCheckUpdates: Boolean = SettingsRepository.DEFAULT_AUTO_CHECK_UPDATES,
        val theme: ThemePref = ThemePref()
    )
    private data class Library(
        val reserveMb: Int,
        val reserveUsed: Long,
        val autoDownload: Boolean,
        val cacheBytes: Long,
        val smooth: Boolean = true,
        val journal: PlaybackJournal.Snapshot = PlaybackJournal.Snapshot(),
        val reserveOnDrive: Boolean = false
    )

    private val sound: Flow<Sound> = combine(
        combine(soundMode.compatible, settings.crossfadeSeconds, settings.normalizeVolume, ::Triple),
        combine(settings.startMusic, settings.bluetoothAutoplay, settings.skipExplicit, ::Triple),
        equalizerFound,
        combine(settings.skipAi, settings.autoStartWithDevice, ::Pair),
        appEqualizer.state
    ) { (compatible, crossfade, normalize), (start, bluetooth, explicit), eq, (ai, autoStart), appEq ->
        Sound(compatible, crossfade, normalize, start, bluetooth, explicit, eq, ai, appEq, autoStart)
    }

    private val look: Flow<Look> = combine(
        combine(settings.startPage, settings.expandPlayerOnPlay, settings.swipeToCloseMiniPlayer, settings.steeringWheelOnLeft, settings.carClock, ::Look),
        settings.carUnderCover,
        settings.miniOnWave,
        settings.carLayout
    ) { look, under, mini, carLayout -> look.copy(under = under, mini = mini, carLayout = carLayout) }

    private val themePref: Flow<ThemePref> = combine(
        settings.themeMode,
        settings.themeScheduleType,
        settings.themeLightMinutes,
        settings.themeDarkMinutes,
        settings.fullscreenMode,
        ::ThemePref
    )

    private val scale: Flow<Scale> = combine(
        settings.uiScaleAuto,
        settings.uiScaleManual,
        settings.animationMode,
        settings.autoCheckUpdates,
        themePref,
        ::Scale
    )

    private val library: Flow<Library> = combine(
        combine(settings.roadReserveMb, roadReserve.usedBytes, settings.autoDownloadLikedTracks, downloadManager.cacheSizeBytes) { mb, used, auto, cache ->
            Library(mb, used, auto, cache)
        },
        combine(settings.smoothPlayback, settings.roadReserveOnDrive, ::Pair),
        journal.state
    ) { lib, (smooth, onDrive), journalState -> lib.copy(smooth = smooth, journal = journalState, reserveOnDrive = onDrive) }

    val state: StateFlow<SettingsUiState> = combine(sound, look, scale, library, storage.state) { s, l, sc, lib, card ->
        SettingsUiState(
            compatibleSound = s.compatible,
            crossfadeSeconds = s.crossfade,
            normalizeVolume = s.normalize,
            equalizerAvailable = s.equalizer != null,
            equalizerApp = (s.equalizer as? SystemEqualizer.Target.App)?.label,
            appEqualizer = s.appEqualizer,
            startMusic = s.startMusic,
            bluetoothAutoplay = s.bluetooth,
            autoStart = s.autoStart,
            skipExplicit = s.explicit,
            skipAi = s.ai,
            reserveMb = lib.reserveMb,
            reserveUsedBytes = lib.reserveUsed,
            reserveOnDrive = lib.reserveOnDrive,
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
            miniOnWave = l.mini,
            carLayout = l.carLayout,
            uiScaleAuto = sc.auto,
            uiScaleManual = sc.manual,
            animationMode = sc.animations,
            autoCheckUpdates = sc.autoCheckUpdates,
            themeMode = sc.theme.mode,
            themeScheduleType = sc.theme.scheduleType,
            themeLightMinutes = sc.theme.lightMinutes,
            themeDarkMinutes = sc.theme.darkMinutes,
            fullscreenMode = sc.theme.fullscreen
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

    /**
     * The app's own equalizer: each change goes straight to it, in the order made (a drag sends many; on a pool of
     * threads they could arrive out of order). The work is a few numbers and a write in the background.
     */
    val equalizerActions = EqualizerActions(
        onEnabled = appEqualizer::setEnabled,
        onGain = appEqualizer::setGain,
        onBass = appEqualizer::setBass,
        onTreble = appEqualizer::setTreble,
        onPreamp = appEqualizer::setPreamp,
        onNoClipping = appEqualizer::setNoClipping,
        onPreset = appEqualizer::usePreset,
        onSaveCustom = appEqualizer::saveCustom,
        onRenameCustom = appEqualizer::renameCustom,
        onDeleteCustom = appEqualizer::deleteCustom,
        onReset = appEqualizer::reset,
        onComparing = appEqualizer::setComparing,
        onOpenSystem = { openEqualizer() }
    )

    fun setSmoothPlayback(enabled: Boolean) = save { setSmoothPlayback(enabled) }

    fun clearJournal() = journal.clear()

    /** The journal as text, to a messenger or mail of the user's choice. */
    fun shareJournal(text: String) {
        // With the phone and its settings: battery saving and background limits explain many stops
        val s = state.value
        val eq = s.appEqualizer
        val sound = listOf(
            "Эквалайзер: " + if (eq.settings.enabled) "включён (" + (eq.preset?.name ?: "своя настройка") + ")" else "выключен",
            "Кроссфейд: " + if (s.crossfadeSeconds == 0) "выкл" else "${s.crossfadeSeconds} с",
            "Выравнивание громкости: " + if (s.normalizeVolume) "вкл" else "выкл",
            "Качество: " + if (s.compatibleSound) "совместимое" else "максимальное"
        )
        val full = text + "\n\n" + runCatching { PlaybackEnvironment.describe(context, sound) }.getOrDefault("")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, full.trimEnd())
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w("SettingsViewModel", "Journal not shared", it) }
    }

    fun refreshEqualizer() {
        viewModelScope.launch(Dispatchers.Default) {
            equalizerFound.value = equalizer.find()
        }
    }

    init {
        refreshEqualizer()
    }
    fun setStartMusic(value: StartMusic) = save { setStartMusic(value) }
    fun setBluetoothAutoplay(enabled: Boolean) = save { setBluetoothAutoplay(enabled) }

    /**
     * «Запуск с устройством». From Android 10 the app may open itself only with «Поверх других приложений»:
     * switching it on shows that system screen when it is not allowed yet (without it — a notification at start).
     */
    fun setAutoStart(enabled: Boolean) {
        save { setAutoStartWithDevice(enabled) }
        if (enabled && !DeviceStartReceiver.mayOpenItself(context)) askToOpenItself()
    }

    private fun askToOpenItself() {
        val ours = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ("package:" + context.packageName).toUri())
        val any = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        // Some head units have no such screen at all: then the notification at start stays the way
        for (intent in listOf(ours, any)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w("SettingsViewModel", "No screen for «Поверх других приложений»", e)
            }
        }
    }
    fun setSkipExplicit(enabled: Boolean) = save { setSkipExplicit(enabled) }
    fun setSkipAi(enabled: Boolean) = save { setSkipAi(enabled) }
    fun setReserveMb(megabytes: Int) = save { setRoadReserveMb(megabytes) }

    /** The cache moves between the phone's memory and the drive: it fills afresh at the new place. */
    fun setReserveOnDrive(onDrive: Boolean) = save { setRoadReserveOnDrive(onDrive) }
    fun clearReserve() {
        viewModelScope.launch { roadReserve.clear() }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // From now on: the likes made from here are downloaded; the collection so far — «Скачать всю коллекцию»
            if (enabled) {
                libraryRepository.loadCached()
                val liked = libraryRepository.likedTracks.value
                if (liked.isNotEmpty()) autoDownloadBaseline.set(liked.map { it.idString }) else autoDownloadBaseline.clear()
            } else {
                autoDownloadBaseline.clear()
            }
            settings.setAutoDownloadLikedTracks(enabled)
        }
    }

    fun upgradeDownloads() {
        viewModelScope.launch { downloadScheduler.upgradeDownloadedQuality() }
    }

    fun setDownloadsOnCard(onCard: Boolean) = storage.moveTo(onCard)
    fun setStoragePlace(place: TrackDownloadManager.Place) = storage.moveTo(place)
    fun useUsbFolder(tree: Uri) = storage.useUsbFolder(tree)
    fun useDrive(path: String) = storage.useDrive(path)
    fun checkStoragePath(input: String) = storage.checkPath(input)
    fun setStartPage(value: StartPage) = save { setStartPage(value) }
    fun setExpandPlayer(enabled: Boolean) = save { setExpandPlayerOnPlay(enabled) }
    fun setSwipeToClose(enabled: Boolean) = save { setSwipeToCloseMiniPlayer(enabled) }
    fun setSteeringWheelOnLeft(onLeft: Boolean) = save { setSteeringWheelOnLeft(onLeft) }
    fun setCarClock(enabled: Boolean) = save { setCarClock(enabled) }
    fun setCarUnderCover(value: CarUnderCover) = save { setCarUnderCover(value) }
    fun setMiniOnWave(value: MiniOnWave) = save { setMiniOnWave(value) }
    fun setCarLayout(value: CarLayout) = save { setCarLayout(value) }
    fun setUiScaleAuto(enabled: Boolean) = save { setUiScaleAuto(enabled) }
    fun setUiScaleManual(scale: Float) = save { setUiScaleManual(scale) }
    fun setAnimationMode(value: AnimationMode) = save { setAnimationMode(value) }
    fun setAutoCheckUpdates(enabled: Boolean) = save { setAutoCheckUpdates(enabled) }
    fun setThemeMode(mode: AppThemeMode) = save { setThemeMode(mode) }
    fun setThemeScheduleType(type: ThemeScheduleType) = save { setThemeScheduleType(type) }
    fun setThemeLightMinutes(minutes: Int) = save { setThemeLightMinutes(minutes) }
    fun setThemeDarkMinutes(minutes: Int) = save { setThemeDarkMinutes(minutes) }
    fun setFullscreenMode(enabled: Boolean) = save { setFullscreenMode(enabled) }
}

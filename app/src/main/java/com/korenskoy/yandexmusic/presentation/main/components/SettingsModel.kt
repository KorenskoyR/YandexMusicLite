package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import android.net.Uri
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
import com.korenskoy.yandexmusic.data.download.StorageStatus
import androidx.compose.runtime.Immutable
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.StartPage

/** The pages of the settings: a short root list and one page per section. */
enum class SettingsPage { ACCOUNT, PLAYBACK, INTERFACE, ABOUT }

/** Everything the settings show, in one value: the sheet draws it and calls [SettingsActions]. */
@Immutable
data class SettingsUiState(
    val accountName: String = "",
    val avatarUrl: String? = null,
    val hasPlus: Boolean = false,
    val defaultAccountName: String? = null,
    val updateStatus: String? = null,
    val crashReports: Int = 0,
    /** "Не прерывать музыку". */
    val smoothPlayback: Boolean = true,
    /** The last hour of music, for "Журнал воспроизведения". */
    val journal: PlaybackJournal.Snapshot = PlaybackJournal.Snapshot(),
    val compatibleSound: Boolean = false,
    val crossfadeSeconds: Int = 3,
    val normalizeVolume: Boolean = false,
    val equalizerAvailable: Boolean = false,
    val startMusic: StartMusic = StartMusic.OFF,
    val bluetoothAutoplay: Boolean = false,
    val skipExplicit: Boolean = false,
    val skipAi: Boolean = false,
    val reserveMb: Int = 100,
    val reserveUsedBytes: Long = 0L,
    val autoDownload: Boolean = false,
    val cacheSizeBytes: Long = 0L,
    val storage: StorageStatus = StorageStatus(),
    val startPage: StartPage = StartPage.WAVE,
    val expandPlayerOnPlay: Boolean = false,
    val swipeToCloseMiniPlayer: Boolean = true,
    val steeringWheelOnLeft: Boolean = true,
    val carClock: Boolean = true,
    val carUnderCover: CarUnderCover = CarUnderCover.QUEUE,
    val uiScaleAuto: Boolean = true,
    val uiScaleManual: Float = 1f,
    val animationMode: AnimationMode = AnimationMode.AUTO
)

/** What the settings can do. Every action has a harmless default, so previews and tests pass only what they need. */
data class SettingsActions(
    val onAccountManagement: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onOpenCrashLog: () -> Unit = {},
    val onSmoothPlaybackChange: (Boolean) -> Unit = {},
    val onShareJournal: (String) -> Unit = {},
    val onClearJournal: () -> Unit = {},
    val onCompatibleSoundChange: (Boolean) -> Unit = {},
    val onCrossfadeChange: (Int) -> Unit = {},
    val onNormalizeVolumeChange: (Boolean) -> Unit = {},
    val onOpenEqualizer: () -> Unit = {},
    /** The sound settings opened: look for an equalizer again. */
    val onRefreshEqualizer: () -> Unit = {},
    val onStartMusicChange: (StartMusic) -> Unit = {},
    val onBluetoothAutoplayChange: (Boolean) -> Unit = {},
    val onSkipExplicitChange: (Boolean) -> Unit = {},
    val onSkipAiChange: (Boolean) -> Unit = {},
    val onReserveChange: (Int) -> Unit = {},
    val onClearReserve: () -> Unit = {},
    val onAutoDownloadChange: (Boolean) -> Unit = {},
    val onDownloadAll: () -> Unit = {},
    val onUpgradeDownloads: () -> Unit = {},
    val onDownloadsOnCardChange: (Boolean) -> Unit = {},
    val onClearDownloads: () -> Unit = {},
    val onStartPageChange: (StartPage) -> Unit = {},
    val onExpandPlayerChange: (Boolean) -> Unit = {},
    val onSwipeToCloseChange: (Boolean) -> Unit = {},
    val onSteeringWheelChange: (Boolean) -> Unit = {},
    val onCarClockChange: (Boolean) -> Unit = {},
    val onCarUnderCoverChange: (CarUnderCover) -> Unit = {},
    val onStoragePlaceChange: (TrackDownloadManager.Place) -> Unit = {},
    val onUsbFolderChosen: (Uri) -> Unit = {},
    val onUiScaleAutoChange: (Boolean) -> Unit = {},
    val onUiScaleManualChange: (Float) -> Unit = {},
    val onAnimationModeChange: (AnimationMode) -> Unit = {}
)

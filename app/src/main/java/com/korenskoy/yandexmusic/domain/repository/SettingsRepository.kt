package com.korenskoy.yandexmusic.domain.repository

import com.korenskoy.yandexmusic.domain.model.CarLayout
import com.korenskoy.yandexmusic.domain.model.MiniOnWave
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
import com.korenskoy.yandexmusic.domain.model.StartPage
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDarkTheme: Flow<Boolean>
    val autoDownloadLikedTracks: Flow<Boolean>
    /** Crossfade between tracks in seconds; 0 turns it off. */
    val crossfadeSeconds: Flow<Int>
    /**
     * Steering wheel side, for the car-mode landscape layout. True (left-hand drive) puts the
     * controls on the left (near the driver) and the cover on the right; false mirrors it.
     */
    val steeringWheelOnLeft: Flow<Boolean>
    /** Auto UI scale: the app picks the scale from the screen metrics instead of [uiScaleManual]. */
    val uiScaleAuto: Flow<Boolean>
    /** Manual UI-scale multiplier used when [uiScaleAuto] is off ([MIN_UI_SCALE]…[MAX_UI_SCALE]). */
    val uiScaleManual: Flow<Float>
    /** The update the user chose to skip ("1.9.3"); the launch check does not offer it again. */
    val skippedUpdateVersion: Flow<String?>
    /** The road reserve in megabytes, one of [ROAD_RESERVE_SIZES_MB]; 0 turns it off. */
    val roadReserveMb: Flow<Int>

    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAutoDownloadLikedTracks(enabled: Boolean)
    suspend fun setCrossfadeSeconds(seconds: Int)
    suspend fun setSteeringWheelOnLeft(onLeft: Boolean)
    suspend fun setUiScaleAuto(enabled: Boolean)
    suspend fun setUiScaleManual(scale: Float)
    suspend fun setSkippedUpdateVersion(version: String?)
    suspend fun setRoadReserveMb(megabytes: Int)

    /** True: only the ordinary stream, for a device that plays FLAC in silence. Null: nothing chosen yet. */
    val compatibleSound: Flow<Boolean?>
    suspend fun setCompatibleSound(enabled: Boolean)

    /** What plays by itself when the app starts. */
    val startMusic: Flow<StartMusic>
    suspend fun setStartMusic(value: StartMusic)

    /** Which page the app opens on; [lastTab] is remembered for [StartPage.LAST]. */
    val startPage: Flow<StartPage>
    suspend fun setStartPage(value: StartPage)
    val lastTab: Flow<String?>
    suspend fun setLastTab(route: String)

    /** Open the full player when the user starts music from a list. */
    val expandPlayerOnPlay: Flow<Boolean>
    suspend fun setExpandPlayerOnPlay(enabled: Boolean)

    /** Swiping the mini-player away stops the music. */
    val swipeToCloseMiniPlayer: Flow<Boolean>
    suspend fun setSwipeToCloseMiniPlayer(enabled: Boolean)

    /** The clock and date in the car mode. */
    val carClock: Flow<Boolean>
    suspend fun setCarClock(enabled: Boolean)

    /** «В авто»: under the cover the "Очередь" button (the biggest cover) or the "Далее" row. */
    val carUnderCover: Flow<CarUnderCover>
    suspend fun setCarUnderCover(value: CarUnderCover)

    /** «В авто» lying on a wide head unit: three columns, two, or by the room. */
    val carLayout: Flow<CarLayout>
    suspend fun setCarLayout(value: CarLayout)

    val animationMode: Flow<AnimationMode>
    suspend fun setAnimationMode(value: AnimationMode)

    /** "Моя волна": in which orientation the mini-player shows under the playing wave. */
    val miniOnWave: Flow<MiniOnWave>
    suspend fun setMiniOnWave(value: MiniOnWave)

    /** Quiet the tracks louder than the target so all of them sound alike. */
    val normalizeVolume: Flow<Boolean>
    suspend fun setNormalizeVolume(enabled: Boolean)

    /** Start the music when the phone connects to a Bluetooth audio device (the car). */
    val bluetoothAutoplay: Flow<Boolean>
    suspend fun setBluetoothAutoplay(enabled: Boolean)

    /** Skip tracks the service marks as explicit in the wave and when moving on by itself. */
    val skipExplicit: Flow<Boolean>
    suspend fun setSkipExplicit(enabled: Boolean)

    /** Tracks Yandex marks as made (wholly or partly) with AI do not play by themselves. Off by default. */
    val skipAi: Flow<Boolean>
    suspend fun setSkipAi(enabled: Boolean)

    /** "Не прерывать музыку": on an uneven link, copies from the cache and a light stream instead of stops. */
    val smoothPlayback: Flow<Boolean>
    suspend fun setSmoothPlayback(enabled: Boolean)

    /** Downloads on the removable SD card (encrypted) instead of the internal storage. */
    val downloadsOnSdCard: Flow<Boolean>
    suspend fun setDownloadsOnSdCard(enabled: Boolean)

    /** New downloads go to the folder picked on a USB flash drive (wins over the SD card). */
    val downloadsOnUsb: Flow<Boolean>
    suspend fun setDownloadsOnUsb(enabled: Boolean)

    /** The folder on the flash drive picked in the system window (a tree uri); null until one was. */
    val usbFolder: Flow<String?>
    suspend fun setUsbFolder(uri: String?)

    /**
     * The removable drive's folder chosen without the system window (from the drives found, or typed); null —
     * the first removable drive, as before.
     */
    val cardRoot: Flow<String?>
    suspend fun setCardRoot(path: String?)

    companion object {
        const val DEFAULT_CROSSFADE_SECONDS = 3
        const val MAX_CROSSFADE_SECONDS = 12
        /** Left-hand drive is the global majority, so it is the default. */
        const val DEFAULT_STEERING_WHEEL_ON_LEFT = true
        /** UI scale defaults to automatic so misreported head-unit screens are corrected out of the box. */
        const val DEFAULT_UI_SCALE_AUTO = true
        const val DEFAULT_UI_SCALE = 1.0f
        const val MIN_UI_SCALE = 0.5f
        const val MAX_UI_SCALE = 2.0f
        const val DEFAULT_ROAD_RESERVE_MB = 100
        val ROAD_RESERVE_SIZES_MB = listOf(0, 100, 300, 500, 1024)
    }
}

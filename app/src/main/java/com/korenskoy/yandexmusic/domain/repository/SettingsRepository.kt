package com.korenskoy.yandexmusic.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDarkTheme: Flow<Boolean>
    val autoDownloadLikedTracks: Flow<Boolean>
    val autoStartCarModeVibe: Flow<Boolean>
    val keepScreenOnInCarMode: Flow<Boolean>
    /** Crossfade between tracks in seconds; 0 turns it off. */
    val crossfadeSeconds: Flow<Int>
    /** Offline downloads only on unmetered networks (Wi-Fi). */
    val downloadOverWifiOnly: Flow<Boolean>
    /** Mobile (metered) network: stream at most [ECONOMY_BITRATE_KBPS] instead of the best quality. */
    val economyOnMobileData: Flow<Boolean>
    /**
     * Steering wheel side, for the car-mode landscape layout. True (left-hand drive) puts the
     * controls on the left (near the driver) and the cover on the right; false mirrors it.
     */
    val steeringWheelOnLeft: Flow<Boolean>
    /** Auto UI scale: the app picks the scale from the screen metrics instead of [uiScaleManual]. */
    val uiScaleAuto: Flow<Boolean>
    /** Manual UI-scale multiplier used when [uiScaleAuto] is off ([MIN_UI_SCALE]…[MAX_UI_SCALE]). */
    val uiScaleManual: Flow<Float>

    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAutoDownloadLikedTracks(enabled: Boolean)
    suspend fun setAutoStartCarModeVibe(enabled: Boolean)
    suspend fun setKeepScreenOnInCarMode(enabled: Boolean)
    suspend fun setCrossfadeSeconds(seconds: Int)
    suspend fun setDownloadOverWifiOnly(enabled: Boolean)
    suspend fun setEconomyOnMobileData(enabled: Boolean)
    suspend fun setSteeringWheelOnLeft(onLeft: Boolean)
    suspend fun setUiScaleAuto(enabled: Boolean)
    suspend fun setUiScaleManual(scale: Float)

    companion object {
        const val DEFAULT_CROSSFADE_SECONDS = 3
        const val MAX_CROSSFADE_SECONDS = 12
        const val ECONOMY_BITRATE_KBPS = 128
        /** Left-hand drive is the global majority, so it is the default. */
        const val DEFAULT_STEERING_WHEEL_ON_LEFT = true
        /** UI scale defaults to automatic so misreported head-unit screens are corrected out of the box. */
        const val DEFAULT_UI_SCALE_AUTO = true
        const val DEFAULT_UI_SCALE = 1.0f
        const val MIN_UI_SCALE = 0.5f
        const val MAX_UI_SCALE = 2.0f
    }
}

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

    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAutoDownloadLikedTracks(enabled: Boolean)
    suspend fun setAutoStartCarModeVibe(enabled: Boolean)
    suspend fun setKeepScreenOnInCarMode(enabled: Boolean)
    suspend fun setCrossfadeSeconds(seconds: Int)
    suspend fun setDownloadOverWifiOnly(enabled: Boolean)
    suspend fun setEconomyOnMobileData(enabled: Boolean)

    companion object {
        const val DEFAULT_CROSSFADE_SECONDS = 3
        const val MAX_CROSSFADE_SECONDS = 12
        const val ECONOMY_BITRATE_KBPS = 128
    }
}

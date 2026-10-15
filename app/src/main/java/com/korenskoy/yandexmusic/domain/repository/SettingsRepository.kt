package com.korenskoy.yandexmusic.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDarkTheme: Flow<Boolean>
    val autoDownloadLikedTracks: Flow<Boolean>
    val autoStartCarModeVibe: Flow<Boolean>
    val keepScreenOnInCarMode: Flow<Boolean>

    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAutoDownloadLikedTracks(enabled: Boolean)
    suspend fun setAutoStartCarModeVibe(enabled: Boolean)
    suspend fun setKeepScreenOnInCarMode(enabled: Boolean)
}

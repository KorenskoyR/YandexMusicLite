package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl @Inject constructor(
    private val context: Context
) : SettingsRepository {

    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val AUTO_DOWNLOAD_KEY = booleanPreferencesKey("auto_download_liked")
    private val AUTO_START_CAR_MODE_VIBE_KEY = booleanPreferencesKey("auto_start_car_mode_vibe")
    private val KEEP_SCREEN_ON_IN_CAR_MODE_KEY = booleanPreferencesKey("keep_screen_on_in_car_mode")
    private val CROSSFADE_SECONDS_KEY = androidx.datastore.preferences.core.intPreferencesKey("crossfade_seconds")

    override val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_THEME_KEY] ?: true
    }

    override val autoDownloadLikedTracks: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_DOWNLOAD_KEY] ?: false
    }

    override val autoStartCarModeVibe: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START_CAR_MODE_VIBE_KEY] ?: false
    }

    override val keepScreenOnInCarMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEEP_SCREEN_ON_IN_CAR_MODE_KEY] ?: true
    }

    override val crossfadeSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CROSSFADE_SECONDS_KEY] ?: SettingsRepository.DEFAULT_CROSSFADE_SECONDS
    }

    private val DOWNLOAD_WIFI_ONLY_KEY = booleanPreferencesKey("download_wifi_only")

    override val downloadOverWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_WIFI_ONLY_KEY] ?: true
    }

    private val ECONOMY_ON_MOBILE_KEY = booleanPreferencesKey("economy_on_mobile_data")
    // Off by default: the owner wants the best quality everywhere
    override val economyOnMobileData: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ECONOMY_ON_MOBILE_KEY] ?: false
    }

    override suspend fun setEconomyOnMobileData(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[ECONOMY_ON_MOBILE_KEY] = enabled }
    }

    override suspend fun setDownloadOverWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_WIFI_ONLY_KEY] = enabled }
    }

    override suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[CROSSFADE_SECONDS_KEY] = seconds.coerceIn(0, SettingsRepository.MAX_CROSSFADE_SECONDS)
        }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_THEME_KEY] = enabled
        }
    }

    override suspend fun setAutoDownloadLikedTracks(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_DOWNLOAD_KEY] = enabled
        }
    }

    override suspend fun setAutoStartCarModeVibe(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_START_CAR_MODE_VIBE_KEY] = enabled
        }
    }

    override suspend fun setKeepScreenOnInCarMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEEP_SCREEN_ON_IN_CAR_MODE_KEY] = enabled
        }
    }
}

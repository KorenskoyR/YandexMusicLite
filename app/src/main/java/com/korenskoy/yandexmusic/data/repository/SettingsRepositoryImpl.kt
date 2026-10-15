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

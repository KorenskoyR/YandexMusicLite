package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.radioDataStore: DataStore<Preferences> by preferencesDataStore(name = "radio_settings")

@Singleton
class RadioRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val FAVORITES_KEY = stringSetPreferencesKey("favorite_station_ids")

    val favoriteStationIds: Flow<Set<String>> = context.radioDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[FAVORITES_KEY] ?: emptySet()
        }

    // Bundled logos come from verified sources: the stations' official sites, the Radio Record stations API
    // and the logo files of the stations' own Wikipedia articles. A station without a logo
    // (iconRes = null, empty logoUrl) shows its initial on the brand colours.
    fun getBuiltinStations(): List<RadioStation> {
        return listOf(
            // POPULAR
            RadioStation("evropa_plus", "Европа Плюс", "106.2 FM", "http://ep256.hostingradio.ru:8052/europaplus256.mp3", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Europa_Plus_Logo.svg/1024px-Europa_Plus_Logo.svg.png", RadioCategory.POPULAR, iconRes = R.drawable.radio_evropa_plus, brandColorStart = 0xFF1E3A8A, brandColorEnd = 0xFF7C3AED),
            RadioStation("novoe_radio", "Новое Радио", "98.4 FM", "http://icecast.newradio.cdnvideo.ru/newradio3", "", RadioCategory.POPULAR, iconRes = R.drawable.radio_novoe_radio, brandColorStart = 0xFFB91C1C, brandColorEnd = 0xFFF59E0B),
            RadioStation("like_fm", "Like FM", "87.9 FM", "https://hls-01-gpm.hostingradio.ru/likefm495/playlist.m3u8", "", RadioCategory.POPULAR, iconRes = R.drawable.radio_like_fm, brandColorStart = 0xFFBE185D, brandColorEnd = 0xFF8B5CF6),
            RadioStation("rus_radio", "Русское Радио", "105.7 FM", "https://rusradio.hostingradio.ru/rusradio96.aacp", "https://upload.wikimedia.org/wikipedia/ru/0/07/Russkoe_radio_logo.png", RadioCategory.POPULAR, iconRes = R.drawable.radio_rus_radio, brandColorStart = 0xFF991B1B, brandColorEnd = 0xFFE11D48),
            RadioStation("energy", "Energy (NRJ)", "104.2 FM", "https://gpm.hostingradio.ru/gpm-energyfm495.aacp", "https://upload.wikimedia.org/wikipedia/commons/0/07/NRJ_logo.png", RadioCategory.POPULAR, iconRes = R.drawable.radio_energy, brandColorStart = 0xFF1F2937, brandColorEnd = 0xFFEAB308),
            RadioStation("avtoradio", "Авторадио", "90.3 FM", "https://hls-01-gpm.hostingradio.ru/avtoradio495/playlist.m3u8", "https://upload.wikimedia.org/wikipedia/ru/5/52/Avtoradio_logo_2022.png", RadioCategory.POPULAR, iconRes = R.drawable.radio_avtoradio, brandColorStart = 0xFF1E40AF, brandColorEnd = 0xFF06B6D4),
            RadioStation("hit_fm", "Хит FM", "107.4 FM", "http://hitfm.hostingradio.ru/hitfm96.aacp", "https://upload.wikimedia.org/wikipedia/ru/b/b3/Hit_fm_logo.png", RadioCategory.POPULAR, iconRes = R.drawable.radio_hit_fm, brandColorStart = 0xFFDB2777, brandColorEnd = 0xFFF97316),
            RadioStation("love_radio", "Love Radio", "106.6 FM", "http://stream.loveradio.ru/love128.mp3", "", RadioCategory.POPULAR, iconRes = R.drawable.radio_love_radio, brandColorStart = 0xFFBE123C, brandColorEnd = 0xFFF43F5E),

            // DANCE
            RadioStation("record", "Radio Record", "98.4 FM", "https://radiorecord.hostingradio.ru/rr_main96.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_record, brandColorStart = 0xFF431407, brandColorEnd = 0xFFF97316),
            RadioStation("dfm", "DFM", "101.2 FM", "https://dfm.hostingradio.ru/dfm96.aacp", "https://upload.wikimedia.org/wikipedia/ru/b/b5/DFM_logo.png", RadioCategory.DANCE, iconRes = R.drawable.radio_dfm, brandColorStart = 0xFF14532D, brandColorEnd = 0xFF84CC16),
            RadioStation("record_deep", "Record Deep", "Internet", "https://radiorecord.hostingradio.ru/deep96.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_record_deep, brandColorStart = 0xFF0F172A, brandColorEnd = 0xFF4338CA),
            RadioStation("record_chillout", "Record Chillout", "Internet", "https://radiorecord.hostingradio.ru/chil96.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_record_chillout, brandColorStart = 0xFF134E4A, brandColorEnd = 0xFF06B6D4),
            RadioStation("record_russian_mix", "Record Russian Mix", "Internet", "https://radiorecord.hostingradio.ru/rus96.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_record_russian_mix, brandColorStart = 0xFF7F1D1D, brandColorEnd = 0xFFF97316),
            RadioStation("dfm_disco_90", "DFM Диско 90-х", "Internet", "https://dfm-disc90.hostingradio.ru/disc9096.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_dfm_disco_90, brandColorStart = 0xFF1E3A8A, brandColorEnd = 0xFFDB2777),
            RadioStation("studio_21", "Studio 21", "89.9 FM", "https://stream.studio21.ru/studio2196.aacp", "", RadioCategory.DANCE, iconRes = R.drawable.radio_studio_21, brandColorStart = 0xFF450A0A, brandColorEnd = 0xFFDC2626),

            // ROCK
            RadioStation("maximum", "Радио MAXIMUM", "103.7 FM", "https://maximum.hostingradio.ru/maximum96.aacp", "https://upload.wikimedia.org/wikipedia/ru/8/89/Radio_Maximum_logo.png", RadioCategory.ROCK, iconRes = R.drawable.radio_maximum, brandColorStart = 0xFF450A0A, brandColorEnd = 0xFFB91C1C),
            RadioStation("nashe_radio", "Наше Радио", "101.7 FM", "https://nashe1.hostingradio.ru/nashe-128.mp3", "", RadioCategory.ROCK, iconRes = R.drawable.radio_nashe_radio, brandColorStart = 0xFF7F1D1D, brandColorEnd = 0xFFEA580C),
            RadioStation("rock_fm", "Rock FM", "95.2 FM", "http://nashe.streamr.ru/rock-128.mp3", "", RadioCategory.ROCK, iconRes = R.drawable.radio_rock_fm, brandColorStart = 0xFF111827, brandColorEnd = 0xFF4B5563),
            RadioStation("radio_ultra", "Radio Ultra", "Internet", "http://nashe.streamr.ru/ultra-128.mp3", "", RadioCategory.ROCK, iconRes = R.drawable.radio_radio_ultra, brandColorStart = 0xFF312E81, brandColorEnd = 0xFF7C3AED),

            // RETRO
            RadioStation("retro_fm", "Ретро FM", "88.3 FM", "https://retro.hostingradio.ru:8043/retro256.mp3", "https://upload.wikimedia.org/wikipedia/ru/5/55/Retro_FM_logo.png", RadioCategory.RETRO, iconRes = R.drawable.radio_retro_fm, brandColorStart = 0xFF713F12, brandColorEnd = 0xFFEAB308),
            RadioStation("dorojnoe", "Дорожное Радио", "96.0 FM", "http://dorognoe.hostingradio.ru:8000/dorognoe", "", RadioCategory.RETRO, iconRes = R.drawable.radio_dorojnoe, brandColorStart = 0xFF14532D, brandColorEnd = 0xFF84CC16),
            RadioStation("radio_7", "Радио 7", "104.7 FM", "http://radio7server.streamr.ru:8040/radio7256.mp3", "https://upload.wikimedia.org/wikipedia/ru/3/30/Radio_7_logo.png", RadioCategory.RETRO, iconRes = R.drawable.radio_radio_7, brandColorStart = 0xFF1E3A8A, brandColorEnd = 0xFFD97706),
            RadioStation("radio_dacha", "Радио Дача", "92.4 FM", "http://stream.radiodacha.ru/dacha128.mp3", "https://upload.wikimedia.org/wikipedia/ru/2/23/Radio_Dacha_logo.png", RadioCategory.RETRO, iconRes = R.drawable.radio_radio_dacha, brandColorStart = 0xFF064E3B, brandColorEnd = 0xFF10B981),
            RadioStation("shanson", "Радио Шансон", "103.0 FM", "http://chanson.hostingradio.ru:8041/chanson128.mp3", "", RadioCategory.RETRO, iconRes = R.drawable.radio_shanson, brandColorStart = 0xFF78350F, brandColorEnd = 0xFFD97706),
            RadioStation("radio_vanya", "Радио Ваня", "100.1 FM", "http://icecast-radiovanya.cdnvideo.ru/radiovanya", "", RadioCategory.RETRO, iconRes = R.drawable.radio_radio_vanya, brandColorStart = 0xFF1D4ED8, brandColorEnd = 0xFFFBBF24),

            // RELAX
            RadioStation("relax_fm", "Relax FM", "90.8 FM", "https://gpm.hostingradio.ru/gpm-relaxfm495.aacp", "https://upload.wikimedia.org/wikipedia/ru/4/4b/Relax_FM_logo.png", RadioCategory.RELAX, iconRes = R.drawable.radio_relax_fm, brandColorStart = 0xFF064E3B, brandColorEnd = 0xFF14B8A6),
            RadioStation("monte_carlo", "Радио Monte Carlo", "102.1 FM", "https://montecarlo.hostingradio.ru/montecarlo96.aacp", "https://upload.wikimedia.org/wikipedia/ru/c/ca/Monte_Carlo_logo.png", RadioCategory.RELAX, iconRes = R.drawable.radio_monte_carlo, brandColorStart = 0xFF334155, brandColorEnd = 0xFF94A3B8),
            RadioStation("radio_jazz", "Радио Jazz", "89.1 FM", "http://nashe.streamr.ru/jazz-128.mp3", "", RadioCategory.RELAX, iconRes = R.drawable.radio_radio_jazz, brandColorStart = 0xFF451A03, brandColorEnd = 0xFFD97706),
            RadioStation("radio_kultura", "Радио Культура", "91.6 FM", "http://icecast-vgtrk.cdnvideo.ru/kulturafm_mp3_192kbps", "", RadioCategory.RELAX, iconRes = R.drawable.radio_radio_kultura, brandColorStart = 0xFF581C87, brandColorEnd = 0xFF9333EA),
            
            // TALK
            RadioStation("comedy_radio", "Comedy Radio", "102.5 FM", "http://23.105.238.4/gpm-comedyradio495.aacp", "https://upload.wikimedia.org/wikipedia/ru/f/f6/Comedy_Radio_logo.png", RadioCategory.TALK, iconRes = R.drawable.radio_comedy_radio, brandColorStart = 0xFF991B1B, brandColorEnd = 0xFFEAB308),
            RadioStation("vesti_fm", "Вести FM", "97.6 FM", "https://icecast-vgtrk.cdnvideo.ru/vestifm_mp3_192kbps", "", RadioCategory.TALK, iconRes = R.drawable.radio_vesti_fm, brandColorStart = 0xFF1E3A8A, brandColorEnd = 0xFF3B82F6),
            RadioStation("mayak", "Радио Маяк", "103.4 FM", "https://icecast-vgtrk.cdnvideo.ru/mayakfm_mp3_192kbps", "", RadioCategory.TALK, iconRes = R.drawable.radio_mayak, brandColorStart = 0xFF7F1D1D, brandColorEnd = 0xFF1D4ED8),
            RadioStation("humor_fm", "Юмор FM", "88.7 FM", "https://gpm.hostingradio.ru/gpm-humorfm495.aacp", "", RadioCategory.TALK, iconRes = R.drawable.radio_humor_fm, brandColorStart = 0xFFB45309, brandColorEnd = 0xFFFCD34D),
            RadioStation("radio_zvezda", "Радио Звезда", "95.6 FM", "https://icecast-zvezda.mediacdn.ru/radio/zvezda/zvezda_128", "", RadioCategory.TALK, iconRes = R.drawable.radio_radio_zvezda, brandColorStart = 0xFF991B1B, brandColorEnd = 0xFFEF4444),
            RadioStation("business_fm", "Business FM", "87.5 FM", "http://bfm.hostingradio.ru:8004/fm", "", RadioCategory.TALK, iconRes = R.drawable.radio_business_fm, brandColorStart = 0xFF064E3B, brandColorEnd = 0xFF10B981),
            RadioStation("radio_kp", "Радио КП", "97.2 FM", "https://kpradio.hostingradio.ru:8000/128", "", RadioCategory.TALK, iconRes = R.drawable.radio_radio_kp, brandColorStart = 0xFF7F1D1D, brandColorEnd = 0xFFF87171),
            RadioStation("radio_rossii", "Радио России", "97.6 FM", "http://icecast.vgtrk.cdnvideo.ru/rrzonam_mp3_128kbps", "", RadioCategory.TALK, iconRes = R.drawable.radio_radio_rossii, brandColorStart = 0xFF1E3A8A, brandColorEnd = 0xFF60A5FA)
        )
    }

    suspend fun addFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            newFavorites.add(stationId)
            prefs[FAVORITES_KEY] = newFavorites
        }
    }

    suspend fun removeFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            newFavorites.remove(stationId)
            prefs[FAVORITES_KEY] = newFavorites
        }
    }

    suspend fun toggleFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            if (newFavorites.contains(stationId)) {
                newFavorites.remove(stationId)
            } else {
                newFavorites.add(stationId)
            }
            prefs[FAVORITES_KEY] = newFavorites
        }
    }
}

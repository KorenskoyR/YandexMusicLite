package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.crossfade
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.AppThemeMode
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RepeatMode
import com.korenskoy.yandexmusic.domain.model.ThemeScheduleType
import com.korenskoy.yandexmusic.presentation.car.CarPlaylists
import com.korenskoy.yandexmusic.presentation.car.CarRadio
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.presentation.main.QuickAdd
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.TamperBannerOverlay
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1162Test {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .coroutineContext(Dispatchers.Unconfined)
                .components {
                    add(Interceptor { chain -> chain.withRequest(chain.request.newBuilder().crossfade(false).build()).proceed() })
                }
                .build()
        )
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private fun track(id: String, title: String, artist: String = "Исполнитель") =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val tracks = listOf(
        track("1", "Группа крови", "Кино"),
        track("2", "Выхода нет", "Сплин"),
        track("3", "Звезда по имени Солнце", "Кино")
    )

    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    private fun show(darkTheme: Boolean, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalArtworkTint provides tint,
                    LocalLyrics provides LyricsUi(known = { LyricsAnswer.Text("Текст песни") }, load = { LyricsAnswer.Text("Текст песни") }),
                    LocalCarClock provides { 1_792_567_800_000L },
                    LocalLinkState provides LinkState.GOOD
                ) {
                    Box(Modifier.fillMaxSize()) {
                        HiFiBackdrop(tint = tint)
                        content()
                    }
                }
            }
        }
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/v1162_$name.png", options)

    private fun renderRadio(darkTheme: Boolean) {
        val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
        show(darkTheme = darkTheme) {
            RadioScreen(
                stations = stations, allStations = stations, selectedCategory = RadioCategory.ALL,
                onCategorySelect = {}, currentStationId = stations.first().id, isPlaying = true,
                favoriteStationIds = setOf(stations[1].id), onStationClick = {}, onToggleFavorite = {}, onPlayPause = {}
            )
        }
    }

    private fun renderPlayer(darkTheme: Boolean, repeatMode: RepeatMode, shuffled: Boolean) {
        show(darkTheme = darkTheme) {
            FullScreenPlayer(
                track = tracks[0], isPlaying = true, isLiked = true, progress = { 70_000L }, queue = tracks,
                onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
                sourceLabel = "Мне нравится", onTrackWave = {}, offline = TrackOfflineState.NONE,
                repeatMode = repeatMode, shuffled = shuffled, showRepeatAndShuffle = true
            )
        }
    }

    private fun renderSearch(darkTheme: Boolean) {
        show(darkTheme = darkTheme) {
            SearchScreen(
                query = "", onQueryChange = {}, results = emptyList(),
                weeklyHits = tracks, topChart = tracks, trendTracks = tracks,
                isSearching = false, currentTrackId = "1", isPlaying = true, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {}
            )
        }
    }

    private fun renderSettings(darkTheme: Boolean) {
        val state = SettingsUiState(
            themeMode = AppThemeMode.SCHEDULE,
            themeScheduleType = ThemeScheduleType.CUSTOM,
            fullscreenMode = true
        )
        show(darkTheme = darkTheme) {
            SettingsSheet(state = state, actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.INTERFACE)
        }
    }

    private fun renderCar(darkTheme: Boolean, repeatMode: RepeatMode, shuffled: Boolean) {
        val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
        val radio = CarRadio(stations, emptySet(), onAir = null, onPlay = {}, onToggleFavourite = {})
        val playlists = CarPlaylists(emptyList(), QuickAdd("1", emptyMap(), null), {}, { _, _ -> }, { _, _ -> })
        show(darkTheme = darkTheme) {
            CarScreen(
                currentTrack = tracks[0], isPlaying = true, likedTrackIds = setOf("1"),
                onPlayPauseClick = {}, onNextClick = {}, onPrevClick = {}, onToggleLike = {}, onDislikeClick = {},
                onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {}, onPlayDownloaded = {}, onPlayRadio = {}, onExit = {},
                isLandscape = true, source = PlaylistSource.LIBRARY, shuffled = shuffled, repeatMode = repeatMode,
                radio = radio, playlists = playlists
            )
        }
    }

    @Test fun radioNight() { renderRadio(darkTheme = true); snap("radio_night") }
    @Test fun radioDay() { renderRadio(darkTheme = false); snap("radio_day") }

    @Test fun playerRepeatNight() { renderPlayer(darkTheme = true, repeatMode = RepeatMode.ONE, shuffled = true); snap("player_repeat_night") }
    @Test fun playerRepeatDay() { renderPlayer(darkTheme = false, repeatMode = RepeatMode.ALL, shuffled = false); snap("player_repeat_day") }

    @Test fun searchTopChartNight() { renderSearch(darkTheme = true); snap("search_topchart_night") }
    @Test fun searchTopChartDay() { renderSearch(darkTheme = false); snap("search_topchart_day") }

    @Test fun settingsScheduleNight() { renderSettings(darkTheme = true); snap("settings_schedule_night") }
    @Test fun settingsScheduleDay() { renderSettings(darkTheme = false); snap("settings_schedule_day") }

    @Test fun tamperNight() { show(darkTheme = true) { TamperBannerOverlay() }; snap("tamper_night") }
    @Test fun tamperDay() { show(darkTheme = false) { TamperBannerOverlay() }; snap("tamper_day") }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
    fun carRepeatNight() { renderCar(darkTheme = true, repeatMode = RepeatMode.ALL, shuffled = true); snap("car_repeat_night") }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
    fun carRepeatDay() { renderCar(darkTheme = false, repeatMode = RepeatMode.ONE, shuffled = false); snap("car_repeat_day") }
}

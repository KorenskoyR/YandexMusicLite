package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
import com.korenskoy.yandexmusic.presentation.main.components.WaveTuneSheet
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
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

/**
 * 1.14.3 references (issue #22, a Monjaro with a flat 1280 × 480 screen): the wave's tuning and the track menu as
 * panels at the side, the words of a song in the player and in the car, time at the car's timeline, "Перемешать"
 * in "Любимое", the mini-player with "Назад" and its setting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1143Test {
    private companion object {
        const val FLAT = "w1280dp-h480dp-land-mdpi"
        const val CAR = "w1280dp-h720dp-land-mdpi"
        const val TALL = "w412dp-h1900dp-port-xxhdpi"
        const val FIXED_TIME = 1_792_567_800_000L
    }

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private val tint = ArtworkTint(Color(0xFF7A2E3A), Color(0xFF2A0E18))

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000, lyricsAvailable = true)

    private val tracks = listOf(
        track("201", "Звезда по имени Солнце", "Кино"),
        track("202", "Хочешь?", "Земфира"),
        track("203", "Выхода нет", "Сплин"),
        track("204", "Группа крови", "Кино"),
        track("205", "Романс", "Сплин")
    )
    private val words = """
        Белый снег, серый лёд
        На растрескавшейся земле
        Одеялом лоскутным на ней
        Город в дорожной петле

        А над городом плывут облака
        Закрывая небесный свет
        А над городом — жёлтый дым
        Городу две тысячи лет
    """.trimIndent()
    private val lyrics = LyricsUi(known = { LyricsAnswer.Text(words) }, load = { LyricsAnswer.Text(words) })

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(
                    LocalArtworkTint provides tint,
                    LocalLyrics provides lyrics,
                    LocalCarClock provides { FIXED_TIME },
                    LocalLinkState provides LinkState.GOOD
                ) {
                    Box(Modifier.fillMaxSize()) {
                        HiFiBackdrop(tint = tint)
                        content()
                    }
                }
            }
        }
        repeat(25) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    private fun player(lyricsOpen: Boolean, coverOnRight: Boolean = true) = show {
        FullScreenPlayer(
            track = tracks[0], isPlaying = true, isLiked = true, progress = { 61_000L }, queue = tracks,
            onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
            onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
            sourceLabel = "Моя волна", onTrackWave = {}, offline = TrackOfflineState.NONE,
            initialLyricsOpen = lyricsOpen, coverOnRight = coverOnRight
        )
    }

    private fun car(source: PlaylistSource = PlaylistSource.VIBE, lyricsOpen: Boolean = false, shuffled: Boolean = false) = show {
        CarScreen(
            currentTrack = tracks[0], isPlaying = true, likedTrackIds = setOf("201"), onPlayPauseClick = {}, onNextClick = {},
            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
            onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = true, progress = { 82_000L }, durationMs = 215_000,
            upcoming = tracks.drop(1), source = source, initialLyricsOpen = lyricsOpen, shuffled = shuffled,
            onOpenSettings = {}, onVoiceSearch = {}, waveTuning = WaveTuning()
        )
    }

    @Test
    @Config(qualifiers = FLAT)
    fun theWaveTuningAtTheSideOfAFlatScreen() {
        show { WaveTuneSheet(WaveTuning(), onChange = {}, onDismiss = {}) }
        snap("v1143_wave_tune_flat")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun theTrackMenuAtTheSideOfAFlatScreen() {
        show {
            TrackContextMenu(
                track = tracks[1], inPlaylist = UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 42, revision = 7),
                playlists = emptyList(), isLiked = true, startWithPlaylists = false,
                onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {}, onCreatePlaylist = {}, onRemoveFromPlaylist = {},
                onOpenArtist = {}, onOpenAlbum = {}, onTrackWave = {}, offline = TrackOfflineState.NONE
            )
        }
        snap("v1143_track_menu_flat")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun theWordsInThePlayerOnAFlatScreen() {
        player(lyricsOpen = true)
        snap("v1143_player_lyrics_flat")
    }

    @Test
    fun theWordsInThePlayerOnAPhone() {
        player(lyricsOpen = true)
        snap("v1143_player_lyrics_phone")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun theCoverOnThePassengersSide() {
        player(lyricsOpen = false, coverOnRight = false)
        snap("v1143_player_right_wheel_flat")
    }

    @Test
    @Config(qualifiers = CAR)
    fun theWordsInTheCar() {
        car(lyricsOpen = true)
        snap("v1143_car_lyrics")
    }

    @Test
    @Config(qualifiers = CAR)
    fun lovedShuffledInTheCar() {
        car(source = PlaylistSource.LIBRARY, shuffled = true)
        snap("v1143_car_liked_shuffled")
    }

    @Test
    fun theMiniPlayerWithBack() {
        show {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MiniPlayer(
                    track = tracks[0], isPlaying = true, progress = { 61_000L }, onPlayPauseClick = {}, onNextClick = {},
                    onClick = {}, onDismiss = {}, onPrevClick = {}
                )
            }
        }
        snap("v1143_mini_player")
    }

    @Test
    @Config(qualifiers = TALL)
    fun theMiniPlayerOnTheWaveSetting() {
        show { SettingsSheet(state = SettingsUiState(), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.INTERFACE) }
        snap("v1143_settings_interface")
    }
}

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
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.AiMarksUi
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalAiMarks
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
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

/** 1.14.2: the "ИИ" mark on tracks Yandex marks as made with AI, and the switch that passes them over. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotsAiTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private val tint = ArtworkTint(Color(0xFF5A3C8E), Color(0xFF2B1B4E))
    private val marks = AiMarksUi(
        mapOf(
            "102" to "Трек полностью создан с использованием ИИ",
            "104" to "Трек частично создан с использованием ИИ",
            "106" to "Трек полностью создан с использованием ИИ"
        )
    )

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalArtworkTint provides tint, LocalAiMarks provides marks) {
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

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val liked = listOf(
        track("101", "Хочешь?", "Земфира"),
        track("102", "Лучше всех", "Нейробег"),
        track("103", "Группа крови", "Кино"),
        track("104", "Я Нейромузыкант", "Stratum"),
        track("105", "Выхода нет", "Сплин"),
        track("106", "Один я в сети", "Нейробег"),
        track("107", "Кукушка", "Кино")
    )
    private val aiTrack = liked[1]

    @Test
    fun theMarkInAList() {
        show {
            LibraryScreen(
                tracks = liked, isLoading = false, currentTrackId = null, isPlaying = false, likedTrackIds = setOf("101", "103", "106"),
                downloadedTrackIds = emptySet(), downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {}, initialSection = "LIKED"
            )
        }
        snapScreen("v1142_ai_list")
    }

    @Test
    fun theMarkInThePlayer() {
        show {
            FullScreenPlayer(
                track = aiTrack, isPlaying = true, isLiked = false, progress = { 61_000L }, queue = liked,
                onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
                sourceLabel = "Моя волна", onTrackWave = {}
            )
        }
        snapScreen("v1142_ai_player")
    }

    @Test
    fun theMenuSaysWhatYandexSays() {
        show {
            TrackContextMenu(
                track = aiTrack, inPlaylist = null, playlists = emptyList(), isLiked = false, startWithPlaylists = false,
                onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {}, onCreatePlaylist = {}, onRemoveFromPlaylist = {}
            )
        }
        snapScreen("v1142_ai_menu")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theMarkInTheCar() {
        show {
            CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.GOOD) {
                CarScreen(
                    currentTrack = aiTrack, isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
                    onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                    onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = true, progress = { 82_000L }, durationMs = 215_000,
                    upcoming = liked.drop(2), source = PlaylistSource.VIBE, initialQueueOpen = true, onOpenSettings = {}, onVoiceSearch = {}
                )
            }
        }
        snapScreen("v1142_ai_car")
    }

    // A tall screen shows the whole page
    @Test
    @Config(qualifiers = "w412dp-h1900dp-port-xxhdpi")
    fun theSwitchBesideTheExplicitFilter() {
        show {
            SettingsSheet(state = SettingsUiState(skipAi = true, crossfadeSeconds = 3, reserveMb = 100), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
        }
        snapScreen("v1142_ai_settings")
    }

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
    }
}

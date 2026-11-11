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
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
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

/**
 * 1.14.1 additions: the car on a flat head unit, the microphone in the car and on the search page, and one
 * track's download button in the player and its menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotsAdditionsTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalArtworkTint provides tint) {
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

    private val now = track("1", "Хочешь?", "Земфира")
    private val next = listOf(
        track("2", "Группа крови", "Кино"),
        track("3", "Небомореоблака", "Земфира"),
        track("4", "Восьмиклассница", "Кино"),
        track("5", "Кукла колдуна", "Король и Шут"),
        track("6", "Звезда по имени Солнце", "Кино"),
        track("7", "Выхода нет", "Сплин")
    )
    private val station = TrackDetailsDto(id = JsonPrimitive("radio_evropa_plus"), title = "Европа Плюс", isRadio = true)

    @Composable
    private fun Car(wheelOnLeft: Boolean = true, tuneOpen: Boolean = false, track: TrackDetailsDto = now, voice: Boolean = true) =
        CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.GOOD) {
            CarScreen(
                currentTrack = track, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = true, steeringWheelOnLeft = wheelOnLeft,
                progress = { 82_000L }, durationMs = 286_000, upcoming = if (track.isRadio) emptyList() else next,
                source = if (track.isRadio) PlaylistSource.RADIO else PlaylistSource.VIBE,
                initialTuneOpen = tuneOpen, onOpenSettings = {}, onVoiceSearch = if (voice) ({}) else null
            )
        }

    // --- The car on a flat head unit: 1920×720 ---

    @Test
    @Config(qualifiers = FLAT)
    fun flatHeadUnit() {
        show { Car() }
        snapScreen("v1141_car_flat")
    }

    @Test
    @Config(qualifiers = FLAT_HDPI)
    fun flatHeadUnitAtAHigherDensity() {
        show { Car() }
        snapScreen("v1141_car_flat_hdpi")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun flatHeadUnitRightWheel() {
        show { Car(wheelOnLeft = false) }
        snapScreen("v1141_car_flat_right")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun flatHeadUnitTuneOpen() {
        show { Car(tuneOpen = true) }
        snapScreen("v1141_car_flat_tune")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun flatHeadUnitRadio() {
        show { Car(track = station) }
        snapScreen("v1141_car_flat_radio")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun usualHeadUnitWithTheMicrophone() {
        show { Car() }
        snapScreen("v1141_car_voice")
    }

    // --- The microphone on the search page ---

    @Test
    fun searchWithTheMicrophone() {
        show {
            SearchScreen(
                query = "", onQueryChange = {}, results = emptyList(), weeklyHits = next, trendTracks = next,
                isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино", "земфира"), onVoice = {}
            )
        }
        snapScreen("v1141_search_voice")
    }

    // --- One track on the device ---

    @Composable
    private fun Player(offline: TrackOfflineState) = FullScreenPlayer(
        track = now, isPlaying = true, isLiked = true, progress = { 82_000L }, queue = listOf(now) + next,
        onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
        onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
        sourceLabel = "Моя волна", offline = offline
    )

    @Test
    fun playerDownload() {
        show { Player(TrackOfflineState.NONE) }
        snapScreen("v1141_player_download")
    }

    @Test
    fun playerDownloading() {
        show { Player(TrackOfflineState.DOWNLOADING) }
        snapScreen("v1141_player_downloading")
    }

    @Test
    fun playerDownloaded() {
        show { Player(TrackOfflineState.DONE) }
        snapScreen("v1141_player_downloaded")
    }

    @Test
    fun trackMenuDownload() {
        show {
            TrackContextMenu(
                track = now, inPlaylist = null, playlists = emptyList(), isLiked = true, startWithPlaylists = false,
                onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {}, onCreatePlaylist = {}, onRemoveFromPlaylist = {},
                offline = TrackOfflineState.NONE
            )
        }
        snapScreen("v1141_menu_download")
    }

    @Test
    fun trackMenuOnDevice() {
        show {
            TrackContextMenu(
                track = now, inPlaylist = null, playlists = emptyList(), isLiked = true, startWithPlaylists = false,
                onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {}, onCreatePlaylist = {}, onRemoveFromPlaylist = {},
                offline = TrackOfflineState.DONE
            )
        }
        snapScreen("v1141_menu_on_device")
    }

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
        /** 1920×720 at mdpi: 1920×720 dp. */
        const val FLAT = "w1920dp-h720dp-land-mdpi"
        /** The same panel reporting hdpi: 1280×480 dp. */
        const val FLAT_HDPI = "w1280dp-h480dp-land-hdpi"
    }
}

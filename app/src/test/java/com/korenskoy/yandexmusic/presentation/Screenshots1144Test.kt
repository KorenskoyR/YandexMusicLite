package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.auth.DeviceSignIn
import com.korenskoy.yandexmusic.data.download.Drive
import com.korenskoy.yandexmusic.data.download.StorageStatus
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.CarLayout
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.auth.CodeFallbackStrip
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInScreen
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInState
import com.korenskoy.yandexmusic.presentation.car.CarPlaylists
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.QuickAdd
import com.korenskoy.yandexmusic.presentation.main.QuickMark
import com.korenskoy.yandexmusic.presentation.main.QuickNote
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
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
 * 1.14.4 references: «В плейлист» in the car, three columns kept on a flat unit at a large interface scale, the
 * way out of Yandex's sign-in page by code, a flash drive chosen without the system's folder window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1144Test {
    private companion object {
        const val CAR = "w1280dp-h720dp-land-mdpi"
        const val STANDING = "w960dp-h1280dp-port-mdpi"

        /** 1920 × 720 at a large interface scale: the room is a little under the full three columns. */
        const val FLAT_SCALED = "w960dp-h360dp-land-xhdpi"
        const val TALL = "w412dp-h1900dp-port-xxhdpi"
        const val FIXED_TIME = 1_792_567_800_000L
    }

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        coil3.SingletonImageLoader.setUnsafe(coil3.ImageLoader.Builder(context).coroutineContext(kotlinx.coroutines.Dispatchers.Unconfined).build())
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private val tint = ArtworkTint(Color(0xFF7A2E3A), Color(0xFF2A0E18))

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val tracks = listOf(
        track("201", "Хочешь?", "Земфира"),
        track("202", "Звезда по имени Солнце", "Кино"),
        track("203", "Выхода нет", "Сплин"),
        track("204", "Группа крови", "Кино"),
        track("205", "Романс", "Сплин")
    )

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalArtworkTint provides tint, LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.GOOD) {
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

    /** The settings are long: the rows of the picture brought into sight first. */
    private fun scrollTo(text: String) {
        // The test's own clock runs for the scroll: with it stopped the scroll waits for ever
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(text).performScrollTo()
        compose.mainClock.autoAdvance = false
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    private val playlists = CarPlaylists(
        listOf("В дорогу" to 42, "Рок" to 118, "Русское" to 67, "Спокойное" to 23, "Для пробежки" to 31).mapIndexed { i, (name, count) ->
            UserPlaylistDto(kind = JsonPrimitive(1003 + i), title = name, trackCount = count, revision = 1)
        },
        QuickAdd("201", mapOf("1004" to QuickMark.ADDED, "1006" to QuickMark.ALREADY), QuickNote("Добавлено в «Рок»", 0)),
        onOpen = {}, onAdd = { _, _ -> }, onCreate = { _, _ -> }
    )

    private fun car(landscape: Boolean = true, playlistsOpen: Boolean = false, layout: CarLayout = CarLayout.AUTO) = show {
        CarScreen(
            currentTrack = tracks[0], isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
            onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = landscape, progress = { 82_000L }, durationMs = 215_000,
            upcoming = tracks.drop(1), source = PlaylistSource.VIBE, onOpenSettings = {}, onVoiceSearch = {}, waveTuning = WaveTuning(),
            playlists = playlists, initialPlaylistsOpen = playlistsOpen, layout = layout
        )
    }

    @Test
    @Config(qualifiers = CAR)
    fun theButtonBesideTheTitle() {
        car()
        snap("v1144_car_playlist_button")
    }

    @Test
    @Config(qualifiers = CAR)
    fun thePlaylistsOverTheCover() {
        car(playlistsOpen = true)
        snap("v1144_car_playlists")
    }

    @Test
    @Config(qualifiers = STANDING)
    fun thePlaylistsStanding() {
        car(landscape = false, playlistsOpen = true)
        snap("v1144_car_playlists_standing")
    }

    @Test
    @Config(qualifiers = FLAT_SCALED)
    fun threeColumnsKeptAtALargeScale() {
        car(layout = CarLayout.THREE)
        snap("v1144_car_three_scaled")
    }

    @Test
    @Config(qualifiers = FLAT_SCALED)
    fun twoColumnsByTheRoomAtALargeScale() {
        car(layout = CarLayout.AUTO)
        snap("v1144_car_two_scaled")
    }

    @Test
    fun theWayOutOfTheSignInPage() {
        show {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF2F3F5)))
                CodeFallbackStrip(reset = true, onSignInByCode = {})
            }
        }
        snap("v1144_auth_strip")
    }

    @Test
    @Config(qualifiers = CAR)
    fun theWayOutOfTheSignInPageOnAHeadUnit() {
        show {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF2F3F5)))
                CodeFallbackStrip(reset = false, onSignInByCode = {})
            }
        }
        snap("v1144_auth_strip_wide")
    }

    @Test
    @Config(qualifiers = TALL)
    fun theSignInByCodeChosen() {
        show {
            PhoneSignInScreen(
                PhoneSignInState.Waiting(DeviceSignIn.Code("d", "LH2EL4HK", "https://ya.ru/device", 5, 300), expiresAt = android.os.SystemClock.elapsedRealtime() + 300_000L),
                onNewCode = {}, onBackToPage = {}
            )
        }
        snap("v1144_auth_code_chosen")
    }

    @Test
    @Config(qualifiers = TALL)
    fun theFlashDrivesFoundWithoutTheFolderWindow() {
        val drives = listOf(
            Drive("/storage/1A2B-3C4D/Android/data/app/files", "USB-накопитель", usb = true, freeBytes = 28_400_000_000L),
            Drive("/storage/5F1E-22A0/Android/data/app/files", "USB-накопитель (2)", usb = true, freeBytes = 7_100_000_000L)
        )
        val storage = StorageStatus(
            cardAvailable = true, onCard = true, cardFreeBytes = 28_400_000_000L, pickerAvailable = false,
            drives = drives, cardPath = drives[0].path, cardIsUsb = true
        )
        show { SettingsSheet(state = SettingsUiState(storage = storage), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK) }
        scrollTo("Указать путь")
        snap("v1144_settings_drives")
    }

    @Test
    @Config(qualifiers = TALL)
    fun noFlashDriveShownToTheApp() {
        show { SettingsSheet(state = SettingsUiState(storage = StorageStatus(pickerAvailable = false)), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK) }
        scrollTo("Указать путь")
        snap("v1144_settings_no_drive")
    }
}

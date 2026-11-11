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
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalRules
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.data.local.LocalTrack
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarDeviceFolder
import com.korenskoy.yandexmusic.presentation.car.CarDeviceMusic
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.local.LocalMusicUi
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
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

/** 1.14.1: the music already on the device — a folder in "Скачанные", its page, and the car's "Скачанные". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotsLocalTest {

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

    private val liked = listOf(
        track("1", "Хочешь?", "Земфира"),
        track("2", "Группа крови", "Кино"),
        track("3", "Небомореоблака", "Земфира"),
        track("4", "Восьмиклассница", "Кино"),
        track("5", "Кукла колдуна", "Король и Шут")
    )

    private fun local(id: Int, title: String, artist: String?, source: LocalSource, folder: String, added: Long, ms: Long = 228_000) = LocalTrack(
        id = "local_m$id", uri = "content://media/external/audio/media/$id", title = title, artist = artist, album = null,
        durationMs = ms, source = source, folder = folder, addedAt = added, format = "MP3", bitrateKbps = 320
    )

    private val device = listOf(
        local(1, "Ночной город", null, LocalSource.FLASH, "Дорожное/", 90, 252_000),
        local(2, "Track 05", "Сборник 2019", LocalSource.FLASH, "Сборник 2019/", 80, 227_000),
        local(3, "Звезда по имени Солнце", "Кино", LocalSource.PHONE, "Music/", 70, 225_000),
        local(4, "Lullaby", "Unknown artist", LocalSource.SD, "Music/Lullaby/", 60, 320_000),
        local(5, "Кукушка", "Кино", LocalSource.FLASH, "Рок/", 50, 395_000),
        local(6, "Выхода нет", "Сплин", LocalSource.FLASH, "Рок/", 40, 248_000),
        local(7, "Перемен", "Кино", LocalSource.FLASH, "Рок/", 30, 296_000),
        local(8, "Батарейка", "Жуки", LocalSource.PHONE, "Download/", 20, 216_000),
        local(9, "Город золотой", "Аквариум", LocalSource.FLASH, "Дорожное/", 10, 183_000)
    )

    private fun ui(library: LocalMusicRepository.Library, seen: Boolean = true) = LocalMusicUi(
        library = library,
        seen = seen,
        toTrack = { TrackDetailsDto(id = JsonPrimitive(it.id), title = it.title, localUri = it.uri) }
    )

    private val withMusic = LocalMusicRepository.Library(LocalRules.sorted(device, com.korenskoy.yandexmusic.data.local.LocalSort.RECENT), hasAccess = true, scanned = true)

    @Composable
    private fun Library(section: String, library: LocalMusicRepository.Library, seen: Boolean = true, view: Int = 0, playingId: String? = null) = LibraryScreen(
        tracks = liked, isLoading = false, currentTrackId = playingId, isPlaying = playingId != null, likedTrackIds = setOf("1", "2", "3"),
        downloadedTrackIds = setOf("1", "2", "3", "4"), downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
        onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {},
        downloadedTracks = liked.take(4),
        offlineCollections = listOf(OfflineCollection("playlist:me:3", "В дорогу", tracks = liked.take(3))),
        localMusic = ui(library, seen),
        initialSection = section,
        initialLocalView = view
    )

    // --- "Скачанные": the folder first in the row ---

    @Test
    fun downloadsOpenWithTheDevicesFolder() {
        show { Library("DOWNLOADED", withMusic, seen = false) }
        snapScreen("v1141_local_in_downloads")
    }

    @Test
    fun theFolderAsksWhenThereIsNoAccessYet() {
        show { Library("DOWNLOADED", LocalMusicRepository.Library(hasAccess = false, scanned = true)) }
        snapScreen("v1141_local_in_downloads_ask")
    }

    // --- "Музыка с устройства" ---

    @Test
    fun theDevicesMusicAsTracks() {
        show { Library("LOCAL", withMusic, playingId = "local_m3") }
        snapScreen("v1141_local_tracks")
    }

    @Test
    fun theDevicesMusicAsFolders() {
        show { Library("LOCAL", withMusic, view = 1) }
        snapScreen("v1141_local_folders")
    }

    @Test
    fun theDevicesMusicByArtist() {
        show { Library("LOCAL", withMusic, view = 2) }
        snapScreen("v1141_local_artists")
    }

    @Test
    fun theFirstLookAsksForTheAccess() {
        show { Library("LOCAL", LocalMusicRepository.Library(hasAccess = false, scanned = true)) }
        snapScreen("v1141_local_access")
    }

    @Test
    fun nothingOnTheDeviceSaysWhatToDo() {
        show { Library("LOCAL", LocalMusicRepository.Library(hasAccess = true, scanned = true)) }
        snapScreen("v1141_local_empty")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theDevicesMusicOnAHeadUnitLying() {
        show { Library("LOCAL", withMusic) }
        snapScreen("v1141_local_tracks_land")
    }

    // --- The player: the user's own file has no like, wave or download ---

    @Test
    fun theFullPlayerWithAFileOfTheDevice() {
        val file = TrackDetailsDto(
            id = JsonPrimitive("local_m3"), title = "Звезда по имени Солнце", artists = listOf(ArtistDto(name = "Кино")),
            durationMs = 225_000, localUri = "content://media/external/audio/media/3"
        )
        show {
            CompositionLocalProvider(com.korenskoy.yandexmusic.utils.LocalStreamQuality provides "MP3 · 320 кбит/с · с устройства") {
                FullScreenPlayer(
                    track = file, isPlaying = true, isLiked = false, progress = { 61_000L }, queue = listOf(file),
                    onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                    onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
                    sourceLabel = "Музыка с устройства", onTrackWave = {}, offline = null
                )
            }
        }
        snapScreen("v1141_local_player")
    }

    // --- The car: "Скачанные" opens a choice ---

    private val carMusic = CarDeviceMusic(
        downloadedCount = 128,
        deviceCount = 240,
        sources = listOf(LocalSource.PHONE, LocalSource.FLASH),
        folders = listOf(
            CarDeviceFolder("FLASH:", null, 12, LocalSource.FLASH),
            CarDeviceFolder("FLASH:Рок/", "Рок", 64, LocalSource.FLASH),
            CarDeviceFolder("FLASH:Дорожное/", "Дорожное", 38, LocalSource.FLASH),
            CarDeviceFolder("FLASH:Сборник 2019/", "Сборник 2019", 21, LocalSource.FLASH)
        )
    )
    private val playing = track("1", "Хочешь?", "Земфира")

    @Composable
    private fun Car(wheelOnLeft: Boolean = true, landscape: Boolean = true, open: Boolean = true, current: TrackDetailsDto = playing, source: PlaylistSource = PlaylistSource.VIBE) =
        CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.GOOD) {
            CarScreen(
                currentTrack = current, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = landscape, steeringWheelOnLeft = wheelOnLeft,
                progress = { 82_000L }, durationMs = 286_000, upcoming = liked.drop(1), source = source,
                onOpenSettings = {}, onVoiceSearch = {}, deviceMusic = carMusic, initialDownloadsOpen = open
            )
        }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun carDownloads() {
        show { Car() }
        snapScreen("v1141_car_downloads")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun carDownloadsRightWheel() {
        show { Car(wheelOnLeft = false) }
        snapScreen("v1141_car_downloads_right")
    }

    @Test
    @Config(qualifiers = FLAT)
    fun carDownloadsOnAFlatHeadUnit() {
        show { Car() }
        snapScreen("v1141_car_downloads_flat")
    }

    @Test
    @Config(qualifiers = "w960dp-h1280dp-port-hdpi")
    fun carDownloadsStanding() {
        show { Car(landscape = false) }
        snapScreen("v1141_car_downloads_standing")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun carPlaysAFileOfTheDevice() {
        val file = TrackDetailsDto(
            id = JsonPrimitive("local_m5"), title = "Кукушка", artists = listOf(ArtistDto(name = "Кино")),
            durationMs = 395_000, localUri = "content://media/external/audio/media/5"
        )
        show { Car(open = false, current = file, source = PlaylistSource.DEVICE) }
        snapScreen("v1141_car_local_playing")
    }

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
        const val FLAT = "w1920dp-h720dp-land-mdpi"
    }
}

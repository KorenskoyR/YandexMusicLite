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
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.crossfade
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import com.korenskoy.yandexmusic.data.download.OfflineState
import com.korenskoy.yandexmusic.data.download.StorageStatus
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.WaveDiversity
import com.korenskoy.yandexmusic.domain.model.WaveLanguage
import com.korenskoy.yandexmusic.domain.model.WaveMood
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.catalog.CatalogActions
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPage
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageState
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.OpenedPlaylist
import com.korenskoy.yandexmusic.presentation.main.PlaylistScreen
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
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

/**
 * 1.13.0 references: the bigger cover in the car with "Далее" as small covers (and without it), playlists
 * and albums kept on the device, "Скачанные" with them, and where to keep downloads (a flash drive too).
 * Recorded and checked like [ScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots113Test {

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
        track("1", "Первая песня", "Группа"),
        track("2", "Очень длинное название трека, которое не помещается в одну строку", "Исполнитель с длинным именем"),
        track("3", "Третья песня")
    )
    private val playlists = listOf(UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 7, revision = 7))
    private val tuned = WaveTuning(WaveDiversity.DISCOVER, WaveMood.CALM, WaveLanguage.FOREIGN)
    private val seed = track("9", "Звезда по имени Солнце", "Кино")
    private val longSeed = track("10", "Очень длинное название трека, по которому запущена волна", "Кино")
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

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", options)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    // --- В авто: a bigger cover, "Далее" as small covers ---

    @Composable
    private fun Car(
        isLandscape: Boolean,
        wheelOnLeft: Boolean = true,
        seedTrack: TrackDetailsDto? = null,
        next: List<TrackDetailsDto> = listOf(tracks[2], tracks[1], tracks[0], seed),
        loading: Boolean = false,
        showNext: Boolean = true
    ) = CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.GOOD) {
        CarScreen(
            currentTrack = tracks[0], isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
            onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = wheelOnLeft,
            progress = { 82_000L }, durationMs = 286_000, upcoming = next, upcomingLoading = loading, source = PlaylistSource.VIBE,
            showNext = showNext, waveTuning = tuned, trackWaveSeed = seedTrack
        )
    }

    @Test
    @Config(qualifiers = "+land")
    fun carBigCover() {
        show { Car(isLandscape = true) }
        snap("v113_car")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carBigCoverRightWheel() {
        show { Car(isLandscape = true, wheelOnLeft = false, seedTrack = longSeed) }
        snap("v113_car_right")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carWithoutNext() {
        show { Car(isLandscape = true, showNext = false) }
        snap("v113_car_no_next")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carNextLoading() {
        show { Car(isLandscape = true, next = emptyList(), loading = true) }
        snap("v113_car_loading")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun headUnitCar() {
        show { Car(isLandscape = true) }
        snap("v113_head_car")
    }

    @Test
    @Config(qualifiers = OLD_HEAD_UNIT)
    fun oldHeadUnitCar() {
        show { Car(isLandscape = true) }
        snap("v113_head_old_car")
    }

    @Test
    @Config(qualifiers = WIDE_HEAD_UNIT)
    fun wideHeadUnitCar() {
        show { Car(isLandscape = true) }
        snap("v113_head_wide_car")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun phoneOnItsSideCar() {
        show { Car(isLandscape = true) }
        snap("v113_phone_side_car")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCar() {
        show { Car(isLandscape = false) }
        snap("v113_geely_car")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarWithoutNext() {
        show { Car(isLandscape = false, showNext = false) }
        snap("v113_geely_car_no_next")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarLoading() {
        show { Car(isLandscape = false, next = emptyList(), loading = true) }
        snap("v113_geely_car_loading")
    }

    @Test
    fun phoneCarPortrait() {
        show { Car(isLandscape = false) }
        snap("v113_car_portrait")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneCarPortrait() {
        show { Car(isLandscape = false) }
        snap("v113_small_car_portrait")
    }

    // --- A playlist or an album on the device ---

    private val roadTracks = (1..7).map { track("$it", "Песня номер $it", "Кино") }

    @Composable
    private fun Playlist(offline: OfflineState, downloaded: Set<String>) = PlaylistScreen(
        opened = OpenedPlaylist(playlists[0], roadTracks, isLoading = false),
        currentTrackId = null, isPlaying = false, likedTrackIds = setOf("1", "3"), downloadedTrackIds = downloaded,
        contentBottom = 0.dp, onBack = {}, onPlay = {}, onTrackClick = {}, onToggleLike = {}, onRetry = {},
        onRename = {}, onDelete = {}, offline = offline
    )

    @Test
    fun playlistNotKept() {
        show { Playlist(OfflineState.None, emptySet()) }
        snap("v113_playlist_none")
    }

    @Test
    fun playlistDownloading() {
        show { Playlist(OfflineState.Downloading(3, 7, "4"), setOf("1", "2", "3")) }
        snap("v113_playlist_downloading")
    }

    @Test
    fun playlistKept() {
        show { Playlist(OfflineState.Done(7, 7, 61_000_000L), roadTracks.map { it.idString }.toSet()) }
        snap("v113_playlist_done")
    }

    @Test
    fun playlistKeptPartly() {
        show { Playlist(OfflineState.Done(5, 7, 43_000_000L), setOf("1", "2", "3", "4", "5")) }
        snap("v113_playlist_partly")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun headUnitPlaylistDownloading() {
        show { Playlist(OfflineState.Downloading(3, 7, "4"), setOf("1", "2", "3")) }
        snap("v113_head_playlist_downloading")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhonePlaylistKept() {
        show { Playlist(OfflineState.Done(7, 7, 61_000_000L), roadTracks.map { it.idString }.toSet()) }
        snap("v113_small_playlist_done")
    }

    private val album = AlbumDto(
        id = JsonPrimitive(2), title = "Группа крови", year = 1988, trackCount = 3,
        artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Кино")), volumes = listOf(tracks)
    )

    @Test
    fun albumKept() {
        show {
            CatalogPageScreen(
                state = CatalogPageState(CatalogPage.Album("2", "Группа крови"), album = album, isLoading = false),
                currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), contentBottom = 0.dp,
                actions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}),
                downloadedTrackIds = setOf("1", "2", "3"), offline = OfflineState.Done(3, 3, 24_000_000L)
            )
        }
        snap("v113_album_done")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun headUnitAlbumNotKept() {
        show {
            CatalogPageScreen(
                state = CatalogPageState(CatalogPage.Album("2", "Группа крови"), album = album, isLoading = false),
                currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), contentBottom = 0.dp,
                actions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {})
            )
        }
        snap("v113_head_album_none")
    }

    @Test
    fun downloadedWithKeptPlaylists() {
        val kept = listOf(
            OfflineCollection("playlist:me:1003", "В дорогу", null, roadTracks),
            OfflineCollection("album:2", "Группа крови", null, tracks)
        )
        val ids = (roadTracks + tracks).map { it.idString }.toSet()
        show {
            LibraryScreen(
                tracks = tracks, isLoading = false, currentTrackId = null, isPlaying = false, likedTrackIds = setOf("1", "2", "3"),
                downloadedTrackIds = ids, downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {}, playlists = playlists,
                downloadedTracks = (tracks + roadTracks).distinctBy { it.idString }, offlineCollections = kept,
                initialSection = "DOWNLOADED"
            )
        }
        snap("v113_downloaded")
    }

    // --- Where to keep downloads: memory, SD card, flash drive ---

    private val settingsState = SettingsUiState(
        accountName = "Тестовый Аккаунт", hasPlus = true, defaultAccountName = null, crashReports = 0,
        crossfadeSeconds = 3, reserveMb = 100, reserveUsedBytes = 64L * 1024 * 1024, autoDownload = true, cacheSizeBytes = 3_800_000_000L,
        equalizerAvailable = true
    )

    private fun settings(name: String, storage: StorageStatus) {
        show { SettingsSheet(state = settingsState.copy(storage = storage), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK) }
        snapScreen(name)
    }

    private val onUsb = StorageStatus(onUsb = true, usbChosen = true, usbAvailable = true, usbFreeBytes = 28_400_000_000L, usbLabel = "USB-накопитель › YandexMusicLite")

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun storageOnUsb() = settings("v113_storage_usb", onUsb)

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun storageUsbTakenOut() = settings("v113_storage_usb_out", onUsb.copy(usbAvailable = false, usbFreeBytes = 0, usbLabel = null))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun storageWithCardAndUsb() = settings("v113_storage_three", StorageStatus(cardAvailable = true, cardFreeBytes = 21_400_000_000L))

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
        const val SMALL_PHONE = "w360dp-h640dp-port-xhdpi"
        const val HEAD_UNIT = "w1280dp-h720dp-land-mdpi"
        const val OLD_HEAD_UNIT = "w1024dp-h600dp-land-mdpi"
        const val WIDE_HEAD_UNIT = "w1920dp-h720dp-land-mdpi"
        const val PHONE_ON_SIDE = "w800dp-h360dp-land-xhdpi"
        const val GEELY = "w960dp-h1280dp-port-hdpi"
        const val TALL_PHONE = "w411dp-h2300dp-port-xhdpi"
    }
}

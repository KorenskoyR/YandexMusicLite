package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistBriefDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogActions
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPage
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageState
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.OpenedPlaylist
import com.korenskoy.yandexmusic.presentation.main.PlaylistScreen
import com.korenskoy.yandexmusic.presentation.main.PlaylistsRow
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenu
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.presentation.main.components.TrackMenuLauncher
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.crossfade
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import java.util.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of the main screens and components (Roborazzi on Robolectric). They catch
 * visual regressions the old source-text checks could not, like the "empty square" radio cards.
 * Images: app/src/test/screenshots. `./gradlew recordRoborazziDebug` re-records them after an intended
 * change, `./gradlew verifyRoborazziDebug` fails on any difference. A plain test run only renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        // Images (bundled radio logos) load synchronously and without the crossfade, which runs on real
        // time and would leave them transparent in the frozen frames
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

    private val playlists = listOf(
        UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 42, revision = 7),
        UserPlaylistDto(kind = JsonPrimitive(1004), title = "Спокойное", trackCount = 1, revision = 2)
    )

    /** Renders [content] on the app's background, with infinite animations frozen. */
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF111115))) { content() }
            }
        }
        // Frames in steps: an image loaded meanwhile is laid out and drawn (crossfade included)
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle() // Coil delivers results on the main looper
        }
    }

    /**
     * Half scale keeps the references small in git and still shows layout and image regressions. A 0.5 %
     * pixel tolerance absorbs font antialiasing differences between Windows (recorded) and Linux (CI).
     */
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", options)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    @Test
    fun trackItems() {
        show {
            Column(Modifier.padding(8.dp)) {
                TrackItem(track = tracks[0], isPlaying = false, onClick = {})
                TrackItem(track = tracks[1], isPlaying = true, isLiked = true, isDownloaded = true, onClick = {})
                // With a track menu, every row gets the "⋮" button
                CompositionLocalProvider(LocalTrackMenu provides TrackMenuLauncher { _, _ -> }) {
                    TrackItem(track = tracks[2], isPlaying = false, isLiked = true, onClick = {})
                }
            }
        }
        snap("track_items")
    }

    @Test
    fun libraryWithPlaylists() {
        show {
            LibraryScreen(
                tracks = tracks, isLoading = false, isDownloadingAll = false, autoDownloadEnabled = false,
                currentTrackId = "1", isPlaying = true, likedTrackIds = setOf("1", "2", "3"), downloadedTrackIds = setOf("2"),
                cacheSizeBytes = 0, downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onEnableAutoDownload = {}, onCancelDownload = {}, onClearCache = {}, onToggleLike = {},
                accountName = "Тестовый Аккаунт", playlists = playlists
            )
        }
        snap("library")
    }

    @Test
    fun playlistsRowStartsWithNewPlaylist() {
        show { PlaylistsRow(playlists = playlists, onOpen = {}, onCreate = {}, modifier = Modifier.padding(16.dp)) }
        snap("playlists_row")
    }

    @Test
    fun openedPlaylist() {
        show {
            PlaylistScreen(
                opened = OpenedPlaylist(playlists[0], tracks, isLoading = false),
                currentTrackId = null, isPlaying = false, likedTrackIds = setOf("1"), downloadedTrackIds = emptySet(),
                contentBottom = 0.dp, onBack = {}, onPlay = {}, onTrackClick = {}, onToggleLike = {}, onRetry = {},
                onRename = {}, onDelete = {}
            )
        }
        snap("playlist")
    }

    @Test
    fun trackMenu() {
        show {
            TrackContextMenu(
                track = tracks[0], inPlaylist = playlists[0], playlists = playlists, isLiked = false,
                startWithPlaylists = false, onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {},
                onCreatePlaylist = {}, onRemoveFromPlaylist = {}
            )
        }
        snapScreen("track_menu")
    }

    @Test
    fun playlistPicker() {
        show {
            TrackContextMenu(
                track = tracks[0], inPlaylist = null, playlists = playlists, isLiked = true,
                startWithPlaylists = true, onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {},
                onCreatePlaylist = {}, onRemoveFromPlaylist = {}
            )
        }
        snapScreen("playlist_picker")
    }

    @Test
    fun miniPlayerAndBottomBar() {
        show {
            Column(Modifier.padding(top = 24.dp)) {
                MiniPlayer(
                    track = tracks[0], isPlaying = true, isLiked = true, progress = 60_000,
                    onPlayPauseClick = {}, onToggleLike = {}, onClick = {}, onDismiss = {}
                )
                FloatingGlassBottomBar(currentRoute = "library", onNavigate = {})
            }
        }
        snap("mini_player_bottom_bar")
    }

    @Test
    fun fullPlayer() {
        show {
            FullScreenPlayer(
                track = tracks[0], isPlaying = true, isLiked = true, progress = 60_000, queue = tracks,
                onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {}
            )
        }
        snap("full_player")
    }

    /** Bundled station logos on their cards: the 1.4.x "empty square" regression shows up here. */
    @Test
    fun radioStations() {
        val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
        show {
            RadioScreen(
                stations = stations, searchQuery = "", onQueryChange = {}, selectedCategory = RadioCategory.ALL,
                onCategorySelect = {}, currentStationId = stations.first().id, isPlaying = true,
                favoriteStationIds = setOf(stations[1].id), onStationClick = {}, onToggleFavorite = {}
            )
        }
        snap("radio")
    }

    private val album = AlbumDto(
        id = JsonPrimitive(2), title = "Группа крови", year = 1988, trackCount = 3,
        artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Кино")), volumes = listOf(tracks)
    )

    @Test
    fun artistPage() {
        show {
            CatalogPageScreen(
                state = CatalogPageState(
                    CatalogPage.Artist("7", "Кино"),
                    artist = ArtistBriefDto(ArtistDto(id = JsonPrimitive(7), name = "Кино"), tracks, listOf(album, album.copy(id = JsonPrimitive(3), title = "Звезда по имени Солнце", year = 1989))),
                    isLoading = false
                ),
                currentTrackId = "1", isPlaying = true, likedTrackIds = setOf("1"), contentBottom = 0.dp, actions = catalogActions
            )
        }
        snap("artist")
    }

    @Test
    fun albumPage() {
        show {
            CatalogPageScreen(
                state = CatalogPageState(CatalogPage.Album("2", "Группа крови"), album = album, isLoading = false),
                currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), contentBottom = 0.dp, actions = catalogActions
            )
        }
        snap("album")
    }

    @Test
    fun searchWithArtistsAndAlbums() {
        show {
            SearchScreen(
                query = "кино", onQueryChange = {}, results = tracks, weeklyHits = emptyList(), trendTracks = emptyList(),
                isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {},
                artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Кино"), ArtistDto(id = JsonPrimitive(8), name = "Кино и немцы")),
                albums = listOf(album)
            )
        }
        snap("search_results")
    }

    @Test
    fun searchHistory() {
        show {
            SearchScreen(
                query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, trendTracks = tracks,
                isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино", "ария", "radio record")
            )
        }
        snap("search_history")
    }

    private val catalogActions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {})

    @Test
    fun carPortrait() {
        show { car(isLandscape = false) }
        snap("car_portrait")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscape() {
        show { car(isLandscape = true) }
        snap("car_landscape")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscapeRightHandDrive() {
        show { car(isLandscape = true, steeringWheelOnLeft = false) }
        snap("car_landscape_right_wheel")
    }

    @Composable
    private fun car(isLandscape: Boolean, steeringWheelOnLeft: Boolean = true) = CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }) { CarScreen(
        currentTrack = tracks[0], isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
        onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
        onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = steeringWheelOnLeft, progress = 60_000, durationMs = 215_000
    ) }

    private companion object {
        /** 21.10.2026 10:30 MSK: the car clock in the screenshots. */
        const val FIXED_TIME = 1_792_567_800_000L
    }
}

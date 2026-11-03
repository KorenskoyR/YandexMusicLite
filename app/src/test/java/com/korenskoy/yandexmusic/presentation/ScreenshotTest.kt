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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
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

    /** A fixed blue cover tint, so the living background is the same on every machine. */
    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    /** Renders [content] on the app's living background, with infinite animations frozen. */
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
                tracks = tracks, isLoading = false, autoDownloadEnabled = false,
                currentTrackId = "1", isPlaying = true, likedTrackIds = setOf("1", "2", "3"), downloadedTrackIds = setOf("2"),
                cacheSizeBytes = 0, downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onEnableAutoDownload = {}, onCancelDownload = {}, onClearCache = {}, onToggleLike = {},
                accountName = "Тестовый Аккаунт", playlists = playlists
            )
        }
        snap("library")
    }

    @Test
    fun wavePlaying() {
        show {
            HomeScreen(
                waveTrack = tracks[0], isPlaying = true, isLoading = false, isLiked = true, progress = { 70_000L },
                shelf = waveShelfOf(true, tracks.drop(1), emptyList()), shimmer = false,
                onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {}
            )
        }
        snap("wave_playing")
    }

    @Test
    fun wavePaused() {
        show {
            HomeScreen(
                waveTrack = tracks[0], isPlaying = false, isLoading = false, isLiked = false, progress = { 70_000L },
                shelf = waveShelfOf(true, tracks.drop(1), emptyList()), shimmer = false,
                onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {}
            )
        }
        snap("wave_paused")
    }

    @Test
    fun waveIdle() {
        show {
            HomeScreen(
                waveTrack = null, isPlaying = false, isLoading = false, isLiked = false, progress = { 0L },
                shelf = waveShelfOf(false, emptyList(), tracks), shimmer = false,
                onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {}
            )
        }
        snap("wave_idle")
    }

    /** First launch: nothing saved yet — the shelf keeps its place with placeholders. */
    @Test
    fun waveLoading() {
        show {
            HomeScreen(
                waveTrack = null, isPlaying = false, isLoading = true, isLiked = false, progress = { 0L },
                shelf = waveShelfOf(false, emptyList(), emptyList()), shimmer = false,
                onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {}
            )
        }
        snap("wave_loading")
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
    fun accountsDialog() {
        val me = com.korenskoy.yandexmusic.domain.model.UserAccount(uid = "1", login = "me", displayName = "Тестовый Аккаунт", token = "t")
        val other = com.korenskoy.yandexmusic.domain.model.UserAccount(uid = "2", login = "car", displayName = "Машина", token = "t")
        show {
            com.korenskoy.yandexmusic.presentation.account.AccountManagementDialog(
                accounts = listOf(me, other), activeAccount = me, defaultAccountUid = "1",
                onSwitchAccount = {}, onRemoveAccount = {}, onAddNewAccount = {}, onLogoutCurrent = {}, onLogoutAll = {}, onDismiss = {}
            )
        }
        snapScreen("dialog_accounts")
    }

    @Test
    fun updateDialog() {
        show {
            com.korenskoy.yandexmusic.presentation.update.UpdateDialog(
                updateInfo = com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable(
                    versionName = "1.8.2", title = "Yandex Music Lite 1.8.2", changelog = "• Новый дизайн диалогов",
                    apkDownloadUrl = "", apkSizeBytes = 8_000_000
                ),
                downloadProgress = -1f, onStartDownload = {}, onInstall = {}, onDismiss = {}
            )
        }
        snapScreen("dialog_update")
    }

    @Test
    fun deletePlaylistDialog() {
        show {
            com.korenskoy.yandexmusic.presentation.main.components.DeletePlaylistDialog(playlistTitle = "В дорогу", onConfirm = {}, onDismiss = {})
        }
        snapScreen("dialog_delete_playlist")
    }

    @Test
    fun settingsSheet() {
        show {
            com.korenskoy.yandexmusic.presentation.main.components.CacheMenuBottomSheet(
                onDismiss = {}, cacheSizeBytes = 1_288_000_000L, autoDownloadEnabled = true,
                onEnableAutoDownload = {}, onDownloadAllClick = {}, onClearCache = {},
                accountName = "Тестовый Аккаунт", hasPlus = true, crossfadeSeconds = 3,
                steeringWheelOnLeft = true, uiScaleAuto = true, crashReports = 0
            )
        }
        snapScreen("settings")
    }

    @Test
    @Config(qualifiers = "+land")
    fun settingsSheetLandscape() {
        show {
            com.korenskoy.yandexmusic.presentation.main.components.CacheMenuBottomSheet(
                onDismiss = {}, cacheSizeBytes = 134_000_000L, autoDownloadEnabled = true,
                onEnableAutoDownload = {}, onDownloadAllClick = {}, onClearCache = {},
                accountName = "Тестовый Аккаунт", hasPlus = true, crossfadeSeconds = 3,
                steeringWheelOnLeft = true, uiScaleAuto = true, crashReports = 2
            )
        }
        snapScreen("settings_landscape")
    }

    @Test
    fun miniPlayerAndBottomBar() {
        show {
            Column(Modifier.padding(top = 24.dp)) {
                MiniPlayer(
                    track = tracks[0], isPlaying = true, progress = { 60_000L },
                    onPlayPauseClick = {}, onNextClick = {}, onClick = {}, onDismiss = {}
                )
                FloatingGlassBottomBar(currentRoute = "library", onNavigate = {})
            }
        }
        snap("mini_player_bottom_bar")
    }

    @Composable
    private fun Player(
        track: TrackDetailsDto = tracks[0],
        isPlaying: Boolean = true,
        quality: String? = "MP3 · 320 кбит/с",
        queueOpen: Boolean = false,
        fetchingMore: Boolean = false,
        source: String = "Моя волна"
    ) {
        CompositionLocalProvider(com.korenskoy.yandexmusic.utils.LocalStreamQuality provides quality) {
            FullScreenPlayer(
                track = track, isPlaying = isPlaying, isLiked = true, progress = { 82_000L }, queue = tracks,
                onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
                sourceLabel = source, isFetchingMoreQueue = fetchingMore, initialQueueOpen = queueOpen
            )
        }
    }

    @Test
    fun fullPlayer() {
        show { Player() }
        snap("full_player")
    }

    @Test
    fun fullPlayerPaused() {
        show { Player(isPlaying = false, quality = null) }
        snap("full_player_paused")
    }

    /** Radio: the station card with "В ЭФИРЕ", the live line instead of the timeline, no "Далее". */
    @Test
    fun fullPlayerRadio() {
        show { Player(track = tracks[0].copy(id = JsonPrimitive("radio_europa"), title = "Европа Плюс", isRadio = true, artists = emptyList()), source = "Радио") }
        snap("full_player_radio")
    }

    @Test
    @Config(qualifiers = "+land")
    fun fullPlayerLandscape() {
        show { Player() }
        snap("full_player_land")
    }

    @Test
    fun playerQueue() {
        show { Player(queueOpen = true, fetchingMore = true) }
        snap("player_queue")
    }

    /** Bundled station logos on their cards: the 1.4.x "empty square" regression shows up here. */
    @Test
    fun radioStations() {
        val stations = RadioRepository(ApplicationProvider.getApplicationContext()).getBuiltinStations()
        show {
            RadioScreen(
                stations = stations, allStations = stations, selectedCategory = RadioCategory.ALL,
                onCategorySelect = {}, currentStationId = stations.first().id, isPlaying = true,
                favoriteStationIds = setOf(stations[1].id, stations[2].id), onStationClick = {}, onToggleFavorite = {}, onPlayPause = {}
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

    @Test
    fun searchRecentPanelOpen() {
        show {
            SearchScreen(
                query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, trendTracks = tracks,
                isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {}, history = listOf("кино", "ария", "radio record"),
                initiallyShowHistory = true
            )
        }
        snap("search_recent_open")
    }

    private val catalogActions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {})

    @Test
    fun carPortrait() {
        show { Car(isLandscape = false) }
        snap("car_portrait")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscape() {
        show { Car(isLandscape = true) }
        snap("car_landscape")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscapeRightHandDrive() {
        show { Car(isLandscape = true, steeringWheelOnLeft = false) }
        snap("car_landscape_right_wheel")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscapeUiScaled() {
        // Enlarged UI, as the global density override produces for a low-reported-density screen.
        show {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * 1.4f, base.fontScale)) {
                Car(isLandscape = true)
            }
        }
        snap("car_landscape_scaled")
    }

    @Composable
    private fun Car(isLandscape: Boolean, steeringWheelOnLeft: Boolean = true, track: TrackDetailsDto = tracks[0]) = CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }) { CarScreen(
        currentTrack = track, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
        onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
        onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = steeringWheelOnLeft, progress = { 82_000L }, durationMs = 286_000,
        nextTrack = tracks[2], source = com.korenskoy.yandexmusic.data.player.PlaylistSource.VIBE
    ) }

    /** Radio in the car: no timeline, the on-air line instead. */
    @Test
    @Config(qualifiers = "+land")
    fun carRadio() {
        show { Car(isLandscape = true, track = tracks[0].copy(id = JsonPrimitive("radio_europa"), title = "Европа Плюс", isRadio = true)) }
        snap("car_radio")
    }

    private companion object {
        /** 21.10.2026 10:30 MSK: the car clock in the screenshots. */
        const val FIXED_TIME = 1_792_567_800_000L
    }
}

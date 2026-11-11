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
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.catalog.CatalogActions
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPage
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageState
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
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

/**
 * 1.14.0 references: albums and other people's playlists saved in the official app, the heart on an album
 * page, and the equalizer row that stays in its place when there is no equalizer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots114Test {

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

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", options)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(id = JsonPrimitive(1), name = artist)))

    private val tracks = listOf(track("1", "Первая песня", "Группа"), track("2", "Вторая песня", "Исполнитель"))

    private fun album(id: Int, title: String, artist: String, year: Int) =
        AlbumDto(id = JsonPrimitive(id), title = title, year = year, artists = listOf(ArtistDto(id = JsonPrimitive(id * 10), name = artist)))

    private val albums = listOf(
        album(2, "Группа крови", "Кино", 1988),
        album(3, "Второй альбом", "Исполнитель", 2023),
        album(4, "Концерт", "Группа", 2008),
        album(5, "Сборник", "Разные исполнители", 2021),
        album(6, "Дебют", "Исполнитель с длинным именем", 2016)
    )

    private val mine = listOf(UserPlaylistDto(uid = JsonPrimitive(1), kind = JsonPrimitive(1003), title = "Для дороги", trackCount = 34, revision = 7))
    private val saved = listOf(
        UserPlaylistDto(uid = JsonPrimitive(2), kind = JsonPrimitive(1003), title = "Вечерний джаз", trackCount = 56, revision = 3),
        UserPlaylistDto(uid = JsonPrimitive(3), kind = JsonPrimitive(1010), title = "Рок 2000-х", trackCount = 80, revision = 1),
        UserPlaylistDto(uid = JsonPrimitive(4), kind = JsonPrimitive(1001), title = "Лучшее за месяц", trackCount = 1, revision = 9)
    )

    @Composable
    private fun Library(section: String?, kept: List<OfflineCollection> = emptyList()) = LibraryScreen(
        tracks = tracks, isLoading = false, currentTrackId = null, isPlaying = false, likedTrackIds = setOf("1", "2"), downloadedTrackIds = emptySet(),
        downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
        onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {},
        playlists = mine, savedPlaylists = saved, albums = albums, offlineCollections = kept,
        initialSection = section
    )

    @Test
    fun libraryHasAlbums() {
        show { Library(null) }
        snap("v114_library")
    }

    @Test
    fun playlistsMineAndSaved() {
        show { Library("PLAYLISTS", kept = listOf(OfflineCollection(OfflineCollection.keyOf(saved[1]), "Рок 2000-х", null, emptyList()))) }
        snap("v114_playlists_saved")
    }

    @Test
    fun albumsInTheCollection() {
        show { Library("ALBUMS", kept = listOf(OfflineCollection(OfflineCollection.albumKey("2"), "Группа крови", null, emptyList()))) }
        snap("v114_albums")
    }

    @Test
    fun noAlbumsYet() {
        show {
            LibraryScreen(
                tracks = tracks, isLoading = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), downloadedTrackIds = emptySet(),
                downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {},
                initialSection = "ALBUMS"
            )
        }
        snap("v114_albums_empty")
    }

    @Test
    fun albumInTheCollectionHasARedHeart() {
        show {
            CatalogPageScreen(
                state = CatalogPageState(CatalogPage.Album("2", "Группа крови"), album = albums[0].copy(volumes = listOf(tracks)), isLoading = false),
                currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), contentBottom = 0.dp,
                actions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}),
                albumLiked = true
            )
        }
        snap("v114_album_liked")
    }

    @Test
    fun soundWithoutAnEqualizer() {
        show {
            SettingsSheet(
                state = SettingsUiState(equalizerAvailable = false),
                actions = SettingsActions(),
                onDismiss = {},
                initialPage = SettingsPage.PLAYBACK
            )
        }
        snapScreen("v114_sound_no_equalizer")
    }

    @Test
    fun soundWithAnEqualizer() {
        show {
            SettingsSheet(
                state = SettingsUiState(equalizerAvailable = true),
                actions = SettingsActions(),
                onDismiss = {},
                initialPage = SettingsPage.PLAYBACK
            )
        }
        snapScreen("v114_sound_equalizer")
    }
}

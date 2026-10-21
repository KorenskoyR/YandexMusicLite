package com.korenskoy.yandexmusic

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge screens on a real window: nothing a user reads or taps may sit under the status bar
 * (or, in car mode, under the navigation bar). Replaces the old source-text checks for
 * `statusBarsPadding()`, which passed even when the padding was on the wrong element.
 */
@RunWith(AndroidJUnit4::class)
class ScreenInsetsInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tracks = listOf(
        TrackDetailsDto(id = JsonPrimitive("1"), title = "Первая песня", artists = listOf(ArtistDto(name = "Группа"))),
        TrackDetailsDto(id = JsonPrimitive("2"), title = "Вторая песня", artists = listOf(ArtistDto(name = "Исполнитель")))
    )

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { YandexMusicTheme { content() } }
        compose.mainClock.autoAdvance = false // infinite equalizer animations
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun systemBars(): androidx.core.graphics.Insets {
        var insets = androidx.core.graphics.Insets.NONE
        compose.runOnUiThread {
            val root = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            insets = root.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
        return insets
    }

    /** Visible nodes that show text or an icon with a description: what the user reads or taps. */
    private fun visibleContent(): List<SemanticsNode> {
        val readable = SemanticsMatcher("text or description") {
            it.config.contains(SemanticsProperties.Text) || it.config.contains(SemanticsProperties.ContentDescription)
        }
        val height = compose.activity.window.decorView.height
        return compose.onAllNodes(readable, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInWindow.height > 0 && it.boundsInWindow.bottom > 0 && it.boundsInWindow.top < height }
    }

    private fun assertBelowStatusBar(screen: String) {
        val statusBar = systemBars().top
        assertTrue("status bar must be measured", statusBar > 0)
        val nodes = visibleContent()
        assertTrue("$screen shows content", nodes.isNotEmpty())
        val top = nodes.minOf { it.boundsInWindow.top }
        assertTrue("$screen: content at y=$top is under the status bar (${statusBar}px)", top >= statusBar - 1)
    }

    @Test
    fun home() {
        show { HomeScreen(isPlaying = false, isWavePlaying = false, onPlayPauseVibe = {}) }
        assertBelowStatusBar("Home")
    }

    @Test
    fun search() {
        show {
            SearchScreen(
                query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks, trendTracks = tracks,
                isSearching = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(),
                onTrackClick = { _, _ -> }, onToggleLike = {}
            )
        }
        assertBelowStatusBar("Search")
    }

    @Test
    fun library() {
        show {
            LibraryScreen(
                tracks = tracks, isLoading = false, isDownloadingAll = false, autoDownloadEnabled = false,
                currentTrackId = null, isPlaying = false, likedTrackIds = setOf("1", "2"), downloadedTrackIds = emptySet(),
                cacheSizeBytes = 0, downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                onDownloadAllClick = {}, onEnableAutoDownload = {}, onCancelDownload = {}, onClearCache = {}, onToggleLike = {}
            )
        }
        assertBelowStatusBar("Library")
    }

    @Test
    fun radio() {
        val stations = RadioRepository(compose.activity.applicationContext).getBuiltinStations()
        show {
            RadioScreen(
                stations = stations, searchQuery = "", onQueryChange = {}, selectedCategory = RadioCategory.ALL,
                onCategorySelect = {}, currentStationId = null, isPlaying = false, favoriteStationIds = emptySet(),
                onStationClick = {}, onToggleFavorite = {}
            )
        }
        assertBelowStatusBar("Radio")
    }

    @Test
    fun carStaysClearOfBothSystemBars() {
        show {
            CarScreen(
                currentTrack = tracks[0], isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, progress = 60_000, durationMs = 215_000
            )
        }
        assertBelowStatusBar("Car")
        val navBar = systemBars().bottom
        val windowHeight = compose.activity.window.decorView.height
        val bottom = visibleContent().maxOf { it.boundsInWindow.bottom }
        assertTrue("Car: content down to y=$bottom is under the navigation bar", bottom <= windowHeight - navBar + 1)
    }
}

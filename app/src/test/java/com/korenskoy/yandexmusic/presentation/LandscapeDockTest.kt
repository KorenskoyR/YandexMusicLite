package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.presentation.main.components.landscapeColumns
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.data.dto.ArtistBriefDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.catalog.CatalogActions
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPage
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageState
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.OpenedPlaylist
import com.korenskoy.yandexmusic.presentation.main.PlaylistScreen
import com.korenskoy.yandexmusic.presentation.main.components.DockBottomGap
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassDock
import com.korenskoy.yandexmusic.presentation.main.components.dockTabsWidth
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On a side the tabs and the mini-player lie along the bottom: every page must end above them, and the
 * head of a page must not reach them, on a head unit and on a phone alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LandscapeDockTest {
    @get:Rule
    val compose = createComposeRule()

    private val tracks = (1..30).map { TrackDetailsDto(id = JsonPrimitive("$it"), title = "Песня $it", artists = listOf(ArtistDto(name = "Группа"))) }
    private val last = "Песня 30"

    private val actions = CatalogActions({}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {})

    private fun docked(page: @Composable () -> Unit) {
        compose.setContent {
            YandexMusicTheme {
                Box(Modifier.fillMaxSize()) {
                    page()
                    FloatingGlassDock(
                        currentRoute = "library",
                        onNavigate = {},
                        modifier = Modifier.align(Alignment.BottomCenter).testTag("dock").padding(start = 16.dp, end = 16.dp, bottom = DockBottomGap),
                        player = {}
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun artistPage() = docked {
        CatalogPageScreen(
            state = CatalogPageState(
                CatalogPage.Artist("7", "Кино"),
                artist = ArtistBriefDto(ArtistDto(id = JsonPrimitive(7), name = "Кино"), tracks, emptyList()),
                isLoading = false
            ),
            currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), contentBottom = DockClearance, actions = actions
        )
    }

    private fun playlistPage() = docked {
        PlaylistScreen(
            opened = OpenedPlaylist(UserPlaylistDto(kind = JsonPrimitive(1003), title = "Избранное", trackCount = 30, revision = 2), tracks, isLoading = false),
            currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), downloadedTrackIds = emptySet(),
            contentBottom = DockClearance, onBack = {}, onPlay = {}, onTrackClick = {}, onToggleLike = {}, onRetry = {},
            onRename = {}, onDelete = {}
        )
    }

    private fun libraryPage() = docked {
        LibraryScreen(
            tracks = tracks, isLoading = false, currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), downloadedTrackIds = emptySet(),
            downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
            onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {}
        )
    }

    private fun dockTop() = compose.onNodeWithTag("dock").getUnclippedBoundsInRoot().top

    private fun assertLastRowClearsTheDock(row: String = last) {
        // As a finger would, to the very end: that is where the bottom padding counts
        repeat(30) {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        val bottom = compose.onNodeWithText(row).getUnclippedBoundsInRoot().bottom
        assertTrue("$row ends at $bottom, the dock starts at ${dockTop()}", bottom <= dockTop())
    }

    private fun assertBackClearsTheDock() {
        val back = compose.onNodeWithContentDescription("Назад").getUnclippedBoundsInRoot()
        assertTrue(back.bottom < dockTop())
    }

    private fun assertDockIsOnScreen() {
        val dock = compose.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(root.bottom, dock.bottom)
        assertTrue(dock.left >= root.left && dock.right <= root.right)
    }

    @Test @Config(qualifiers = HEAD_UNIT)
    fun artistOnAHeadUnit() {
        artistPage()
        assertBackClearsTheDock()
        assertDockIsOnScreen()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = OLD_HEAD_UNIT)
    fun artistOnAnOldHeadUnit() {
        artistPage()
        assertBackClearsTheDock()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = PHONE_ON_SIDE)
    fun artistOnAPhoneOnItsSide() {
        artistPage()
        assertBackClearsTheDock()
        assertDockIsOnScreen()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = HEAD_UNIT)
    fun playlistOnAHeadUnit() {
        playlistPage()
        assertBackClearsTheDock()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = OLD_HEAD_UNIT)
    fun playlistOnAnOldHeadUnit() {
        playlistPage()
        assertBackClearsTheDock()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = PHONE_ON_SIDE)
    fun playlistOnAPhoneOnItsSide() {
        playlistPage()
        assertBackClearsTheDock()
        assertLastRowClearsTheDock()
    }

    @Test @Config(qualifiers = HEAD_UNIT)
    fun libraryOnAHeadUnit() {
        libraryPage()
        // Two rows of six covers on this screen: the last of them must stay reachable
        assertLastRowClearsTheDock("Песня 12")
    }

    @Test @Config(qualifiers = PHONE_ON_SIDE)
    fun libraryOnAPhoneOnItsSide() {
        libraryPage()
        assertLastRowClearsTheDock("Песня 8")
    }

    @Test
    fun aWiderScreenTakesMoreCoversNotBiggerOnes() {
        assertEquals(4, landscapeColumns(800))
        assertEquals(4, landscapeColumns(1024))
        assertEquals(6, landscapeColumns(1280))
        assertEquals(6, landscapeColumns(1920))
    }

    @Test
    fun theTabsLeaveTheMiniPlayerRoomOnEveryWidth() {
        assertEquals(430, dockTabsWidth(1280))
        assertEquals(330, dockTabsWidth(800))
        assertEquals(330, dockTabsWidth(640))
        // A big interface scale on a small panel: the tabs give way
        assertTrue(dockTabsWidth(512) < 512 * 0.6f)
    }

    private companion object {
        const val HEAD_UNIT = "w1280dp-h720dp-land-mdpi"
        const val OLD_HEAD_UNIT = "w1024dp-h600dp-land-mdpi"
        const val PHONE_ON_SIDE = "w800dp-h360dp-land-xhdpi"
    }
}

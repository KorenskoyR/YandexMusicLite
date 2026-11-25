package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import com.korenskoy.yandexmusic.utils.LocalAppWindowSize
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bug-first layout checks for HomeScreen in compact splits and FlexWindow:
 * the title «Моя волна» must never be pushed above the top edge (top >= 0) or overlap the track row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private fun track(id: String, title: String, artist: String = "Исполнитель") =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val sampleTracks = listOf(
        track("1", "Ночной горизонт", "Синтетик Драйв"),
        track("2", "Огни проспекта", "Северный Пульс"),
        track("3", "Тёплый винил", "Артур Соколов"),
        track("4", "Дождь за стеклом", "Тихий Квартал")
    )

    private fun renderHome(qualifiers: String): IntSize {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val dm = RuntimeEnvironment.getApplication().resources.displayMetrics
        val winSize = IntSize(dm.widthPixels, dm.heightPixels)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppWindowSize provides winSize) {
                YandexMusicTheme {
                    CompositionLocalProvider(
                        LocalArtworkTint provides tint,
                        LocalLinkState provides LinkState.GOOD
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HiFiBackdrop(tint = tint)
                            HomeScreen(
                                waveTrack = sampleTracks[0],
                                isPlaying = true,
                                isLoading = false,
                                isLiked = true,
                                progress = { 45_000L },
                                shelf = waveShelfOf(true, sampleTracks.drop(1), emptyList()),
                                shimmer = false,
                                onPlayPause = {},
                                onLike = {},
                                onDislike = {},
                                onOpenPlayer = {},
                                onShelfClick = {}
                            )
                        }
                    }
                }
            }
        }
        repeat(15) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        return winSize
    }

    @Test
    fun changan6040SplitKeepsWaveTitleInsideTopBound() {
        val win = renderHome("w768dp-h640dp-land-mdpi")
        val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }).fetchSemanticsNodes()
        val title = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Моя волна" } == true }
        assertNotNull("Title «Моя волна» must exist", title)
        val b = title!!.boundsInRoot
        assertTrue("Title top ${b.top} must be >= 0 (not pushed off-screen)", b.top >= -1f)
        assertTrue("Title bottom ${b.bottom} must fit window height ${win.height}", b.bottom <= win.height + 1f)
    }

    @Test
    fun dudu7QuarterSplit400DpHighDoesNotPushHeaderUp() {
        val win = renderHome("w667dp-h400dp-land-hdpi")
        val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }).fetchSemanticsNodes()
        val title = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Моя волна" } == true }
        assertNotNull("Title «Моя волна» must exist", title)
        val b = title!!.boundsInRoot
        assertTrue("Title top ${b.top} must be >= 0 on 400dp split (was pushed up by WaveTrackAndShelf)", b.top >= -1f)
        assertTrue("Title bottom ${b.bottom} must fit window height ${win.height}", b.bottom <= win.height + 1f)
    }

    @Test
    fun flipFlexWindow360x374KeepsHeaderAndTrackWithoutOverlap() {
        val win = renderHome("w360dp-h374dp-port-xhdpi")
        val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }).fetchSemanticsNodes()
        val title = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Моя волна" } == true }
        val track = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Ночной горизонт" } == true }
        val knob = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Пауза") == true }
        assertNotNull("Title must exist", title)
        assertNotNull("Track must exist", track)
        assertNotNull("Knob must exist", knob)
        assertTrue("Title top >= 0", title!!.boundsInRoot.top >= -1f)
        assertTrue("Track bottom <= window height", track!!.boundsInRoot.bottom <= win.height + 1f)
        assertTrue(
            "Knob (${knob!!.boundsInRoot.top}..${knob.boundsInRoot.bottom}) must not overlap track (${track.boundsInRoot.top})",
            knob.boundsInRoot.bottom <= track.boundsInRoot.top + 2f
        )
    }
}


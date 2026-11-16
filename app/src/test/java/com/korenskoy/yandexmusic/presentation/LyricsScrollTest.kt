package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.data.lyrics.LyricLine
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.presentation.hifi.LyricsPanel
import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Timed words follow the music (1.14.8 report: "the current line soon runs off the bottom, the words have to be
 * scrolled by hand"). The list's own animated scroll counted as the user's and paused following for 3.5 s after every
 * line. Made-up words, whatever the source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-xxhdpi", application = Application::class)
class LyricsScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val lines = List(40) { LyricLine(timeMs = it * 1_000L, text = "Строка номер ${it + 1}") }
    private val position = mutableLongStateOf(0L)

    private fun show() {
        compose.setContent {
            Box(Modifier.size(360.dp, 360.dp)) {
                LyricsPanel(
                    lyrics = TrackLyrics(LyricsAnswer.Synced(lines), offered = true, retry = {}),
                    onClose = {},
                    progress = { position.longValue }
                )
            }
        }
        compose.waitForIdle()
    }

    /** The line is in the panel, and in its upper two thirds (with the next lines under it). */
    private fun assertLineInView(number: Int) {
        val panel = compose.onRoot().getBoundsInRoot()
        val line = compose.onNodeWithText("Строка номер $number").getBoundsInRoot()
        assertTrue("line $number at ${line.top}..${line.bottom} in 0..${panel.bottom}", line.top >= panel.top && line.bottom <= panel.top + (panel.bottom - panel.top) * 0.67f)
    }

    @Test
    fun theCurrentLineStaysInViewLineAfterLine() {
        show()
        for (n in 1..30) {
            position.longValue = (n - 1) * 1_000L + 100
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            assertLineInView(n)
        }
    }

    @Test
    fun aSeekFarAheadIsFollowedAtOnce() {
        show()
        position.longValue = 35_100
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertLineInView(36)
    }

    @Test
    fun aFingerPausesFollowingAndItComesBackByItself() {
        show()
        position.longValue = 20_100
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertLineInView(21)
        // The user looks back up: the words stay where the finger left them...
        compose.onRoot().performTouchInput { swipeDown() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val panel = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val looked = compose.onAllNodesWithText("Строка номер 21").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        assertTrue("moved down by the finger, or out of view", looked == null || looked.top > panel.top + (panel.bottom - panel.top) * 0.5f)
        // ...and come back to the current line by themselves once the pause is over, with no new line needed
        ShadowSystemClock.advanceBy(Duration.ofMillis(4_000))
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertLineInView(21)
    }
}

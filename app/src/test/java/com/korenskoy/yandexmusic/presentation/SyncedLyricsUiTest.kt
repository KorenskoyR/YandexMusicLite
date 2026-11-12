package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.korenskoy.yandexmusic.data.lyrics.LyricLine
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.presentation.hifi.LyricsPanel
import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import com.korenskoy.yandexmusic.presentation.hifi.lyricsClose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncedLyricsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingSyncedLineSeeksToLineTimestamp() {
        val lines = listOf(
            LyricLine(1_000L, "First line"),
            LyricLine(5_000L, "Second line"),
            LyricLine(10_000L, "Third line")
        )
        val lyrics = TrackLyrics(
            answer = LyricsAnswer.Synced(lines),
            offered = true,
            retry = {}
        )

        var seekTargetMs = -1L
        var closed = false

        compose.setContent {
            LyricsPanel(
                lyrics = lyrics,
                onClose = { closed = true },
                progress = { 2_000L },
                onSeek = { seekTargetMs = it }
            )
        }

        // Tap second line -> seeks to 5000ms
        compose.onNodeWithText("Second line").performClick()
        assertEquals(5_000L, seekTargetMs)
        assertFalse(closed)

        // Tap third line -> seeks to 10000ms
        compose.onNodeWithText("Third line").performClick()
        assertEquals(10_000L, seekTargetMs)
        assertFalse(closed)
    }

    @Test
    fun clickingPanelClosesLyrics() {
        val lines = listOf(LyricLine(1_000L, "First line"))
        val lyrics = TrackLyrics(
            answer = LyricsAnswer.Synced(lines),
            offered = true,
            retry = {}
        )

        var closed = false

        compose.setContent {
            LyricsPanel(
                lyrics = lyrics,
                onClose = { closed = true },
                progress = { 0L }
            )
        }

        compose.onRoot().performClick()
        assertTrue("panel clicked to close", closed)
    }

    @Test
    fun syncedLyricsStayOpenAcrossTracks() {
        val lines = listOf(LyricLine(1_000L, "Line"))
        val trackLyrics = TrackLyrics(LyricsAnswer.Synced(lines), offered = true) {}
        assertFalse("synced lyrics do not close automatically", lyricsClose(trackLyrics))
    }
}


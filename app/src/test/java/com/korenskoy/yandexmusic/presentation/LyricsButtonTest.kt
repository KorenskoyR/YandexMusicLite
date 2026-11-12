package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.korenskoy.yandexmusic.data.dto.LyricsInfoDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.presentation.hifi.rememberLyrics
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * The "Текст" button never shows and then goes: it comes only with the words. Owner's phone, 1.14.3 (e9f63cb):
 * it blinked, because "has words" was read from lyricsInfo, which the open method does not follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LyricsButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(available: Boolean?, info: Boolean? = null) =
        TrackDetailsDto(JsonPrimitive("555"), title = "Песня", lyricsAvailable = available, lyricsInfo = info?.let { LyricsInfoDto(it) })

    /** Every frame's "is the button shown", over [ms] of the clock. */
    private fun frames(track: TrackDetailsDto, ms: Long, load: suspend (String) -> LyricsAnswer): List<Boolean> {
        val shown = mutableListOf<Boolean>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLyrics provides LyricsUi(load = load)) {
                val offered = rememberLyrics(track)?.offered == true
                SideEffect { shown += offered }
            }
        }
        var left = ms
        while (left > 0) {
            compose.mainClock.advanceTimeBy(100)
            left -= 100
        }
        compose.waitForIdle()
        return shown
    }

    @Test
    fun theNewerFlagSayingYesDoesNotShowTheButtonForNothing() {
        // The chart's usual case: lyricsInfo "yes", lyricsAvailable "no" — nothing is asked, nothing shows
        val asked = AtomicInteger()
        val shown = frames(track(available = false, info = true), 2_000) { asked.incrementAndGet(); LyricsAnswer.None }
        assertFalse(shown.any { it })
        assertEquals("the open method has none: not asked", 0, asked.get())
    }

    @Test
    fun noWordsAfterAllNeverBlinks() {
        val shown = frames(track(available = true), 2_000) { delay(300); LyricsAnswer.None }
        assertFalse("never shown, never taken back: $shown", shown.any { it })
    }

    @Test
    fun wordsShowOnceTheyComeAndStay() {
        val shown = frames(track(available = null), 2_000) { delay(300); LyricsAnswer.Text("слова") }
        assertTrue(shown.last())
        assertEquals("hidden while asked, then shown for good: $shown", shown.dropWhile { !it }.size, shown.count { it })
    }

    @Test
    fun aMomentWithoutConnectionIsAskedAgain() {
        val asked = AtomicInteger()
        val shown = frames(track(available = true), 15_000) {
            if (asked.incrementAndGet() < 3) LyricsAnswer.Failed else LyricsAnswer.Text("слова")
        }
        assertEquals(3, asked.get())
        assertTrue(shown.last())
        assertEquals("shown only once the words came: $shown", shown.dropWhile { !it }.size, shown.count { it })
    }
}

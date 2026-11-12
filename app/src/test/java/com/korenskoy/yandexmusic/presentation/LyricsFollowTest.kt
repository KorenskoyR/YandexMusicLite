package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import com.korenskoy.yandexmusic.presentation.hifi.lyricsClose
import com.korenskoy.yandexmusic.presentation.hifi.rememberLyrics
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
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
import java.util.concurrent.ConcurrentHashMap

/**
 * The words follow the music (a tester, 1.14.4): open words showed the last track's words on the next one, and in
 * the car the first tap on "next" only closed them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class LyricsFollowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String, title: String) =
        TrackDetailsDto(JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = "Кино")), durationMs = 200_000, lyricsAvailable = true)

    private val cache = ConcurrentHashMap<String, LyricsAnswer>()
    private val ui = LyricsUi(known = { cache[it] }, load = { id -> delay(200); LyricsAnswer.Text("слова $id").also { cache[id] = it } })

    @Test
    fun theNextTracksWordsReplaceTheLastOnes() {
        val shown = mutableStateOf(track("555", "Первая"))
        val answers = mutableListOf<Pair<String, LyricsAnswer?>>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLyrics provides ui) {
                val now = shown.value
                val words = rememberLyrics(now)
                androidx.compose.runtime.SideEffect { answers += now.idString to words?.answer }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(LyricsAnswer.Text("слова 555"), answers.last().second)

        switchTo(shown, track("556", "Вторая"))
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("frames: $answers", LyricsAnswer.Text("слова 556"), answers.last().second)
        // Not one frame of the second track with the first one's words
        assertFalse("frames: $answers", answers.any { (id, answer) -> id == "556" && answer == LyricsAnswer.Text("слова 555") })

        // Back to the first: known at once, in the same frame
        switchTo(shown, track("555", "Первая"))
        compose.mainClock.advanceTimeBy(16)
        assertEquals(LyricsAnswer.Text("слова 555"), answers.last { it.first == "555" }.second)
        assertFalse(answers.any { (id, answer) -> id == "555" && answer == LyricsAnswer.Text("слова 556") })
    }

    private fun switchTo(state: androidx.compose.runtime.MutableState<TrackDetailsDto>, track: TrackDetailsDto) {
        state.value = track
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    @Test
    fun openWordsCloseOnlyWhenTheTrackHasNone() {
        assertTrue("a station: nothing to show", lyricsClose(null))
        assertTrue(lyricsClose(TrackLyrics(LyricsAnswer.None, offered = false) {}))
        assertFalse("asked for: stays open", lyricsClose(TrackLyrics(null, offered = false) {}))
        assertFalse("a moment without connection: stays open", lyricsClose(TrackLyrics(LyricsAnswer.Failed, offered = false) {}))
        assertFalse(lyricsClose(TrackLyrics(LyricsAnswer.Text("слова"), offered = true) {}))
    }

    @Test
    fun inTheCarNextWorksAtOnceWithTheWordsOpen() {
        cache["555"] = LyricsAnswer.Text("слова 555")
        var next = 0
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalLyrics provides ui) {
                    CarScreen(
                        currentTrack = track("555", "Первая"), isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = { next++ },
                        onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                        onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = true, progress = { 10_000L }, durationMs = 200_000,
                        source = PlaylistSource.VIBE, initialLyricsOpen = true
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Следующий трек").performClick()
        assertEquals("the first tap switches the track", 1, next)
    }
}

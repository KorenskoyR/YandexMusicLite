package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.domain.model.WaveDiversity
import com.korenskoy.yandexmusic.domain.model.WaveLanguage
import com.korenskoy.yandexmusic.domain.model.WaveMood
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.main.components.MiniGesture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/** Loudness, the explicit skip, the wave's settings and the mini-player's locked swipe. Pure rules. */
class NewPlaybackRulesTest {

    private fun db(gain: Float) = 20 * log10(gain.toDouble())

    @Test
    fun aLoudMasterIsBroughtDownToTheTarget() {
        // -5.51 LUFS is 8.49 dB above -14
        assertEquals(-8.49, db(Loudness.gainFor(-5.51)), 0.01)
    }

    @Test
    fun aQuietTrackIsNeverMadeLouder() {
        assertEquals(1f, Loudness.gainFor(-14.0))
        assertEquals(1f, Loudness.gainFor(-20.5))
    }

    @Test
    fun aWrongMeasurementCannotSilenceATrack() {
        assertEquals(Loudness.MAX_CUT_DB, db(Loudness.gainFor(-0.5)).coerceAtLeast(-12.0), 0.01)
        assertEquals(1f, Loudness.gainFor(null))
        assertEquals(1f, Loudness.gainFor(Double.NaN))
        assertEquals(1f, Loudness.gainFor(3.0))
        assertEquals(1f, Loudness.gainFor(-90.0))
    }

    @Test
    fun theServiceFieldsAreRead() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val track = json.decodeFromString<TrackDetailsDto>("""{"id":"1","r128":{"i":-7.74,"tp":0.18},"contentWarning":"explicit"}""")
        assertEquals(-7.74, track.r128?.integrated ?: 0.0, 0.0001)
        assertTrue(track.isExplicit)
        assertFalse(json.decodeFromString<TrackDetailsDto>("""{"id":"2"}""").isExplicit)
    }

    private fun t(id: String, explicit: Boolean = false) =
        TrackDetailsDto(id = JsonPrimitive(id), contentWarning = if (explicit) "explicit" else null)

    @Test
    fun explicitTracksArePassedOverOnTheWay() {
        val queue = listOf(t("a"), t("b", explicit = true), t("c", explicit = true), t("d"))
        assertEquals(NextAction.PlayIndex(3), PlaybackQueue.nextAction(queue, 0, PlaylistSource.LIBRARY) { it.isExplicit })
    }

    @Test
    fun theEndOfAPlaylistLoopsToTheFirstAllowedTrack() {
        val queue = listOf(t("a", explicit = true), t("b"), t("c", explicit = true))
        assertEquals(NextAction.PlayIndex(1), PlaybackQueue.nextAction(queue, 1, PlaylistSource.LIBRARY) { it.isExplicit })
    }

    @Test
    fun theWaveFetchesMoreInsteadOfPlayingASkippedTrack() {
        val queue = listOf(t("a"), t("b", explicit = true))
        assertEquals(NextAction.FetchMoreWave, PlaybackQueue.nextAction(queue, 0, PlaylistSource.VIBE) { it.isExplicit })
    }

    @Test
    fun aQueueOfOnlySkippedTracksStillPlays() {
        val queue = listOf(t("a", explicit = true), t("b", explicit = true))
        assertEquals(NextAction.PlayIndex(1), PlaybackQueue.nextAction(queue, 0, PlaylistSource.LIBRARY) { it.isExplicit })
    }

    @Test
    fun withTheFilterOffNothingChanges() {
        val queue = listOf(t("a"), t("b", explicit = true))
        assertEquals(
            PlaybackQueue.nextAction(queue.size, 0, PlaylistSource.LIBRARY),
            PlaybackQueue.nextAction(queue, 0, PlaylistSource.LIBRARY) { false }
        )
    }

    @Test
    fun waveSettingsComeFromTheServerNames() {
        val tuning = WaveTuning.fromWire("discover", "active", "russian")
        assertEquals(WaveTuning(WaveDiversity.DISCOVER, WaveMood.ACTIVE, WaveLanguage.RUSSIAN), tuning)
        assertTrue(WaveTuning.fromWire("default", "all", "any").isDefault)
        // Something new on the server reads as "any", not as a crash
        assertTrue(WaveTuning.fromWire("something-new", null, "klingon").isDefault)
    }

    @Test
    fun waveSettingsGoBackUnderTheServerNames() {
        assertEquals(listOf("favorite", "discover", "popular", "default"), WaveDiversity.entries.map { it.wire })
        assertEquals(listOf("active", "fun", "calm", "sad", "all"), WaveMood.entries.map { it.wire })
        assertEquals(listOf("russian", "not-russian", "without-words", "any"), WaveLanguage.entries.map { it.wire })
    }

    @Test
    fun stationsAndTheirFeedbackSource() {
        assertEquals("track:42", WaveQueue.trackStation("42"))
        assertEquals("radio-user-onyourwave", WaveQueue.feedbackFrom(WaveQueue.MY_WAVE))
        assertEquals("radio-track-42", WaveQueue.feedbackFrom("track:42"))
    }

    @Test
    fun aLockedSidewaysDragNeitherDismissesNorOpens() {
        assertEquals(MiniGesture.Outcome.NONE, MiniGesture.outcome(MiniGesture.Axis.LOCKED, 900f, 0f, 2f))
        assertEquals(MiniGesture.Outcome.DISMISS, MiniGesture.outcome(MiniGesture.Axis.HORIZONTAL, 900f, 0f, 2f))
        assertTrue(abs(Loudness.TARGET_LUFS + 14) < 1e-9)
    }

    @Test
    fun theWaveKnobOpensThePlayerOnlyWhenItSwitchesMusicOn() {
        // Starting the wave from another source or from nothing
        assertEquals(WaveKnob.Press(WaveKnob.Action.START_WAVE, true), WaveKnob.press(waveActive = false, isPlaying = false, hasPlayed = false))
        assertEquals(WaveKnob.Press(WaveKnob.Action.START_WAVE, true), WaveKnob.press(waveActive = false, isPlaying = true, hasPlayed = true))
        // The wave restored at launch, played for the first time
        assertEquals(WaveKnob.Press(WaveKnob.Action.TOGGLE, true), WaveKnob.press(waveActive = true, isPlaying = false, hasPlayed = false))
        // A pause, and a play after the user's own pause
        assertEquals(WaveKnob.Press(WaveKnob.Action.TOGGLE, false), WaveKnob.press(waveActive = true, isPlaying = true, hasPlayed = true))
        assertEquals(WaveKnob.Press(WaveKnob.Action.TOGGLE, false), WaveKnob.press(waveActive = true, isPlaying = false, hasPlayed = true))
    }

    private fun ids(list: List<TrackDetailsDto>) = list.map { it.idString }

    @Test
    fun theCarShowsWhatWillReallyPlayNext() {
        val queue = listOf(t("a"), t("b", explicit = true), t("c"), t("d"), t("e"))
        // Explicit passed over with the filter on, kept with it off
        assertEquals(listOf("c", "d", "e"), ids(PlaybackQueue.upcoming(queue, 0, PlaylistSource.LIBRARY, 3) { it.isExplicit }))
        assertEquals(listOf("b", "c", "d"), ids(PlaybackQueue.upcoming(queue, 0, PlaylistSource.LIBRARY, 3) { false }))
        // A playlist goes on from its start, without the current track and without repeats
        assertEquals(listOf("e", "a", "c"), ids(PlaybackQueue.upcoming(queue, 3, PlaylistSource.LIBRARY, 3) { it.isExplicit }))
        assertEquals(listOf("a"), ids(PlaybackQueue.upcoming(listOf(t("a"), t("b")), 1, PlaylistSource.LIBRARY, 3) { false }))
        // The wave shows only what it has fetched
        assertEquals(listOf("e"), ids(PlaybackQueue.upcoming(queue, 3, PlaylistSource.VIBE, 3) { false }))
        assertEquals(emptyList<String>(), ids(PlaybackQueue.upcoming(queue, 4, PlaylistSource.VIBE, 3) { false }))
        // Unknown current track: nothing
        assertEquals(emptyList<String>(), ids(PlaybackQueue.upcoming(queue, -1, PlaylistSource.LIBRARY, 3) { false }))
    }
}

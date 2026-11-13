package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.dto.TrackAlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.14.6: the wave learns what was heard, goes on from where it is, and does not bring back what was just heard. */
class WaveRepeatsTest {

    private fun track(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "T$id")
    private fun answer(vararg ids: String, batch: String = "b") = WaveQueue.Answer(ids.map(::track), batch)

    /** [answers] by the queue asked with (null — from the station's start); the queues asked are recorded. */
    private fun gather(
        lastQueued: String?,
        queued: Set<String> = emptySet(),
        heard: List<String> = emptyList(),
        answers: Map<String?, WaveQueue.Answer?>
    ): Pair<WaveQueue.Gathered, List<String?>> {
        val asked = mutableListOf<String?>()
        val recent = RecentlyPlayed(300).apply { restore(heard) }
        val result = runBlocking {
            WaveQueue.gather(lastQueued, queued, recent::age) { queue -> asked += queue; answers[queue] }
        }
        return result to asked
    }

    @Test
    fun theWaveGoesOnFromTheLastQueuedTrackInOneRequest() {
        val (got, asked) = gather("9", answers = mapOf("9" to answer("1", "2", batch = "B1")))
        assertEquals(listOf("9"), asked)
        assertEquals(listOf("1", "2"), got.tracks.map { it.idString })
        assertEquals(mapOf("1" to "B1", "2" to "B1"), got.batches)
    }

    @Test
    fun heardLatelyIsLeftOutAndTheStationIsAskedOnThenFromItsStart() {
        val (got, asked) = gather(
            "9", queued = setOf("3"), heard = listOf("1", "2"),
            answers = mapOf("9" to answer("1", "3"), "3" to answer("2"), null to answer("2", "5", batch = "B3"))
        )
        assertEquals("on from the end of the first answer, then from the start", listOf("9", "3", null), asked)
        assertEquals(listOf("5"), got.tracks.map { it.idString })
        assertEquals("B3", got.batches["5"])
        assertEquals("offered: 1, 3, 2, 5", 4, got.offered)
        assertEquals("heard lately among them", 2, got.heard)
    }

    @Test
    fun onlyHeardTracksOnOfferBringTheOnesHeardLongestAgoAFew() {
        val heard = listOf("1", "2", "3", "4", "5") // 1 is the oldest
        val (got, _) = gather("9", heard = heard, answers = mapOf("9" to answer("5", "4"), "4" to answer("3"), null to answer("2", "1")))
        assertEquals(listOf("1", "2", "3"), got.tracks.map { it.idString })
    }

    @Test
    fun anEmptyWaveAsksFromTheStartOnceAndFailuresEndQuietly() {
        val (got, asked) = gather(null, answers = mapOf(null to null))
        assertEquals("from the start, never twice", listOf<String?>(null), asked)
        assertTrue(got.tracks.isEmpty())
        assertEquals(0, got.offered)
        val (_, asked2) = gather(null, heard = listOf("1"), answers = mapOf(null to answer("1"), "1" to answer("1")))
        assertEquals("from the start, then on from its end", listOf(null, "1"), asked2)
    }

    @Test
    fun aTrackHeardAgainIsFreshAgainAndTheMemoryKeepsItsSize() {
        val recent = RecentlyPlayed(3)
        recent.record("a")
        recent.record("b")
        recent.record("c")
        assertEquals(0, recent.age("c"))
        assertEquals(2, recent.age("a"))
        assertEquals(listOf("b", "c", "a"), recent.record("a"))
        assertEquals(0, recent.age("a"))
        assertNull("the last one again: nothing to save", recent.record("a"))
        recent.record("d")
        assertFalse("the oldest leaves", "b" in recent)
        assertNull(recent.age("b"))
        assertNull("radio is not music heard", recent.record("radio_dfm"))
    }

    @Test
    fun feedbackIsAJsonBodyWithTheTrackAndItsAlbum() {
        val t = RotorFeedback(WaveQueue.MY_WAVE, RotorFeedback.TRACK_FINISHED, 0L, "42", "7", "B1", 183.5).body()
        assertEquals("trackFinished", t["type"]!!.jsonPrimitive.content)
        assertEquals("1970-01-01T00:00:00.000Z", t["timestamp"]!!.jsonPrimitive.content)
        assertEquals("radio-user-onyourwave", t["from"]!!.jsonPrimitive.content)
        assertEquals("42:7", t["trackId"]!!.jsonPrimitive.content)
        assertEquals(183.5, t["totalPlayedSeconds"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertFalse("the batch goes with the request, not in the body", "batchId" in t)

        val started = RotorFeedback("track:5", RotorFeedback.RADIO_STARTED, 1_794_527_824_908L).body()
        assertEquals("radio-track-5", started["from"]!!.jsonPrimitive.content)
        assertEquals("2026-11-12T23:57:04.908Z", started["timestamp"]!!.jsonPrimitive.content)
        assertFalse("no track for the start of the station", "trackId" in started)
        assertEquals("no album known: the id alone", "42", RotorFeedback("s", "skip", 0L, "42").body()["trackId"]!!.jsonPrimitive.content)
    }

    @Test
    fun theJournalCountsWhatTheWaveOfferedAndRepeated() {
        var now = 1_000L
        val journal = PlaybackJournal { now }
        assertFalse(journal.state.value.summary.waveUsed)
        journal.record(PlaybackJournal.Kind.WAVE_BATCH, valueMs = 10, count = 2)
        journal.record(PlaybackJournal.Kind.WAVE_BATCH, valueMs = 5, count = 0)
        journal.record(PlaybackJournal.Kind.WAVE_REPEAT, "Song")
        val s = journal.state.value.summary
        assertEquals(15, s.waveOffered)
        assertEquals(2, s.waveOfferedHeard)
        assertEquals(1, s.waveRepeats)
        assertTrue(s.waveUsed)
        now += PlaybackJournal.WINDOW_MS + 1
        journal.addPlayed(1)
        assertFalse("an hour later it is gone", journal.state.value.summary.waveUsed)
    }

    @Test
    fun aTrackIsNamedWithItsAlbumForFeedback() {
        val withAlbum = track("42").copy(albums = listOf(TrackAlbumDto(id = JsonPrimitive("7"))))
        assertEquals("7", withAlbum.albumId)
    }
}

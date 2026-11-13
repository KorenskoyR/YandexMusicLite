package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.player.PlaybackJournal.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.14.7: every stop of the music is in the journal with its cause, and the journal outlives the app. */
class PlaybackStopsTest {

    private var now = 1_000_000L

    /** A store in memory: what a run of the app saved, the next one reads. */
    private class MemoryStore(var text: String? = null) : JournalStore {
        override fun load(): String? = text
        override fun requestSave(urgent: Boolean, text: () -> String) {
            this.text = text()
        }
    }

    private fun journal(store: JournalStore? = null, exit: ProcessExit? = null) = PlaybackJournal({ now }, store) { exit }

    private fun PlaybackJournal.last(kind: Kind) = state.value.events.last { it.kind == kind }

    @Test
    fun aPauseIsWrittenWithTheCauseSaidJustBefore() {
        val j = journal()
        j.expectPause(StopCause.FOCUS_TRANSIENT, SoundTaker.CALL)
        now += 300
        j.onPaused("Song")
        val e = j.last(Kind.PAUSED)
        assertEquals(StopCause.FOCUS_TRANSIENT, e.cause)
        assertEquals(SoundTaker.CALL, e.detail)
        assertEquals("Song", e.title)
        assertEquals(1, j.state.value.summary.pausesNotAsked)
    }

    @Test
    fun aPauseNobodyAnnouncedIsUnknownAndAnOldWordDoesNotCount() {
        val j = journal()
        j.onPaused("A")
        assertEquals(StopCause.UNKNOWN, j.last(Kind.PAUSED).cause)
        j.expectPause(StopCause.USER_APP)
        now += PlaybackJournal.EXPECT_MS + 1
        j.onPaused("B")
        assertEquals("said too long ago", StopCause.UNKNOWN, j.last(Kind.PAUSED).cause)
        // A word is used once
        j.expectPause(StopCause.USER_APP)
        j.onPaused("C")
        j.onPaused("D")
        assertEquals(StopCause.UNKNOWN, j.last(Kind.PAUSED).cause)
    }

    @Test
    fun theHeadphonesGoingAwayIsTheirsWhateverWasSaid() {
        val j = journal()
        j.expectPause(StopCause.USER_APP)
        j.onPaused("A", outputGone = true)
        assertEquals(StopCause.OUTPUT_GONE, j.last(Kind.PAUSED).cause)
        assertTrue(j.lastPause()!!.isOutputBlip(now + PlaybackJournal.OUTPUT_BLIP_MS))
        assertFalse("too late to be a blip", j.lastPause()!!.isOutputBlip(now + PlaybackJournal.OUTPUT_BLIP_MS + 1))
    }

    @Test
    fun aPauseTheListenerDidNotAskForEndsWithHowLongTheSilenceWas() {
        val j = journal()
        j.expectPause(StopCause.FOCUS_TRANSIENT)
        j.onPaused("A")
        now += 12_300
        j.onPlaying("A")
        val back = j.last(Kind.RESUMED)
        assertEquals(12_300, back.valueMs)
        assertEquals(StopCause.FOCUS_TRANSIENT, back.cause)
        assertNull("over", j.lastPause())

        // The listener's own pause is no stop: nothing to tell when the music plays again
        j.expectPause(StopCause.USER_DEVICE, "com.android.systemui")
        j.onPaused("A")
        j.onPlaying("A")
        assertEquals(1, j.state.value.events.count { it.kind == Kind.RESUMED })
        assertEquals("the listener's pause is not counted", 1, j.state.value.summary.pausesNotAsked)
    }

    @Test
    fun theStopsOfTheSettingsRowAreEveryKindOfStop() {
        val j = journal()
        j.record(Kind.STALL, "A", 300)
        j.onPaused("A")
        j.record(Kind.ERROR, "A", 1000, Recovery.RETRY.ordinal, "ERROR_CODE_IO_BAD_HTTP_STATUS")
        j.expectPause(StopCause.SLEEP_TIMER)
        j.onPaused("A")
        assertEquals(3, j.state.value.summary.stops)
    }

    @Test
    fun gapsOfTheDeviceSoundCloseTogetherMakeOneLine() {
        val j = journal()
        repeat(5) {
            j.recordUnderrun("A")
            now += 1_000
        }
        j.recordUnderrun("B")
        now += PlaybackJournal.UNDERRUN_MERGE_MS
        j.recordUnderrun("B")
        val lines = j.state.value.events.filter { it.kind == Kind.UNDERRUN }
        assertEquals(listOf(5, 1, 1), lines.map { it.count })
        assertEquals(7, j.state.value.summary.underruns)
    }

    @Test
    fun theJournalOutlivesTheAppAndTellsItWasClosedWhilePlaying() {
        val store = MemoryStore()
        val first = journal(store)
        first.record(Kind.STALL, "Tab\there", 500)
        first.expectPause(StopCause.FOCUS_LOST, SoundTaker.APP)
        first.onPaused("A")
        repeat(30) { first.addPlayed(1_000) }
        first.setPlaying(true)
        // ... and Android closes the app

        now += 60_000
        val second = journal(store, ProcessExit(now - 30_000, ProcessExits.SYSTEM))
        val s = second.state.value
        assertEquals(30_000, s.summary.playedMs)
        assertEquals("Tab here", s.events.first { it.kind == Kind.STALL }.title)
        assertEquals(StopCause.FOCUS_LOST, s.events.first { it.kind == Kind.PAUSED }.cause)
        val killed = s.events.last()
        assertEquals(Kind.KILLED, killed.kind)
        assertEquals(ProcessExits.SYSTEM, killed.detail)
        assertEquals(now - 30_000, killed.valueMs)
        assertEquals(killed, second.takeKillNotice())
        assertNull("told once", second.takeKillNotice())

        // Closed with the music paused: nothing to tell
        second.setPlaying(true)
        second.setPlaying(false)
        val third = journal(store)
        assertEquals(1, third.state.value.events.count { it.kind == Kind.KILLED })
        assertNull(third.takeKillNotice())
    }

    @Test
    fun anHourOfMusicIsAFewHundredEntriesNotTensOfThousands() {
        val store = MemoryStore()
        val j = journal(store)
        repeat(36_000) {
            j.addPlayed(100)
            now += 100
        }
        val played = j.state.value.summary.playedMs
        assertTrue("an hour, give or take a step: $played", played in 3_590_000L..3_600_000L)
        j.record(Kind.UNEVEN)
        assertTrue(store.text!!.lines().count { it.startsWith("P") } < 400)
    }

    @Test
    fun theReasonsAndroidGivesAreKeptByName() {
        assertEquals(ProcessExits.LOW_MEMORY, ProcessExits.reasonName(3))
        assertEquals(ProcessExits.SYSTEM, ProcessExits.reasonName(2))
        assertEquals(ProcessExits.USER, ProcessExits.reasonName(10))
        assertEquals(ProcessExits.UPDATED, ProcessExits.reasonName(16))
        assertEquals(ProcessExits.CRASH, ProcessExits.reasonName(4))
        assertEquals(ProcessExits.UNKNOWN, ProcessExits.reasonName(0))
        assertTrue(ProcessExits.closedByThePhone(ProcessExits.SYSTEM))
        assertTrue("Android before 11 does not say", ProcessExits.closedByThePhone(null))
        assertFalse(ProcessExits.closedByThePhone(ProcessExits.USER))
        assertFalse(ProcessExits.closedByThePhone(ProcessExits.CRASH))
        assertFalse(ProcessExits.closedByThePhone(ProcessExits.UPDATED))
    }

    @Test
    fun whatTookTheSoundIsToldByTheKindsOfSoundPlaying() {
        val media = 1
        assertEquals(SoundTaker.APP, SoundTaker.byUsages(listOf(media, media)))
        assertEquals(SoundTaker.VOICE_CHAT, SoundTaker.byUsages(listOf(media, 2)))
        assertEquals(SoundTaker.NAVIGATION, SoundTaker.byUsages(listOf(media, 12)))
        assertEquals(SoundTaker.ASSISTANT, SoundTaker.byUsages(listOf(16)))
        assertEquals(SoundTaker.NOTIFICATION, SoundTaker.byUsages(listOf(5)))
        assertEquals(SoundTaker.ALARM, SoundTaker.byUsages(listOf(4, 5)))
        assertEquals(SoundTaker.GAME, SoundTaker.byUsages(listOf(14)))
    }

    @Test
    fun aJournalOfAnOlderVersionIsIgnoredNotMisread() {
        val j = journal(MemoryStore("something else\nE\t1\tSTALL\t1\t1\t\t"))
        assertTrue(j.state.value.events.isEmpty())
    }
}

package com.korenskoy.yandexmusic.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** After the head unit's sleep the first Play did nothing: a pause the device slept through opens the track afresh. */
class AfterRestTest {

    private var elapsed = 1_000_000L
    private var uptime = 1_000_000L
    private val rest = AfterRest({ elapsed }, { uptime })

    /** Awake: both clocks go on. */
    private fun awake(ms: Long) {
        elapsed += ms
        uptime += ms
    }

    /** Asleep: only the real time goes on. */
    private fun asleep(ms: Long) {
        elapsed += ms
    }

    @Test
    fun playingIsNeverStale() {
        rest.onPlaying()
        asleep(AfterRest.SLEPT_MS * 10)
        assertNull(rest.staleFor())
    }

    @Test
    fun aShortPauseResumesAsItIs() {
        rest.onPaused()
        awake(60_000L)
        asleep(60_000L)
        assertNull(rest.staleFor())
    }

    @Test
    fun theHeadUnitSleptThroughThePause() {
        rest.onPaused()
        awake(10_000L)
        asleep(AfterRest.SLEPT_MS)
        assertEquals(10_000L + AfterRest.SLEPT_MS, rest.staleFor())
    }

    @Test
    fun aVeryLongPauseAwakeIsStaleToo() {
        rest.onPaused()
        awake(AfterRest.LONG_PAUSE_MS)
        assertEquals(AfterRest.LONG_PAUSE_MS, rest.staleFor())
    }

    @Test
    fun sleepBeforeThePauseDoesNotCount() {
        asleep(AfterRest.SLEPT_MS * 3)
        rest.onPaused()
        awake(1_000L)
        assertNull(rest.staleFor())
    }

    @Test
    fun theFirstPauseIsTheStartAndPlayingEndsIt() {
        rest.onPaused()
        asleep(AfterRest.SLEPT_MS)
        // A second "paused" (the player told twice) does not move the start
        rest.onPaused()
        assertEquals(AfterRest.SLEPT_MS, rest.staleFor())
        rest.onPlaying()
        assertNull(rest.staleFor())
    }
}

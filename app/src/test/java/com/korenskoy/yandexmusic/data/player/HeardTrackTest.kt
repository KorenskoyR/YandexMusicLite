package com.korenskoy.yandexmusic.data.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.14.9 report (Pixel 8 Pro, a perfect Wi-Fi): the light stream on every track for five minutes. The start of a track
 * the listener came back to counted as a stop mid-track — two of them made the link "uneven".
 */
class HeardTrackTest {
    private val heard = HeardTrack()

    @Test
    fun aWaitOfTheTrackThatSoundsIsMidTrack() {
        heard.onStart()
        heard.onAudible("A")
        assertTrue(heard.isMidTrack("A"))
    }

    @Test
    fun theStartOfATrackIsNotAStopEvenIfItWasHeardBefore() {
        heard.onStart()
        heard.onAudible("A")
        // Next, and back before the other one sounded
        heard.onStart()
        heard.onStart()
        assertFalse("A again: its start", heard.isMidTrack("A"))
        heard.onAudible("A")
        assertTrue("then a stop of A is mid-track", heard.isMidTrack("A"))
    }

    @Test
    fun anotherTracksWaitIsNeverThisOnesStop() {
        heard.onAudible("A")
        assertFalse(heard.isMidTrack("B"))
        assertFalse(heard.isMidTrack(null))
    }
}

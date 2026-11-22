package com.korenskoy.yandexmusic.data

import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.data.player.Recovery
import com.korenskoy.yandexmusic.data.player.StreamReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back from the device to the streams only by a trial, and ever less often while the trials fail. */
class StreamReturnTest {
    private var clock = 1_000_000L
    private val ret = StreamReturn { clock }

    @Test
    fun beforeAnyFailureAReturnMayBeTried() {
        assertTrue(ret.mayTry())
    }

    @Test
    fun failedReturnsWaitLongerEachTime() {
        ret.onStreamsFailed()
        assertFalse(ret.mayTry())
        clock += 59_000
        assertFalse(ret.mayTry())
        clock += 1_000
        assertTrue(ret.mayTry())
        // The trial stays silent: two minutes now, then four, eight, and never more than fifteen
        ret.onStreamsFailed()
        assertEquals(120_000L, ret.nextTryInMs())
        ret.onStreamsFailed()
        assertEquals(240_000L, ret.nextTryInMs())
        ret.onStreamsFailed()
        ret.onStreamsFailed()
        ret.onStreamsFailed()
        ret.onStreamsFailed()
        assertEquals(StreamReturn.MAX_WAIT_MS, ret.nextTryInMs())
    }

    @Test
    fun streamsHeardForMinutesClearTheRecord() {
        repeat(4) { ret.onStreamsFailed() }
        clock += StreamReturn.MAX_WAIT_MS
        ret.onAudible()
        clock += StreamReturn.HOLD_MS
        ret.onAudible()
        assertTrue(ret.mayTry())
        // A new failure after the good run is the first again: one minute
        ret.onStreamsFailed()
        assertEquals(60_000L, ret.nextTryInMs())
    }

    @Test
    fun aStreamThatSoundsBrieflyDoesNotClearIt() {
        ret.onStreamsFailed()
        clock += 60_000
        ret.onAudible()
        clock += 20_000
        ret.onStreamsFailed()
        assertEquals(120_000L, ret.nextTryInMs())
    }

    @Test
    fun aNetworkFailureWithMusicOnTheDeviceGoesThereAtOnce() {
        // No retry of a stream that did not come, no skipping, never a stop: the device's music plays
        val action = PlaybackQueue.recoveryFor(
            offline = false, isRadio = false, isDownloaded = false, alreadyRetried = false, failedTracksInARow = 1,
            networkFailure = true, deviceHasMusic = true
        )
        assertEquals(Recovery.CONTINUE_OFFLINE, action)
        // Its own copy first
        assertEquals(
            Recovery.RESUME_FROM_COPY,
            PlaybackQueue.recoveryFor(false, false, false, false, 1, hasCopy = true, networkFailure = true, deviceHasMusic = true)
        )
        // Nothing on the device: as before, a fresh link first
        assertEquals(
            Recovery.RETRY,
            PlaybackQueue.recoveryFor(false, false, false, false, 1, networkFailure = true, deviceHasMusic = false)
        )
        // A track's own trouble (not the network): as before
        assertEquals(
            Recovery.RETRY,
            PlaybackQueue.recoveryFor(false, false, false, false, 1, networkFailure = false, deviceHasMusic = true)
        )
    }
}

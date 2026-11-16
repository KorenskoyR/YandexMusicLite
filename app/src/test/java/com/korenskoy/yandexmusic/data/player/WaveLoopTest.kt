package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.14.9, the report: the wave played the same eight tracks in a circle ("повторы 21"; "при белых списках играли
 * только из кеша по кругу"). When no new track came, it started over from its first one. It never does now.
 */
class WaveLoopTest {

    private fun track(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "T$id")

    @Test
    fun aWaveWhoseTracksWereAllHeardHasNoNextNotItsFirstAgain() {
        val wave = listOf(track("1"), track("2"), track("3"))
        assertNull("all heard: nothing, not «1» again", WaveQueue.firstUnheard(wave) { true })
        assertEquals("2", WaveQueue.firstUnheard(wave) { it == "1" }?.idString)
    }

    @Test
    fun theEndOfTheQueueWithNothingNewIsStuckNotTheFirstTrack() {
        assertEquals(PlaybackQueue.AfterWaveFetch.Play(8), PlaybackQueue.afterWaveFetch(following = 8, queueSize = 9))
        assertEquals("the old rule played index 0 here", PlaybackQueue.AfterWaveFetch.Stuck, PlaybackQueue.afterWaveFetch(following = null, queueSize = 8))
        assertEquals(PlaybackQueue.AfterWaveFetch.Nothing, PlaybackQueue.afterWaveFetch(following = null, queueSize = 0))
    }

    @Test
    fun aBrokenLinkIsToldFromABrokenTrack() {
        assertTrue(PlaybackQueue.isNetworkFailure("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
        assertTrue(PlaybackQueue.isNetworkFailure("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"))
        assertTrue(PlaybackQueue.isNetworkFailure("BUFFERING_TIMEOUT"))
        assertFalse(PlaybackQueue.isNetworkFailure("ERROR_CODE_DECODING_FAILED"))
        assertFalse(PlaybackQueue.isNetworkFailure("ERROR_CODE_IO_FILE_NOT_FOUND"))
    }
}

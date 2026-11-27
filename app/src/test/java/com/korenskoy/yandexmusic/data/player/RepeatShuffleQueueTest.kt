package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatShuffleQueueTest {

    @Test
    fun repeatAndShuffleSupportedOnlyForLibraryAndDeviceMusic() {
        assertTrue(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.LIBRARY, isRadio = false))
        assertTrue(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.DEVICE, isRadio = false))

        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.LIBRARY, isRadio = true))
        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.VIBE, isRadio = false))
        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.RADIO, isRadio = true))
        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.TRENDS, isRadio = false))
        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.SEARCH, isRadio = false))
        assertFalse(PlaybackQueue.supportsRepeatAndShuffle(PlaylistSource.NONE, isRadio = false))
    }

    @Test
    fun repeatOneLoopsCurrentTrackOnlyOnNaturalCompletion() {
        val queue = listOf("a", "b", "c")
        val autoNext = PlaybackQueue.nextActionForRepeat(
            queue = queue,
            currentIndex = 1,
            source = PlaylistSource.LIBRARY,
            repeatMode = RepeatMode.ONE,
            isAuto = true,
            skip = { false }
        )
        assertEquals(NextAction.PlayIndex(1), autoNext)

        val manualNext = PlaybackQueue.nextActionForRepeat(
            queue = queue,
            currentIndex = 1,
            source = PlaylistSource.LIBRARY,
            repeatMode = RepeatMode.ONE,
            isAuto = false,
            skip = { false }
        )
        assertEquals(NextAction.PlayIndex(2), manualNext)
    }

    @Test
    fun playlistWrapsAroundAtEndUnderOffAndAll() {
        val queue = listOf("a", "b", "c")
        val offAtEnd = PlaybackQueue.nextActionForRepeat(
            queue = queue,
            currentIndex = 2,
            source = PlaylistSource.DEVICE,
            repeatMode = RepeatMode.OFF,
            isAuto = true,
            skip = { false }
        )
        val allAtEnd = PlaybackQueue.nextActionForRepeat(
            queue = queue,
            currentIndex = 2,
            source = PlaylistSource.LIBRARY,
            repeatMode = RepeatMode.ALL,
            isAuto = true,
            skip = { false }
        )
        assertEquals(NextAction.PlayIndex(0), offAtEnd)
        assertEquals(NextAction.PlayIndex(0), allAtEnd)
    }
}

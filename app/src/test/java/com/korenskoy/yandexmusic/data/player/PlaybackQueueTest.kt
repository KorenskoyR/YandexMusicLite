package com.korenskoy.yandexmusic.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {

    @Test
    fun nextPlaysFollowingTrack() {
        assertEquals(NextAction.PlayIndex(3), PlaybackQueue.nextAction(10, 2, PlaylistSource.LIBRARY))
        assertEquals(NextAction.PlayIndex(3), PlaybackQueue.nextAction(10, 2, PlaylistSource.VIBE))
    }

    @Test
    fun endOfWaveFetchesMoreInsteadOfStopping() {
        assertEquals(NextAction.FetchMoreWave, PlaybackQueue.nextAction(10, 9, PlaylistSource.VIBE))
        assertEquals(NextAction.FetchMoreWave, PlaybackQueue.nextAction(0, -1, PlaylistSource.VIBE))
    }

    @Test
    fun endOfRegularPlaylistLoopsToStart() {
        assertEquals(NextAction.PlayIndex(0), PlaybackQueue.nextAction(10, 9, PlaylistSource.LIBRARY))
        assertEquals(NextAction.PlayIndex(0), PlaybackQueue.nextAction(5, 4, PlaylistSource.SEARCH))
        // Current track removed from the queue: start over rather than stop
        assertEquals(NextAction.PlayIndex(0), PlaybackQueue.nextAction(5, -1, PlaylistSource.TRENDS))
    }

    @Test
    fun emptyQueueFallsBackToLibrary() {
        assertEquals(NextAction.FallbackLibrary, PlaybackQueue.nextAction(0, -1, PlaylistSource.LIBRARY))
        assertEquals(NextAction.FallbackLibrary, PlaybackQueue.nextAction(0, -1, PlaylistSource.NONE))
    }

    @Test
    fun previousGoesBackOnlyAtTheStartOfATrack() {
        assertEquals(4, PlaybackQueue.previousIndex(5, 1_000L))
        assertEquals(4, PlaybackQueue.previousIndex(5, PlaybackQueue.RESTART_ON_PREV_THRESHOLD_MS))
        // Apple Music: after 3 s "previous" restarts the current track
        assertNull(PlaybackQueue.previousIndex(5, 3_001L))
        // First track: restart
        assertNull(PlaybackQueue.previousIndex(0, 0L))
        assertNull(PlaybackQueue.previousIndex(-1, 0L))
    }

    @Test
    fun offlineSkipFindsNextDownloadedTrack() {
        val downloaded = setOf("b", "e")
        val queue = listOf("a", "b", "c", "d", "e")
        assertEquals(4, PlaybackQueue.nextMatching(queue, 2) { it in downloaded })   // c -> e
        assertEquals(1, PlaybackQueue.nextMatching(queue, 4) { it in downloaded })   // wraps: e -> b
        assertEquals(1, PlaybackQueue.nextMatching(queue, -1) { it in downloaded })  // current not in queue
        assertNull(PlaybackQueue.nextMatching(queue, 1) { it == "b" })               // only the current one
        assertNull(PlaybackQueue.nextMatching(emptyList<String>(), 0) { true })
    }

    @Test
    fun snapshotKeepsWindowAroundCurrentTrack() {
        val queue = (0 until 1000).toList()
        val window = PlaybackQueue.snapshotWindow(queue, 500)
        assertEquals(500 - PlaybackQueue.SNAPSHOT_BEFORE, window.first())
        assertEquals(500 + PlaybackQueue.SNAPSHOT_AFTER - 1, window.last())
        assertEquals(listOf(0, 1, 2), PlaybackQueue.snapshotWindow(listOf(0, 1, 2), 1))
        assertEquals(0, PlaybackQueue.snapshotWindow(queue, 0).first())
    }
}

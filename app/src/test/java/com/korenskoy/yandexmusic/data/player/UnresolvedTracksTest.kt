package com.korenskoy.yandexmusic.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** A track the server gives no link to is the track's trouble, not the link's (1.14.8 reports, TOX1). */
class UnresolvedTracksTest {
    private var clock = 0L
    private val tracks = UnresolvedTracks { clock }

    @Test
    fun oneTrackWithoutALinkWhileTheServerAnswersIsTheTracksTrouble() {
        assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("1", serverAnswers = true, markedUnavailable = false))
        // Its retry fails too: still that one track
        assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("1", serverAnswers = true, markedUnavailable = false))
    }

    @Test
    fun aSilentServerIsTheLink() {
        assertEquals(UnresolvedTracks.Verdict.LINK, tracks.onFailed("1", serverAnswers = false, markedUnavailable = false))
    }

    @Test
    fun severalDifferentTracksInARowAreTheLink() {
        tracks.onFailed("1", serverAnswers = true, markedUnavailable = false)
        clock += 10_000
        assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("2", serverAnswers = true, markedUnavailable = false))
        clock += 10_000
        assertEquals(UnresolvedTracks.Verdict.LINK, tracks.onFailed("3", serverAnswers = true, markedUnavailable = false))
    }

    @Test
    fun tracksTheServiceMarksUnavailableNeverMakeTheLinkWeak() {
        repeat(5) { i ->
            assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("$i", serverAnswers = true, markedUnavailable = true))
        }
    }

    @Test
    fun aLinkGivenInBetweenStartsTheCountAgain() {
        tracks.onFailed("1", serverAnswers = true, markedUnavailable = false)
        tracks.onFailed("2", serverAnswers = true, markedUnavailable = false)
        tracks.onResolved()
        assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("3", serverAnswers = true, markedUnavailable = false))
    }

    @Test
    fun failuresLongAgoDoNotCount() {
        tracks.onFailed("1", serverAnswers = true, markedUnavailable = false)
        tracks.onFailed("2", serverAnswers = true, markedUnavailable = false)
        clock += UnresolvedTracks.WINDOW_MS + 1
        assertEquals(UnresolvedTracks.Verdict.TRACK, tracks.onFailed("3", serverAnswers = true, markedUnavailable = false))
    }
}

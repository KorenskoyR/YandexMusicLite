package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.StreamQuality
import com.korenskoy.yandexmusic.data.repository.PendingReaction
import com.korenskoy.yandexmusic.data.repository.PendingReactions
import com.korenskoy.yandexmusic.data.reserve.ReserveEntry
import com.korenskoy.yandexmusic.data.reserve.ReservePolicy
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic of music on the road: how the link is judged, where a track comes from, what the reserve keeps. */
class RoadLogicTest {

    private var clock = 1_000L
    private val link = LinkTracker { clock }

    // --- The state of the link ------------------------------------------------------------------------

    @Test
    fun theLinkIsGoodUntilSomethingHappens() {
        assertEquals(LinkState.GOOD, link.state)
        assertTrue(link.mayTryStream())
    }

    @Test
    fun noNetworkIsNoConnection() {
        link.onNetwork(false)
        assertEquals(LinkState.NONE, link.state)
        assertFalse(link.mayTryStream())
    }

    @Test
    fun aStallWithTheServerInReachIsAWeakLink() {
        link.onTrouble(reachable = true)
        assertEquals(LinkState.WEAK, link.state)
    }

    @Test
    fun aNetworkThatCarriesNoDataIsNoConnection() {
        // A tunnel: Android still reports a network, the server does not answer
        link.onNetwork(true)
        link.onTrouble(reachable = false)
        assertEquals(LinkState.NONE, link.state)
    }

    @Test
    fun theServerAnsweringAgainEndsNoConnection() {
        link.onTrouble(reachable = false)
        link.onServerReachable()
        assertEquals("the next track tries the stream at once", LinkState.GOOD, link.state)
        assertTrue(link.mayTryStream())
    }

    @Test
    fun aReturningNetworkStartsClean() {
        link.onTrouble(reachable = true)
        link.onNetwork(false)
        link.onNetwork(true)
        assertEquals(LinkState.GOOD, link.state)
    }

    @Test
    fun theSameNetworkReportedAgainDoesNotEndAWeakLink() {
        link.onNetwork(true)
        link.onTrouble(reachable = true)
        link.onNetwork(true)
        assertEquals(LinkState.WEAK, link.state)
    }

    @Test
    fun aWeakLinkRetriesTheStreamEveryFiveMinutesAlways() {
        link.onTrouble(reachable = true)
        clock += LinkTracker.WEAK_RETRY_MS - 1
        assertFalse(link.mayTryStream())
        clock += 1
        assertTrue("five minutes passed", link.mayTryStream())

        // The try failed: again five minutes, not more
        link.onTrouble(reachable = true)
        assertFalse(link.mayTryStream())
        clock += LinkTracker.WEAK_RETRY_MS
        assertTrue(link.mayTryStream())
    }

    @Test
    fun aStreamThatPlaysEndsTheWeakLink() {
        link.onTrouble(reachable = true)
        link.onStreamAudible()
        assertEquals(LinkState.GOOD, link.state)
    }

    // --- Where a track comes from ---------------------------------------------------------------------

    @Test
    fun aGoodLinkAlwaysAsksForTheStream() {
        assertEquals(TrackOrigin.STREAM, RoadPlan.originFor(LinkState.GOOD, mayTryStream = true, hasCopy = true))
        assertEquals(TrackOrigin.STREAM, RoadPlan.originFor(LinkState.GOOD, mayTryStream = true, hasCopy = false))
    }

    @Test
    fun aWeakLinkPlaysCopiesUntilTheRetry() {
        assertEquals(TrackOrigin.COPY, RoadPlan.originFor(LinkState.WEAK, mayTryStream = false, hasCopy = true))
        assertEquals("the check goes through the ordinary request", TrackOrigin.STREAM, RoadPlan.originFor(LinkState.WEAK, mayTryStream = true, hasCopy = true))
        assertEquals("no copy: the stream is all there is", TrackOrigin.STREAM, RoadPlan.originFor(LinkState.WEAK, mayTryStream = false, hasCopy = false))
    }

    @Test
    fun noConnectionPlaysCopiesOrMovesOn() {
        assertEquals(TrackOrigin.COPY, RoadPlan.originFor(LinkState.NONE, mayTryStream = false, hasCopy = true))
        assertEquals(TrackOrigin.UNAVAILABLE, RoadPlan.originFor(LinkState.NONE, mayTryStream = false, hasCopy = false))
    }

    @Test
    fun aBetterStreamBeatsTheCopyAnEqualOneDoesNot() {
        val copy = StreamQuality("mp3", 192)
        assertFalse("better bitrate", RoadPlan.copyIsEnough(StreamQuality("mp3", 320), copy))
        assertFalse("lossless", RoadPlan.copyIsEnough(StreamQuality("flac", 1000), copy))
        assertTrue("the same", RoadPlan.copyIsEnough(StreamQuality("mp3", 192), copy))
        assertTrue("worse", RoadPlan.copyIsEnough(StreamQuality("mp3", 128), copy))
        assertTrue("unknown", RoadPlan.copyIsEnough(null, copy))
    }

    // --- What to do when a stream fails ---------------------------------------------------------------

    private fun recovery(
        offline: Boolean = false,
        retried: Boolean = false,
        hasCopy: Boolean = false,
        weak: Boolean = false,
        radio: Boolean = false,
        downloaded: Boolean = false
    ) = PlaybackQueue.recoveryFor(offline, radio, downloaded, retried, 1, hasCopy, weak)

    @Test
    fun noConnectionGoesOnFromTheCopyOfTheSameTrack() {
        assertEquals(Recovery.RESUME_FROM_COPY, recovery(offline = true, hasCopy = true))
        assertEquals(Recovery.CONTINUE_OFFLINE, recovery(offline = true))
    }

    @Test
    fun anExpiredLinkGetsAFreshOneBeforeTheCopy() {
        assertEquals(Recovery.RETRY, recovery(hasCopy = true))
        assertEquals(Recovery.RESUME_FROM_COPY, recovery(retried = true, hasCopy = true))
    }

    @Test
    fun aWeakLinkDoesNotCountAsABrokenTrack() {
        assertEquals(Recovery.CONTINUE_OFFLINE, recovery(retried = true, weak = true))
        assertEquals("a good link: the track is to blame", Recovery.SKIP, recovery(retried = true))
    }

    @Test
    fun aStationIsNeverReplacedByTheReserve() {
        assertEquals(Recovery.STOP, recovery(retried = true, hasCopy = true, weak = true, radio = true))
    }

    // --- The reserve ----------------------------------------------------------------------------------

    private fun entry(id: String, size: Long = 5, added: Long = 0, played: Long? = null) = ReserveEntry(
        track = TrackDetailsDto(id = JsonPrimitive(id)),
        sizeBytes = size,
        codec = "mp3",
        bitrateKbps = 192,
        addedAtMs = added,
        playedAtMs = played
    )

    @Test
    fun nothingLeavesWhileThereIsRoom() {
        assertEquals(emptyList<String>(), ReservePolicy.toEvict(listOf(entry("a")), limitBytes = 10, incomingBytes = 5, keep = emptySet()))
    }

    @Test
    fun whatWasPlayedLongAgoLeavesFirst() {
        val entries = listOf(entry("new", played = null, added = 1), entry("late", played = 200), entry("early", played = 100))
        assertEquals(listOf("early"), ReservePolicy.toEvict(entries, limitBytes = 15, incomingBytes = 5, keep = emptySet()))
        assertEquals(listOf("early", "late"), ReservePolicy.toEvict(entries, limitBytes = 10, incomingBytes = 5, keep = emptySet()))
        assertEquals("then what never played", listOf("early", "late", "new"), ReservePolicy.toEvict(entries, limitBytes = 5, incomingBytes = 5, keep = emptySet()))
    }

    @Test
    fun whatIsAboutToPlayStays() {
        val entries = listOf(entry("next"), entry("after"))
        assertNull("full of what comes next: no room", ReservePolicy.toEvict(entries, limitBytes = 10, incomingBytes = 5, keep = setOf("next", "after")))
        assertEquals(listOf("after"), ReservePolicy.toEvict(entries, limitBytes = 10, incomingBytes = 5, keep = setOf("next")))
    }

    @Test
    fun withNoSignalWhatWasNotHeardPlaysFirst() {
        val entries = listOf(entry("heard", played = 100), entry("older", played = 50), entry("fresh", added = 10), entry("recent", added = 5))
        val order = ReservePolicy.playOrder(entries) { it == "recent" }.map { it.id }
        assertEquals(listOf("fresh", "recent", "older", "heard").first(), order.first())
        assertEquals("heard lately goes among the heard", listOf("fresh", "recent", "older", "heard"), order)
    }

    // --- Reactions without a connection ---------------------------------------------------------------

    @Test
    fun onlyTheLastWordAboutATrackIsSent() {
        var queue = PendingReactions.add(emptyList(), PendingReaction("1", PendingReaction.Kind.LIKE))
        queue = PendingReactions.add(queue, PendingReaction("2", PendingReaction.Kind.DISLIKE))
        queue = PendingReactions.add(queue, PendingReaction("1", PendingReaction.Kind.UNLIKE))
        assertEquals(listOf(PendingReaction("2", PendingReaction.Kind.DISLIKE), PendingReaction("1", PendingReaction.Kind.UNLIKE)), queue)
    }

    @Test
    fun aSavedReactionReadsBack() {
        val reaction = PendingReaction("123:456", PendingReaction.Kind.DISLIKE)
        assertEquals(reaction, PendingReactions.parse(reaction.saved))
        assertNull(PendingReactions.parse("nonsense"))
        assertNull(PendingReactions.parse("LIKE:"))
    }
}

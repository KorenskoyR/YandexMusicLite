package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.data.player.SessionButtons
import com.korenskoy.yandexmusic.presentation.update.UpdatePolicy
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.toCoverUrl
import com.korenskoy.yandexmusic.data.player.WaveQueue
import com.korenskoy.yandexmusic.presentation.hifi.LoadState
import com.korenskoy.yandexmusic.presentation.main.components.MiniGesture
import com.korenskoy.yandexmusic.presentation.main.upcomingOf
import com.korenskoy.yandexmusic.presentation.player.PlayerGesture
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gesture, queue and screen-state rules of 1.9: pure logic, no Android. */
class InteractionLogicTest {

    private fun track(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "T$id")

    // --- Full player pull-down ----------------------------------------------------------------------

    @Test
    fun playerClosesWhenPulledFarEnoughOrFlung() {
        assertFalse("a little pull springs back", PlayerGesture.shouldClose(100f, 2000f, 0f))
        assertTrue("past a fifth of the height", PlayerGesture.shouldClose(500f, 2000f, 0f))
        assertTrue("a downward fling closes from anywhere", PlayerGesture.shouldClose(20f, 2000f, 2500f))
        assertFalse("flung back up: stays open", PlayerGesture.shouldClose(500f, 2000f, -2500f))
        assertFalse("not laid out yet", PlayerGesture.shouldClose(500f, 0f, 0f))
    }

    @Test
    fun newTrackSlidesInFromTheSideItCameFrom() {
        assertEquals("next in the queue", 1, PlayerGesture.direction(3, 4))
        assertEquals("back in the queue", -1, PlayerGesture.direction(4, 3))
        assertEquals("not in the queue", 1, PlayerGesture.direction(4, -1))
        assertEquals("first track", 1, PlayerGesture.direction(-1, 0))
    }

    // --- Mini-player drag -----------------------------------------------------------------------------

    @Test
    fun miniPlayerDragDecidesByItsFirstMovement() {
        assertEquals(MiniGesture.Axis.HORIZONTAL, MiniGesture.axisOf(12f, -3f))
        assertEquals(MiniGesture.Axis.VERTICAL, MiniGesture.axisOf(2f, -9f))
        assertEquals(MiniGesture.Axis.NONE, MiniGesture.axisOf(0f, 0f))
    }

    @Test
    fun miniPlayerOpensUpwardsAndDismissesSideways() {
        val d = 3f // density
        assertEquals(MiniGesture.Outcome.DISMISS, MiniGesture.outcome(MiniGesture.Axis.HORIZONTAL, -101f * d, 0f, d))
        assertEquals(MiniGesture.Outcome.NONE, MiniGesture.outcome(MiniGesture.Axis.HORIZONTAL, 60f * d, 0f, d))
        assertEquals(MiniGesture.Outcome.OPEN, MiniGesture.outcome(MiniGesture.Axis.VERTICAL, 0f, -40f * d, d))
        assertEquals("pulled down: nothing", MiniGesture.Outcome.NONE, MiniGesture.outcome(MiniGesture.Axis.VERTICAL, 0f, 10f * d, d))
        assertEquals("a sideways offset never opens", MiniGesture.Outcome.NONE, MiniGesture.outcome(MiniGesture.Axis.HORIZONTAL, 0f, -90f * d, d))
    }

    // --- Queue ----------------------------------------------------------------------------------------

    @Test
    fun movingInTheQueue() {
        val q = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), PlaybackQueue.moved(q, 1, 3))
        assertEquals(listOf("c", "a", "b", "d"), PlaybackQueue.moved(q, 2, 0))
        assertTrue("out of range: unchanged", PlaybackQueue.moved(q, 1, 9) === q)
        assertTrue("same place: unchanged", PlaybackQueue.moved(q, 2, 2) === q)
    }

    @Test
    fun upcomingIsWhatFollowsTheCurrentTrack() {
        val q = (1..5).map { track("$it") }
        assertEquals(listOf("3", "4"), upcomingOf(q, "2", limit = 2).map { it.idString })
        assertTrue("current unknown", upcomingOf(q, "9").isEmpty())
        assertTrue("last track", upcomingOf(q, "5").isEmpty())
    }

    @Test
    fun waveTakesTracksNotQueuedAndNotHeardLately() {
        val batch = listOf(track("1"), track("2"), track("3"), track("4"))
        val fresh = WaveQueue.freshOf(batch, queued = setOf("1")) { it == "2" }
        assertEquals(listOf("3", "4"), fresh.map { it.idString })
        val allHeard = WaveQueue.freshOf(batch, queued = setOf("1")) { true }
        assertEquals("everything heard lately: still the ones not queued", listOf("2", "3", "4"), allHeard.map { it.idString })
        assertTrue("all queued already", WaveQueue.freshOf(batch, queued = setOf("1", "2", "3", "4")) { false }.isEmpty())
    }

    @Test
    fun aFailingStreamIsRetriedOnceThenSkippedThenStopped() {
        fun r(offline: Boolean = false, radio: Boolean = false, downloaded: Boolean = false, retried: Boolean = false, inARow: Int = 1) =
            PlaybackQueue.recoveryFor(offline, radio, downloaded, retried, inARow)
        assertEquals(com.korenskoy.yandexmusic.data.player.Recovery.RETRY, r())
        assertEquals(com.korenskoy.yandexmusic.data.player.Recovery.SKIP, r(retried = true, inARow = 2))
        assertEquals(com.korenskoy.yandexmusic.data.player.Recovery.STOP, r(retried = true, inARow = 3))
        assertEquals("a station is never skipped through", com.korenskoy.yandexmusic.data.player.Recovery.STOP, r(radio = true, retried = true))
        assertEquals("no network, not on the device", com.korenskoy.yandexmusic.data.player.Recovery.CONTINUE_OFFLINE, r(offline = true))
        assertEquals("offline but downloaded: an ordinary retry", com.korenskoy.yandexmusic.data.player.Recovery.RETRY, r(offline = true, downloaded = true))
    }

    // --- Screen states --------------------------------------------------------------------------------

    @Test
    fun cachedDataWinsOverAFailedRefresh() {
        assertEquals(LoadState.Ready(listOf(1)), LoadState.of(listOf(1), isEmpty = false, loading = true, failed = true))
        assertEquals(LoadState.Loading, LoadState.of(emptyList<Int>(), isEmpty = true, loading = true, failed = true))
        assertEquals(LoadState.Failed, LoadState.of(emptyList<Int>(), isEmpty = true, loading = false, failed = true))
        assertEquals(LoadState.Empty, LoadState.of(emptyList<Int>(), isEmpty = true, loading = false, failed = false))
    }

    // --- 1.9.3: the notification's buttons, skipping an update, cover sizes ------------------------------

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun notificationShowsDislikeAndHeartForTracksAndOnlyTheHeartForStations() {
        assertEquals(listOf(SessionButtons.Kind.DISLIKE, SessionButtons.Kind.LIKE), SessionButtons.kindsFor(track("1"), liked = false))
        assertEquals(listOf(SessionButtons.Kind.DISLIKE, SessionButtons.Kind.UNLIKE), SessionButtons.kindsFor(track("1"), liked = true))
        assertEquals(listOf(SessionButtons.Kind.UNLIKE), SessionButtons.kindsFor(track("radio_x").copy(isRadio = true), liked = true))
        assertTrue("nothing plays", SessionButtons.kindsFor(null, liked = false).isEmpty())
    }

    @Test
    fun aSkippedVersionIsNotOfferedAtLaunchButANewerOneIs() {
        assertFalse("skipped, at launch", UpdatePolicy.offerOnLaunch("1.9.3", skipped = "1.9.3", silent = true))
        assertTrue("a newer one", UpdatePolicy.offerOnLaunch("1.9.4", skipped = "1.9.3", silent = true))
        assertTrue("asked for in Settings", UpdatePolicy.offerOnLaunch("1.9.3", skipped = "1.9.3", silent = false))
        assertTrue("nothing skipped", UpdatePolicy.offerOnLaunch("1.9.3", skipped = null, silent = true))
    }

    @Test
    fun coversComeInThreeSizesOnly() {
        assertEquals(CoverSize.Small, CoverSize.forPixels(100))
        assertEquals(CoverSize.Small, CoverSize.forPixels(200))
        assertEquals(CoverSize.Medium, CoverSize.forPixels(300))
        assertEquals(CoverSize.Large, CoverSize.forPixels(800))
        assertEquals("https://avatars.yandex.net/get/1/200x200",
            "avatars.yandex.net/get/1/%%".toCoverUrl(CoverSize.Small))
        assertEquals("a bundled logo stays as it is", "android.resource://x/1",
            "android.resource://x/1".toCoverUrl(CoverSize.Large))
        assertTrue("one memory key per size", CoverSize.Small.memoryKey("u") != CoverSize.Large.memoryKey("u"))
    }
}

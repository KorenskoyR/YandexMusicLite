package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.network.StreamVariants
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.StreamQuality

/** How well the music gets through: what the screens say and where the next track comes from. */
enum class LinkState {
    GOOD,
    /** The server answers, but the stream does not keep up. */
    WEAK,
    /** No network, or a network that carries no data (a tunnel). */
    NONE
}

/**
 * Tells the state of the link from what actually happens to the music; signal bars and speed tests say
 * little on the road. Short drops the player's buffer covers never get here. Pure; unit-tested.
 */
internal class LinkTracker(private val now: () -> Long) {
    private var networkPresent = true
    private var serverReachable = true
    private var weakSinceMs: Long? = null

    val state: LinkState
        get() = when {
            !networkPresent || !serverReachable -> LinkState.NONE
            weakSinceMs != null -> LinkState.WEAK
            else -> LinkState.GOOD
        }

    /** Android reports the network appearing or going. A network that came back is given a clean start. */
    fun onNetwork(present: Boolean) {
        if (present && !networkPresent) {
            serverReachable = true
            weakSinceMs = null
        }
        networkPresent = present
    }

    /** The stream stalled or a request failed; [reachable] is what the follow-up check of the server found. */
    fun onTrouble(reachable: Boolean) {
        serverReachable = reachable
        weakSinceMs = if (reachable) now() else null
    }

    /** The periodic check of the server got through after it had not. */
    fun onServerReachable() {
        serverReachable = true
    }

    /** A stream really plays: the server is there and the weak-link mode is over. */
    fun onStreamAudible() {
        serverReachable = true
        weakSinceMs = null
    }

    /** While the link is weak, the stream gets another try once every [WEAK_RETRY_MS]. */
    fun mayTryStream(): Boolean = when (state) {
        LinkState.GOOD -> true
        LinkState.WEAK -> now() - (weakSinceMs ?: 0L) >= WEAK_RETRY_MS
        LinkState.NONE -> false
    }

    companion object {
        const val WEAK_RETRY_MS = 5 * 60 * 1000L
    }
}

/** Why the link's state changed: written to the playback journal. */
enum class LinkReason {
    STREAM_STALLED, REQUEST_FAILED, SERVER_SILENT, NETWORK_GONE, NETWORK_BACK, STREAM_SOUNDS, SERVER_ANSWERED,
    /** The service answers, the server of the streams does not (a network letting only some addresses through). */
    STREAMS_BLOCKED,
    /** A stream broke off with a network error. */
    STREAM_FAILED
}

/**
 * The server gave no link to a track: the track, or the link? A track taken out of the catalogue made the link
 * "weak" for five minutes (1.14.8 reports: one such track in a playlist, and the music went to the copies with the
 * internet in order). Now it is the track's trouble while the server answers, unless several different tracks
 * in a row get no link: that is the link after all. Pure; unit-tested.
 */
internal class UnresolvedTracks(private val now: () -> Long) {
    enum class Verdict { TRACK, LINK }

    private val failed = LinkedHashMap<String, Long>()

    fun onFailed(trackId: String, serverAnswers: Boolean, markedUnavailable: Boolean): Verdict {
        val t = now()
        failed.entries.removeAll { t - it.value > WINDOW_MS }
        failed[trackId] = t
        return when {
            !serverAnswers -> Verdict.LINK
            markedUnavailable -> Verdict.TRACK
            failed.size >= TRACKS_FOR_THE_LINK -> Verdict.LINK
            else -> Verdict.TRACK
        }
    }

    /** A link was given: whatever failed before was not the link. */
    fun onResolved() = failed.clear()

    companion object {
        const val WINDOW_MS = 2 * 60 * 1000L
        const val TRACKS_FOR_THE_LINK = 3
    }
}

/** Where a track that is not downloaded should come from. */
enum class TrackOrigin { STREAM, COPY, UNAVAILABLE }

/** Pure; unit-tested. */
object RoadPlan {
    fun originFor(link: LinkState, mayTryStream: Boolean, hasCopy: Boolean): TrackOrigin = when {
        link == LinkState.NONE -> if (hasCopy) TrackOrigin.COPY else TrackOrigin.UNAVAILABLE
        link == LinkState.WEAK && !mayTryStream && hasCopy -> TrackOrigin.COPY
        else -> TrackOrigin.STREAM
    }

    /**
     * With a good link the stream plays whenever it is better than the copy; an equal stream would only
     * spend the traffic a second time.
     */
    fun copyIsEnough(stream: StreamQuality?, copy: StreamQuality): Boolean =
        stream == null || !StreamVariants.isBetter(stream, copy)
}

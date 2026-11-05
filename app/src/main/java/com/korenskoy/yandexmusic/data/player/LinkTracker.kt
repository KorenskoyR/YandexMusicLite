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

    /** A stream really plays: the weak-link mode is over. */
    fun onStreamAudible() {
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

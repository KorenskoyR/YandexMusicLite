package com.korenskoy.yandexmusic.data.player

/** Where the current queue came from. */
enum class PlaylistSource {
    LIBRARY, VIBE, SEARCH, TRENDS, RADIO, NONE
}

/** Sleep timer: either a moment in [android.os.SystemClock.elapsedRealtime] time or "at the end of this track". */
data class SleepTimerState(val endsAtElapsedMs: Long?, val atEndOfTrack: Boolean)

/** What to do when the stream of the current track fails. */
enum class Recovery {
    /** No network and the track is not on the device: go on with downloaded music. */
    CONTINUE_OFFLINE,
    /** First failure of this track: fetch a fresh link and resume where it stopped (links expire). */
    RETRY,
    /** A station, or several tracks in a row: stop and tell the user. */
    STOP,
    /** This track is broken: the next one. */
    SKIP
}

/** What "next" should do for a queue position. Pure logic, unit-tested in PlaybackQueueTest. */
sealed interface NextAction {
    data class PlayIndex(val index: Int) : NextAction
    /** End of My Wave: fetch more tracks and continue. */
    data object FetchMoreWave : NextAction
    /** Nothing to continue from: fall back to the liked tracks. */
    data object FallbackLibrary : NextAction
}

object PlaybackQueue {
    /** Apple Music behaviour: "previous" after this much playback restarts the current track. */
    const val RESTART_ON_PREV_THRESHOLD_MS = 3_000L
    /** Queue window persisted around the current track to keep the session file small. */
    const val SNAPSHOT_BEFORE = 50
    const val SNAPSHOT_AFTER = 150

    fun nextAction(queueSize: Int, currentIndex: Int, source: PlaylistSource): NextAction = when {
        currentIndex != -1 && currentIndex + 1 < queueSize -> NextAction.PlayIndex(currentIndex + 1)
        source == PlaylistSource.VIBE -> NextAction.FetchMoreWave
        // Regular playlists loop back to the start so playback never silently stops.
        queueSize > 0 -> NextAction.PlayIndex(0)
        else -> NextAction.FallbackLibrary
    }

    /** Index of the previous track, or null when "previous" should restart the current one. */
    fun previousIndex(currentIndex: Int, progressMs: Long): Int? =
        if (progressMs > RESTART_ON_PREV_THRESHOLD_MS || currentIndex <= 0) null else currentIndex - 1

    /**
     * Index of the first item after [currentIndex] (wrapping around, never [currentIndex] itself) that
     * satisfies [accept], or null. Used offline to jump to the next downloaded track.
     */
    fun <T> nextMatching(queue: List<T>, currentIndex: Int, accept: (T) -> Boolean): Int? {
        if (queue.isEmpty()) return null
        for (step in 1..queue.size) {
            val index = Math.floorMod(currentIndex + step, queue.size)
            if (index == currentIndex) return null
            if (accept(queue[index])) return index
        }
        return null
    }

    /** Broken tracks in a row after which playback stops instead of skipping on. */
    const val MAX_FAILED_TRACKS = 3

    /** Pure; unit-tested. [failedTracksInARow] counts this failure too. */
    fun recoveryFor(offline: Boolean, isRadio: Boolean, isDownloaded: Boolean, alreadyRetried: Boolean, failedTracksInARow: Int): Recovery = when {
        offline && !isRadio && !isDownloaded -> Recovery.CONTINUE_OFFLINE
        !alreadyRetried -> Recovery.RETRY
        isRadio || failedTracksInARow >= MAX_FAILED_TRACKS -> Recovery.STOP
        else -> Recovery.SKIP
    }

    /** [list] with the item at [from] moved to [to]; out-of-range positions leave it unchanged. */
    fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** The wave page wants at least this many tracks in its "next up" shelf. */
    const val WAVE_SHELF_MIN = 3

    /**
     * Is the wave page's "next up" shelf short of tracks? While the wave plays it shows the queue after the
     * current track; while it is idle, the saved wave without its first track (that one starts the wave).
     */
    fun waveShelfIsShort(waveActive: Boolean, currentId: String?, queue: List<String>, wave: List<String>): Boolean =
        if (waveActive) {
            val index = queue.indexOf(currentId)
            index < 0 || queue.size - index - 1 < WAVE_SHELF_MIN
        } else {
            wave.size - 1 < WAVE_SHELF_MIN
        }

    fun <T> snapshotWindow(queue: List<T>, currentIndex: Int): List<T> {
        if (currentIndex < 0) return queue.take(SNAPSHOT_AFTER)
        val from = (currentIndex - SNAPSHOT_BEFORE).coerceAtLeast(0)
        val to = (currentIndex + SNAPSHOT_AFTER).coerceAtMost(queue.size)
        return queue.subList(from, to)
    }
}

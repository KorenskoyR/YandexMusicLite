package com.korenskoy.yandexmusic.data.player

/** Where the current queue came from. */
enum class PlaylistSource {
    LIBRARY, VIBE, SEARCH, TRENDS, RADIO, NONE
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

    fun <T> snapshotWindow(queue: List<T>, currentIndex: Int): List<T> {
        if (currentIndex < 0) return queue.take(SNAPSHOT_AFTER)
        val from = (currentIndex - SNAPSHOT_BEFORE).coerceAtLeast(0)
        val to = (currentIndex + SNAPSHOT_AFTER).coerceAtMost(queue.size)
        return queue.subList(from, to)
    }
}

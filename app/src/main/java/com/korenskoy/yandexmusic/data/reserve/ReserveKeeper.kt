package com.korenskoy.yandexmusic.data.reserve

import android.util.Log
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.ResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Fills the road reserve along the way: one track at a time, while the music plays and the link is good.
 * Head units are on only on the road, so waiting for a night on Wi-Fi would never fill it. What comes next
 * in the queue goes first, then more of the wave.
 */
internal class ReserveKeeper(
    private val scope: CoroutineScope,
    private val reserve: RoadReserve,
    private val limitMb: Flow<Int>,
    private val mayFill: Flow<Boolean>,
    private val resolve: suspend (String) -> ResolvedStream?,
    private val streamNeedsTheLine: () -> Boolean,
    private val upcoming: () -> List<TrackDetailsDto>,
    private val moreTracks: () -> List<TrackDetailsDto>,
    private val isDownloaded: (String) -> Boolean,
    private val fetchMore: suspend () -> Boolean,
    private val onStored: (TrackDetailsDto) -> Unit
) {
    private var filling: Job? = null

    fun start() {
        scope.launch {
            limitMb.distinctUntilChanged().collectLatest { mb ->
                // A smaller limit applies at once, except to what is about to play
                reserve.makeRoom(mb.toBytes(), 0L, keepFor(mb))
                if (mb <= 0) reserve.clear()
            }
        }
        scope.launch {
            combine(limitMb, mayFill) { mb, may -> if (may && mb > 0) mb else 0 }
                .distinctUntilChanged()
                .collectLatest { mb ->
                    if (mb > 0) {
                        filling?.cancel()
                        filling = scope.launch { fill(mb.toBytes()) }
                    } else {
                        // Track changes flicker the playing state: a download in progress is worth keeping
                        delay(STOP_GRACE_MS)
                        filling?.cancel()
                    }
                }
        }
    }

    private suspend fun fill(limitBytes: Long) {
        val failed = mutableSetOf<String>()
        var askedForMore = false
        while (true) {
            val track = nextTrack(failed)
            if (track == null) {
                if (!askedForMore && reserve.usedBytes.value + ReservePolicy.AVERAGE_TRACK_BYTES <= limitBytes) {
                    askedForMore = true
                    if (runCatching { fetchMore() }.getOrDefault(false)) continue
                }
                delay(IDLE_MS)
                askedForMore = false
                continue
            }
            if (reserve.freeBytes() < MIN_FREE_BYTES ||
                !reserve.makeRoom(limitBytes, ReservePolicy.AVERAGE_TRACK_BYTES, upcomingIds())
            ) {
                delay(IDLE_MS)
                continue
            }
            val stream = resolve(track.idString)
            val stored = stream != null && reserve.store(track, stream, streamNeedsTheLine)
            if (stored) {
                // A big track can run past the estimate; then it does not stay at the cost of what comes next
                if (!reserve.makeRoom(limitBytes, 0L, upcomingIds())) {
                    reserve.remove(track.idString)
                    failed += track.idString
                }
                onStored(track)
                delay(BETWEEN_TRACKS_MS)
            } else {
                Log.w(TAG, "No copy of ${track.idString} this time")
                failed += track.idString
                delay(AFTER_FAILURE_MS)
            }
        }
    }

    private fun nextTrack(failed: Set<String>): TrackDetailsDto? =
        (upcoming().asSequence() + moreTracks().asSequence()).firstOrNull { track ->
            val id = track.idString
            !track.isRadio && id.isNotEmpty() && id !in failed && !reserve.has(id) && !isDownloaded(id)
        }

    private fun upcomingIds(): Set<String> = upcoming().mapTo(mutableSetOf()) { it.idString }

    private fun keepFor(mb: Int): Set<String> = if (mb > 0) upcomingIds() else emptySet()

    private fun Int.toBytes(): Long = this * 1024L * 1024L

    private companion object {
        const val TAG = "ReserveKeeper"
        const val STOP_GRACE_MS = 10_000L
        const val IDLE_MS = 60_000L
        const val BETWEEN_TRACKS_MS = 1_000L
        const val AFTER_FAILURE_MS = 30_000L
        const val MIN_FREE_BYTES = 1024L * 1024L * 1024L
    }
}

package com.korenskoy.yandexmusic.data.player

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/** The tracks heard lately (most recent last), so the wave and the hits do not repeat them. */
internal class RecentlyPlayed(private val limit: Int) {
    private val ids = ArrayDeque<String>(limit)

    operator fun contains(id: String): Boolean = id in ids

    /** Records [id]; returns the new list to save, or null when nothing changed (radio, already there). */
    fun record(id: String): List<String>? {
        if (id.isEmpty() || id.startsWith("radio_") || id in ids) return null
        ids.addLast(id)
        if (ids.size > limit) ids.removeFirst()
        return ids.toList()
    }

    fun restore(saved: List<String>) {
        ids.clear()
        ids.addAll(saved.takeLast(limit))
    }

    fun clear() = ids.clear()
}

/**
 * "Моя волна": its own list of tracks (saved per account), the preview the wave page shows before the
 * wave is started, and fetching more of it. A fetch already running is joined, never repeated: every
 * caller gets told whether new tracks arrived. New tracks go to [onFresh] as well, so the play queue can
 * take them while the wave plays.
 */
internal class WaveQueue(
    private val api: YandexMusicApi,
    private val cacheManager: PlaylistCacheManager,
    private val preloadManager: TrackPreloadManager,
    private val scope: CoroutineScope,
    private val recent: RecentlyPlayed,
    private val isOffline: () -> Boolean,
    private val userFolder: () -> String,
    /** Tracks the user does not want in the wave (explicit, when asked) never join it. */
    private val keep: (TrackDetailsDto) -> Boolean = { true },
    private val onFresh: (List<TrackDetailsDto>) -> Unit
) {
    private val _tracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val tracks: StateFlow<List<TrackDetailsDto>> = _tracks.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    /** What the wave will play when it is started: its saved list without what was just heard. */
    val preview: StateFlow<List<TrackDetailsDto>> = _tracks
        .map { list -> list.filter { it.idString !in recent }.ifEmpty { list }.take(PREVIEW_SIZE) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val pending = mutableListOf<(Boolean) -> Unit>()

    /** The station the wave comes from: My Wave, or a wave by one track. */
    var station: String = MY_WAVE
        private set

    /** Bumped on every switch: a batch fetched for the station before is thrown away. */
    @Volatile
    private var generation = 0

    /** Starts over on [newStation] with [start] as the list (My Wave's saved list, or nothing). */
    fun switchTo(newStation: String, start: List<TrackDetailsDto> = emptyList()) {
        generation++
        station = newStation
        _tracks.value = start
        // Whoever waited for the old station's batch gets "nothing new" now rather than never
        val waiting = pending.toList()
        pending.clear()
        waiting.forEach { it(false) }
    }

    /** The generation a running fetch belongs to; a fetch for an older one does not hold back a new one. */
    private var fetchingGeneration = -1

    /** The first track not heard lately (or simply the first one). */
    fun firstUnplayed(): TrackDetailsDto? = _tracks.value.firstOrNull { it.idString !in recent } ?: _tracks.value.firstOrNull()

    fun remove(trackId: String) {
        _tracks.value = _tracks.value.filterNot { it.idString == trackId }
    }

    fun clear() {
        _tracks.value = emptyList()
    }

    /** The saved wave of an account, without what was just heard, unless the wave is already loaded. */
    fun restore(saved: List<TrackDetailsDto>) {
        if (saved.isNotEmpty() && _tracks.value.isEmpty()) {
            _tracks.value = saved.filter { it.idString !in recent }.ifEmpty { saved }
        }
    }

    /** Fetches more of the wave; [onFetched] learns whether new tracks were added. */
    fun fetchMore(onFetched: ((Boolean) -> Unit)? = null) {
        if (isOffline()) {
            onFetched?.invoke(false)
            return
        }
        onFetched?.let { pending += it }
        if (_isFetching.value && fetchingGeneration == generation) return // the running fetch will call it back
        _isFetching.value = true
        val fetchingFor = generation
        fetchingGeneration = fetchingFor
        scope.launch {
            var hasNew = false
            try {
                val batch = withContext(Dispatchers.IO) { fetchBatch() }.filter(keep)
                // The station changed meanwhile: these tracks belong to the old one
                if (fetchingFor != generation) return@launch
                val fresh = freshOf(batch, _tracks.value.map { it.idString }.toSet()) { it in recent }
                if (fresh.isNotEmpty()) {
                    _tracks.value = _tracks.value + fresh
                    hasNew = true
                    onFresh(fresh)
                    // Only My Wave is kept between launches; a wave by a track is not
                    if (station == MY_WAVE) cacheManager.saveWaveQueue(userFolder(), _tracks.value)
                    preloadManager.preloadTracks(fresh.take(5).map { it.idString })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching more of the wave", e)
            } finally {
                // An old station's fetch neither ends a newer one nor answers its callers
                if (fetchingFor == generation) {
                    _isFetching.value = false
                    val callbacks = pending.toList()
                    pending.clear()
                    callbacks.forEach { it(hasNew) }
                }
            }
        }
    }

    /** [fetchMore] as a suspend call: true when new tracks were added. */
    suspend fun fetchMoreAwait(): Boolean = suspendCancellableCoroutine { cont ->
        fetchMore { added -> if (cont.isActive) cont.resumeWith(Result.success(added)) }
    }

    private suspend fun fetchBatch(): List<TrackDetailsDto> = supervisorScope {
        // One request continues from the last queued track, the other adds variety
        val lastQueued = _tracks.value.lastOrNull()?.idString
        val from = station
        val continued = async { api.getStationTracks(from, queue = lastQueued).result?.sequence?.mapNotNull { it.track } }
        val fresh = async { api.getStationTracks(from).result?.sequence?.mapNotNull { it.track } }
        (continued.awaitOrEmpty() + fresh.awaitOrEmpty()).distinctBy { it.idString }
    }

    /** One of the two requests failing leaves the other; cancellation is never swallowed. */
    private suspend fun Deferred<List<TrackDetailsDto>?>.awaitOrEmpty(): List<TrackDetailsDto> = try {
        await().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Wave request failed", e)
        emptyList()
    }

    companion object {
        private const val TAG = "WaveQueue"
        const val MY_WAVE = "user:onyourwave"

        fun trackStation(trackId: String) = "track:$trackId"

        /** The "from" the service expects with feedback for [station]. */
        fun feedbackFrom(station: String) = "radio-" + station.replace(':', '-')
        const val PREVIEW_SIZE = 10

        /**
         * Which tracks of a fetched [batch] to add: not queued yet and not heard lately; if every one was heard
         * lately, still the ones not queued. Pure; unit-tested.
         */
        fun freshOf(batch: List<TrackDetailsDto>, queued: Set<String>, heardLately: (String) -> Boolean): List<TrackDetailsDto> =
            batch.filter { it.idString !in queued && !heardLately(it.idString) }
                .ifEmpty { batch.filter { it.idString !in queued } }
    }
}

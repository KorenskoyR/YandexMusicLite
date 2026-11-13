package com.korenskoy.yandexmusic.data.player

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The tracks heard lately (most recent last), so the wave and the hits do not repeat them. */
internal class RecentlyPlayed(private val limit: Int) {
    private val ids = ArrayDeque<String>(limit)

    operator fun contains(id: String): Boolean = id in ids

    /**
     * Records [id] as the one heard last (heard before, it moves to the end: it is fresh again); returns the new
     * list to save, or null when nothing changed (radio, the last one already).
     */
    fun record(id: String): List<String>? {
        if (id.isEmpty() || id.startsWith("radio_") || ids.lastOrNull() == id) return null
        ids.remove(id)
        ids.addLast(id)
        if (ids.size > limit) ids.removeFirst()
        return ids.toList()
    }

    /** How long ago [id] was heard, in tracks (0 — the last one); null — not lately. */
    fun age(id: String): Int? = ids.lastIndexOf(id).takeIf { it >= 0 }?.let { ids.size - 1 - it }

    fun snapshot(): List<String> = ids.toList()

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
    /** After a fetch: how many tracks the station offered and how many of them were heard lately (the journal). */
    private val onGathered: (offered: Int, heard: Int) -> Unit = { _, _ -> },
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

    /** The station's answer each track came in: its feedback names it. The latest few hundred are kept. */
    private val batches = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > BATCHES_KEPT
    }

    fun batchOf(trackId: String): String? = batches[trackId]

    /** The generation the service was told the station started for. */
    private var startedGeneration = -1

    /** True once for each start of the station: the first track the wave plays tells the service it started. */
    fun takeStart(): Boolean {
        if (startedGeneration == generation) return false
        startedGeneration = generation
        return true
    }

    /** The music went elsewhere: coming back to the wave starts the station again. */
    fun leave() {
        startedGeneration = -1
    }

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
                val from = station
                val queued = _tracks.value.map { it.idString }.toSet()
                val gathered = gather(_tracks.value.lastOrNull()?.idString, queued, recent::age) { queue -> ask(from, queue) }
                // The station changed meanwhile: these tracks belong to the old one
                if (fetchingFor != generation) return@launch
                batches.putAll(gathered.batches)
                onGathered(gathered.offered, gathered.heard)
                // Tracks that came in meanwhile (a restore) are not added twice
                val now = _tracks.value.map { it.idString }.toSet()
                val fresh = gathered.tracks.filter { it.idString !in now }
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

    /** One request to [from] (a null [queue]: from the station's start); a failure is null, cancellation is not swallowed. */
    private suspend fun ask(from: String, queue: String?): Answer? = try {
        withContext(Dispatchers.IO) { api.getStationTracks(from, queue = queue) }.result?.let { answer ->
            Answer(answer.sequence.mapNotNull { it.track }.filter(keep), answer.batchId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Wave request failed", e)
        null
    }

    /** One answer of the station: its tracks (the unwanted ones left out) and the batch they came in. */
    internal data class Answer(val tracks: List<TrackDetailsDto>, val batchId: String?)

    /** What a fetch brought: the tracks to add, the batch each offered track came in, the offered and heard counts. */
    internal data class Gathered(val tracks: List<TrackDetailsDto>, val batches: Map<String, String>, val offered: Int, val heard: Int)

    companion object {
        private const val TAG = "WaveQueue"
        const val MY_WAVE = "user:onyourwave"

        fun trackStation(trackId: String) = "track:$trackId"

        /** The "from" the service expects with feedback for [station]. */
        fun feedbackFrom(station: String) = "radio-" + station.replace(':', '-')
        const val PREVIEW_SIZE = 10

        /** At most this many requests make one fetch. */
        const val MAX_ASKS = 3

        /** Only tracks heard lately on offer: this many of them, the ones heard longest ago, so the wave goes on. */
        const val FALLBACK_TAKE = 3

        private const val BATCHES_KEPT = 500

        /**
         * One fetch, apart from the network ([ask] is one request; a null queue asks from the station's start).
         * It goes on from [lastQueued]; when all it offers is queued or was heard lately, it goes on once more from
         * the end of that answer, then asks from the start. Only when every answer is made of such tracks do the ones
         * heard longest ago come, a few. ([heardAge]: how long ago a track was heard, in tracks; null — not lately.)
         * Pure; unit-tested.
         */
        suspend fun gather(
            lastQueued: String?,
            queued: Set<String>,
            heardAge: (String) -> Int?,
            ask: suspend (queue: String?) -> Answer?
        ): Gathered {
            val offered = LinkedHashMap<String, TrackDetailsDto>()
            val batches = HashMap<String, String>()
            var next = lastQueued
            var askedFromStart = false
            var fresh = emptyList<TrackDetailsDto>()
            var step = 0
            // Asking from the start twice would bring the same answer
            fun canAsk() = step < MAX_ASKS && (next != null || !askedFromStart)
            while (fresh.isEmpty() && canAsk()) {
                val queue = next
                if (queue == null) askedFromStart = true
                val answer = ask(queue)
                answer?.tracks?.forEach { track ->
                    offered.putIfAbsent(track.idString, track)
                    answer.batchId?.let { batches.putIfAbsent(track.idString, it) }
                }
                fresh = answer?.tracks.orEmpty().filter { it.idString !in queued && heardAge(it.idString) == null }.distinctBy { it.idString }
                // Once more from the end of this answer, then from the station's start
                next = if (step == 0) answer?.tracks?.lastOrNull()?.idString else null
                step++
            }
            val tracks = fresh.ifEmpty {
                offered.values.filter { it.idString !in queued }
                    .sortedByDescending { heardAge(it.idString) ?: Int.MAX_VALUE }
                    .take(FALLBACK_TAKE)
            }
            return Gathered(tracks, batches, offered.size, offered.keys.count { heardAge(it) != null })
        }
    }
}

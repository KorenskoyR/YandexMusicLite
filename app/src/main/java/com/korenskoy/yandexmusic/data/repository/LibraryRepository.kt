package com.korenskoy.yandexmusic.data.repository

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's library (liked tracks) and the catalog charts, shared by all screens.
 *
 * Every load is single-flight: concurrent callers join the request already running instead of starting
 * another one, so app start produces one sync instead of the 2-4 duplicated loads it used to.
 * Likes are synced incrementally: one light request for the liked IDs and the revision; metadata is
 * fetched only for tracks that are not cached yet.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val cacheManager: PlaylistCacheManager
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Library job failed", e) }
    )

    private val _likedTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val likedTracks: StateFlow<List<TrackDetailsDto>> = _likedTracks.asStateFlow()

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _isSyncingLikes = MutableStateFlow(false)
    val isSyncingLikes: StateFlow<Boolean> = _isSyncingLikes.asStateFlow()
    private val _likesSyncFailed = MutableStateFlow(false)
    /** The last sync of the likes failed (no network, server error); cleared by the next successful one. */
    val likesSyncFailed: StateFlow<Boolean> = _likesSyncFailed.asStateFlow()

    private val _weeklyHits = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val weeklyHits: StateFlow<List<TrackDetailsDto>> = _weeklyHits.asStateFlow()

    private val _trendTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val trendTracks: StateFlow<List<TrackDetailsDto>> = _trendTracks.asStateFlow()

    private val _popularTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val popularTracks: StateFlow<List<TrackDetailsDto>> = _popularTracks.asStateFlow()

    private val likesMutex = Mutex()
    private val pendingMutex = Mutex()
    private var likesSync: Deferred<Boolean>? = null
    private var chartsLoad: Deferred<Unit>? = null
    private var chartsLoadedThisSession = false
    private var loadedFolder: String? = null
    private var hitsPage = 1
    private var trendsPage = 1
    private val loadingMoreHits = java.util.concurrent.atomic.AtomicBoolean(false)
    private val loadingMoreTrends = java.util.concurrent.atomic.AtomicBoolean(false)

    private val activeFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    /** Shows cached data instantly (no network). Safe to call repeatedly; reloads only for a new account. */
    suspend fun loadCached() {
        val folder = activeFolder
        if (loadedFolder == folder) return
        loadedFolder = folder
        val cached = cacheManager.loadLikedTracks(folder)
        _likedTracks.value = cached
        _likedIds.value = cached.map { it.idString }.toSet()
        if (_weeklyHits.value.isEmpty()) _weeklyHits.value = cacheManager.loadWeeklyHits()
        if (_trendTracks.value.isEmpty()) _trendTracks.value = cacheManager.loadTrendTracks()
        if (_popularTracks.value.isEmpty()) _popularTracks.value = cacheManager.loadPopularTracks()
    }

    /**
     * Brings the likes up to date. Joins a sync already in progress.
     * @return true when the liked list changed.
     */
    suspend fun syncLikes(): Boolean {
        val running = likesMutex.withLock {
            likesSync?.takeIf { it.isActive } ?: scope.async { doSyncLikes() }.also { likesSync = it }
        }
        return running.await()
    }

    private suspend fun doSyncLikes(): Boolean {
        if (authRepository.getToken().isNullOrEmpty()) return false
        _isSyncingLikes.value = true
        try {
            loadCached()
            // First what was done without a connection, or the sync would take it back
            flushPendingReactions()
            val folder = activeFolder
            val uid = resolveUid()
            val response = try {
                api.getLikedTracks(uid, richTracks = false)
            } catch (e: Exception) {
                if (uid != "me") api.getLikedTracks("me", richTracks = false) else throw e
            }
            val result = response.result ?: return false
            _likesSyncFailed.value = false
            val revision = result.library?.revision ?: result.revision
            var ids = result.allTracks.map { it.idString }.filter { it.isNotEmpty() }.distinct()

            // Tracks saved before 1.12 lack loudness and the explicit mark: fetched again once
            val metaCurrent = cacheManager.likesMetaIsCurrent(folder)
            // Likes unchanged since the last sync and everything is cached: nothing else to do
            if (metaCurrent && revision != null && revision == cacheManager.loadLikesRevision(folder) &&
                ids.toSet() == _likedIds.value && _likedTracks.value.size == ids.size
            ) {
                Log.d(TAG, "Likes unchanged (revision $revision), ${ids.size} tracks")
                return false
            }

            if (ids.isEmpty()) {
                // Some accounts expose likes only through the "Liked" playlist (kind 3)
                ids = runCatching { extractTrackIds(api.getPlaylistRaw(uid, "3").result) }.getOrDefault(emptyList())
            }

            // A failed refresh keeps what was cached: never fewer details than before
            val previousById = _likedTracks.value.associateBy { it.idString }
            val cachedById = if (metaCurrent) previousById else emptyMap()
            val embedded = result.allTracks.mapNotNull { it.track }.associateBy { it.idString }
            val missing = ids.filter { it !in cachedById && it !in embedded }
            val fetched = fetchMetadata(missing).associateBy { it.idString }
            Log.d(TAG, "Likes changed (revision $revision): ${ids.size} tracks, metadata fetched for ${missing.size}")

            // Keep the server's order (most recent like first)
            val tracks = ids.map { id ->
                cachedById[id] ?: embedded[id] ?: fetched[id] ?: previousById[id]
                    ?: TrackDetailsDto(id = kotlinx.serialization.json.JsonPrimitive(id))
            }
            if (activeFolder != folder) return false // account switched while syncing
            _likedTracks.value = tracks
            _likedIds.value = ids.toSet()
            cacheManager.saveLikedTracks(folder, tracks)
            revision?.let { cacheManager.saveLikesRevision(folder, it) }
            if (!metaCurrent && fetched.size >= missing.size / 2) cacheManager.markLikesMetaCurrent(folder)
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Likes sync failed", e)
            _likesSyncFailed.value = true
            return false
        } finally {
            _isSyncingLikes.value = false
        }
    }

    internal suspend fun fetchMetadata(ids: List<String>): List<TrackDetailsDto> {
        val out = mutableListOf<TrackDetailsDto>()
        for (chunk in ids.chunked(METADATA_CHUNK)) {
            val meta = runCatching { api.getTracksMetadata(chunk).result.orEmpty() }.getOrDefault(emptyList())
                .ifEmpty { runCatching { api.getTracksMetadataQuery(chunk.joinToString(",")).result.orEmpty() }.getOrDefault(emptyList()) }
            out += meta
        }
        return out
    }

    /**
     * The account UID needed by the likes endpoints. Taken from the saved account; asks the server only
     * once for an account that was saved before its UID was known.
     */
    internal suspend fun resolveUid(): String {
        val account = authRepository.getActiveAccount()
        account?.takeIf { it.hasRealUid }?.let { return it.uid }
        val status = runCatching { api.getAccountStatus().result }.getOrNull() ?: return "me"
        val uid = status.uidString.takeIf { it.isNotEmpty() } ?: return "me"
        val token = authRepository.getToken().orEmpty()
        if (token.isNotEmpty()) {
            val avatar = status.avatarUrl ?: runCatching { api.getYandexLoginInfo().avatarUrl }.getOrNull()
            authRepository.saveOrUpdateAccount(
                UserAccount(
                    uid = uid,
                    login = status.account?.login.orEmpty(),
                    displayName = status.displayUserName,
                    token = token,
                    avatarUrl = avatar ?: account?.avatarUrl,
                    hasPlus = status.hasPlus
                )
            )
        }
        return uid
    }

    /** Like/unlike with an optimistic update; reverted if the server call fails. */
    fun setLiked(track: TrackDetailsDto, liked: Boolean, onDone: (Boolean) -> Unit = {}) {
        val id = track.idString
        val previousTracks = _likedTracks.value
        val previousIds = _likedIds.value
        _likedIds.value = if (liked) previousIds + id else previousIds - id
        _likedTracks.value = if (liked) listOf(track) + previousTracks.filter { it.idString != id } else previousTracks.filter { it.idString != id }
        val folder = activeFolder
        scope.launch {
            cacheManager.saveLikedTracks(folder, _likedTracks.value)
            val outcome = runCatching {
                val uid = resolveUid()
                if (liked) api.likeTrack(uid, id) else api.unlikeTrack(uid, id)
            }.onFailure { Log.e(TAG, "Like change failed for $id", it) }
            // No connection: the like stands and is sent later. The server refusing it is another matter
            if (outcome.exceptionOrNull() is java.io.IOException) {
                addPendingReaction(folder, PendingReaction(id, if (liked) PendingReaction.Kind.LIKE else PendingReaction.Kind.UNLIKE))
                onDone(true)
                return@launch
            }
            val ok = outcome.isSuccess
            if (!ok) {
                _likedTracks.value = previousTracks
                _likedIds.value = previousIds
                cacheManager.saveLikedTracks(folder, previousTracks)
            }
            onDone(ok)
        }
    }

    fun dislike(track: TrackDetailsDto) {
        val id = track.idString
        val wasLiked = id in _likedIds.value
        _likedIds.value = _likedIds.value - id
        _likedTracks.value = _likedTracks.value.filter { it.idString != id }
        val folder = activeFolder
        scope.launch {
            cacheManager.saveLikedTracks(folder, _likedTracks.value)
            runCatching {
                val uid = resolveUid()
                if (wasLiked) api.unlikeTrack(uid, id)
                api.dislikeTrack(uid, id)
            }.onFailure {
                Log.e(TAG, "Dislike failed for $id", it)
                if (it is java.io.IOException) addPendingReaction(folder, PendingReaction(id, PendingReaction.Kind.DISLIKE))
            }
        }
    }

    private suspend fun addPendingReaction(folder: String, reaction: PendingReaction) {
        pendingMutex.withLock {
            val queue = cacheManager.loadPendingReactions(folder).mapNotNull(PendingReactions::parse)
            cacheManager.savePendingReactions(folder, PendingReactions.add(queue, reaction).map { it.saved })
        }
    }

    /** Sends what was liked and disliked without a connection, oldest first; what still cannot go stays. */
    suspend fun flushPendingReactions() {
        val folder = activeFolder
        pendingMutex.withLock {
            val queue = cacheManager.loadPendingReactions(folder).mapNotNull(PendingReactions::parse)
            if (queue.isEmpty()) return
            val uid = resolveUid()
            var left = queue
            for (reaction in queue) {
                try {
                    when (reaction.kind) {
                        PendingReaction.Kind.LIKE -> api.likeTrack(uid, reaction.trackId)
                        PendingReaction.Kind.UNLIKE -> api.unlikeTrack(uid, reaction.trackId)
                        PendingReaction.Kind.DISLIKE -> {
                            api.unlikeTrack(uid, reaction.trackId)
                            api.dislikeTrack(uid, reaction.trackId)
                        }
                    }
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "Still no connection for the saved reactions", e)
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The server refused this one: sending it again will not help
                    Log.e(TAG, "Saved reaction refused for ${reaction.trackId}", e)
                }
                left = left - reaction
            }
            cacheManager.savePendingReactions(folder, left.map { it.saved })
        }
    }

    /** Charts for the search screen. Loaded once per app session unless [force]d (pull to refresh). */
    suspend fun refreshCharts(force: Boolean = false) {
        if (chartsLoadedThisSession && !force) return
        val running = likesMutex.withLock {
            chartsLoad?.takeIf { it.isActive } ?: scope.async { doRefreshCharts() }.also { chartsLoad = it }
        }
        running.await()
    }

    private suspend fun doRefreshCharts() = supervisorScope {
        val hits = listOf("чарт", "топ 100").map { q -> async { searchTracks(q, 0) } }.flatMap { it.await() }.distinctBy { it.idString }
        val trends = listOf("новинки", "премьеры").map { q -> async { searchTracks(q, 0) } }.flatMap { it.await() }.distinctBy { it.idString }
        if (hits.isNotEmpty()) {
            _weeklyHits.value = hits
            cacheManager.saveWeeklyHits(hits)
        }
        if (trends.isNotEmpty()) {
            _trendTracks.value = trends
            cacheManager.saveTrendTracks(trends)
        }
        val popular = trends.ifEmpty { hits }
        if (popular.isNotEmpty()) {
            _popularTracks.value = popular
            cacheManager.savePopularTracks(popular)
        }
        hitsPage = 1
        trendsPage = 1
        chartsLoadedThisSession = hits.isNotEmpty() || trends.isNotEmpty()
    }

    private suspend fun searchTracks(query: String, page: Int): List<TrackDetailsDto> =
        runCatching { api.search(query, page = page).result?.tracks?.results.orEmpty() }.getOrDefault(emptyList())

    fun loadMoreHits() = loadMore(loadingMoreHits, HITS_QUERIES, { ++hitsPage }) { more ->
        _weeklyHits.value = (_weeklyHits.value + more).distinctBy { it.idString }
    }

    fun loadMoreTrends() = loadMore(loadingMoreTrends, TRENDS_QUERIES, { ++trendsPage }) { more ->
        val combined = (_trendTracks.value + more).distinctBy { it.idString }
        _trendTracks.value = combined
        _popularTracks.value = combined
    }

    private fun loadMore(
        gate: java.util.concurrent.atomic.AtomicBoolean,
        queries: List<String>,
        nextPage: () -> Int,
        append: (List<TrackDetailsDto>) -> Unit
    ) {
        // Hits and trends have their own gate, so one paging does not block the other.
        if (!gate.compareAndSet(false, true)) return
        val page = nextPage()
        scope.launch {
            try {
                val more = searchTracks(queries[page % queries.size], page / queries.size)
                if (more.isNotEmpty()) append(more)
            } finally {
                gate.set(false)
            }
        }
    }

    /** Account switch, logout or "clear cache": forget in-memory state of the previous account. */
    fun resetForAccountChange() {
        likesSync?.cancel()
        likesSync = null
        loadedFolder = null
        _likedTracks.value = emptyList()
        _likedIds.value = emptySet()
    }

    private fun extractTrackIds(element: kotlinx.serialization.json.JsonElement?): List<String> {
        if (element == null) return emptyList()
        val ids = mutableListOf<String>()
        fun search(el: kotlinx.serialization.json.JsonElement) {
            when (el) {
                is kotlinx.serialization.json.JsonObject -> {
                    val trackObj = el["track"]
                    if (trackObj is kotlinx.serialization.json.JsonObject) {
                        (trackObj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }?.let { ids += it }
                    }
                    val directId = el["id"] ?: el["trackId"]
                    if (directId is kotlinx.serialization.json.JsonPrimitive && directId.content.length > 2 &&
                        directId.content.all { it.isDigit() || it == ':' || it == '-' } &&
                        (el.containsKey("albumId") || el.containsKey("timestamp"))
                    ) {
                        ids += directId.content
                    }
                    el.values.forEach { search(it) }
                }
                is kotlinx.serialization.json.JsonArray -> el.forEach { search(it) }
                else -> Unit
            }
        }
        search(element)
        return ids.distinct()
    }

    private companion object {
        const val TAG = "LibraryRepository"
        const val METADATA_CHUNK = 100
        val HITS_QUERIES = listOf("чарт", "топ 100", "главные хиты", "суперхиты")
        val TRENDS_QUERIES = listOf("новинки", "в тренде", "горячие новинки", "премьеры")
    }
}

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

    private val _weeklyHits = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val weeklyHits: StateFlow<List<TrackDetailsDto>> = _weeklyHits.asStateFlow()

    private val _trendTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val trendTracks: StateFlow<List<TrackDetailsDto>> = _trendTracks.asStateFlow()

    private val _popularTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val popularTracks: StateFlow<List<TrackDetailsDto>> = _popularTracks.asStateFlow()

    private val likesMutex = Mutex()
    private var likesSync: Deferred<Boolean>? = null
    private var chartsLoad: Deferred<Unit>? = null
    private var chartsLoadedThisSession = false
    private var loadedFolder: String? = null
    private var hitsPage = 1
    private var trendsPage = 1
    private var isLoadingMoreCharts = false

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
            val folder = activeFolder
            val uid = resolveUid()
            val response = try {
                api.getLikedTracks(uid, richTracks = false)
            } catch (e: Exception) {
                if (uid != "me") api.getLikedTracks("me", richTracks = false) else throw e
            }
            val result = response.result ?: return false
            val revision = result.library?.revision ?: result.revision
            var ids = result.allTracks.map { it.idString }.filter { it.isNotEmpty() }.distinct()

            // Likes unchanged since the last sync and everything is cached: nothing else to do
            if (revision != null && revision == cacheManager.loadLikesRevision(folder) &&
                ids.toSet() == _likedIds.value && _likedTracks.value.size == ids.size
            ) {
                Log.d(TAG, "Likes unchanged (revision $revision), ${ids.size} tracks")
                return false
            }

            if (ids.isEmpty()) {
                // Some accounts expose likes only through the "Liked" playlist (kind 3)
                ids = runCatching { extractTrackIds(api.getPlaylistRaw(uid, "3").result) }.getOrDefault(emptyList())
            }

            val cachedById = _likedTracks.value.associateBy { it.idString }
            val embedded = result.allTracks.mapNotNull { it.track }.associateBy { it.idString }
            val missing = ids.filter { it !in cachedById && it !in embedded }
            val fetched = fetchMetadata(missing).associateBy { it.idString }
            Log.d(TAG, "Likes changed (revision $revision): ${ids.size} tracks, metadata fetched for ${missing.size}")

            // Keep the server's order (most recent like first)
            val tracks = ids.map { id ->
                cachedById[id] ?: embedded[id] ?: fetched[id]
                    ?: TrackDetailsDto(id = kotlinx.serialization.json.JsonPrimitive(id), title = "Трек $id")
            }
            if (activeFolder != folder) return false // account switched while syncing
            _likedTracks.value = tracks
            _likedIds.value = ids.toSet()
            cacheManager.saveLikedTracks(folder, tracks)
            revision?.let { cacheManager.saveLikesRevision(folder, it) }
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Likes sync failed", e)
            return false
        } finally {
            _isSyncingLikes.value = false
        }
    }

    private suspend fun fetchMetadata(ids: List<String>): List<TrackDetailsDto> {
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
    private suspend fun resolveUid(): String {
        val account = authRepository.getActiveAccount()
        account?.uid?.takeIf { it.isNotBlank() && it != "me" }?.let { return it }
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
                    avatarUrl = avatar ?: account?.avatarUrl
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
            val ok = runCatching {
                val uid = resolveUid()
                if (liked) api.likeTrack(uid, id) else api.unlikeTrack(uid, id)
            }.onFailure { Log.e(TAG, "Like change failed for $id", it) }.isSuccess
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
            }.onFailure { Log.e(TAG, "Dislike failed for $id", it) }
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

    fun loadMoreHits() = loadMore(HITS_QUERIES, { ++hitsPage }) { more ->
        _weeklyHits.value = (_weeklyHits.value + more).distinctBy { it.idString }
    }

    fun loadMoreTrends() = loadMore(TRENDS_QUERIES, { ++trendsPage }) { more ->
        val combined = (_trendTracks.value + more).distinctBy { it.idString }
        _trendTracks.value = combined
        _popularTracks.value = combined
    }

    private fun loadMore(queries: List<String>, nextPage: () -> Int, append: (List<TrackDetailsDto>) -> Unit) {
        if (isLoadingMoreCharts) return
        isLoadingMoreCharts = true
        val page = nextPage()
        scope.launch {
            try {
                val more = searchTracks(queries[page % queries.size], page / queries.size)
                if (more.isNotEmpty()) append(more)
            } finally {
                isLoadingMoreCharts = false
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

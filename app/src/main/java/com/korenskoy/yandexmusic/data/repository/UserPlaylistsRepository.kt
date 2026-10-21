package com.korenskoy.yandexmusic.data.repository

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's own playlists (besides "Liked", which [LibraryRepository] handles). The list and each
 * playlist's tracks are cached per account; tracks are keyed by the playlist revision, so an unchanged
 * playlist opens without network.
 */
@Singleton
class UserPlaylistsRepository @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val cacheManager: PlaylistCacheManager
) {
    private val _playlists = MutableStateFlow<List<UserPlaylistDto>>(emptyList())
    val playlists: StateFlow<List<UserPlaylistDto>> = _playlists.asStateFlow()

    /** Serializes server refreshes and edits, so a refresh never overwrites a list mid-edit. */
    private val mutex = Mutex()
    private var loadedFolder: String? = null

    private val activeFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    /** Shows the cached list instantly; reloads only for a new account. */
    suspend fun loadCached() {
        val folder = activeFolder
        if (loadedFolder == folder) return
        loadedFolder = folder
        _playlists.value = cacheManager.loadUserPlaylists(folder)
    }

    /** @return true when the list was updated from the server. */
    suspend fun refresh(): Boolean = mutex.withLock {
        val folder = activeFolder
        try {
            val list = api.getUserPlaylists(libraryRepository.resolveUid()).result.orEmpty()
                .filter { it.kindString.isNotEmpty() && it.kindString != LIKED_KIND }
            if (activeFolder != folder) return false // account switched meanwhile
            _playlists.value = list
            loadedFolder = folder
            cacheManager.saveUserPlaylists(folder, list)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Playlists refresh failed", e)
            false
        }
    }

    /** Tracks in playlist order. Throws on a network error when nothing is cached for this revision. */
    suspend fun tracks(playlist: UserPlaylistDto): List<TrackDetailsDto> {
        val folder = activeFolder
        val kind = playlist.kindString
        cacheManager.loadPlaylistTracks(folder, kind, playlist.revision).takeIf { it.isNotEmpty() }?.let { return it }

        val full = api.getUserPlaylist(libraryRepository.resolveUid(), kind).result ?: return emptyList()
        val tracks = resolveTracks(folder, full)
        if (tracks.isNotEmpty()) cacheManager.savePlaylistTracks(folder, kind, full.revision, tracks)
        return tracks
    }

    /** Track metadata of a playlist fetched with its track list: cached for that revision, else embedded + fetched. */
    private suspend fun resolveTracks(folder: String, full: UserPlaylistDto): List<TrackDetailsDto> {
        cacheManager.loadPlaylistTracks(folder, full.kindString, full.revision).takeIf { it.isNotEmpty() }?.let { return it }
        val ids = full.tracks.mapNotNull { it.trackId }.distinct()
        val embedded = full.tracks.mapNotNull { it.track }.associateBy { it.idString }
        val fetched = libraryRepository.fetchMetadata(ids.filter { it !in embedded }).associateBy { it.idString }
        return ids.mapNotNull { embedded[it] ?: fetched[it] }
    }

    // region Editing. Each call needs the network and throws on failure; the cache changes only on success.

    suspend fun create(title: String): UserPlaylistDto = mutex.withLock {
        val folder = activeFolder
        val created = api.createPlaylist(libraryRepository.resolveUid(), title.trim()).result
            ?: error("Empty create response")
        if (activeFolder == folder) {
            publish(folder, listOf(created.copy(tracks = emptyList())) + _playlists.value)
        }
        created
    }

    suspend fun rename(playlist: UserPlaylistDto, title: String): Unit = mutex.withLock {
        val folder = activeFolder
        val kind = playlist.kindString
        val renamed = api.renamePlaylist(libraryRepository.resolveUid(), kind, title.trim()).result
        if (activeFolder != folder) return@withLock
        val current = _playlists.value.firstOrNull { it.kindString == kind } ?: playlist
        val updated = current.copy(
            title = renamed?.title ?: title.trim(),
            revision = renamed?.revision?.takeIf { it > 0 } ?: current.revision
        )
        if (updated.revision != current.revision) {
            // Same tracks under the new revision, so the playlist still opens without network
            cacheManager.loadPlaylistTracks(folder, kind, current.revision).takeIf { it.isNotEmpty() }
                ?.let { cacheManager.savePlaylistTracks(folder, kind, updated.revision, it) }
        }
        replace(folder, updated)
    }

    suspend fun delete(playlist: UserPlaylistDto): Unit = mutex.withLock {
        val folder = activeFolder
        val kind = playlist.kindString
        api.deletePlaylist(libraryRepository.resolveUid(), kind)
        if (activeFolder != folder) return@withLock
        publish(folder, _playlists.value.filterNot { it.kindString == kind })
        cacheManager.deletePlaylistTracks(folder, kind)
    }

    /** Adds [track] to the top of [playlist], like the official app. */
    suspend fun addTrack(playlist: UserPlaylistDto, track: TrackDetailsDto): AddTrackResult = mutex.withLock {
        val folder = activeFolder
        val uid = libraryRepository.resolveUid()
        val kind = playlist.kindString
        // The current server version: exact positions and revision, so an edit from another device is not lost
        val fresh = api.getUserPlaylist(uid, kind).result ?: error("Empty playlist response")
        if (fresh.tracks.any { it.trackId == track.idString }) return@withLock AddTrackResult.ALREADY_THERE
        val albumId = track.albumId
            ?: libraryRepository.fetchMetadata(listOf(track.idString)).firstOrNull()?.albumId
            ?: error("No album for track ${track.idString}")
        val before = resolveTracks(folder, fresh)
        val changed = api.changePlaylist(uid, kind, PlaylistDiff.insert(0, track.idString, albumId), fresh.revision).result
        if (activeFolder == folder) applyTrackChange(folder, fresh, changed, listOf(track) + before)
        AddTrackResult.ADDED
    }

    /** Removes every occurrence of [trackId] from [playlist]. */
    suspend fun removeTrack(playlist: UserPlaylistDto, trackId: String): Unit = mutex.withLock {
        val folder = activeFolder
        val uid = libraryRepository.resolveUid()
        val kind = playlist.kindString
        val fresh = api.getUserPlaylist(uid, kind).result ?: error("Empty playlist response")
        val positions = fresh.tracks.indices.filter { fresh.tracks[it].trackId == trackId }
        val after = resolveTracks(folder, fresh).filterNot { it.idString == trackId }
        val changed = if (positions.isEmpty()) null // already gone on the server
        else api.changePlaylist(uid, kind, PlaylistDiff.delete(positions), fresh.revision).result
        if (activeFolder == folder) applyTrackChange(folder, fresh, changed, after)
    }

    /**
     * Puts the playlist's tracks in [newOrder]. [shownOrder] is the order the user started from: if the
     * playlist changed elsewhere meanwhile, nothing is saved and [PlaylistChangedException] is thrown.
     */
    suspend fun reorder(playlist: UserPlaylistDto, shownOrder: List<String>, newOrder: List<String>): Unit = mutex.withLock {
        require(newOrder.sorted() == shownOrder.sorted()) { "A new order must keep the same tracks" }
        val folder = activeFolder
        val uid = libraryRepository.resolveUid()
        val kind = playlist.kindString
        val fresh = api.getUserPlaylist(uid, kind).result ?: error("Empty playlist response")
        val freshIds = fresh.tracks.mapNotNull { it.trackId }
        if (freshIds.distinct() != shownOrder) throw PlaylistChangedException()
        // A track added twice keeps its copies together
        val target = newOrder.flatMap { id -> List(freshIds.count { it == id }) { id } }
        val albums = fresh.tracks.associate { it.trackId to it.albumIdString }
        val missing = albums.filterValues { it == null }.keys.filterNotNull()
        val fetchedAlbums = if (missing.isEmpty()) emptyMap()
        else libraryRepository.fetchMetadata(missing).associate { it.idString to it.albumId }
        val diff = PlaylistDiff.reorder(freshIds, target) { id ->
            albums[id] ?: fetchedAlbums[id] ?: error("No album for track $id")
        }
        if (diff.isEmpty()) return@withLock
        val byId = resolveTracks(folder, fresh).associateBy { it.idString }
        val changed = api.changePlaylist(uid, kind, diff, fresh.revision).result
        if (activeFolder == folder) applyTrackChange(folder, fresh, changed, newOrder.mapNotNull { byId[it] })
    }

    suspend fun setPublic(playlist: UserPlaylistDto, public: Boolean): Unit = mutex.withLock {
        val folder = activeFolder
        val kind = playlist.kindString
        val updated = api.setPlaylistVisibility(libraryRepository.resolveUid(), kind, if (public) "public" else "private").result
        if (activeFolder != folder) return@withLock
        val current = _playlists.value.firstOrNull { it.kindString == kind } ?: playlist
        val revision = updated?.revision?.takeIf { it > 0 } ?: current.revision
        if (revision != current.revision) {
            cacheManager.loadPlaylistTracks(folder, kind, current.revision).takeIf { it.isNotEmpty() }
                ?.let { cacheManager.savePlaylistTracks(folder, kind, revision, it) }
        }
        replace(folder, current.copy(visibility = if (public) "public" else "private", revision = revision))
    }

    /** Stores the edited track list under the new revision and updates the playlist entry. */
    private suspend fun applyTrackChange(folder: String, fresh: UserPlaylistDto, changed: UserPlaylistDto?, tracks: List<TrackDetailsDto>) {
        val kind = fresh.kindString
        val revision = changed?.revision?.takeIf { it > 0 } ?: fresh.revision
        val current = _playlists.value.firstOrNull { it.kindString == kind } ?: fresh
        val updated = current.copy(
            revision = revision,
            trackCount = tracks.size,
            cover = changed?.cover ?: current.cover,
            tracks = emptyList()
        )
        if (tracks.isEmpty()) cacheManager.deletePlaylistTracks(folder, kind)
        else cacheManager.savePlaylistTracks(folder, kind, revision, tracks)
        replace(folder, updated)
    }

    private suspend fun replace(folder: String, playlist: UserPlaylistDto) {
        val list = _playlists.value
        val kind = playlist.kindString
        publish(folder, if (list.any { it.kindString == kind }) list.map { if (it.kindString == kind) playlist else it } else listOf(playlist) + list)
    }

    private suspend fun publish(folder: String, list: List<UserPlaylistDto>) {
        _playlists.value = list
        loadedFolder = folder
        cacheManager.saveUserPlaylists(folder, list)
    }

    // endregion

    fun resetForAccountChange() {
        loadedFolder = null
        _playlists.value = emptyList()
    }

    enum class AddTrackResult { ADDED, ALREADY_THERE }

    /** The playlist changed on another device while the user was editing it. */
    class PlaylistChangedException : Exception("Playlist changed elsewhere")

    private companion object {
        const val TAG = "UserPlaylists"
        /** "Liked" is shown separately as the library itself. */
        const val LIKED_KIND = "3"
    }
}

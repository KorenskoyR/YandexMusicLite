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

    private val refreshMutex = Mutex()
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
    suspend fun refresh(): Boolean = refreshMutex.withLock {
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
        val ids = full.tracks.mapNotNull { item ->
            item.track?.idString ?: (item.id as? kotlinx.serialization.json.JsonPrimitive)?.content
        }.distinct()
        val embedded = full.tracks.mapNotNull { it.track }.associateBy { it.idString }
        val fetched = libraryRepository.fetchMetadata(ids.filter { it !in embedded }).associateBy { it.idString }
        val tracks = ids.mapNotNull { embedded[it] ?: fetched[it] }
        if (tracks.isNotEmpty()) cacheManager.savePlaylistTracks(folder, kind, full.revision, tracks)
        return tracks
    }

    fun resetForAccountChange() {
        loadedFolder = null
        _playlists.value = emptyList()
    }

    private companion object {
        const val TAG = "UserPlaylists"
        /** "Liked" is shown separately as the library itself. */
        const val LIKED_KIND = "3"
    }
}

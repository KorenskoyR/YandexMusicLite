package com.korenskoy.yandexmusic.data.repository

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Albums in the user's collection: marked in the official app, or with the heart on an album page here.
 * A refresh first asks only for the ids (a few bytes an album); the albums themselves are fetched again only
 * when that list changed, so a refresh every few minutes costs next to nothing.
 */
@Singleton
class LikedAlbumsRepository @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val cacheManager: PlaylistCacheManager
) {
    private val _albums = MutableStateFlow<List<AlbumDto>>(emptyList())

    /** Newest first, as in the official app; a heart pressed here shows at once. */
    val albums: StateFlow<List<AlbumDto>> = _albums.asStateFlow()

    /** What the server said last (with the hearts it confirmed); [albums] is this plus the unconfirmed ones. */
    private var server: List<AlbumDto> = emptyList()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val mutex = Mutex()
    private var loadedFolder: String? = null

    private val activeFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    /** Shows the cached list instantly; reloads only for a new account. */
    suspend fun loadCached() {
        val folder = activeFolder
        if (loadedFolder == folder) return
        loadedFolder = folder
        server = cacheManager.loadLikedAlbums(folder)
        show()
    }

    /** @return true when the list changed. A failure keeps what is shown. */
    suspend fun refresh(): Boolean = mutex.withLock {
        val folder = activeFolder
        try {
            val uid = libraryRepository.resolveUid()
            val ids = api.getLikedAlbums(uid, rich = false).result.orEmpty().mapNotNull { it.idString }
            if (activeFolder != folder) return false
            if (loadedFolder == folder && !changed(ids, server)) return false
            val fresh = api.getLikedAlbums(uid, rich = true).result.orEmpty()
                .mapNotNull { liked -> liked.album?.takeIf { it.idString != null }?.copy(volumes = emptyList()) }
                .distinctBy { it.idString }
            if (activeFolder != folder) return false
            publish(folder, fresh)
            true
        } catch (e: IOException) {
            notRefreshed(e)
        } catch (e: HttpException) {
            notRefreshed(e)
        } catch (e: SerializationException) {
            notRefreshed(e)
        }
    }

    private fun notRefreshed(e: Exception): Boolean {
        Log.w(TAG, "Albums not refreshed", e)
        return false
    }

    /**
     * Adds [album] to the collection or takes it out. Shown at once; throws when the server refused, and the
     * screen goes back to what it was.
     */
    suspend fun setLiked(album: AlbumDto, liked: Boolean) {
        val id = album.idString ?: return
        val folder = activeFolder
        val bare = album.copy(volumes = emptyList())
        pending[id] = Pending(bare, liked)
        show()
        var confirmed = false
        try {
            val uid = libraryRepository.resolveUid()
            if (liked) api.likeAlbum(uid, id) else api.unlikeAlbum(uid, id)
            confirmed = true
            mutex.withLock {
                pending.remove(id)
                if (activeFolder == folder) {
                    publish(folder, if (liked) listOf(bare) + server.filterNot { it.idString == id } else server.filterNot { it.idString == id })
                }
            }
        } finally {
            // Refused, failed or cancelled: the heart goes back, and the error reaches the caller
            if (!confirmed) {
                pending.remove(id)
                show()
            }
        }
    }

    fun resetForAccountChange() {
        loadedFolder = null
        server = emptyList()
        pending.clear()
        _albums.value = emptyList()
    }

    private suspend fun publish(folder: String, list: List<AlbumDto>) {
        server = list
        loadedFolder = folder
        show()
        cacheManager.saveLikedAlbums(folder, list)
    }

    private fun show() {
        _albums.value = overlay(server, pending.values.toList())
    }

    /** A heart pressed and not yet confirmed by the server. */
    data class Pending(val album: AlbumDto, val liked: Boolean)

    companion object {
        private const val TAG = "LikedAlbums"

        /** The ids the server gave differ from the albums known: the albums are fetched again. Pure. */
        fun changed(serverIds: List<String>, known: List<AlbumDto>): Boolean = serverIds != known.mapNotNull { it.idString }

        /** The server's list with the unconfirmed hearts applied: a new one on top, a removed one gone. Pure. */
        fun overlay(server: List<AlbumDto>, pending: List<Pending>): List<AlbumDto> {
            if (pending.isEmpty()) return server
            val removed = pending.filter { !it.liked }.mapNotNullTo(mutableSetOf()) { it.album.idString }
            val added = pending.filter { it.liked }.map { it.album }
            val addedIds = added.mapNotNullTo(mutableSetOf()) { it.idString }
            return added + server.filter { it.idString !in removed && it.idString !in addedIds }
        }
    }
}

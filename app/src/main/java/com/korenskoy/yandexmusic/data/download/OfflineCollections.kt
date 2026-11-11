package com.korenskoy.yandexmusic.data.download

import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A playlist or an album kept on the device: its tracks are downloaded, and new ones follow. The tracks
 * themselves are kept here too, so the list shows and plays without a connection. Single tracks downloaded
 * one by one ("Скачать" in the player or a track's menu) are one more such set, [SINGLES_KEY]: the same rules
 * of keeping, moving and cleaning apply to them.
 */
@Serializable
data class OfflineCollection(
    /** "playlist:<owner>:<kind>", "album:<id>" or [SINGLES_KEY]. */
    val key: String,
    val title: String,
    val coverUri: String? = null,
    val tracks: List<TrackDetailsDto> = emptyList()
) {
    val trackIds: List<String> get() = tracks.map { it.idString }
    val isAlbum: Boolean get() = key.startsWith(ALBUM)

    /** The tracks downloaded one by one: listed among the downloads, never as a playlist of its own. */
    val isSingles: Boolean get() = key == SINGLES_KEY

    /** The album's id, or the playlist's kind. */
    val itemId: String get() = key.substringAfterLast(':')

    companion object {
        private const val PLAYLIST = "playlist:"
        private const val ALBUM = "album:"
        const val SINGLES_KEY = "tracks:singles"

        fun keyOf(playlist: UserPlaylistDto) = "$PLAYLIST${playlist.ownerUid ?: "me"}:${playlist.kindString}"
        fun albumKey(albumId: String) = "$ALBUM$albumId"
    }
}

/** One track on the device: not there, on its way (or waiting for a connection), there. */
enum class TrackOfflineState { NONE, DOWNLOADING, DONE }

/** What "Убрать с устройства" did, so the screen can say why a track stays. */
enum class RemoveOutcome { REMOVED, KEPT_BY_COLLECTION, KEPT_BY_AUTO_DOWNLOAD }

/** What a playlist's download button shows. */
sealed interface OfflineState {
    data object None : OfflineState

    /** [done] of [total] are on the device; [currentTrackId] is being fetched now. */
    data class Downloading(val done: Int, val total: Int, val currentTrackId: String?) : OfflineState

    /** Not fetching: [done] of [total] are on the device (fewer when some can't be fetched now), [bytes] they take. */
    data class Done(val done: Int, val total: Int, val bytes: Long) : OfflineState {
        val complete: Boolean get() = done >= total
    }
}

/** The rules of what stays on the device, kept pure so they are tested without Android. */
object OfflineRules {

    /** Tracks of [collection] not on the device yet, each once. */
    fun missing(collection: OfflineCollection, downloaded: Set<String>): List<TrackDetailsDto> =
        collection.tracks.filter { !it.isRadio && it.idString !in downloaded }.distinctBy { it.idString }

    /**
     * "Убрать с устройства" for one track: its file goes unless a kept playlist or album holds it, or liked
     * tracks are downloaded by themselves (it would only come back).
     */
    fun removeOutcome(inKeptCollection: Boolean, liked: Boolean, autoDownloadLiked: Boolean): RemoveOutcome = when {
        inKeptCollection -> RemoveOutcome.KEPT_BY_COLLECTION
        liked && autoDownloadLiked -> RemoveOutcome.KEPT_BY_AUTO_DOWNLOAD
        else -> RemoveOutcome.REMOVED
    }

    /** One track's place on the device, for the player's download button and a track's menu. */
    fun trackState(trackId: String, onDevice: Set<String>, singles: OfflineCollection?): TrackOfflineState = when {
        trackId in onDevice -> TrackOfflineState.DONE
        singles != null && trackId in singles.trackIds -> TrackOfflineState.DOWNLOADING
        else -> TrackOfflineState.NONE
    }

    /** A downloaded track stays while it is liked or in any kept playlist or album. */
    fun kept(collections: List<OfflineCollection>, liked: Set<String>): Set<String> =
        liked + collections.flatMap { it.trackIds }

    /** Of [candidates], the files that nothing keeps any more. */
    fun toDelete(candidates: Collection<String>, stillKept: Set<String>): List<String> =
        candidates.filter { it !in stillKept }.distinct()

    /** "Скачанные": liked tracks first, in the library's order, then the playlists' — only what is on the device. */
    fun downloadedTracks(liked: List<TrackDetailsDto>, collections: List<OfflineCollection>, downloaded: Set<String>): List<TrackDetailsDto> =
        (liked + collections.flatMap { it.tracks }).distinctBy { it.idString }.filter { it.idString in downloaded }

    /** The button's state: nothing kept, still fetching (running work), or done. */
    fun stateOf(collection: OfflineCollection?, running: Boolean, currentTrackId: String?, downloaded: Set<String>, bytes: Long): OfflineState {
        collection ?: return OfflineState.None
        val ids = collection.trackIds.distinct()
        val done = ids.count { it in downloaded }
        return if (running && done < ids.size) OfflineState.Downloading(done, ids.size, currentTrackId) else OfflineState.Done(done, ids.size, bytes)
    }
}

/** The kept playlists and albums, in one small JSON file of the app. */
@Singleton
class OfflineCollections(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "offline_collections.json"))

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(OfflineCollection.serializer())
    private val mutex = Mutex()

    private val _all = MutableStateFlow(read())
    val all: StateFlow<List<OfflineCollection>> = _all.asStateFlow()

    fun get(key: String): OfflineCollection? = _all.value.firstOrNull { it.key == key }

    /** Every track some kept playlist or album holds. */
    fun keptTrackIds(): Set<String> = _all.value.flatMapTo(mutableSetOf()) { it.trackIds }

    /** Adds the collection, or replaces the one with the same key (keeping its place). */
    suspend fun save(collection: OfflineCollection) = mutex.withLock {
        val now = _all.value
        val next = if (now.any { it.key == collection.key }) now.map { if (it.key == collection.key) collection else it } else now + collection
        write(next)
    }

    suspend fun remove(key: String): OfflineCollection? = mutex.withLock {
        val removed = get(key) ?: return@withLock null
        write(_all.value.filter { it.key != key })
        removed
    }

    suspend fun clear() = mutex.withLock { write(emptyList()) }

    private suspend fun write(list: List<OfflineCollection>) {
        _all.value = list
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(serializer, list))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }.onFailure { Log.w(TAG, "Kept playlists not saved", it) }
        }
    }

    private fun read(): List<OfflineCollection> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.onFailure { Log.w(TAG, "Kept playlists not read", it) }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "OfflineCollections"
    }
}

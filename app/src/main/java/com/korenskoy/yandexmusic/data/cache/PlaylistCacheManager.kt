package com.korenskoy.yandexmusic.data.cache

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline copy of the user's lists — likes, playlists, the wave queue, history — and the shared charts,
 * in Room ([CacheDatabase]). Each track's metadata is stored once; a list is rewritten in a single
 * transaction. Lists are per account folder, so accounts never see each other's data.
 *
 * Until 1.6 this was JSON files under `cacheDir/playlists`; they are imported once and removed.
 */
@Singleton
class PlaylistCacheManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val json: Json
) {
    private val database by lazy { CacheDatabase.get(context) }
    private val legacyRoot = File(context.cacheDir, "playlists")
    private val importMutex = Mutex()
    @Volatile
    private var legacyImported = false

    fun sanitizeUserFolder(name: String): String {
        val sanitized = name.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").trim('_')
        return sanitized.ifBlank { "default" }.take(50)
    }

    // --- Per-user lists ---

    suspend fun saveLikedTracks(userFolder: String, tracks: List<TrackDetailsDto>) =
        saveList(sanitizeUserFolder(userFolder), LIKED, tracks)

    suspend fun loadLikedTracks(userFolder: String): List<TrackDetailsDto> =
        loadList(sanitizeUserFolder(userFolder), LIKED)

    /** Server revision of the likes list the cached tracks correspond to (incremental sync). */
    suspend fun saveLikesRevision(userFolder: String, revision: Int) =
        putValue(sanitizeUserFolder(userFolder), LIKES_REVISION, revision.toString())

    suspend fun loadLikesRevision(userFolder: String): Int? =
        getValue(sanitizeUserFolder(userFolder), LIKES_REVISION)?.toIntOrNull()

    suspend fun saveUserPlaylists(userFolder: String, playlists: List<UserPlaylistDto>) =
        putValue(sanitizeUserFolder(userFolder), USER_PLAYLISTS, json.encodeToString(playlists))

    suspend fun loadUserPlaylists(userFolder: String): List<UserPlaylistDto> =
        getValue(sanitizeUserFolder(userFolder), USER_PLAYLISTS)
            ?.let { runCatching { json.decodeFromString<List<UserPlaylistDto>>(it) }.getOrNull() }
            .orEmpty()

    /** Tracks of one playlist, stored with the revision they belong to; an older revision is replaced. */
    suspend fun savePlaylistTracks(userFolder: String, kind: String, revision: Int, tracks: List<TrackDetailsDto>) {
        val owner = sanitizeUserFolder(userFolder)
        io {
            database.withTransaction {
                replaceList(owner, playlistList(kind), tracks)
                dao().putValue(ValueEntity(owner, playlistRevisionKey(kind), revision.toString()))
            }
        }
    }

    /** Empty when nothing is cached for exactly this revision. */
    suspend fun loadPlaylistTracks(userFolder: String, kind: String, revision: Int): List<TrackDetailsDto> {
        val owner = sanitizeUserFolder(userFolder)
        if (getValue(owner, playlistRevisionKey(kind)) != revision.toString()) return emptyList()
        return loadList(owner, playlistList(kind))
    }

    /** Drops every cached revision of a deleted playlist. */
    suspend fun deletePlaylistTracks(userFolder: String, kind: String) {
        val owner = sanitizeUserFolder(userFolder)
        io {
            database.withTransaction {
                dao().deleteList(owner, playlistList(kind))
                dao().deleteValue(owner, playlistRevisionKey(kind))
                dao().deleteOrphanTracks()
            }
        }
    }

    suspend fun saveWaveQueue(userFolder: String, tracks: List<TrackDetailsDto>) =
        saveList(sanitizeUserFolder(userFolder), WAVE, tracks)

    suspend fun loadWaveQueue(userFolder: String): List<TrackDetailsDto> =
        loadList(sanitizeUserFolder(userFolder), WAVE)

    /** Listening history: ids only, the tracks themselves need not be cached. */
    suspend fun saveRecentTrackIds(userFolder: String, trackIds: List<String>) {
        val owner = sanitizeUserFolder(userFolder)
        io {
            database.withTransaction {
                dao().deleteList(owner, RECENT)
                dao().insertItems(trackIds.mapIndexed { i, id -> ListItemEntity(owner, RECENT, i, id) })
            }
        }
    }

    suspend fun loadRecentTrackIds(userFolder: String): List<String> =
        io { dao().loadIds(sanitizeUserFolder(userFolder), RECENT) }.orEmpty()

    suspend fun clearUserCache(userFolder: String): Boolean {
        val owner = sanitizeUserFolder(userFolder)
        return io {
            dao().deleteOwner(owner)
            File(legacyRoot, owner).deleteRecursively()
        } ?: false
    }

    suspend fun clearAllUserCaches(): Boolean = io {
        dao().deleteAll()
        legacyRoot.deleteRecursively()
    } ?: false

    // --- Lists shared by all accounts (Weekly Hits, Trends, Popular) ---

    suspend fun saveWeeklyHits(tracks: List<TrackDetailsDto>) = saveList(CacheDatabase.SHARED, WEEKLY_HITS, tracks)
    suspend fun loadWeeklyHits(): List<TrackDetailsDto> = loadList(CacheDatabase.SHARED, WEEKLY_HITS)

    suspend fun saveTrendTracks(tracks: List<TrackDetailsDto>) = saveList(CacheDatabase.SHARED, TRENDS, tracks)
    suspend fun loadTrendTracks(): List<TrackDetailsDto> = loadList(CacheDatabase.SHARED, TRENDS)

    suspend fun savePopularTracks(tracks: List<TrackDetailsDto>) = saveList(CacheDatabase.SHARED, POPULAR, tracks)
    suspend fun loadPopularTracks(): List<TrackDetailsDto> = loadList(CacheDatabase.SHARED, POPULAR)

    // --- Helpers ---

    private suspend fun saveList(owner: String, list: String, tracks: List<TrackDetailsDto>) {
        io { replaceList(owner, list, tracks) }
    }

    private suspend fun replaceList(owner: String, list: String, tracks: List<TrackDetailsDto>) {
        val entities = tracks.distinctBy { it.idString }.map { TrackEntity(it.idString, json.encodeToString(it)) }
        dao().replaceList(owner, list, entities, tracks.map { it.idString })
    }

    private suspend fun loadList(owner: String, list: String): List<TrackDetailsDto> = io {
        dao().loadTracks(owner, list).mapNotNull { runCatching { json.decodeFromString<TrackDetailsDto>(it) }.getOrNull() }
    }.orEmpty()

    private suspend fun putValue(owner: String, key: String, value: String) {
        io { dao().putValue(ValueEntity(owner, key, value)) }
    }

    private suspend fun getValue(owner: String, key: String): String? = io { dao().getValue(owner, key) }

    private fun dao() = database.dao()

    /** On the IO dispatcher, after the one-time import; a storage error is logged, never thrown. */
    private suspend fun <T> io(block: suspend () -> T): T? = withContext(Dispatchers.IO) {
        try {
            importLegacyFiles()
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cache operation failed", e)
            null
        }
    }

    // --- One-time import of the JSON files written before 1.6 ---

    private suspend fun importLegacyFiles() {
        if (legacyImported) return
        importMutex.withLock {
            if (legacyImported) return
            if (legacyRoot.exists()) {
                legacyRoot.listFiles { f -> f.isDirectory }?.forEach { dir ->
                    runCatching { importFolder(dir) }.onFailure { Log.w(TAG, "Legacy import of ${dir.name} failed", it) }
                }
                legacyRoot.deleteRecursively()
            }
            legacyImported = true
        }
    }

    private suspend fun importFolder(dir: File) {
        fun read(name: String) = File(dir, name).takeIf { it.isFile }?.readText()
        fun tracks(text: String?) = text?.let { runCatching { json.decodeFromString<List<TrackDetailsDto>>(it) }.getOrNull() }

        if (dir.name == "shared") {
            tracks(read("weekly_hits.json"))?.let { replaceList(CacheDatabase.SHARED, WEEKLY_HITS, it) }
            tracks(read("trend_tracks.json"))?.let { replaceList(CacheDatabase.SHARED, TRENDS, it) }
            tracks(read("popular_tracks.json"))?.let { replaceList(CacheDatabase.SHARED, POPULAR, it) }
            return
        }
        val owner = sanitizeUserFolder(dir.name)
        tracks(read("liked_tracks.json"))?.let { replaceList(owner, LIKED, it) }
        read("likes_revision.txt")?.trim()?.toIntOrNull()?.let { dao().putValue(ValueEntity(owner, LIKES_REVISION, it.toString())) }
        read("user_playlists.json")?.let { dao().putValue(ValueEntity(owner, USER_PLAYLISTS, it)) }
        tracks(read("wave_queue.json"))?.let { replaceList(owner, WAVE, it) }
        read("recent_track_ids.json")
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?.let { ids -> dao().insertItems(ids.mapIndexed { i, id -> ListItemEntity(owner, RECENT, i, id) }) }
        dir.listFiles()?.forEach { file ->
            val match = LEGACY_PLAYLIST.matchEntire(file.name) ?: return@forEach
            val (kind, revision) = match.destructured
            tracks(file.readText())?.let {
                replaceList(owner, playlistList(kind), it)
                dao().putValue(ValueEntity(owner, playlistRevisionKey(kind), revision))
            }
        }
    }

    private companion object {
        const val TAG = "PlaylistCache"
        const val LIKED = "liked"
        const val WAVE = "wave"
        const val RECENT = "recent"
        const val WEEKLY_HITS = "weekly_hits"
        const val TRENDS = "trends"
        const val POPULAR = "popular"
        const val LIKES_REVISION = "likes_revision"
        const val USER_PLAYLISTS = "user_playlists"
        val LEGACY_PLAYLIST = Regex("""playlist_(.+)_(\d+)\.json""")

        fun playlistList(kind: String) = "playlist:$kind"
        fun playlistRevisionKey(kind: String) = "playlist_revision:$kind"
    }
}

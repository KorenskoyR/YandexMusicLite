package com.korenskoy.yandexmusic.data.cache

import android.content.Context
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCacheManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val json: Json
) {
    private val rootCacheDir = File(context.cacheDir, "playlists").apply {
        if (!exists()) mkdirs()
    }

    private val sharedCacheDir = File(rootCacheDir, "shared").apply {
        if (!exists()) mkdirs()
    }

    fun sanitizeUserFolder(name: String): String {
        val sanitized = name.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").trim('_')
        return sanitized.ifBlank { "default" }.take(50)
    }

    private fun getUserCacheDir(userFolder: String): File {
        val folder = sanitizeUserFolder(userFolder)
        return File(rootCacheDir, folder).apply {
            if (!exists()) mkdirs()
        }
    }

    // --- Per-User Isolated Methods ---

    suspend fun saveLikedTracks(userFolder: String, tracks: List<TrackDetailsDto>) =
        saveList(getUserCacheDir(userFolder), "liked_tracks.json", tracks)

    suspend fun loadLikedTracks(userFolder: String): List<TrackDetailsDto> = withContext(Dispatchers.IO) {
        val userDir = getUserCacheDir(userFolder)
        val userFile = File(userDir, "liked_tracks.json")
        if (userFile.exists()) {
            loadList(userDir, "liked_tracks.json")
        } else {
            emptyList()
        }
    }

    /** Server revision of the likes list the cached tracks correspond to (incremental sync). */
    suspend fun saveLikesRevision(userFolder: String, revision: Int) = withContext(Dispatchers.IO) {
        runCatching { File(getUserCacheDir(userFolder), "likes_revision.txt").writeText(revision.toString()) }
        Unit
    }

    suspend fun loadLikesRevision(userFolder: String): Int? = withContext(Dispatchers.IO) {
        runCatching { File(getUserCacheDir(userFolder), "likes_revision.txt").readText().trim().toInt() }.getOrNull()
    }

    suspend fun saveUserPlaylists(userFolder: String, playlists: List<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto>) =
        withContext(Dispatchers.IO) {
            runCatching { File(getUserCacheDir(userFolder), "user_playlists.json").writeText(json.encodeToString(playlists)) }
            Unit
        }

    suspend fun loadUserPlaylists(userFolder: String): List<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(getUserCacheDir(userFolder), "user_playlists.json")
                if (file.exists()) json.decodeFromString<List<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto>>(file.readText()) else emptyList()
            }.getOrDefault(emptyList())
        }

    /** Tracks of one playlist, stored with the revision they belong to. */
    suspend fun savePlaylistTracks(userFolder: String, kind: String, revision: Int, tracks: List<TrackDetailsDto>) {
        val dir = getUserCacheDir(userFolder)
        val prefix = "playlist_${sanitizeUserFolder(kind)}_"
        // Older revisions of this playlist are stale
        withContext(Dispatchers.IO) { dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() } }
        saveList(dir, "$prefix$revision.json", tracks)
    }

    suspend fun loadPlaylistTracks(userFolder: String, kind: String, revision: Int): List<TrackDetailsDto> =
        loadList(getUserCacheDir(userFolder), "playlist_${sanitizeUserFolder(kind)}_$revision.json")

    suspend fun saveWaveQueue(userFolder: String, tracks: List<TrackDetailsDto>) =
        saveList(getUserCacheDir(userFolder), "wave_queue.json", tracks)

    suspend fun loadWaveQueue(userFolder: String): List<TrackDetailsDto> =
        loadList(getUserCacheDir(userFolder), "wave_queue.json")

    suspend fun saveRecentTrackIds(userFolder: String, trackIds: List<String>) = withContext(Dispatchers.IO) {
        try {
            val file = File(getUserCacheDir(userFolder), "recent_track_ids.json")
            file.writeText(json.encodeToString(trackIds))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadRecentTrackIds(userFolder: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(getUserCacheDir(userFolder), "recent_track_ids.json")
            if (file.exists()) {
                json.decodeFromString<List<String>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearUserCache(userFolder: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getUserCacheDir(userFolder)
            if (dir.exists()) {
                dir.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllUserCaches(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (rootCacheDir.exists()) {
                rootCacheDir.deleteRecursively()
                rootCacheDir.mkdirs()
                sharedCacheDir.mkdirs()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Shared Playlists (Weekly Hits, Trends, Popular) ---

    suspend fun saveWeeklyHits(tracks: List<TrackDetailsDto>) = saveList(sharedCacheDir, "weekly_hits.json", tracks)
    suspend fun loadWeeklyHits(): List<TrackDetailsDto> = loadList(sharedCacheDir, "weekly_hits.json")

    suspend fun saveTrendTracks(tracks: List<TrackDetailsDto>) = saveList(sharedCacheDir, "trend_tracks.json", tracks)
    suspend fun loadTrendTracks(): List<TrackDetailsDto> = loadList(sharedCacheDir, "trend_tracks.json")

    suspend fun savePopularTracks(tracks: List<TrackDetailsDto>) = saveList(sharedCacheDir, "popular_tracks.json", tracks)
    suspend fun loadPopularTracks(): List<TrackDetailsDto> = loadList(sharedCacheDir, "popular_tracks.json")

    // --- Helpers ---

    private suspend fun saveList(directory: File, fileName: String, tracks: List<TrackDetailsDto>) = withContext(Dispatchers.IO) {
        try {
            val file = File(directory, fileName)
            file.writeText(json.encodeToString(tracks))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadList(directory: File, fileName: String): List<TrackDetailsDto> = withContext(Dispatchers.IO) {
        try {
            val file = File(directory, fileName)
            if (file.exists()) {
                json.decodeFromString<List<TrackDetailsDto>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

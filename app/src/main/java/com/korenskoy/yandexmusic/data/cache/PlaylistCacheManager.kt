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
    private val cacheDir = File(context.cacheDir, "playlists").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveLikedTracks(tracks: List<TrackDetailsDto>) = saveList("liked_tracks.json", tracks)
    suspend fun loadLikedTracks(): List<TrackDetailsDto> = loadList("liked_tracks.json")

    suspend fun saveWeeklyHits(tracks: List<TrackDetailsDto>) = saveList("weekly_hits.json", tracks)
    suspend fun loadWeeklyHits(): List<TrackDetailsDto> = loadList("weekly_hits.json")

    suspend fun saveTrendTracks(tracks: List<TrackDetailsDto>) = saveList("trend_tracks.json", tracks)
    suspend fun loadTrendTracks(): List<TrackDetailsDto> = loadList("trend_tracks.json")

    suspend fun saveWaveQueue(tracks: List<TrackDetailsDto>) = saveList("wave_queue.json", tracks)
    suspend fun loadWaveQueue(): List<TrackDetailsDto> = loadList("wave_queue.json")
    
    suspend fun savePopularTracks(tracks: List<TrackDetailsDto>) = saveList("popular_tracks.json", tracks)
    suspend fun loadPopularTracks(): List<TrackDetailsDto> = loadList("popular_tracks.json")

    suspend fun saveRecentTrackIds(trackIds: List<String>) = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "recent_track_ids.json")
            file.writeText(json.encodeToString(trackIds))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadRecentTrackIds(): List<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "recent_track_ids.json")
            if (file.exists()) {
                json.decodeFromString<List<String>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveList(fileName: String, tracks: List<TrackDetailsDto>) = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            file.writeText(json.encodeToString(tracks))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadList(fileName: String): List<TrackDetailsDto> = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
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

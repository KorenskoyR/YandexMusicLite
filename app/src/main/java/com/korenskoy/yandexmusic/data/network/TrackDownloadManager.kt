package com.korenskoy.yandexmusic.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgressInfo(
    val current: Int,
    val total: Int,
    val currentTrackTitle: String = "",
    val currentArtistName: String = "",
    val percent: Float = if (total > 0) current.toFloat() / total else 0f
)

@Singleton
class TrackDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val tracksDir = File(context.filesDir, "tracks").apply {
        if (!exists()) mkdirs()
    }

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds = _downloadedTrackIds.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow<Long>(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgressInfo?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    init {
        updateCacheStats()
    }

    private fun updateCacheStats() {
        val files = tracksDir.listFiles()?.filter { it.extension == "mp3" && it.length() > 0 } ?: emptyList()
        _downloadedTrackIds.value = files.map { it.nameWithoutExtension }.toSet()
        _cacheSizeBytes.value = files.sumOf { it.length() }
    }

    fun setDownloadProgress(progress: DownloadProgressInfo?) {
        _downloadProgress.value = progress
    }

    fun getLocalTrackFile(trackId: String): File? {
        val file = File(tracksDir, "$trackId.mp3")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        return getLocalTrackFile(trackId) != null
    }

    /** Main-thread friendly check against the in-memory index (no disk access). */
    fun isKnownDownloaded(trackId: String): Boolean = trackId in _downloadedTrackIds.value

    suspend fun downloadTrack(trackId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        if (isTrackDownloaded(trackId)) return@withContext true

        val file = File(tracksDir, "$trackId.mp3")
        val tempFile = File(tracksDir, "$trackId.tmp")
        
        try {
            val request = Request.Builder().url(url).build()
            val isSuccess = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body
                
                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().use { inputStream ->
                        inputStream.copyTo(fos)
                    }
                }
                true
            }
            
            if (!isSuccess) {
                tempFile.delete()
                return@withContext false
            }
            
            // Rename after successful download
            if (file.exists()) file.delete()
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            updateCacheStats()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            return@withContext false
        }
    }
    
    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        val file = File(tracksDir, "$trackId.mp3")
        if (file.exists()) {
            file.delete()
            updateCacheStats()
        }
    }
    
    suspend fun clearMusicCache() = withContext(Dispatchers.IO) {
        tracksDir.listFiles()?.forEach { it.delete() }
        updateCacheStats()
    }
}


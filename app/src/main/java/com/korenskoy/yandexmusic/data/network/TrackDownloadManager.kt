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
        // Remove leftover temp files from a download the previous process did not finish.
        tracksDir.listFiles()?.filter { it.extension == "tmp" }?.forEach { it.delete() }
        updateCacheStats()
    }

    @Volatile
    private var savedQualities: Map<String, TrackPreloadManager.StreamQuality> = emptyMap()

    private fun updateCacheStats() {
        val all = tracksDir.listFiles()?.toList().orEmpty()
        val files = all.filter { it.extension == "mp3" && it.length() > 0 }
        savedQualities = all.filter { it.extension == "quality" }.mapNotNull { file ->
            runCatching {
                val (codec, kbps) = file.readText().split(':')
                file.nameWithoutExtension to TrackPreloadManager.StreamQuality(codec, kbps.trim().toInt())
            }.getOrNull()
        }.toMap()
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

    /** Codec and bitrate a downloaded file was saved in; null for files from before 1.6 (the best MP3 then). */
    fun downloadedQuality(trackId: String): TrackPreloadManager.StreamQuality? = savedQualities[trackId]

    /**
     * Saves the track for offline listening. With [replace] an existing copy is swapped for the new one
     * only once the new file is complete (a better quality), so the track stays playable meanwhile.
     * The file keeps the `.mp3` name whatever the codec: the player detects the format from its content.
     */
    suspend fun downloadTrack(
        trackId: String,
        url: String,
        quality: TrackPreloadManager.StreamQuality? = null,
        replace: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!replace && isTrackDownloaded(trackId)) return@withContext true

        val file = File(tracksDir, "$trackId.mp3")
        val tempFile = File(tracksDir, "$trackId.tmp")

        val encrawKey = if (url.contains("#encraw_key=")) {
            url.substringAfter("#encraw_key=").takeIf { it.isNotBlank() }
        } else null
        val cleanUrl = if (encrawKey != null) url.substringBefore("#encraw_key=") else url
        
        try {
            val request = Request.Builder().url(cleanUrl).build()
            val isSuccess = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body
                
                FileOutputStream(tempFile).use { fos ->
                    val rawStream = body.byteStream()
                    val inputStream = if (encrawKey != null) EncrawDecryptor.wrapInputStream(rawStream, encrawKey) else rawStream
                    inputStream.use { input ->
                        input.copyTo(fos)
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
            val qualityFile = File(tracksDir, "$trackId.quality")
            if (quality != null) qualityFile.writeText("${quality.codec}:${quality.bitrateKbps}") else qualityFile.delete()
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
        File(tracksDir, "$trackId.quality").delete()
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


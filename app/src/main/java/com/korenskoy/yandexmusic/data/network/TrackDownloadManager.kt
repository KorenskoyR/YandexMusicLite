package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.download.CardStorage
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
    private val okHttpClient: OkHttpClient,
    /** Null in tests that do not need the card. */
    private val card: CardStorage? = null
) {
    private val tracksDir = File(context.filesDir, "tracks").apply {
        if (!exists()) mkdirs()
    }

    /** The card's folder while a card is in; its files are encrypted ("<id>.enc"). */
    private fun cardDir(): File? = card?.cardTracksDir()

    private fun internalFile(trackId: String) = File(tracksDir, "$trackId.mp3")
    private fun cardFile(dir: File, trackId: String) = File(dir, "$trackId.${CardStorage.EXTENSION}")

    /** True when new downloads go to the card: chosen in the settings, and the card is in and usable. */
    private fun downloadsToCard(): Boolean = card?.wanted?.value == true && card.cardUsable

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds = _downloadedTrackIds.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow<Long>(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgressInfo?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    // Before init: declared after it, this empty value would overwrite what init reads from the disk
    @Volatile
    private var savedQualities: Map<String, TrackPreloadManager.StreamQuality> = emptyMap()

    init {
        // Remove leftover temp files from a download the previous process did not finish.
        tracksDir.listFiles()?.filter { it.extension == "tmp" }?.forEach { it.delete() }
        updateCacheStats()
    }

    /** Re-reads both places, for instance after the card was put in or taken out. */
    fun refresh() = updateCacheStats()

    private fun updateCacheStats() {
        val internal = tracksDir.listFiles()?.toList().orEmpty()
        val onCard = cardDir()?.listFiles()?.toList().orEmpty()
        val all = internal + onCard
        val files = internal.filter { it.extension == "mp3" && it.length() > 0 } +
            onCard.filter { it.extension == CardStorage.EXTENSION && it.length() > 0 }
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

    /** The internal file, or the encrypted one on the card while the card is in. */
    fun getLocalTrackFile(trackId: String): File? {
        val file = internalFile(trackId)
        if (file.exists() && file.length() > 0) return file
        val onCard = cardDir()?.let { cardFile(it, trackId) } ?: return null
        return if (onCard.exists() && onCard.length() > 0) onCard else null
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

        val toCard = downloadsToCard()
        val key = if (toCard) card?.key() else null
        val dir = if (toCard && key != null) cardDir() ?: tracksDir else tracksDir
        val encrypted = dir != tracksDir
        val file = if (encrypted) cardFile(dir, trackId) else internalFile(trackId)
        val tempFile = File(dir, "$trackId.tmp")

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
                    // On the card the file is written encrypted; the stream itself is untouched
                    val target = if (encrypted && key != null) CardStorage.encrypting(key, fos) else fos
                    inputStream.use { input ->
                        target.use { out -> input.copyTo(out) }
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
            val qualityFile = File(dir, "$trackId.quality")
            if (quality != null) qualityFile.writeText("${quality.codec}:${quality.bitrateKbps}") else qualityFile.delete()
            // A replaced copy in the other place goes, so a track is never kept twice
            removeOtherCopy(trackId, keep = file)
            updateCacheStats()
            return@withContext true
        } catch (e: kotlinx.coroutines.CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            android.util.Log.w("TrackDownloadManager", "Download failed", e)
            tempFile.delete()
            return@withContext false
        }
    }

    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        var removed = false
        for (dir in listOfNotNull(tracksDir, cardDir())) {
            File(dir, "$trackId.quality").delete()
            for (file in listOf(internalFile(trackId).takeIf { dir == tracksDir }, cardFile(dir, trackId).takeIf { dir != tracksDir })) {
                if (file != null && file.exists()) removed = file.delete() || removed
            }
        }
        if (removed) updateCacheStats()
    }

    suspend fun clearMusicCache() = withContext(Dispatchers.IO) {
        tracksDir.listFiles()?.forEach { it.delete() }
        cardDir()?.listFiles()?.forEach { it.delete() }
        updateCacheStats()
    }

    private fun removeOtherCopy(trackId: String, keep: File) {
        val other = if (keep.extension == CardStorage.EXTENSION) internalFile(trackId) else cardDir()?.let { cardFile(it, trackId) }
        if (other != null && other.exists()) {
            other.delete()
            File(other.parentFile, "$trackId.quality").takeIf { it.parentFile != keep.parentFile }?.delete()
        }
    }

    /**
     * Moves every downloaded track to the card (encrypting) or back to the internal storage (decrypting),
     * one at a time: each track is complete in its new place before the old copy goes, so stopping half-way
     * loses nothing. [onProgress] gets (moved, total). Returns false when it could not finish.
     */
    suspend fun moveAll(toCard: Boolean, onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val cardTracks = cardDir() ?: return@withContext false
        val key = card?.key() ?: return@withContext false
        val sources = if (toCard) {
            tracksDir.listFiles { f -> f.extension == "mp3" && f.length() > 0 }.orEmpty()
        } else {
            cardTracks.listFiles { f -> f.extension == CardStorage.EXTENSION && f.length() > 0 }.orEmpty()
        }
        var moved = 0
        onProgress(0, sources.size)
        for (source in sources) {
            val id = source.nameWithoutExtension
            val targetDir = if (toCard) cardTracks else tracksDir
            val target = if (toCard) cardFile(cardTracks, id) else internalFile(id)
            val temp = File(targetDir, "$id.tmp")
            try {
                if (toCard) {
                    source.inputStream().use { input -> FileOutputStream(temp).use { fos -> CardStorage.encrypting(key, fos).use { input.copyTo(it) } } }
                } else {
                    CardStorage.decryptingStream(key, source).use { input -> FileOutputStream(temp).use { input.copyTo(it) } }
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                val quality = File(source.parentFile, "$id.quality")
                if (quality.exists()) quality.copyTo(File(targetDir, "$id.quality"), overwrite = true).also { quality.delete() }
                source.delete()
            } catch (e: kotlinx.coroutines.CancellationException) {
                temp.delete()
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TrackDownloadManager", "Move of $id failed", e)
                temp.delete()
                updateCacheStats()
                return@withContext false
            }
            moved++
            onProgress(moved, sources.size)
            if (moved % 10 == 0) updateCacheStats()
        }
        updateCacheStats()
        true
    }
}


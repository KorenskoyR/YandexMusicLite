package com.korenskoy.yandexmusic.data.network

import java.io.IOException
import android.net.Uri
import android.util.Log
import com.korenskoy.yandexmusic.data.download.UsbStorage
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
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
    private val card: CardStorage? = null,
    /** Null in tests that do not need a flash drive. */
    private val usb: UsbStorage? = null
) {
    /** Where a downloaded track lives. */
    enum class Place { INTERNAL, CARD, USB }

    private val tracksDir = File(context.filesDir, "tracks").apply {
        if (!exists()) mkdirs()
    }

    /** The card's folder while a card is in; its files are encrypted ("<id>.enc"). */
    private fun cardDir(): File? = card?.cardTracksDir()

    private fun internalFile(trackId: String) = File(tracksDir, "$trackId.mp3")
    private fun cardFile(dir: File, trackId: String) = File(dir, "$trackId.${CardStorage.EXTENSION}")

    /** True when new downloads go to the card: chosen in the settings, and the card is in and usable. */
    private fun downloadsToCard(): Boolean = usb?.wanted?.value != true && card?.wanted?.value == true && card.cardUsable

    /** True when new downloads go to the flash drive: chosen, the drive is in, and the key is there. */
    private fun downloadsToUsb(): Boolean = usb?.wanted?.value == true && card?.key() != null && usb.present

    /** Tracks on the flash drive by id, as last listed ("<id>.enc"). */
    @Volatile
    private var usbFiles: Map<String, UsbStorage.Entry> = emptyMap()

    private fun usbName(trackId: String) = "$trackId.${CardStorage.EXTENSION}"

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
        usbFiles = usb?.files().orEmpty()
            .filter { (name, entry) -> name.endsWith(".${CardStorage.EXTENSION}") && entry.size > 0 }
            .mapKeys { (name, _) -> name.removeSuffix(".${CardStorage.EXTENSION}") }
        savedQualities = all.filter { it.extension == "quality" }.mapNotNull { file ->
            runCatching {
                val (codec, kbps) = file.readText().split(':')
                file.nameWithoutExtension to TrackPreloadManager.StreamQuality(codec, kbps.trim().toInt())
            }.getOrNull()
        }.toMap()
        _downloadedTrackIds.value = files.map { it.nameWithoutExtension }.toSet() + usbFiles.keys
        _cacheSizeBytes.value = files.sumOf { it.length() } + usbFiles.values.sumOf { it.size }
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

    /** How much the files of [trackIds] take on the device (disk access: call off the main thread). */
    fun sizeOf(trackIds: Collection<String>): Long = trackIds.distinct().sumOf { getLocalTrackFile(it)?.length() ?: usbFiles[it]?.size ?: 0L }

    fun isTrackDownloaded(trackId: String): Boolean {
        return getLocalTrackFile(trackId) != null || trackId in usbFiles
    }

    /**
     * What the player opens for a downloaded track: the file, or the document on the flash drive while the
     * drive is in. Disk and system access: call off the main thread.
     */
    fun localTrackUri(trackId: String): String? {
        getLocalTrackFile(trackId)?.let { return Uri.fromFile(it).toString() }
        val onUsb = usbFiles[trackId]?.takeIf { usb?.present == true }
        if (onUsb != null) return onUsb.uri.toString()
        // Listed as downloaded, but not there: the card or the drive went without the system saying so.
        // The list is read again, so the track counts as not downloaded and plays from the network.
        if (trackId in _downloadedTrackIds.value) updateCacheStats()
        return null
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
    ): Boolean {
        // One download per track: several jobs may want it at once (all liked, the like just given, a playlist,
        // the quality upgrade), and they would write the same temporary file together. The others wait and
        // find the finished file.
        // The locks stay in the map: dropping one while a job is about to take it would let two in again
        return trackLocks.getOrPut(trackId) { Mutex() }.withLock { downloadNow(trackId, url, quality, replace) }
    }

    private val trackLocks = ConcurrentHashMap<String, Mutex>()

    private suspend fun downloadNow(
        trackId: String,
        url: String,
        quality: TrackPreloadManager.StreamQuality?,
        replace: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!replace && isTrackDownloaded(trackId)) return@withContext true

        val toUsb = downloadsToUsb()
        val toCard = !toUsb && downloadsToCard()
        val key = if (toCard || toUsb) card?.key() else null
        val dir = if (toCard && key != null) cardDir() ?: tracksDir else tracksDir
        val encrypted = dir != tracksDir || (toUsb && key != null)
        val file = if (dir != tracksDir) cardFile(dir, trackId) else internalFile(trackId)
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

            if (toUsb && key != null) {
                // Across to the flash drive; the encrypted temp file goes either way
                val written = usb?.put(usbName(trackId)) { out -> tempFile.inputStream().use { it.copyTo(out) } } == true
                tempFile.delete()
                if (!written) return@withContext false
                // Its quality is noted in the app's own folder; the copies elsewhere go
                val qualityFile = File(tracksDir, "$trackId.quality")
                if (quality != null) qualityFile.writeText("${quality.codec}:${quality.bitrateKbps}") else qualityFile.delete()
                internalFile(trackId).delete()
                cardDir()?.let { cardFile(it, trackId).delete(); File(it, "$trackId.quality").delete() }
                updateCacheStats()
                return@withContext true
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
            usbFiles[trackId]?.let { usb?.delete(it.uri) }
            updateCacheStats()
            return@withContext true
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            Log.w("TrackDownloadManager", "Download failed", e)
            tempFile.delete()
            return@withContext false
        }
    }

    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        var removed = usbFiles[trackId]?.let { usb?.delete(it.uri) } == true
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
        usbFiles.values.forEach { usb?.delete(it.uri) }
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
     * Moves every downloaded track to [target]: the internal storage (plain), the card or the flash drive
     * (encrypted). One track at a time, each complete in its new place before the old copy goes, so stopping
     * half-way loses nothing. [onProgress] gets (moved, total). Returns false when it could not finish.
     */
    suspend fun moveAll(target: Place, onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val key = card?.key()
        if (target != Place.INTERNAL && key == null) return@withContext false
        val cardTracks = cardDir()
        if (target == Place.CARD && cardTracks == null) return@withContext false
        if (target == Place.USB && usb?.present != true) return@withContext false
        updateCacheStats()
        // Every copy that is not in the target place yet
        val sources = buildList {
            if (target != Place.INTERNAL) tracksDir.listFiles { f -> f.extension == "mp3" && f.length() > 0 }.orEmpty().forEach { add(it.nameWithoutExtension to Place.INTERNAL) }
            if (target != Place.CARD && cardTracks != null) cardTracks.listFiles { f -> f.extension == CardStorage.EXTENSION && f.length() > 0 }.orEmpty().forEach { add(it.nameWithoutExtension to Place.CARD) }
            if (target != Place.USB && usb?.present == true) usbFiles.keys.forEach { add(it to Place.USB) }
        }
        var moved = 0
        onProgress(0, sources.size)
        for ((id, from) in sources) {
            val temp = File(if (target == Place.CARD) cardTracks!! else tracksDir, "$id.tmp")
            try {
                // The plain sound, wherever it is now
                val plain: () -> InputStream = when (from) {
                    Place.INTERNAL -> { -> internalFile(id).inputStream() }
                    Place.CARD -> { -> CardStorage.decryptingStream(key!!, cardFile(cardTracks!!, id)) }
                    Place.USB -> { -> CardStorage.decryptingStream(key!!, card!!.openSeekable(usbFiles.getValue(id).uri)) }
                }
                plain().use { input ->
                    FileOutputStream(temp).use { fos ->
                        if (target == Place.INTERNAL) input.copyTo(fos) else CardStorage.encrypting(key!!, fos).use { input.copyTo(it) }
                    }
                }
                when (target) {
                    Place.INTERNAL, Place.CARD -> {
                        val dest = if (target == Place.INTERNAL) internalFile(id) else cardFile(cardTracks!!, id)
                        if (dest.exists()) dest.delete()
                        if (!temp.renameTo(dest)) {
                            temp.copyTo(dest, overwrite = true)
                            temp.delete()
                        }
                    }
                    Place.USB -> {
                        val written = usb!!.put(usbName(id)) { out -> temp.inputStream().use { it.copyTo(out) } }
                        temp.delete()
                        if (!written) error("The flash drive refused $id")
                    }
                }
                // The quality note: on the card beside a card copy, otherwise in the app's own folder
                val qualityDir = if (target == Place.CARD) cardTracks!! else tracksDir
                val oldQualityDir = if (from == Place.CARD) cardTracks!! else tracksDir
                if (qualityDir != oldQualityDir) {
                    val quality = File(oldQualityDir, "$id.quality")
                    if (quality.exists()) quality.copyTo(File(qualityDir, "$id.quality"), overwrite = true).also { quality.delete() }
                }
                when (from) {
                    Place.INTERNAL -> internalFile(id).delete()
                    Place.CARD -> cardFile(cardTracks!!, id).delete()
                    Place.USB -> usb?.delete(usbFiles.getValue(id).uri)
                }
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (e: Exception) {
                Log.w("TrackDownloadManager", "Move of $id failed", e)
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

    /** The two-place move of before: to the card or back to the internal storage. */
    /**
     * Moves the encrypted copies (and their quality notes) from one drive's folder to another's: the key is the
     * same, so the files go as they are. False when something could not be moved (the drive was taken out).
     */
    suspend fun moveCardFiles(from: File, to: File, onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val files = from.listFiles { f -> f.extension == CardStorage.EXTENSION || f.extension == "quality" }.orEmpty()
        var allMoved = true
        onProgress(0, files.size)
        files.forEachIndexed { index, file ->
            val dest = File(to, file.name)
            try {
                file.copyTo(dest, overwrite = true)
                file.delete()
            } catch (e: IOException) {
                Log.w("TrackDownloadManager", "Move of ${file.name} between drives failed", e)
                allMoved = false
            }
            onProgress(index + 1, files.size)
        }
        refresh()
        allMoved
    }

    suspend fun moveAll(toCard: Boolean, onProgress: (Int, Int) -> Unit): Boolean =
        moveAll(if (toCard) Place.CARD else Place.INTERNAL, onProgress)

}


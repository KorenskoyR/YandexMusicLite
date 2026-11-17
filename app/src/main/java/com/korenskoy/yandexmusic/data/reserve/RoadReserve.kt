package com.korenskoy.yandexmusic.data.reserve

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import com.korenskoy.yandexmusic.data.download.CardStorage
import android.annotation.SuppressLint
import kotlinx.coroutines.CompletableDeferred
import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.ResolvedStream
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.StreamQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ReserveEntry(
    val track: TrackDetailsDto,
    val sizeBytes: Long,
    val codec: String,
    val bitrateKbps: Int,
    val addedAtMs: Long,
    val playedAtMs: Long? = null
) {
    val id: String get() = track.idString
}

/** What to throw out and what to play from the road reserve. Pure; unit-tested. */
object ReservePolicy {
    /** A track at 192 kbit/s; a reserve size in the settings is explained in these. */
    const val AVERAGE_TRACK_BYTES = 5L * 1024 * 1024

    /**
     * What to remove so that [incomingBytes] fit into [limitBytes]: what was played long ago first, then
     * tracks that never played and left the queue. Null when even that is not enough.
     */
    fun toEvict(entries: Collection<ReserveEntry>, limitBytes: Long, incomingBytes: Long, keep: Set<String>): List<String>? {
        var excess = entries.sumOf { it.sizeBytes } + incomingBytes - limitBytes
        if (excess <= 0) return emptyList()
        val evicted = mutableListOf<String>()
        val order = entries.filter { it.id !in keep }
            .sortedWith(compareBy({ it.playedAtMs == null }, { it.playedAtMs ?: it.addedAtMs }))
        for (entry in order) {
            if (excess <= 0) break
            evicted += entry.id
            excess -= entry.sizeBytes
        }
        return if (excess > 0) null else evicted
    }

    /** The reserve in the order to play it with no signal: what has not been heard first, then the longest ago. */
    fun playOrder(entries: Collection<ReserveEntry>, heardLately: (String) -> Boolean): List<ReserveEntry> =
        entries.sortedWith(compareBy({ heardLately(it.id) || it.playedAtMs != null }, { it.playedAtMs ?: it.addedAtMs }))
}

/** Where the reserve lives: a folder, and the key its files are encrypted with (null: plain, the phone's own cache). */
class ReservePlace(val dir: File, val key: ByteArray? = null)

/**
 * The road reserve: tracks saved ahead while the music plays, to fall back on when the link is weak or
 * gone. It lives in the app's cache, apart from the user's downloads, and rolls over by itself — or, chosen in the
 * settings, encrypted on the SD card or the flash drive of the downloads (a head unit with little memory).
 *
 * It only ever holds the ordinary stream the service offers this app, so its size stays predictable.
 */
@Singleton
class RoadReserve(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    /** Wants the reserve on the drive. */
    onDrive: Flow<Boolean> = flowOf(false),
    /** The reserve's folder on the drive with its key; null — no drive to write to (taken out, no key). */
    private val drivePlace: () -> ReservePlace? = { null }
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        json: Json,
        settings: SettingsRepository,
        card: CardStorage
    ) : this(context, okHttpClient, json, settings.roadReserveOnDrive, {
        val root = card.cardRoot()
        val key = card.key()
        if (root != null && key != null) ReservePlace(File(root, "road_reserve"), key) else null
    })

    private val internal = ReservePlace(File(context.cacheDir, "road_reserve"))

    @Volatile
    private var place = internal
    private val dir: File get() = place.dir
    private val indexFile: File get() = File(dir, "index.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<Map<String, ReserveEntry>>(emptyMap())
    val entries: StateFlow<Map<String, ReserveEntry>> = _entries.asStateFlow()

    private val _usedBytes = MutableStateFlow(0L)
    val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()

    /** Nothing is written before the index is read: reading it clears files the index does not know. */
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                mutex.withLock {
                    place = placeFor(runCatching { onDrive.first() }.getOrDefault(false))
                    load()
                }
            } finally {
                loaded.complete(Unit)
            }
            // Moved between the phone and the drive: it starts afresh there, what was on the old place goes
            onDrive.distinctUntilChanged().collect { wanted ->
                mutex.withLock {
                    val next = placeFor(wanted)
                    if (next.dir == place.dir) return@withLock
                    runCatching { place.dir.listFiles()?.forEach { it.delete() } }
                    place = next
                    load()
                }
            }
        }
    }

    /** The drive when wanted and there (with a key to encrypt with), else the phone's cache. */
    private fun placeFor(wanted: Boolean): ReservePlace =
        if (wanted) runCatching { drivePlace() }.getOrNull()?.takeIf { it.dir.isDirectory || it.dir.mkdirs() } ?: internal else internal

    /** The reserve is encrypted on a drive now. */
    val onDrive: Boolean get() = place.key != null

    fun has(trackId: String): Boolean = trackId in _entries.value

    fun fileOf(trackId: String): File? = if (has(trackId)) fileFor(trackId).takeIf { it.isFile } else null

    fun qualityOf(trackId: String): StreamQuality? =
        _entries.value[trackId]?.let { StreamQuality(it.codec, it.bitrateKbps) }

    /** Space that is really free: a reserve is not worth making the system clear other apps' caches for. */
    @SuppressLint("UsableSpace")
    fun freeBytes(): Long = dir.apply { mkdirs() }.usableSpace

    fun markPlayed(trackId: String) {
        scope.launch {
            loaded.await()
            mutex.withLock {
                val entry = _entries.value[trackId] ?: return@launch
                publish(_entries.value + (trackId to entry.copy(playedAtMs = System.currentTimeMillis())))
            }
        }
    }

    /** Saves [track] from [stream]; while [yieldTo] says so, the download steps back for the playing stream. */
    suspend fun store(track: TrackDetailsDto, stream: ResolvedStream, yieldTo: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        val id = track.idString
        loaded.await()
        val target = place
        val temp = File(target.dir, "$id.tmp")
        target.dir.mkdirs()
        try {
            val saved = okHttpClient.newCall(Request.Builder().url(stream.url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                response.body.byteStream().use { input ->
                    // On a drive the track lands encrypted, as the downloads there
                    val file = FileOutputStream(temp)
                    (target.key?.let { CardStorage.encrypting(it, file) } ?: file).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            ensureActive()
                            while (yieldTo()) delay(YIELD_POLL_MS)
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                true
            }
            if (!saved) {
                temp.delete()
                return@withContext false
            }
            if (target !== place) {
                // The place changed while it downloaded
                temp.delete()
                return@withContext false
            }
            adopt(track, temp, stream.quality, encrypted = target.key != null)
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save $id", e)
            temp.delete()
            false
        }
    }

    /** Takes the finished [temp] file in as the copy of [track]; a plain one is encrypted when the reserve is on a drive. */
    internal suspend fun adopt(track: TrackDetailsDto, temp: File, quality: StreamQuality, encrypted: Boolean = false): Boolean {
        loaded.await()
        dir.mkdirs()
        val file = fileFor(track.idString)
        if (temp.length() == 0L) {
            temp.delete()
            return false
        }
        val key = place.key
        val ready = if (key != null && !encrypted) encryptInto(key, temp, file) else temp.renameTo(file)
        if (!ready) {
            temp.delete()
            file.delete()
            return false
        }
        mutex.withLock {
            val entry = ReserveEntry(track, file.length(), quality.codec, quality.bitrateKbps, System.currentTimeMillis())
            publish(_entries.value + (track.idString to entry))
        }
        return true
    }

    /** Makes room for [incomingBytes] within [limitBytes]; false when only [keep] tracks are left to remove. */
    suspend fun makeRoom(limitBytes: Long, incomingBytes: Long, keep: Set<String>): Boolean = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            val evict = ReservePolicy.toEvict(_entries.value.values, limitBytes, incomingBytes, keep) ?: return@withLock false
            removeLocked(evict)
            true
        }
    }

    suspend fun remove(trackId: String) = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock { removeLocked(listOf(trackId)) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
            publish(emptyMap())
        }
    }

    private fun removeLocked(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.forEach { fileFor(it).delete() }
        publish(_entries.value - ids.toSet())
    }

    /** On a drive "<id>.enc": the player reads such a file through the downloads' decryption. */
    private fun fileFor(trackId: String) = File(dir, "$trackId." + if (place.key != null) CardStorage.EXTENSION else "audio")

    private fun encryptInto(key: ByteArray, plain: File, target: File): Boolean = runCatching {
        plain.inputStream().use { input -> CardStorage.encrypting(key, FileOutputStream(target)).use { input.copyTo(it) } }
        plain.delete()
        true
    }.onFailure { Log.w(TAG, "Could not encrypt ${plain.name}", it) }.getOrDefault(false)

    private fun load() {
        val saved = runCatching { json.decodeFromString<List<ReserveEntry>>(indexFile.readText()) }.getOrDefault(emptyList())
        val present = saved.filter { fileFor(it.id).isFile }.associateBy { it.id }
        // Temp files of a download that did not finish, and files the index lost track of
        dir.listFiles()?.filter { it != indexFile && it.nameWithoutExtension !in present }?.forEach { it.delete() }
        _entries.value = present
        _usedBytes.value = present.values.sumOf { it.sizeBytes }
    }

    private fun publish(new: Map<String, ReserveEntry>) {
        _entries.value = new
        _usedBytes.value = new.values.sumOf { it.sizeBytes }
        dir.mkdirs()
        runCatching {
            val temp = File(dir, "index.tmp")
            temp.writeText(json.encodeToString<List<ReserveEntry>>(new.values.toList()))
            // Not every file system renames over an existing file
            if (!temp.renameTo(indexFile)) {
                indexFile.delete()
                temp.renameTo(indexFile)
            }
        }.onFailure { Log.w(TAG, "Could not save the index", it) }
    }

    private companion object {
        const val TAG = "RoadReserve"
        const val BUFFER_BYTES = 16 * 1024
        const val YIELD_POLL_MS = 500L
    }
}

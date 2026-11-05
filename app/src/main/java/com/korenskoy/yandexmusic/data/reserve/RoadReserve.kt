package com.korenskoy.yandexmusic.data.reserve

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

/**
 * The road reserve: tracks saved ahead while the music plays, to fall back on when the link is weak or
 * gone. It lives in the app's cache, apart from the user's downloads, and rolls over by itself.
 *
 * It only ever holds the ordinary stream the service offers this app, so its size stays predictable.
 */
@Singleton
class RoadReserve @Inject constructor(
    @param:ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val dir = File(context.cacheDir, "road_reserve")
    private val indexFile = File(dir, "index.json")
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
                mutex.withLock { load() }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

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
        val file = fileFor(id)
        val temp = File(dir, "$id.tmp")
        loaded.await()
        dir.mkdirs()
        try {
            val saved = okHttpClient.newCall(Request.Builder().url(stream.url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                response.body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
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
            adopt(track, temp, stream.quality)
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save $id", e)
            temp.delete()
            false
        }
    }

    /** Takes the finished [temp] file in as the copy of [track]. */
    internal suspend fun adopt(track: TrackDetailsDto, temp: File, quality: StreamQuality): Boolean {
        loaded.await()
        dir.mkdirs()
        val file = fileFor(track.idString)
        if (temp.length() == 0L || !temp.renameTo(file)) {
            temp.delete()
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

    private fun fileFor(trackId: String) = File(dir, "$trackId.audio")

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

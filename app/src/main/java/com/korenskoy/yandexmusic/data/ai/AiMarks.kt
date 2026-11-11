package com.korenskoy.yandexmusic.data.ai

import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.dto.CreditDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What Yandex says about AI in a track's credits. Pure; unit-tested. */
object AiRules {
    /** The credits line, in Russian or (if the server answers so) in English. */
    private val titles = setOf("использование ии", "ai use")

    /** "Возможно, трек создан с использованием ИИ" is a guess, not a mark: such a track is not AI here. */
    private val guesses = listOf("возможно", "possibl", "probabl", "may have", "might")

    /**
     * Yandex's own words when it marks the track as made (wholly or partly) with AI — "Трек полностью создан с
     * использованием ИИ"; null for no mark, or only "возможно".
     */
    fun markOf(credits: List<CreditDto>): String? {
        val line = credits.firstOrNull { it.title?.trim()?.lowercase(Locale.ROOT) in titles } ?: return null
        val words = line.value?.trim().orEmpty()
        if (words.isEmpty()) return null
        val lower = words.lowercase(Locale.ROOT)
        return words.takeUnless { guesses.any { it in lower } }
    }

    /** Tracks of the catalogue only: not the user's own files, not stations, not uploads (they have no numeric id). */
    fun isAskable(trackId: String): Boolean = trackId.isNotEmpty() && trackId.all { it.isDigit() }
}

/**
 * Which tracks Yandex marks as made with AI. The mark is only in a track's credits, one request per track, so
 * tracks are asked about as they come into view or near in the queue — the newest wish first, a few at a time —
 * and the answer is kept on the device for a month. No answer (no connection) means no mark: the track plays.
 */
@Singleton
class AiMarks internal constructor(
    private val fetch: suspend (String) -> Answer,
    private val file: File?,
    private val now: () -> Long = System::currentTimeMillis,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    @Inject
    constructor(api: YandexMusicApi, @ApplicationContext context: Context) : this(
        fetch = { id -> ask(api, id) },
        file = File(context.filesDir, "ai_marks.json")
    )

    /** What the server answered about one track. */
    sealed interface Answer {
        data class Credits(val credits: List<CreditDto>) : Answer
        /** The track has no credits at all (an upload, a removed track): nothing to ask again. */
        data object None : Answer
        /** No connection, a server error: asked again later. */
        data object Failed : Answer
    }

    @Serializable
    private data class Entry(val ai: String? = null, val at: Long)

    @Serializable
    private data class Store(val tracks: Map<String, Entry> = emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val entries = ConcurrentHashMap<String, Entry>()
    private val lock = Any()
    /** Tracks to ask about, the newest wish last (and first to go). */
    private val wanted = LinkedHashSet<String>()
    private val asking = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var saveJob: Job? = null

    private val _marks = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Track id → Yandex's words, for the tracks marked as made with AI. */
    val marks: StateFlow<Map<String, String>> = _marks.asStateFlow()

    init {
        scope.launch {
            load()
            repeat(PARALLEL) { launch { work() } }
        }
    }

    fun isAi(trackId: String): Boolean = _marks.value.containsKey(trackId)

    /** These tracks matter now (on the screen, next in the queue): asked about soon, the last of them first. */
    fun want(trackIds: Collection<String>) {
        var added = false
        synchronized(lock) {
            for (id in trackIds) {
                if (!needsAsking(id)) continue
                wanted.remove(id)
                wanted.add(id)
                added = true
            }
        }
        if (added) wake.trySend(Unit)
    }

    fun want(trackId: String) = want(listOf(trackId))

    /** The track left the screen before it was asked about: not worth a request any more. */
    fun unwant(trackId: String) {
        synchronized(lock) { wanted.remove(trackId) }
    }

    private fun needsAsking(id: String): Boolean {
        if (!AiRules.isAskable(id) || id in asking) return false
        val known = entries[id] ?: return true
        return now() - known.at > KEEP_MS
    }

    private suspend fun work() {
        while (true) {
            val id = synchronized(lock) {
                wanted.lastOrNull()?.also {
                    wanted.remove(it)
                    asking += it
                }
            }
            if (id == null) {
                wake.receive()
                continue
            }
            // More waiting: the next free worker takes it
            if (synchronized(lock) { wanted.isNotEmpty() }) wake.trySend(Unit)
            try {
                remember(id, fetch(id))
            } finally {
                synchronized(lock) { asking -= id }
            }
        }
    }

    private fun remember(id: String, answer: Answer) {
        val ai = when (answer) {
            is Answer.Credits -> AiRules.markOf(answer.credits)
            Answer.None -> null
            Answer.Failed -> return
        }
        entries[id] = Entry(ai, now())
        _marks.update { if (ai != null) it + (id to ai) else it - id }
        save()
    }

    private fun load() {
        val source = file ?: return
        if (!source.exists()) return
        val store = try {
            json.decodeFromString(Store.serializer(), source.readText())
        } catch (e: IOException) {
            Log.w(TAG, "The marks read before are lost", e)
            return
        } catch (e: SerializationException) {
            Log.w(TAG, "The marks read before are lost", e)
            return
        }
        val fresh = store.tracks.filterValues { now() - it.at <= KEEP_MS }
        entries.putAll(fresh)
        _marks.value = fresh.mapNotNull { (id, entry) -> entry.ai?.let { id to it } }.toMap()
    }

    /** Written a moment after the last change: a screenful of answers is one write. */
    private fun save() {
        val target = file ?: return
        synchronized(lock) {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(SAVE_DELAY_MS)
                val kept = entries.entries.sortedByDescending { it.value.at }.take(MAX_TRACKS).associate { it.key to it.value }
                val part = File(target.parentFile, target.name + ".part")
                try {
                    part.writeText(json.encodeToString(Store.serializer(), Store(kept)))
                    part.renameTo(target)
                } catch (e: IOException) {
                    Log.w(TAG, "Cannot keep the marks", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AiMarks"
        private const val PARALLEL = 4
        /** A month: a track's mark rarely changes. */
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
        private const val SAVE_DELAY_MS = 2_000L
        private const val MAX_TRACKS = 30_000

        private suspend fun ask(api: YandexMusicApi, id: String): Answer = try {
            Answer.Credits(api.getTrackCredits(id).result?.credits.orEmpty())
        } catch (e: HttpException) {
            Log.d(TAG, "Credits of $id: HTTP ${e.code()}")
            if (e.code() in 400..499) Answer.None else Answer.Failed
        } catch (e: IOException) {
            Log.d(TAG, "Credits of $id: ${e.message}")
            Answer.Failed
        } catch (e: SerializationException) {
            Log.d(TAG, "Credits of $id unreadable: ${e.message}")
            Answer.None
        }
    }
}

package com.korenskoy.yandexmusic.data.lyrics

import android.util.Log
import com.korenskoy.yandexmusic.data.ai.AiRules
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.TrackSupplementDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What the server answered about the words of one track. */
sealed interface LyricsAnswer {
    data class Text(val text: String) : LyricsAnswer
    /** Yandex has no words for the track: not asked again. */
    data object None : LyricsAnswer
    /** No connection, a server error: asked again next time. */
    data object Failed : LyricsAnswer
}

/** Which tracks can have words, and the words out of the server's answer. Pure; unit-tested. */
object LyricsRules {
    /** A catalogue track Yandex did not say "no words" about: not a station, not the user's own file. */
    fun canHave(track: TrackDetailsDto): Boolean =
        !track.isRadio && !track.isLocal && track.hasLyrics != false && AiRules.isAskable(track.idString)

    /** The words as the reader sees them: no blank lines at the ends, at most one empty line in a row. */
    fun textOf(answer: TrackSupplementDto?): String? {
        val lyrics = answer?.lyrics ?: return null
        if (lyrics.hasRights == false) return null
        val lines = lyrics.fullLyrics.orEmpty().lines().map { it.trimEnd() }
        val kept = lines.filterIndexed { i, line -> line.isNotEmpty() || (i > 0 && lines[i - 1].isNotEmpty()) }
        return kept.joinToString("\n").trim().ifEmpty { null }
    }
}

/**
 * The words of tracks, from Yandex's open "supplement" method (plain text: the timed words are only behind a
 * signed method this app does not use). Asked when the player shows a track that can have them, kept in
 * memory for the session.
 */
@Singleton
class Lyrics internal constructor(private val fetch: suspend (String) -> LyricsAnswer) {
    @Inject
    constructor(api: YandexMusicApi) : this({ id -> ask(api, id) })

    private val known = object : LinkedHashMap<String, LyricsAnswer>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsAnswer>?) = size > KEEP
    }

    /** The words of [trackId]; a failed answer is not kept, so the next look asks again. */
    suspend fun of(trackId: String): LyricsAnswer {
        synchronized(known) { known[trackId] }?.let { return it }
        val answer = fetch(trackId)
        if (answer != LyricsAnswer.Failed) synchronized(known) { known[trackId] = answer }
        return answer
    }

    /** Already known, without asking: the player opens with the words at once. */
    fun known(trackId: String): LyricsAnswer? = synchronized(known) { known[trackId] }

    companion object {
        private const val TAG = "Lyrics"
        private const val KEEP = 64

        private suspend fun ask(api: YandexMusicApi, id: String): LyricsAnswer = try {
            LyricsRules.textOf(api.getTrackSupplement(id).result)?.let { LyricsAnswer.Text(it) } ?: LyricsAnswer.None
        } catch (e: HttpException) {
            Log.d(TAG, "Words of $id: HTTP ${e.code()}")
            if (e.code() in 400..499) LyricsAnswer.None else LyricsAnswer.Failed
        } catch (e: IOException) {
            Log.d(TAG, "Words of $id: ${e.message}")
            LyricsAnswer.Failed
        } catch (e: SerializationException) {
            Log.d(TAG, "Words of $id unreadable: ${e.message}")
            LyricsAnswer.None
        }
    }
}

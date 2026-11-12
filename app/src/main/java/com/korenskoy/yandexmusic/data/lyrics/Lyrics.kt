package com.korenskoy.yandexmusic.data.lyrics

import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.ai.AiRules
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.TrackSupplementDto
import com.korenskoy.yandexmusic.data.network.LosslessSigner
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.di.UpdateClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One timed line of lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

/** What the server answered about the words of one track. */
sealed interface LyricsAnswer {
    /** Synced (timed) lyrics: line-by-line karaoke following the song position. */
    data class Synced(val lines: List<LyricLine>) : LyricsAnswer
    /** Static plain text lyrics. */
    data class Text(val text: String) : LyricsAnswer
    /** Yandex has no words for the track: not asked again. */
    data object None : LyricsAnswer
    /** No connection, a server error: asked again next time. */
    data object Failed : LyricsAnswer
}

/** Which tracks can have words, and the words out of the server's answer. Pure; unit-tested. */
object LyricsRules {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val inlineTag = Regex("""<[^>]+>""")

    /** A catalogue track Yandex did not say "no words" about: not a station, not the user's own file. */
    fun canHave(track: TrackDetailsDto): Boolean =
        !track.isRadio && !track.isLocal &&
        (track.hasLyrics != false || track.lyricsInfo?.hasAvailableSyncLyrics == true) &&
        AiRules.isAskable(track.idString)

    /** The words as the reader sees them: no blank lines at the ends, at most one empty line in a row. */
    fun textOf(answer: TrackSupplementDto?): String? {
        val lyrics = answer?.lyrics ?: return null
        if (lyrics.hasRights == false) return null
        val lines = lyrics.fullLyrics.orEmpty().lines().map { it.trimEnd() }
        val kept = lines.filterIndexed { i, line -> line.isNotEmpty() || (i > 0 && lines[i - 1].isNotEmpty()) }
        return kept.joinToString("\n").trim().ifEmpty { null }
    }

    /** Parses standard LRC format into timed lines, sorted by timestamp. Pure; unit-tested. */
    fun parseLrc(raw: String): List<LyricLine> {
        val result = ArrayList<LyricLine>()
        for (rawLine in raw.removePrefix("\uFEFF").lineSequence()) {
            var rest = rawLine.trim()
            val times = ArrayList<Long>()
            while (true) {
                val match = timeTag.matchAt(rest, 0) ?: break
                val (min, sec, frac) = match.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100L
                    2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                times += min.toLong() * 60_000L + sec.toLong() * 1_000L + fracMs
                rest = rest.substring(match.range.last + 1)
            }
            if (times.isEmpty()) continue
            val text = rest.replace(inlineTag, "").trim()
            times.forEach { result += LyricLine(it, text) }
        }
        if (result.none { it.text.isNotBlank() }) return emptyList()
        return result.sortedBy { it.timeMs }
    }

    /** Index of the line sung at [positionMs], or -1 before the first one. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/**
 * The words of tracks: time-synced (LRC) when available, falling back to plain text from the signed
 * or open method. Cached in memory and locally in [cacheDir] for offline playback.
 */
@Singleton
class Lyrics internal constructor(
    private val fetch: suspend (String) -> LyricsAnswer
) {
    private var cacheDir: File? = null

    internal constructor(
        fetch: suspend (String) -> LyricsAnswer,
        cacheDir: File?
    ) : this(fetch) {
        this.cacheDir = cacheDir
    }

    @Inject
    constructor(
        api: YandexMusicApi,
        @ApplicationContext context: Context,
        @UpdateClient downloadClient: OkHttpClient
    ) : this({ id -> ask(api, downloadClient, id) }, File(context.cacheDir, "lyrics"))

    private val known = object : LinkedHashMap<String, LyricsAnswer>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsAnswer>?) = size > KEEP
    }

    /** The words of [trackId]; a failed answer is not kept, so the next look asks again. */
    suspend fun of(trackId: String): LyricsAnswer {
        val cleanId = trackId.substringBefore(':').trim()
        synchronized(known) { known[cleanId] }?.let { return it }

        readDisk(cleanId)?.let { diskAnswer ->
            synchronized(known) { known[cleanId] = diskAnswer }
            return diskAnswer
        }

        val answer = fetch(cleanId)
        if (answer != LyricsAnswer.Failed) {
            synchronized(known) { known[cleanId] = answer }
            writeDisk(cleanId, answer)
        }
        return answer
    }

    /** Already known, without asking: the player opens with the words at once. */
    fun known(trackId: String): LyricsAnswer? {
        val cleanId = trackId.substringBefore(':').trim()
        return synchronized(known) { known[cleanId] }
    }

    private fun readDisk(cleanId: String): LyricsAnswer? {
        val dir = cacheDir ?: return null
        return runCatching {
            val lrcFile = File(dir, "$cleanId.lrc")
            if (lrcFile.exists() && lrcFile.length() > 0) {
                val lines = LyricsRules.parseLrc(lrcFile.readText())
                if (lines.isNotEmpty()) return LyricsAnswer.Synced(lines)
            }
            val txtFile = File(dir, "$cleanId.txt")
            if (txtFile.exists() && txtFile.length() > 0) {
                val text = txtFile.readText().trim()
                if (text.isNotEmpty()) return LyricsAnswer.Text(text)
            }
            null
        }.getOrNull()
    }

    private fun writeDisk(cleanId: String, answer: LyricsAnswer) {
        val dir = cacheDir ?: return
        runCatching {
            dir.mkdirs()
            when (answer) {
                is LyricsAnswer.Synced -> {
                    val content = answer.lines.joinToString("\n") { line ->
                        val min = line.timeMs / 60_000L
                        val sec = (line.timeMs % 60_000L) / 1000L
                        val cs = (line.timeMs % 1000L) / 10L
                        String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, cs, line.text)
                    }
                    File(dir, "$cleanId.lrc").writeText(content)
                }
                is LyricsAnswer.Text -> {
                    File(dir, "$cleanId.txt").writeText(answer.text)
                }
                else -> Unit
            }
        }
    }

    companion object {
        private const val TAG = "Lyrics"
        private const val KEEP = 64

        private sealed interface FetchOutcome {
            data class Success(val answer: LyricsAnswer) : FetchOutcome
            data object Missing : FetchOutcome
            data object NetworkError : FetchOutcome
        }

        internal suspend fun ask(
            api: YandexMusicApi,
            downloadClient: OkHttpClient,
            id: String
        ): LyricsAnswer {
            val cleanId = id.substringBefore(':').trim()
            var hadNetworkError = false

            when (val lrc = fetchSigned(api, downloadClient, cleanId, "LRC")) {
                is FetchOutcome.Success -> return lrc.answer
                FetchOutcome.NetworkError -> hadNetworkError = true
                FetchOutcome.Missing -> Unit
            }

            when (val text = fetchSigned(api, downloadClient, cleanId, "TEXT")) {
                is FetchOutcome.Success -> return text.answer
                FetchOutcome.NetworkError -> hadNetworkError = true
                FetchOutcome.Missing -> Unit
            }

            when (val supp = fetchSupplement(api, cleanId)) {
                is FetchOutcome.Success -> return supp.answer
                FetchOutcome.NetworkError -> hadNetworkError = true
                FetchOutcome.Missing -> Unit
            }

            return if (hadNetworkError) LyricsAnswer.Failed else LyricsAnswer.None
        }

        private suspend fun fetchSigned(
            api: YandexMusicApi,
            downloadClient: OkHttpClient,
            cleanId: String,
            format: String
        ): FetchOutcome = try {
            val sign = LosslessSigner.signLyrics(cleanId)
            val resp = api.getTrackLyrics(cleanId, format, sign.ts, sign.sign)
            val url = resp.result?.downloadUrl?.takeIf { it.isNotBlank() }
            if (url == null) FetchOutcome.Missing
            else parseDownloaded(downloadFile(downloadClient, url)?.trim(), format)
        } catch (e: HttpException) {
            if (e.code() in 400..499) FetchOutcome.Missing else { Log.d(TAG, "$format of $cleanId: HTTP ${e.code()}"); FetchOutcome.NetworkError }
        } catch (e: IOException) {
            Log.d(TAG, "$format of $cleanId: ${e.message}")
            FetchOutcome.NetworkError
        } catch (e: SerializationException) {
            Log.d(TAG, "$format of $cleanId unreadable: ${e.message}")
            FetchOutcome.Missing
        }

        private fun parseDownloaded(content: String?, format: String): FetchOutcome {
            if (content.isNullOrEmpty()) return FetchOutcome.Missing
            if (format == "LRC") {
                val lines = LyricsRules.parseLrc(content)
                if (lines.isNotEmpty()) return FetchOutcome.Success(LyricsAnswer.Synced(lines))
                return FetchOutcome.Missing
            }
            return FetchOutcome.Success(LyricsAnswer.Text(content))
        }

        private suspend fun fetchSupplement(
            api: YandexMusicApi,
            cleanId: String
        ): FetchOutcome = try {
            val supplement = api.getTrackSupplement(cleanId)
            val text = LyricsRules.textOf(supplement.result)
            if (text != null) FetchOutcome.Success(LyricsAnswer.Text(text)) else FetchOutcome.Missing
        } catch (e: HttpException) {
            if (e.code() in 400..499) FetchOutcome.Missing else { Log.d(TAG, "Supplement of $cleanId: HTTP ${e.code()}"); FetchOutcome.NetworkError }
        } catch (e: IOException) {
            Log.d(TAG, "Supplement of $cleanId: ${e.message}")
            FetchOutcome.NetworkError
        } catch (e: SerializationException) {
            Log.d(TAG, "Supplement of $cleanId unreadable: ${e.message}")
            FetchOutcome.Missing
        }

        private fun downloadFile(client: OkHttpClient, url: String): String? {
            val httpUrl = url.toHttpUrlOrNull() ?: return null
            val request = Request.Builder().url(httpUrl).build()
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        }
    }
}

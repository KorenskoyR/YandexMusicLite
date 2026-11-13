package com.korenskoy.yandexmusic.data.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the music went through in the last hour: stops and their causes, errors, switches to a copy, light streams,
 * crossfades. Kept on the device, so it outlives the app being closed by Android (and tells that it was); shown in
 * "О приложении → Журнал воспроизведения" and shared as text with a report, so a "it stops" can be told apart: the
 * link, another app, the headphones, the device or the app.
 */
@Singleton
class PlaybackJournal(
    private val now: () -> Long = System::currentTimeMillis,
    private val store: JournalStore? = null,
    /** How the app's previous run ended, if it ended after the given time; null when Android does not say. */
    private val exitSince: (Long) -> ProcessExit? = { null }
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        System::currentTimeMillis,
        FileJournalStore(File(context.filesDir, FILE_NAME)),
        ProcessExits(context)::lastSince
    )

    enum class Kind {
        /** The music ran dry mid-track; [Event.valueMs] is how long. */
        STALL,
        /** The link became uneven. */
        UNEVEN,
        /** The playing track went on from its copy in the cache, at [Event.valueMs]. */
        COPY,
        /** The track plays from a light stream. */
        LIGHT,
        /** The link has been steady long enough: the chosen quality again. */
        STEADY,
        /** A crossfade with the next track ready in time. */
        CROSSFADE,
        /** A crossfade where the next track came late and faded in after the previous one. */
        CROSSFADE_LATE,
        /** "Слабая связь" lit up; [Event.valueMs] is the [LinkReason]'s ordinal. */
        LINK_WEAK,
        /** "Нет связи"; [Event.valueMs] is the [LinkReason]'s ordinal. */
        LINK_NONE,
        /** The link is good again; [Event.valueMs] is the [LinkReason]'s ordinal. */
        LINK_BACK,
        /** The wave fetched more: [Event.valueMs] tracks offered, [Event.count] of them heard lately. */
        WAVE_BATCH,
        /** The wave played a track heard lately. */
        WAVE_REPEAT,
        /** The music paused; [Event.count] is the [StopCause]'s ordinal, [Event.detail] who or what. */
        PAUSED,
        /** The music plays again after a pause the listener did not ask for: [Event.valueMs] of silence, [Event.count] its [StopCause]. */
        RESUMED,
        /** The track failed: [Event.detail] the error, [Event.count] the [Recovery]'s ordinal (-1: a file of the device), [Event.valueMs] where. */
        ERROR,
        /** The stream's link ran out mid-track and a fresh one went on from the same byte, unheard. */
        LINK_RENEWED,
        /** The device did not keep up with the sound: [Event.count] gaps. Not the link. */
        UNDERRUN,
        /** Android closed the app while the music played: [Event.detail] its [ProcessExit.reason], [Event.valueMs] when. */
        KILLED,
        /** A track took [Event.valueMs] to start sounding. */
        SLOW_START
    }

    data class Event(
        val atMs: Long,
        val kind: Kind,
        val title: String? = null,
        val valueMs: Long = 0L,
        val count: Int = 0,
        val detail: String? = null
    ) {
        /** Worth a line in the list; ordinary crossfades are only counted. */
        val notable: Boolean get() = kind != Kind.CROSSFADE

        val cause: StopCause? get() = if (kind == Kind.PAUSED || kind == Kind.RESUMED) StopCause.entries.getOrNull(count) else null
    }

    data class Summary(
        val playedMs: Long = 0L,
        val stalls: Int = 0,
        val stalledMs: Long = 0L,
        val copySwitches: Int = 0,
        val lightTracks: Int = 0,
        val crossfades: Int = 0,
        val crossfadesLate: Int = 0,
        /** Tracks the wave's station offered, and how many of them were heard lately. */
        val waveOffered: Int = 0,
        val waveOfferedHeard: Int = 0,
        val waveRepeats: Int = 0,
        /** Pauses the listener did not ask for: another app, the headphones, no connection, unknown. */
        val pausesNotAsked: Int = 0,
        val errors: Int = 0,
        val kills: Int = 0,
        val underruns: Int = 0,
        val renewedLinks: Int = 0,
        val slowStarts: Int = 0
    ) {
        /** The wave played in this hour: its lines are shown. */
        val waveUsed: Boolean get() = waveOffered > 0 || waveRepeats > 0

        /** Every time the music stopped without being asked to: the row of the settings counts these. */
        val stops: Int get() = stalls + pausesNotAsked + errors + kills
    }

    data class Snapshot(val summary: Summary = Summary(), val events: List<Event> = emptyList())

    /** The last pause of the music, while it lasts. */
    data class Pause(val cause: StopCause, val atMs: Long) {
        /** The headphones or the Bluetooth went away a moment ago and paused it: their coming back brings the music back. */
        fun isOutputBlip(nowMs: Long): Boolean = cause == StopCause.OUTPUT_GONE && nowMs - atMs in 0..OUTPUT_BLIP_MS
    }

    private val events = ArrayDeque<Event>()
    private val played = ArrayDeque<Pair<Long, Long>>()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private var expected: Triple<StopCause, String?, Long>? = null
    private var lastPause: Pause? = null
    private var playing = false
    private var playingSince = 0L
    private var killNotice: Event? = null

    init {
        store?.load()?.let(::restore)
        publish(save = false)
    }

    @Synchronized
    fun record(kind: Kind, title: String? = null, valueMs: Long = 0L, count: Int = 0, detail: String? = null) {
        events.addLast(Event(now(), kind, title, valueMs, count, detail))
        publish()
    }

    /** [ms] more of music heard. */
    @Synchronized
    fun addPlayed(ms: Long) {
        if (ms <= 0L) return
        val at = now()
        val last = played.lastOrNull()
        // A few seconds together: an hour is a few hundred entries, not tens of thousands
        if (last != null && at - last.first < PLAYED_STEP_MS) {
            played.removeLast()
            played.addLast(last.first to last.second + ms)
        } else {
            played.addLast(at to ms)
        }
        publish(save = false)
    }

    /** The next pause is for [cause]: said just before the player is paused. */
    @Synchronized
    fun expectPause(cause: StopCause, detail: String? = null) {
        expected = Triple(cause, detail, now())
    }

    /**
     * The playing track has just paused. [outputGone]: the player itself paused because the headphones or the
     * Bluetooth went away. Otherwise the cause is the one said just before, or nobody's: [StopCause.UNKNOWN].
     */
    @Synchronized
    fun onPaused(title: String?, outputGone: Boolean = false) {
        val said = expected?.takeIf { now() - it.third <= EXPECT_MS }
        expected = null
        val cause = if (outputGone) StopCause.OUTPUT_GONE else said?.first ?: StopCause.UNKNOWN
        lastPause = Pause(cause, now())
        record(Kind.PAUSED, title, count = cause.ordinal, detail = if (outputGone) null else said?.second)
    }

    /** The playing track sounds (again): a pause the listener did not ask for is over, and how long it was is told. */
    @Synchronized
    fun onPlaying(title: String?) {
        val pause = lastPause ?: return
        lastPause = null
        if (!pause.cause.byListener) record(Kind.RESUMED, title, valueMs = now() - pause.atMs, count = pause.cause.ordinal)
    }

    /** What the last pause was, while the music has not played since. */
    @Synchronized
    fun lastPause(): Pause? = lastPause

    /** Adds what was learned a moment later (who took the sound) to the last pause. */
    @Synchronized
    fun amendLastPause(detail: String) {
        val index = events.indexOfLast { it.kind == Kind.PAUSED }
        if (index < 0 || events[index].detail != null) return
        events[index] = events[index].copy(detail = detail)
        publish()
    }

    /** One more gap of the device's sound; gaps of one track close together make one line. */
    @Synchronized
    fun recordUnderrun(title: String?) {
        val index = events.indexOfLast { it.kind == Kind.UNDERRUN }
        val last = events.getOrNull(index)
        if (last != null && last.title == title && now() - last.atMs < UNDERRUN_MERGE_MS) {
            events[index] = last.copy(count = last.count + 1)
            publish()
        } else {
            record(Kind.UNDERRUN, title, count = 1)
        }
    }

    /** The music wants to play (or no longer does): kept on the device at once, so a closed app is noticed. */
    @Synchronized
    fun setPlaying(on: Boolean) {
        if (on == playing) return
        playing = on
        if (on) playingSince = now()
        store?.requestSave(urgent = true, ::encode)
    }

    /** The app was closed by Android while the music played, found at this start: told once. */
    @Synchronized
    fun takeKillNotice(): Event? = killNotice.also { killNotice = null }

    @Synchronized
    fun clear() {
        events.clear()
        played.clear()
        lastPause = null
        publish()
    }

    private fun publish(save: Boolean = true) {
        val since = now() - WINDOW_MS
        while (events.isNotEmpty() && events.first().atMs < since) events.removeFirst()
        while (played.isNotEmpty() && played.first().first < since) played.removeFirst()
        while (events.size > MAX_KEPT) events.removeFirst()
        fun count(kind: Kind) = events.count { it.kind == kind }
        val stalls = events.filter { it.kind == Kind.STALL }
        val batches = events.filter { it.kind == Kind.WAVE_BATCH }
        _state.value = Snapshot(
            Summary(
                playedMs = played.sumOf { it.second },
                stalls = stalls.size,
                stalledMs = stalls.sumOf { it.valueMs },
                copySwitches = count(Kind.COPY),
                lightTracks = count(Kind.LIGHT),
                crossfades = count(Kind.CROSSFADE) + count(Kind.CROSSFADE_LATE),
                crossfadesLate = count(Kind.CROSSFADE_LATE),
                waveOffered = batches.sumOf { it.valueMs.toInt() },
                waveOfferedHeard = batches.sumOf { it.count },
                waveRepeats = count(Kind.WAVE_REPEAT),
                pausesNotAsked = events.count { it.kind == Kind.PAUSED && it.cause?.byListener == false },
                errors = count(Kind.ERROR),
                kills = count(Kind.KILLED),
                underruns = events.filter { it.kind == Kind.UNDERRUN }.sumOf { it.count },
                renewedLinks = count(Kind.LINK_RENEWED),
                slowStarts = count(Kind.SLOW_START)
            ),
            events.filter { it.notable }.takeLast(MAX_SHOWN)
        )
        if (save) store?.requestSave(urgent = false, ::encode)
    }

    /** The journal as lines of text, for the device. */
    @Synchronized
    internal fun encode(): String = buildString {
        appendLine(VERSION)
        appendLine(listOf("S", if (playing) 1 else 0, playingSince, now()).joinToString(SEP))
        played.forEach { (at, ms) -> appendLine(listOf("P", at, ms).joinToString(SEP)) }
        events.forEach { e ->
            appendLine(listOf("E", e.atMs, e.kind.name, e.valueMs, e.count, clean(e.title), clean(e.detail)).joinToString(SEP))
        }
    }

    private fun restore(text: String) {
        val lines = text.lines()
        if (lines.firstOrNull() != VERSION) return
        var wasPlaying = false
        var since = 0L
        var savedAt = 0L
        for (line in lines.drop(1)) {
            val f = line.split(SEP)
            when (f.firstOrNull()) {
                "S" -> if (f.size >= 4) {
                    wasPlaying = f[1] == "1"
                    since = f[2].toLongOrNull() ?: 0L
                    savedAt = f[3].toLongOrNull() ?: 0L
                }
                "P" -> if (f.size >= 3) {
                    val at = f[1].toLongOrNull() ?: continue
                    played.addLast(at to (f[2].toLongOrNull() ?: continue))
                }
                "E" -> if (f.size >= 7) {
                    val kind = Kind.entries.firstOrNull { it.name == f[2] } ?: continue
                    events.addLast(
                        Event(
                            f[1].toLongOrNull() ?: continue, kind, f[5].ifEmpty { null },
                            f[3].toLongOrNull() ?: 0L, f[4].toIntOrNull() ?: 0, f[6].ifEmpty { null }
                        )
                    )
                }
            }
        }
        if (wasPlaying) {
            // The music was playing when the app went: nobody paused it, the app was closed
            val exit = runCatching { exitSince(since) }.getOrNull()
            val killed = Event(now(), Kind.KILLED, valueMs = exit?.atMs ?: savedAt, detail = exit?.reason)
            events.addLast(killed)
            killNotice = killed
            store?.requestSave(urgent = true, ::encode)
        }
    }

    companion object {
        const val WINDOW_MS = 60 * 60_000L
        private const val MAX_SHOWN = 100
        private const val MAX_KEPT = 1_000
        private const val PLAYED_STEP_MS = 10_000L
        /** A pause said this long before the player paused is still the one meant. */
        const val EXPECT_MS = 2_000L
        const val UNDERRUN_MERGE_MS = 60_000L

        /** The headphones or the Bluetooth back within this: the music plays on by itself. */
        const val OUTPUT_BLIP_MS = 15_000L
        private const val VERSION = "journal 1"
        private const val SEP = "\t"
        const val FILE_NAME = "playback_journal.txt"

        private fun clean(text: String?): String = text?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ').orEmpty()
    }
}

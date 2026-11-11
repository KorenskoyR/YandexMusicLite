package com.korenskoy.yandexmusic.data.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the music went through in the last hour: stops, switches to a copy, light streams, crossfades. Kept in
 * memory only; shown in "О приложении → Журнал воспроизведения" and shared as text with a report, so a
 * "it stutters" can be told apart: the link, the track or the device.
 */
@Singleton
class PlaybackJournal(private val now: () -> Long) {
    @Inject
    constructor() : this(System::currentTimeMillis)

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
        CROSSFADE_LATE
    }

    data class Event(val atMs: Long, val kind: Kind, val title: String? = null, val valueMs: Long = 0L) {
        /** Worth a line in the list; ordinary crossfades are only counted. */
        val notable: Boolean get() = kind != Kind.CROSSFADE
    }

    data class Summary(
        val playedMs: Long = 0L,
        val stalls: Int = 0,
        val stalledMs: Long = 0L,
        val copySwitches: Int = 0,
        val lightTracks: Int = 0,
        val crossfades: Int = 0,
        val crossfadesLate: Int = 0
    )

    data class Snapshot(val summary: Summary = Summary(), val events: List<Event> = emptyList())

    private val events = ArrayDeque<Event>()
    private val played = ArrayDeque<Pair<Long, Long>>()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    @Synchronized
    fun record(kind: Kind, title: String? = null, valueMs: Long = 0L) {
        events.addLast(Event(now(), kind, title, valueMs))
        publish()
    }

    /** [ms] more of music heard. */
    @Synchronized
    fun addPlayed(ms: Long) {
        if (ms <= 0L) return
        played.addLast(now() to ms)
        publish()
    }

    @Synchronized
    fun clear() {
        events.clear()
        played.clear()
        publish()
    }

    private fun publish() {
        val since = now() - WINDOW_MS
        while (events.isNotEmpty() && events.first().atMs < since) events.removeFirst()
        while (played.isNotEmpty() && played.first().first < since) played.removeFirst()
        val stalls = events.filter { it.kind == Kind.STALL }
        _state.value = Snapshot(
            Summary(
                playedMs = played.sumOf { it.second },
                stalls = stalls.size,
                stalledMs = stalls.sumOf { it.valueMs },
                copySwitches = events.count { it.kind == Kind.COPY },
                lightTracks = events.count { it.kind == Kind.LIGHT },
                crossfades = events.count { it.kind == Kind.CROSSFADE || it.kind == Kind.CROSSFADE_LATE },
                crossfadesLate = events.count { it.kind == Kind.CROSSFADE_LATE }
            ),
            events.filter { it.notable }.takeLast(MAX_SHOWN)
        )
    }

    companion object {
        const val WINDOW_MS = 60 * 60_000L
        private const val MAX_SHOWN = 100
    }
}

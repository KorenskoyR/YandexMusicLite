package com.korenskoy.yandexmusic.data.player

import kotlin.math.min

/**
 * When the music may go back from what is on the device to the streams. A check that "the server answers" is not
 * enough: under a hard block a connection opens and no data comes, the check says yes, the stream hangs, and the
 * music stood still for hours (1.15 report: «уходит на кэш отлично, а обратно — и на этом всё», music only in the
 * evening). So a return is a trial: a stream that does not sound within [TRIAL_MS] sends the music back to the
 * device at once, and every failed return waits longer before the next one: 1, 2, 4, 8 minutes, at most 15.
 * Streams heard for [HOLD_MS] clear the record. Pure; unit-tested.
 */
internal class StreamReturn(private val now: () -> Long) {
    private var failures = 0
    private var lastFailureAt = 0L
    private var audibleSince: Long? = null

    /** The streams failed the music (a stall, a network error, a trial that stayed silent). */
    fun onStreamsFailed() {
        // A failure after a long good run starts a new count
        val held = audibleSince?.let { now() - it >= HOLD_MS } == true
        if (held) failures = 0
        failures++
        lastFailureAt = now()
        audibleSince = null
    }

    /** A stream sounds. */
    fun onAudible() {
        if (audibleSince == null) audibleSince = now()
        if (now() - (audibleSince ?: now()) >= HOLD_MS) failures = 0
    }

    /** Whether a return to the streams may be tried now. */
    fun mayTry(): Boolean = failures == 0 || now() - lastFailureAt >= waitAfter(failures)

    /** How long to wait after [failures] failures in a row. */
    fun waitAfter(failures: Int): Long =
        if (failures <= 0) 0L else min(FIRST_WAIT_MS shl (failures - 1).coerceAtMost(MAX_SHIFT), MAX_WAIT_MS)

    /** The wait before the next try, from now; 0 — may try. */
    fun nextTryInMs(): Long = if (mayTry()) 0L else waitAfter(failures) - (now() - lastFailureAt)

    companion object {
        /** A stream tried after the device's music has this long to sound. */
        const val TRIAL_MS = 8_000L
        const val FIRST_WAIT_MS = 60_000L
        const val MAX_WAIT_MS = 15 * 60_000L
        const val HOLD_MS = 3 * 60_000L
        private const val MAX_SHIFT = 5
    }
}

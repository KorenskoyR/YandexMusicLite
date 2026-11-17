package com.korenskoy.yandexmusic.data.player

import android.os.SystemClock

/**
 * A pause the device slept through, or a very long one: the track still loaded in the player is not to be trusted
 * any more (its link ran out, the sound output it held was closed while the head unit slept). Play then opens the
 * track afresh at the same place. After a head unit's sleep the first Play did nothing and the steering wheel did not
 * answer, while the next track played at once (owner's users, 1.14.10).
 */
class AfterRest(
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val uptime: () -> Long = SystemClock::uptimeMillis
) {
    private var pausedAt: Long? = null

    /** The time the device had slept so far, when the pause began. */
    private var sleptBefore = 0L

    @Synchronized
    fun onPaused() {
        if (pausedAt != null) return
        pausedAt = elapsed()
        sleptBefore = elapsed() - uptime()
    }

    @Synchronized
    fun onPlaying() {
        pausedAt = null
    }

    /** How long the pause has lasted when the loaded track should be opened afresh; null — it plays on as it is. */
    @Synchronized
    fun staleFor(): Long? {
        val at = pausedAt ?: return null
        val paused = elapsed() - at
        val slept = elapsed() - uptime() - sleptBefore
        return if (slept >= SLEPT_MS || paused >= LONG_PAUSE_MS) paused else null
    }

    companion object {
        /** A phone with the screen off for a minute sleeps too: only a real rest of the device counts. */
        const val SLEPT_MS = 5 * 60_000L
        const val LONG_PAUSE_MS = 30 * 60_000L
    }
}

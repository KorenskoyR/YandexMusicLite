package com.korenskoy.yandexmusic.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * How much music the player keeps ahead, and how it rides out a broken connection. Shared by both players.
 *
 * The defaults started after about a second of sound and gave up on a broken download after three tries,
 * which on an uneven mobile link meant stutters and restarted tracks.
 */
@OptIn(UnstableApi::class)
object PlaybackBuffering {
    /** Sound needed before a track starts: a little later start, far fewer stops right after it. */
    const val START_MS = 2_500

    /** Sound needed to go on after running dry: enough not to stop again at once. */
    const val AFTER_STALL_MS = 5_000

    /** Kept ahead while there is room: a tunnel or a dead zone of up to three minutes goes unheard. */
    const val AHEAD_MIN_MS = 120_000
    const val AHEAD_MAX_MS = 180_000

    /** Memory per player, whatever the bitrate: head units with little memory have two players running. */
    const val MAX_BYTES = 16 * 1024 * 1024

    fun loadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(AHEAD_MIN_MS, AHEAD_MAX_MS, START_MS, AFTER_STALL_MS)
        .setTargetBufferBytes(MAX_BYTES)
        // The byte limit wins over the time: a lossless stream keeps less time ahead rather than more memory
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    /**
     * A download that broke is tried again from where it stopped, with growing pauses, for about a minute,
     * while the music plays on from what is ahead. A link the server refuses (expired, gone) is not retried:
     * it fails at once, and the controller asks for a new link and goes on from the same second.
     */
    class PatientLoadErrors : DefaultLoadErrorHandlingPolicy(RETRIES) {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (refusedByServer(info.exception)) return C.TIME_UNSET
            // Broken files and the like stay fatal, as by default
            if (super.getRetryDelayMsFor(info) == C.TIME_UNSET) return C.TIME_UNSET
            return retryDelayMs(info.errorCount)
        }
    }

    /** Tries before a broken download counts as an error: about a minute of pauses. */
    const val RETRIES = 14

    /** 1, 2, 3, 4, 5, 5, … seconds. Pure. */
    fun retryDelayMs(errorCount: Int): Long = (errorCount.coerceAtLeast(1) * 1_000L).coerceAtMost(5_000L)

    /**
     * The server answered and said no: another try with the same link cannot help. "Busy, later" (429) and "too
     * slow" (408) are not a no: they are tried again (a station's relay that is busy hands over to another).
     */
    fun refusedByServer(error: Throwable?): Boolean {
        val response = generateSequence(error) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        return response != null && response.responseCode in 400..499 && response.responseCode !in TRY_LATER
    }

    /** Answers that ask to come back later. */
    private val TRY_LATER = setOf(408, 429)
}

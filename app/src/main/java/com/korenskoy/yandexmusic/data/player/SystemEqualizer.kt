package com.korenskoy.yandexmusic.data.player

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.media.audiofx.AudioEffect
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's or head unit's own equalizer. Both players share one audio session, so an equalizer
 * attached to it shapes every track, the crossfade included. The app has no equalizer of its own.
 *
 * Equalizer apps (Poweramp Equalizer and the like) learn about the session from a broadcast. Some of them
 * lose it when the system takes the sound for a moment (a speed camera alert, a navigation prompt, a call),
 * so the session is announced again when the sound comes back, and closed when the player service goes away.
 */
@Singleton
class SystemEqualizer(
    private val context: Context,
    /** One session for both players; 0 lets each player get its own when the system cannot make one. */
    val audioSessionId: Int,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        runCatching { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId() }
            .getOrDefault(0).coerceAtLeast(0)
    )

    private fun panelIntent() = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

    /**
     * Something on the device can show an equalizer for this session. Asked each time: an equalizer app
     * installed, removed or switched off while the app runs counts at once.
     */
    fun isAvailable(): Boolean =
        audioSessionId > 0 && runCatching { panelIntent().resolveActivity(context.packageManager) != null }.getOrDefault(false)

    private var lastAnnounced: Long? = null

    /**
     * Tells equalizer apps the session exists, so a system-wide one applies its preset to the music. Twice
     * within a second is once: after a call the regained focus and the sound coming back would both announce.
     */
    @Synchronized
    fun announce() {
        if (audioSessionId <= 0) return
        val at = now()
        lastAnnounced?.let { if (at - it < REPEAT_WINDOW_MS) return }
        lastAnnounced = at
        runCatching {
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            )
        }.onFailure { Log.w(TAG, "Equalizer session not announced", it) }
    }

    /** Tells equalizer apps the session is over: they let go of it instead of holding a dead one. */
    fun close() {
        if (audioSessionId <= 0) return
        runCatching {
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            )
        }.onFailure { Log.w(TAG, "Equalizer session not closed", it) }
    }

    /**
     * Announces the session when the sound first starts and when it comes back after a real pause. Not on a new
     * track, not when the other player takes over in a crossfade: equalizer apps re-attach their effects on
     * every announcement, and doing that at each track start (1.13.0) cut the sound for a moment almost every
     * track. A sound that returns after an alert or a call is announced by the service on regaining focus.
     */
    fun keepAnnounced(scope: CoroutineScope, isPlaying: Flow<Boolean>): Job =
        scope.launch {
            val moments = AnnounceMoments()
            isPlaying.distinctUntilChanged().collect { playing -> if (moments.onPlaying(playing, now())) announce() }
        }

    /**
     * When the sound starting again deserves an announcement. A track change flips "playing" off and on for
     * an instant; only a pause longer than [RESUME_GAP_MS] counts. Pure; unit-tested.
     */
    internal class AnnounceMoments {
        private var started = false
        private var pausedAt: Long? = null

        fun onPlaying(playing: Boolean, now: Long): Boolean {
            if (!playing) {
                if (started && pausedAt == null) pausedAt = now
                return false
            }
            val since = pausedAt
            pausedAt = null
            if (!started) {
                started = true
                return true
            }
            return since != null && now - since >= RESUME_GAP_MS
        }
    }

    fun open() {
        if (!isAvailable()) return
        try {
            context.startActivity(panelIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No equalizer to open", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Equalizer refused to open", e)
        }
    }

    companion object {
        private const val TAG = "SystemEqualizer"
        /** A pause at least this long is a real one, not the blink between two tracks. */
        const val RESUME_GAP_MS = 5_000L
        private const val REPEAT_WINDOW_MS = 1_000L
    }
}

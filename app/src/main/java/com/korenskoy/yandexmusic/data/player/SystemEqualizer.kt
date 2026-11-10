package com.korenskoy.yandexmusic.data.player

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
 * lose it when the system takes the sound for a moment (a speed camera alert, a navigation prompt, a call)
 * and only find it again on the next track, so the session is announced again every time the sound comes
 * back, and closed when the player service goes away.
 */
@Singleton
class SystemEqualizer(
    private val context: Context,
    /** One session for both players; 0 lets each player get its own when the system cannot make one. */
    val audioSessionId: Int
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

    /** Something on the device can show an equalizer for this session. */
    val available: Boolean by lazy {
        audioSessionId > 0 && runCatching { panelIntent().resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }

    /** Tells equalizer apps the session exists, so a system-wide one applies its preset to the music. */
    fun announce() {
        if (audioSessionId <= 0) return
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
     * Announces the session again whenever the sound starts after a pause, a new track begins, or the other
     * player takes over (crossfade, gapless). Nothing is sent while paused.
     */
    fun keepAnnounced(scope: CoroutineScope, isPlaying: Flow<Boolean>, trackId: Flow<String?>, player: Flow<Any>): Job =
        scope.launch {
            combine(isPlaying, trackId, player) { playing, track, active -> if (playing) Moment(track, active) else null }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { announce() }
        }

    private data class Moment(val trackId: String?, val player: Any)

    fun open() {
        if (!available) return
        try {
            context.startActivity(panelIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No equalizer to open", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Equalizer refused to open", e)
        }
    }

    private companion object {
        const val TAG = "SystemEqualizer"
    }
}

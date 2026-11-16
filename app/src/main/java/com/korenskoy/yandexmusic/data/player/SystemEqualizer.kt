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

    /** Where the device's own equalizer is. */
    sealed interface Target {
        /** Android's standard equalizer panel for this session (Samsung SoundAlive, Wavelet, Poweramp Equalizer…). */
        data object Panel : Target

        /** An equalizer app of its own that does not answer the standard request (head units: Teyes, FYT; some phones). */
        data class App(val packageName: String, val label: String) : Target
    }

    /**
     * The device's own equalizer: the standard panel, else an equalizer app found by its name. Users wrote "the app
     * does not see the system equalizer": head units keep theirs in an app of their own that never answers the
     * standard request. Asked each time: an equalizer installed, removed or switched off counts at once.
     */
    fun find(): Target? {
        if (audioSessionId > 0 && runCatching { panelIntent().resolveActivity(context.packageManager) != null }.getOrDefault(false)) {
            return Target.Panel
        }
        return runCatching { equalizerApp() }.getOrNull()
    }

    fun isAvailable(): Boolean = find() != null

    private fun equalizerApp(): Target.App? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { Target.App(it.packageName, it.loadLabel(pm).toString()) }
            .filter { EqualizerNames.looksLikeOne(it.label, it.packageName) }
            .sortedByDescending { EqualizerNames.strength(it.label, it.packageName) }
            .firstOrNull()
    }

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
        val target = find() ?: return
        val intent = when (target) {
            Target.Panel -> panelIntent()
            is Target.App -> context.packageManager.getLaunchIntentForPackage(target.packageName) ?: return
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No equalizer to open", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Equalizer refused to open", e)
        }
    }

    /** Tells an equalizer app by its name or package. Pure; unit-tested. */
    internal object EqualizerNames {
        /** Words only an equalizer or a sound-effects app has. */
        private val STRONG = listOf(
            "эквалайз", "equaliz", "equalis", "звуковые эффекты", "аудиоэффект", "sound effect", "audio effect",
            "soundalive", "misound", "dolby", "dirac", "viper4", "viperfx", "wavelet", "jamesdsp", "boom3d"
        )
        private val WORD = Regex("(^|[^a-zа-я])(eq|dsp)([^a-zа-я]|$)")
        private val PACKAGE = Regex("(^|\\.)(eq|equalizer|equaliser|dsp|audiofx|soundfx|musicfx)(\\.|$)")

        fun looksLikeOne(label: String, packageName: String): Boolean = strength(label, packageName) > 0

        fun strength(label: String, packageName: String): Int {
            val l = label.lowercase()
            val p = packageName.lowercase()
            return when {
                STRONG.any { it in l } -> 3
                WORD.containsMatchIn(l) -> 2
                PACKAGE.containsMatchIn(p) || STRONG.any { it.replace(" ", "") in p } -> 1
                else -> 0
            }
        }
    }

    companion object {
        private const val TAG = "SystemEqualizer"
        /** A pause at least this long is a real one, not the blink between two tracks. */
        const val RESUME_GAP_MS = 5_000L
        private const val REPEAT_WINDOW_MS = 1_000L
    }
}

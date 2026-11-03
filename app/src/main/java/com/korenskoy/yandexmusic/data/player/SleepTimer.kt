package com.korenskoy.yandexmusic.data.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The sleep timer: after some minutes, or at the end of the current track, playback fades out and
 * pauses. [fadeOutAndPause] does the fade, [onFinished] runs once playback has stopped.
 */
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val fadeOutAndPause: (Long) -> Unit,
    private val onFinished: suspend () -> Unit
) {
    private val _state = MutableStateFlow<SleepTimerState?>(null)
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()
    private var job: Job? = null

    /** Pause after [minutes] with a gentle fade, like the sleep timer in Apple Music. */
    fun start(minutes: Int) {
        job?.cancel()
        val durationMs = minutes * 60_000L
        _state.value = SleepTimerState(android.os.SystemClock.elapsedRealtime() + durationMs, atEndOfTrack = false)
        job = scope.launch {
            delay((durationMs - FADE_MS).coerceAtLeast(0L))
            fadeOutAndPause(FADE_MS)
            delay(FADE_MS)
            _state.value = null
            onFinished()
        }
    }

    /** Pause when the current track finishes instead of moving on. */
    fun atEndOfTrack() {
        job?.cancel()
        _state.value = SleepTimerState(endsAtElapsedMs = null, atEndOfTrack = true)
    }

    fun cancel() {
        job?.cancel()
        _state.value = null
    }

    /**
     * The track ended: if the timer waits for that, fade out over [tailMs] (the crossfade tail, when the
     * end is announced early) and return true — the caller must not move on.
     */
    fun takeEndOfTrack(tailMs: Long): Boolean {
        if (_state.value?.atEndOfTrack != true) return false
        _state.value = null
        fadeOutAndPause(minOf(tailMs, FADE_MS))
        scope.launch { onFinished() }
        return true
    }

    companion object {
        const val FADE_MS = 8_000L
    }
}

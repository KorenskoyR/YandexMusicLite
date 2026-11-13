package com.korenskoy.yandexmusic.data.player

import android.media.AudioManager

/**
 * The sound shared with other apps (Android's audio focus), as rules apart from the service, so each order of
 * events can be checked:
 *  - another app takes the sound for good (a video, another player): the music pauses and stays paused;
 *  - for a while (a call, an assistant): it pauses and comes back after, also when it was only starting, or a
 *    track came to start meanwhile;
 *  - with a quieter voice on top (a navigator, a notification): it plays on, quieter;
 *  - while the sound is away, nothing starts over it without asking again: during a call Android says no, and
 *    the music waits for the call to end;
 *  - a pause by the listener is kept: the end of a call does not start the music again.
 */
class FocusPolicy(private val actions: Actions) {

    interface Actions {
        /** Asks Android for the sound: one of [AudioManager]'s AUDIOFOCUS_REQUEST_* results, or [UNAVAILABLE]. */
        fun request(): Int
        fun abandon()
        /** Whether the music plays or is starting. */
        fun wantsToPlay(): Boolean
        fun pause(cause: StopCause)
        fun resume()
        fun duck(on: Boolean)
    }

    var hasFocus = false
        private set

    /** The music comes back when the sound does. */
    var resumeOnGain = false
        private set

    /** Another app holds the sound for a while. */
    var lostForAWhile = false
        private set

    fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                lostForAWhile = false
                resumeOnGain = false
                actions.duck(false)
                if (actions.wantsToPlay()) actions.pause(StopCause.FOCUS_LOST)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                lostForAWhile = true
                // Already waiting to come back (a second loss in a row) stays waiting
                if (actions.wantsToPlay()) {
                    resumeOnGain = true
                    actions.pause(StopCause.FOCUS_TRANSIENT)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> actions.duck(true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                lostForAWhile = false
                actions.duck(false)
                if (resumeOnGain) {
                    resumeOnGain = false
                    actions.resume()
                } else if (!actions.wantsToPlay()) {
                    // Paused by the listener meanwhile: the sound is not held in silence
                    abandon()
                }
            }
        }
    }

    /** The music started or stopped wanting to play. */
    fun onWantsToPlay(playing: Boolean) {
        if (!playing) {
            // Waiting for the sound to come back: holding the place in line is what brings it back
            if (!resumeOnGain && !lostForAWhile) abandon()
            return
        }
        if (hasFocus) return
        when (actions.request()) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasFocus = true
                lostForAWhile = false
                // Playing already: nothing to bring back later
                resumeOnGain = false
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                resumeOnGain = true
                actions.pause(StopCause.FOCUS_DENIED)
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                // A call is on: the music waits for it to end, when it was the music's sound that the call took
                if (lostForAWhile) resumeOnGain = true
                actions.pause(StopCause.FOCUS_DENIED)
            }
            else -> hasFocus = true // no AudioManager: nothing to share with
        }
    }

    /** The listener paused: the end of a call does not bring the music back. */
    fun onListenerPause() {
        resumeOnGain = false
    }

    fun abandon() {
        hasFocus = false
        lostForAWhile = false
        actions.abandon()
    }

    companion object {
        /** [Actions.request] without an AudioManager. */
        const val UNAVAILABLE = -1
    }
}

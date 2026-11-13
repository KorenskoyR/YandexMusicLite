package com.korenskoy.yandexmusic.data.player

import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED
import android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.14.7: the sound shared with other apps, in every order of events met on phones and head units. */
class FocusPolicyTest {

    /** A player and Android as the rules see them: what they were asked to do is written down. */
    private class World : FocusPolicy.Actions {
        var playing = false
        var answer = AUDIOFOCUS_REQUEST_GRANTED
        val done = mutableListOf<String>()
        override fun request(): Int = answer.also { done += "request" }
        override fun abandon() {
            done += "abandon"
        }
        override fun wantsToPlay(): Boolean = playing
        override fun pause(cause: StopCause) {
            playing = false
            done += "pause $cause"
        }
        override fun resume() {
            playing = true
            done += "resume"
        }
        override fun duck(on: Boolean) {
            done += "duck $on"
        }
    }

    private val world = World()
    private val focus = FocusPolicy(world)

    /** The music starts, and the rules hear of it as the service does. */
    private fun start() {
        world.playing = true
        focus.onWantsToPlay(true)
    }

    private fun changed(change: Int) {
        focus.onFocusChange(change)
        focus.onWantsToPlay(world.playing)
    }

    @Test
    fun aCallPausesTheMusicAndItsEndBringsItBack() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue("pause FOCUS_TRANSIENT" in world.done)
        assertFalse("its place in line is kept", "abandon" in world.done)
        changed(AUDIOFOCUS_GAIN)
        assertTrue(world.playing)
        assertEquals(1, world.done.count { it == "resume" })
    }

    @Test
    fun aSecondLossInARowStillBringsTheMusicBack() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        changed(AUDIOFOCUS_LOSS_TRANSIENT) // a ringtone, then the call itself
        changed(AUDIOFOCUS_GAIN)
        assertTrue(world.playing)
    }

    @Test
    fun aTrackThatCameToStartDuringTheCallWaitsForItsEnd() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        // The next track's start lands while the call is on: Android says no, the music does not play over it
        world.answer = AUDIOFOCUS_REQUEST_FAILED
        start()
        assertEquals("pause FOCUS_DENIED", world.done.last())
        assertFalse(world.playing)
        changed(AUDIOFOCUS_GAIN)
        assertTrue("after the call", world.playing)
    }

    @Test
    fun anotherAppTakingTheSoundForGoodLeavesTheMusicPaused() {
        start()
        changed(AUDIOFOCUS_LOSS)
        assertTrue("pause FOCUS_LOST" in world.done)
        changed(AUDIOFOCUS_GAIN)
        assertFalse(world.playing)
        assertFalse("resume" in world.done)
    }

    @Test
    fun theListenersPauseDuringACallIsKept() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        focus.onListenerPause()
        changed(AUDIOFOCUS_GAIN)
        assertFalse(world.playing)
        assertEquals("the sound is not held in silence", "abandon", world.done.last())
    }

    @Test
    fun aNavigatorsVoicePlaysOverTheMusicQuieter() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(world.playing)
        assertEquals("duck true", world.done.last())
        changed(AUDIOFOCUS_GAIN)
        assertTrue(world.playing)
        assertTrue("duck false" in world.done)
    }

    @Test
    fun aLateAnswerPausesTheMusicUntilTheSoundComes() {
        world.answer = AUDIOFOCUS_REQUEST_DELAYED
        start()
        assertFalse(world.playing)
        changed(AUDIOFOCUS_GAIN)
        assertTrue(world.playing)
    }

    @Test
    fun theSoundIsAskedForOnceAndGivenBackWhenTheMusicStops() {
        start()
        start()
        assertEquals(1, world.done.count { it == "request" })
        world.playing = false
        focus.onWantsToPlay(false)
        assertEquals("abandon", world.done.last())
        assertFalse(focus.hasFocus)
    }

    @Test
    fun aLossWhileNothingPlaysPausesNothing() {
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        changed(AUDIOFOCUS_LOSS)
        assertFalse(world.done.any { it.startsWith("pause") })
        changed(AUDIOFOCUS_GAIN)
        assertFalse(world.playing)
    }

    @Test
    fun aPlayDuringAnAssistantTakesTheSoundBack() {
        start()
        changed(AUDIOFOCUS_LOSS_TRANSIENT)
        // The listener presses Play while the assistant still holds the sound: Android gives it
        start()
        assertTrue(world.playing)
        assertTrue(focus.hasFocus)
        assertFalse(focus.lostForAWhile)
    }
}

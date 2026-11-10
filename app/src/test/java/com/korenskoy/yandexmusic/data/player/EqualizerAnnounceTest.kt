package com.korenskoy.yandexmusic.data.player

import android.app.Application
import android.media.audiofx.AudioEffect
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Equalizer apps hear about the session again whenever the sound comes back, and when it is over. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EqualizerAnnounceTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val equalizer = SystemEqualizer(app, audioSessionId = 77)

    private fun sent(action: String) = shadowOf(app).broadcastIntents.count {
        it.action == action && it.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) == 77
    }

    private val opened get() = sent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)

    @Test
    fun theSessionIsAnnouncedEachTimeTheSoundComesBack() = runTest(UnconfinedTestDispatcher()) {
        val playing = MutableStateFlow(false)
        val track = MutableStateFlow<String?>("a")
        val player = MutableStateFlow<Any>("A")
        val job = equalizer.keepAnnounced(backgroundScope, playing, track, player)

        assertEquals("nothing while paused", 0, opened)
        playing.value = true
        assertEquals("the sound starts", 1, opened)
        playing.value = false
        playing.value = true
        assertEquals("after a pause (a camera alert, a call)", 2, opened)
        track.value = "b"
        assertEquals("a new track", 3, opened)
        player.value = "B"
        assertEquals("the other player takes over (crossfade)", 4, opened)
        playing.value = false
        track.value = "c"
        assertEquals("a track picked while paused waits for the sound", 4, opened)
        playing.value = true
        assertEquals(5, opened)
        job.cancel()
    }

    @Test
    fun announcingByHandAndClosing() {
        equalizer.announce()
        assertEquals(1, opened)
        equalizer.close()
        assertEquals(1, sent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION))
    }

    @Test
    fun withoutASessionNothingIsSent() {
        val none = SystemEqualizer(app, audioSessionId = 0)
        none.announce()
        none.close()
        assertEquals(0, shadowOf(app).broadcastIntents.size)
    }
}

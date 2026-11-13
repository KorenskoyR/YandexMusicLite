package com.korenskoy.yandexmusic.presentation.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.14.7: a head unit without the "Google" app's voice window can still be spoken to. */
class VoiceRouteTest {

    @Test
    fun theSystemWindowFirstTheServiceSecondOtherwiseNoMicrophone() {
        assertEquals(VoiceRoute.SYSTEM_WINDOW, VoiceInput.routeOf(window = true, recognizer = true))
        assertEquals(VoiceRoute.SYSTEM_WINDOW, VoiceInput.routeOf(window = true, recognizer = false))
        assertEquals("the head unit of the report, with a recognition service", VoiceRoute.RECOGNIZER, VoiceInput.routeOf(window = false, recognizer = true))
        assertEquals(VoiceRoute.NONE, VoiceInput.routeOf(window = false, recognizer = false))
    }

    @Test
    fun eachErrorOfTheServiceSaysWhatToDo() {
        assertEquals(Listening.Missed, Listening.ofError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(Listening.Missed, Listening.ofError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(Listening.NoMic, Listening.ofError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(Listening.Failed(Listening.Why.NETWORK), Listening.ofError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(Listening.Failed(Listening.Why.NETWORK), Listening.ofError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(Listening.Failed(Listening.Why.NETWORK), Listening.ofError(SpeechRecognizer.ERROR_SERVER))
        assertEquals("Android 12: server disconnected", Listening.Failed(Listening.Why.NETWORK), Listening.ofError(11))
        assertEquals("Android 12: no Russian", Listening.Failed(Listening.Why.LANGUAGE), Listening.ofError(12))
        assertEquals(Listening.Failed(Listening.Why.LANGUAGE), Listening.ofError(13))
        assertEquals(Listening.Failed(Listening.Why.OTHER), Listening.ofError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }

    @Test
    fun theRingFollowsTheVoice() {
        assertEquals(0f, Listening.levelOf(-2f), 0.001f)
        assertEquals(0f, Listening.levelOf(-10f), 0.001f)
        assertEquals(0.5f, Listening.levelOf(4f), 0.001f)
        assertEquals(1f, Listening.levelOf(10f), 0.001f)
        assertEquals(1f, Listening.levelOf(30f), 0.001f)
    }
}

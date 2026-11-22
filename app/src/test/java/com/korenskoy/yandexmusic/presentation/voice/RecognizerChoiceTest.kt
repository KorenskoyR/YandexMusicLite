package com.korenskoy.yandexmusic.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.16: several speech services on one device — the owner's pick is kept, a vanished one is forgotten. */
class RecognizerChoiceTest {
    private val google = Recognizer("com.google.android.googlequicksearchbox/.Svc", "Google")
    private val alice = Recognizer("ru.yandex.searchplugin/.Svc", "Яндекс")

    @Test
    fun theSavedOneWinsWhenItIsStillThere() {
        assertEquals(alice, VoiceInput.chosen(listOf(google, alice), saved = alice.id))
    }

    @Test
    fun aVanishedOrUnsetPickFallsBackToTheFirstOrNone() {
        assertEquals(google, VoiceInput.chosen(listOf(google, alice), saved = "gone/.Svc"))
        assertEquals(google, VoiceInput.chosen(listOf(google, alice), saved = null))
        assertNull(VoiceInput.chosen(emptyList(), saved = alice.id))
    }

    @Test
    fun nextCyclesThroughAllAndWraps() {
        val all = listOf(google, alice)
        assertEquals(alice, VoiceInput.next(all, current = google))
        assertEquals(google, VoiceInput.next(all, current = alice))
        assertNull(VoiceInput.next(emptyList(), current = null))
    }
}

package com.korenskoy.yandexmusic.data.lyrics

import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.data.dto.LyricsInfoDto
import com.korenskoy.yandexmusic.data.dto.SupplementLyricsDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.TrackSupplementDto
import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.domain.model.MiniOnWave
import com.korenskoy.yandexmusic.presentation.main.components.sidePanelWidth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** 1.14.3: the words of tracks, "Перемешать" in "Любимое", the mini-player on the wave, the side panels' width. */
class UiRules1143Test {

    private fun track(id: String, available: Boolean? = null, info: Boolean? = null) =
        TrackDetailsDto(JsonPrimitive(id), title = "T$id", lyricsAvailable = available, lyricsInfo = info?.let { LyricsInfoDto(it) })

    // --- Lyrics ---

    @Test
    fun theWordsAreTidiedNotChanged() {
        val raw = "\n\nПервый куплет  \r\nвторая строка\r\n\r\n\r\n\r\nПрипев\n\n"
        assertEquals("Первый куплет\nвторая строка\n\nПрипев", LyricsRules.textOf(TrackSupplementDto(SupplementLyricsDto(fullLyrics = raw))))
    }

    @Test
    fun noWordsNoRightsNoText() {
        assertNull(LyricsRules.textOf(null))
        assertNull(LyricsRules.textOf(TrackSupplementDto(null)))
        assertNull(LyricsRules.textOf(TrackSupplementDto(SupplementLyricsDto(fullLyrics = " \n \n"))))
        assertNull("no rights to show them", LyricsRules.textOf(TrackSupplementDto(SupplementLyricsDto(fullLyrics = "слова", hasRights = false))))
    }

    @Test
    fun onlyCatalogueTracksYandexDidNotDenyAreAsked() {
        assertTrue(LyricsRules.canHave(track("123")))
        assertTrue(LyricsRules.canHave(track("123", available = true)))
        assertFalse("Yandex said no words", LyricsRules.canHave(track("123", available = false)))
        // lyricsInfo speaks of words behind a signed method: the open one follows lyricsAvailable only
        assertTrue("lyricsInfo does not decide", LyricsRules.canHave(track("123", available = true, info = false)))
        assertFalse("lyricsInfo does not decide", LyricsRules.canHave(track("123", available = false, info = true)))
        assertFalse("a station", LyricsRules.canHave(track("123").copy(isRadio = true)))
        assertFalse("the user's own file", LyricsRules.canHave(track("local_5")))
        assertFalse("an upload", LyricsRules.canHave(track("abc-def")))
    }

    @Test
    fun theServerSaysWhetherATrackHasWords() {
        val json = Json { ignoreUnknownKeys = true }
        // Most of the chart says so (live check, 100 tracks: 74 like this, none had words from "supplement")
        val withInfo = json.decodeFromString<TrackDetailsDto>("""{"id":"1","lyricsAvailable":false,"lyricsInfo":{"hasAvailableTextLyrics":true,"hasAvailableSyncLyrics":false}}""")
        assertEquals(false, withInfo.hasLyrics)
        val old = json.decodeFromString<TrackDetailsDto>("""{"id":"1","lyricsAvailable":true}""")
        assertEquals(true, old.hasLyrics)
        assertNull(json.decodeFromString<TrackDetailsDto>("""{"id":"1"}""").hasLyrics)
        val answer = json.decodeFromString<TrackSupplementDto>("""{"id":"x","lyrics":{"id":7,"fullLyrics":"а\nб","hasRights":true,"textLanguage":"ru","showTranslation":false}}""")
        assertEquals("а\nб", LyricsRules.textOf(answer))
    }

    @Test
    fun aFailureIsAskedAgainAnAnswerIsNot() = runBlocking {
        val asked = AtomicInteger()
        var reply: LyricsAnswer = LyricsAnswer.Failed
        val lyrics = Lyrics { asked.incrementAndGet(); reply }
        assertEquals(LyricsAnswer.Failed, lyrics.of("1"))
        assertNull("a failure is not kept", lyrics.known("1"))
        reply = LyricsAnswer.Text("слова")
        assertEquals(LyricsAnswer.Text("слова"), lyrics.of("1"))
        assertEquals(LyricsAnswer.Text("слова"), lyrics.of("1"))
        assertEquals(LyricsAnswer.Text("слова"), lyrics.known("1"))
        assertEquals(2, asked.get())
        reply = LyricsAnswer.None
        lyrics.of("2"); lyrics.of("2")
        assertEquals("no words is an answer too", 3, asked.get())
    }

    // --- "Перемешать" ---

    @Test
    fun shufflingKeepsTheCurrentTrackFirstAndEveryTrackOnce() {
        val queue = (1..30).map { "t$it" }
        val shuffled = PlaybackQueue.shuffledAround(queue, "t12", { it }, Random(7))
        assertEquals("t12", shuffled.first())
        assertEquals(queue.toSet(), shuffled.toSet())
        assertEquals(queue.size, shuffled.size)
        assertTrue("in fact shuffled", shuffled.drop(1) != queue.filter { it != "t12" })
    }

    @Test
    fun unshufflingRestoresTheOrderWithWhatChangedMeanwhile() {
        val original = listOf("a", "b", "c", "d", "e")
        val shuffled = listOf("c", "e", "a", "d", "b")
        assertEquals(original, PlaybackQueue.restored(shuffled, original) { it })
        // "d" removed, "x" added after shuffling
        assertEquals(listOf("a", "b", "c", "e", "x"), PlaybackQueue.restored(listOf("c", "x", "e", "a", "b"), original) { it })
    }

    // --- Mini-player on the wave ---

    @Test
    fun theMiniPlayerShowsWhereChosen() {
        assertTrue(MiniOnWave.ALWAYS.shows(true) && MiniOnWave.ALWAYS.shows(false))
        assertTrue(MiniOnWave.PORTRAIT.shows(false) && !MiniOnWave.PORTRAIT.shows(true))
        assertTrue(MiniOnWave.LANDSCAPE.shows(true) && !MiniOnWave.LANDSCAPE.shows(false))
    }

    @Test
    fun theWavePageLeavesRoomForTheMiniPlayerWhenItShows() {
        val all = 168.dp
        assertEquals("standing, the wave alone: only the tab bar", 100.dp, com.korenskoy.yandexmusic.presentation.main.waveContentBottom(true, false, false, all))
        assertEquals("standing with the mini-player («Всегда», «Вертикально»)", all, com.korenskoy.yandexmusic.presentation.main.waveContentBottom(true, false, true, all))
        assertEquals("lying: the dock's room", all, com.korenskoy.yandexmusic.presentation.main.waveContentBottom(true, true, false, all))
        assertEquals("another source plays: as every page", all, com.korenskoy.yandexmusic.presentation.main.waveContentBottom(false, false, true, all))
    }

    // --- Side panels ---

    @Test
    fun sidePanelsAreNeitherCrampedNorHuge() {
        assertEquals(360.dp, sidePanelWidth(640.dp))
        assertEquals(460.8.dp.value, sidePanelWidth(1024.dp).value, 0.01f)
        assertEquals(520.dp, sidePanelWidth(1920.dp))
        assertEquals("never wider than the screen", 320.dp, sidePanelWidth(320.dp))
    }
}

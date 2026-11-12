package com.korenskoy.yandexmusic.data.lyrics

import com.korenskoy.yandexmusic.data.dto.LyricsInfoDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.LosslessSigner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncedLyricsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- Signatures ---

    @Test
    fun lyricsSignatureHasPaddingAndStripsAlbum() {
        val sign1 = LosslessSigner.signLyrics("12345", tsSeconds = 1700000000L)
        val sign2 = LosslessSigner.signLyrics("12345:6789", tsSeconds = 1700000000L)
        assertEquals(sign1.sign, sign2.sign)
        assertEquals(1700000000L, sign1.ts)
        assertTrue("Base64 string", sign1.sign.isNotBlank())
    }

    // --- LRC Parsing ---

    @Test
    fun parsesStandardLrcWithVariousTimestampFormats() {
        val lrc = """
            [ti:Test Title]
            [ar:Artist]
            [00:05.50]First line
            [00:10.123]Second line with 3 fraction digits
            [00:15]Third line with no fraction
            [01:02.4]Fourth line with 1 fraction digit
        """.trimIndent()

        val lines = LyricsRules.parseLrc(lrc)
        assertEquals(4, lines.size)
        assertEquals(LyricLine(5_500L, "First line"), lines[0])
        assertEquals(LyricLine(10_123L, "Second line with 3 fraction digits"), lines[1])
        assertEquals(LyricLine(15_000L, "Third line with no fraction"), lines[2])
        assertEquals(LyricLine(62_400L, "Fourth line with 1 fraction digit"), lines[3])
    }

    @Test
    fun parsesMultipleTimestampsOnSingleLine() {
        val lrc = "[00:10.00][00:30.00]Chorus line"
        val lines = LyricsRules.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals(LyricLine(10_000L, "Chorus line"), lines[0])
        assertEquals(LyricLine(30_000L, "Chorus line"), lines[1])
    }

    @Test
    fun ignoresUtf8BomAndStripsInlineKaraokeTags() {
        val lrc = "\uFEFF[00:02.00]<00:02.00>Hello <00:02.50>world"
        val lines = LyricsRules.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(LyricLine(2_000L, "Hello world"), lines[0])
    }

    @Test
    fun emptyOrMetadataOnlyLrcReturnsEmpty() {
        val metadataOnly = "[ti:Title]\n[ar:Artist]\n[al:Album]"
        assertTrue(LyricsRules.parseLrc(metadataOnly).isEmpty())
        assertTrue(LyricsRules.parseLrc("").isEmpty())
    }

    // --- Current Line Tracking (Binary Search) ---

    @Test
    fun currentIndexTracksSongPosition() {
        val lines = listOf(
            LyricLine(2_000L, "Intro line"),
            LyricLine(6_000L, "Verse 1"),
            LyricLine(12_000L, "Chorus")
        )

        assertEquals("before the first line", -1, LyricsRules.currentIndex(lines, 1_000L))
        assertEquals("at line 1", 0, LyricsRules.currentIndex(lines, 2_000L))
        assertEquals("during line 1", 0, LyricsRules.currentIndex(lines, 4_500L))
        assertEquals("at line 2", 1, LyricsRules.currentIndex(lines, 6_000L))
        assertEquals("during line 2", 1, LyricsRules.currentIndex(lines, 10_000L))
        assertEquals("at line 3", 2, LyricsRules.currentIndex(lines, 12_000L))
        assertEquals("after line 3", 2, LyricsRules.currentIndex(lines, 99_000L))
    }

    // --- Offline Disk Cache ---

    @Test
    fun diskCacheStoresAndRestoresSyncedLyrics() = runBlocking {
        val cacheDir = tempFolder.newFolder("lyrics_cache")
        val lines = listOf(LyricLine(1_000L, "Hello"), LyricLine(5_000L, "World"))
        var networkCalls = 0

        val lyrics = Lyrics(
            fetch = {
                networkCalls++
                LyricsAnswer.Synced(lines)
            },
            cacheDir = cacheDir
        )

        // First call fetches from network and writes to disk
        val answer1 = lyrics.of("42")
        assertEquals(LyricsAnswer.Synced(lines), answer1)
        assertEquals(1, networkCalls)
        assertTrue(File(cacheDir, "42.lrc").exists())

        // Create new Lyrics instance pointing to same disk cache: reads from disk without network
        val freshLyrics = Lyrics(
            fetch = {
                networkCalls++
                LyricsAnswer.Failed
            },
            cacheDir = cacheDir
        )

        val answer2 = freshLyrics.of("42")
        assertEquals(LyricsAnswer.Synced(lines), answer2)
        assertEquals("no extra network calls on disk cache hit", 1, networkCalls)
    }

    // --- Main-Safety ---

    @Test
    fun ofIsMainSafeAndExecutesFetchOnIoDispatcher() = runBlocking {
        val lines = listOf(LyricLine(1_000L, "IO thread line"))
        var fetchThread = ""
        val lyrics = Lyrics(fetch = {
            fetchThread = Thread.currentThread().name
            LyricsAnswer.Synced(lines)
        })

        val answer = lyrics.of("999")
        assertEquals(LyricsAnswer.Synced(lines), answer)
        assertTrue("fetch must execute on Dispatchers.IO worker thread: $fetchThread", fetchThread.contains("DefaultDispatcher") || fetchThread.contains("IO"))
    }

    // --- CanHave rules ---

    @Test
    fun canHaveAllowsSyncedLyricsEvenWhenAvailableIsFalse() {
        val trackWithSync = TrackDetailsDto(
            id = JsonPrimitive("100"),
            title = "Track",
            lyricsAvailable = false,
            lyricsInfo = LyricsInfoDto(hasAvailableSyncLyrics = true)
        )
        assertTrue("track with synced lyrics is allowed to ask", LyricsRules.canHave(trackWithSync))

        val trackDenied = TrackDetailsDto(
            id = JsonPrimitive("101"),
            title = "Track",
            lyricsAvailable = false,
            lyricsInfo = LyricsInfoDto(hasAvailableSyncLyrics = false)
        )
        assertFalse("track with explicitly denied lyrics is rejected", LyricsRules.canHave(trackDenied))
    }
}


package com.korenskoy.yandexmusic.data.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/** The rules that keep the music going on an uneven link. No player needed; Robolectric only for media3's Uri. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SmoothPlaybackTest {
    private var clock = 0L
    private val steadiness = LinkSteadiness { clock }

    @Test
    fun oneLongStopMakesTheLinkUneven() {
        assertFalse("a stop alone is not yet a pattern", steadiness.onStall())
        clock += 1_000
        assertFalse("1 s is still short", steadiness.onStallLasted())
        clock += 500
        assertTrue("1.5 s is long", steadiness.onStallLasted())
        assertTrue(steadiness.uneven)
        assertFalse("said once", steadiness.onStallLasted())
    }

    @Test
    fun twoShortStopsWithinTwoMinutesMakeItUneven() {
        steadiness.onStall()
        clock += 400
        assertEquals(400L, steadiness.onStallOver())
        clock += 90_000
        assertTrue(steadiness.onStall())
    }

    @Test
    fun stopsFarApartDoNot() {
        steadiness.onStall()
        clock += 300
        steadiness.onStallOver()
        clock += LinkSteadiness.WINDOW_MS + 1_000
        assertFalse(steadiness.onStall())
        assertFalse(steadiness.uneven)
    }

    @Test
    fun fiveSteadyMinutesBringTheChosenQualityBack() {
        steadiness.onStall()
        clock += 2_000
        steadiness.onStallLasted()
        steadiness.onStallOver()
        clock += LinkSteadiness.STEADY_MS - 1
        assertFalse(steadiness.checkSteady())
        clock += 1
        assertTrue(steadiness.checkSteady())
        assertFalse(steadiness.uneven)
    }

    @Test
    fun notSteadyWhileTheMusicStillWaits() {
        steadiness.onStall()
        clock += 2_000
        steadiness.onStallLasted()
        clock += LinkSteadiness.STEADY_MS * 2
        assertFalse("still standing", steadiness.checkSteady())
    }

    @Test
    fun anUnevenLinkPlaysTheCopyThenALightStream() {
        assertEquals(SmoothOrigin.COPY, SmoothPlan.originFor(uneven = true, smoothOn = true, hasCopy = true))
        assertEquals(SmoothOrigin.LIGHT_STREAM, SmoothPlan.originFor(uneven = true, smoothOn = true, hasCopy = false))
        assertEquals("the setting off: as before", SmoothOrigin.CHOSEN_STREAM, SmoothPlan.originFor(uneven = true, smoothOn = false, hasCopy = true))
        assertEquals(SmoothOrigin.CHOSEN_STREAM, SmoothPlan.originFor(uneven = false, smoothOn = true, hasCopy = true))
    }

    private fun errorInfo(error: IOException, count: Int) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(0L, DataSpec.Builder().setUri("https://example.org/a.mp3").build(), 0L),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        error,
        count
    )

    private fun httpError(code: Int) = HttpDataSource.InvalidResponseCodeException(
        code, null, null, emptyMap(), DataSpec.Builder().setUri("https://example.org/a.mp3").build(), ByteArray(0)
    )

    @Test
    fun aBrokenDownloadIsRetriedWithGrowingPausesForAboutAMinute() {
        val policy = PlaybackBuffering.PatientLoadErrors()
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(SocketTimeoutException(), 1)))
        assertEquals(3_000L, policy.getRetryDelayMsFor(errorInfo(IOException("reset"), 3)))
        assertEquals(5_000L, policy.getRetryDelayMsFor(errorInfo(IOException("reset"), 9)))
        val total = (1..PlaybackBuffering.RETRIES).sumOf { PlaybackBuffering.retryDelayMs(it) }
        assertTrue("about a minute in all: $total", total in 50_000L..70_000L)
        assertEquals(PlaybackBuffering.RETRIES, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun aLinkTheServerRefusesIsNotRetried() {
        val policy = PlaybackBuffering.PatientLoadErrors()
        assertEquals("expired link: a new one at once", C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(httpError(403), 1)))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(httpError(410), 1)))
        assertTrue("a busy server is tried again", policy.getRetryDelayMsFor(errorInfo(httpError(503), 1)) > 0)
    }

    @Test
    fun theJournalKeepsTheLastHour() {
        var now = 0L
        val journal = PlaybackJournal { now }
        journal.addPlayed(60_000)
        journal.record(PlaybackJournal.Kind.STALL, "Хочешь?", 2_000)
        journal.record(PlaybackJournal.Kind.CROSSFADE)
        journal.record(PlaybackJournal.Kind.CROSSFADE_LATE, "Группа крови")
        journal.record(PlaybackJournal.Kind.COPY, "Хочешь?", 82_000)
        val s = journal.state.value.summary
        assertEquals(1, s.stalls)
        assertEquals(2_000L, s.stalledMs)
        assertEquals(2, s.crossfades)
        assertEquals(1, s.crossfadesLate)
        assertEquals(1, s.copySwitches)
        assertEquals("ordinary crossfades are only counted", 3, journal.state.value.events.size)

        now += PlaybackJournal.WINDOW_MS + 1
        journal.record(PlaybackJournal.Kind.UNEVEN)
        assertEquals(0, journal.state.value.summary.stalls)
        assertEquals(0L, journal.state.value.summary.playedMs)
        assertEquals(1, journal.state.value.events.size)
    }
}

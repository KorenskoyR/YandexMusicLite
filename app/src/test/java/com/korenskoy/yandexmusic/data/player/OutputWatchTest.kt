package com.korenskoy.yandexmusic.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.14.8 report: with Bluetooth headphones the music fell silent in every track while the timer counted on and
 * jumped back 3–5 s; the journal was empty. The watch tells both.
 */
class OutputWatchTest {
    private val watch = OutputWatch()
    private var clock = 10_000L
    private var fed = 10_000L
    private var position = 0L

    /** Plays [ms] in ticks; [taking] — the output takes the sound (the player hands it on). */
    private fun play(ms: Long, taking: Boolean = true, advancing: Boolean = true): List<OutputWatch.Finding> {
        val found = mutableListOf<OutputWatch.Finding>()
        var left = ms
        while (left > 0) {
            clock += OutputWatch.TICK_MS
            left -= OutputWatch.TICK_MS
            if (taking) fed = clock - 20
            if (advancing) position += OutputWatch.TICK_MS
            watch.tick(clock, playing = true, lastFedAtMs = fed, positionMs = position)?.let(found::add)
        }
        return found
    }

    @Test
    fun steadyMusicIsQuiet() {
        assertEquals(emptyList<OutputWatch.Finding>(), play(60_000))
    }

    @Test
    fun anOutputThatStopsTakingSoundIsToldWithHowLong() {
        play(5_000)
        val silentFrom = fed
        assertEquals(emptyList<OutputWatch.Finding>(), play(4_000, taking = false))
        val found = play(1_000)
        assertEquals(1, found.size)
        val stall = found.single() as OutputWatch.Finding.OutputStall
        assertEquals(silentFrom, stall.startedAtMs)
        assertEquals(4_000.0, stall.durationMs.toDouble(), 300.0)
    }

    @Test
    fun aShortBreathOfTheSinkIsNotAStall() {
        play(5_000)
        play(OutputWatch.STALL_MS - OutputWatch.TICK_MS, taking = false)
        assertEquals(emptyList<OutputWatch.Finding>(), play(2_000))
    }

    @Test
    fun theTimerJumpingBackIsTold() {
        play(10_000)
        position -= 4_000
        val found = play(OutputWatch.TICK_MS)
        assertEquals(1, found.size)
        assertEquals(4_000.0 - OutputWatch.TICK_MS, (found.single() as OutputWatch.Finding.PositionBack).byMs.toDouble(), 1.0)
    }

    @Test
    fun aSeekBackIsNotAJump() {
        play(10_000)
        watch.reset()
        position -= 8_000
        assertEquals(emptyList<OutputWatch.Finding>(), play(2_000))
    }

    @Test
    fun theLastSoundOfAnOldPlayIsNotTheStartOfAStall() {
        // The output last took sound an hour and twenty minutes ago (another play); the watch starts now
        fed = clock - 4_891_000
        play(3_000, taking = false)
        val found = play(1_000)
        val stall = found.single() as OutputWatch.Finding.OutputStall
        assertTrue("counted from the start of the watch, not 81 minutes back: ${stall.durationMs}", stall.durationMs in 2_500..3_500)
    }

    @Test
    fun aPauseIsNotAStall() {
        play(5_000)
        clock += 20_000
        assertNull(watch.tick(clock, playing = false, lastFedAtMs = fed, positionMs = position))
        fed = clock
        assertEquals(emptyList<OutputWatch.Finding>(), play(3_000))
    }
}

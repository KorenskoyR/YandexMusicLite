package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.presentation.car.CarSource
import com.korenskoy.yandexmusic.presentation.car.carSourceOf
import com.korenskoy.yandexmusic.presentation.hifi.TimelineMath
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLitePolicy
import com.korenskoy.yandexmusic.presentation.hifi.RingMode
import com.korenskoy.yandexmusic.presentation.hifi.SpectrumMath
import com.korenskoy.yandexmusic.presentation.hifi.TintMath
import com.korenskoy.yandexmusic.presentation.radio.RadioDial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure logic of the Hi-Fi redesign: lite mode, cover tint, spectrum ring, FM dial, car timeline, wave shelf. */
class HiFiLogicTest {

    // --- Lite mode -------------------------------------------------------------------------------

    @Test
    fun liteModeOnlyWhenTheDeviceAsksForIt() {
        assertFalse(HiFiLitePolicy.isLite(sdkInt = 35, lowRamDevice = false, powerSave = false, animatorScale = 1f))
        assertTrue("Android 9 and older", HiFiLitePolicy.isLite(28, false, false, 1f))
        assertTrue("low-RAM head units", HiFiLitePolicy.isLite(35, true, false, 1f))
        assertTrue("battery saver", HiFiLitePolicy.isLite(35, false, true, 1f))
        assertTrue("animations switched off", HiFiLitePolicy.isLite(35, false, false, 0f))
        assertFalse("slowed animations are still animations", HiFiLitePolicy.isLite(35, false, false, 0.5f))
        assertFalse("Android 10 is the floor", HiFiLitePolicy.isLite(29, false, false, 1f))
    }

    // --- Cover tint ------------------------------------------------------------------------------

    private fun hsv(argb: Int): FloatArray =
        TintMath.rgbToHsv(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)

    @Test
    fun hsvRoundTripKeepsTheColor() {
        listOf(0xFF0A6CFF.toInt(), 0xFFFF3366.toInt(), 0xFF33CC66.toInt(), 0xFF808080.toInt(), 0xFFFFCC00.toInt()).forEach { c ->
            val h = hsv(c)
            val back = TintMath.hsvToArgb(h[0], h[1], h[2])
            for (shift in intArrayOf(16, 8, 0)) {
                val d = abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("channel drift $d for ${Integer.toHexString(c)}", d <= 1)
            }
            assertEquals("opaque", 0xFF, (back ushr 24))
        }
    }

    @Test
    fun tintIsNeverMuddyNorWashedOut() {
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFF203040.toInt(), 0xFFFFE0E0.toInt(), 0xFF0A6CFF.toInt())
            .forEach { c ->
                val v = hsv(TintMath.normalize(c))
                assertTrue("value ${v[2]} in 0.42..0.80 for ${Integer.toHexString(c)}", v[2] in 0.41f..0.81f)
            }
    }

    @Test
    fun colorfulCoversKeepTheirHueAndGetEnoughColor() {
        val pale = hsv(TintMath.normalize(0xFFD9A0A0.toInt())) // pale red
        assertTrue("hue stays red: ${pale[0]}", pale[0] < 10f || pale[0] > 350f)
        assertTrue("saturation lifted: ${pale[1]}", pale[1] >= 0.34f)
    }

    @Test
    fun greyCoversBecomeQuietBlueGrey() {
        val grey = hsv(TintMath.normalize(0xFF777777.toInt()))
        assertTrue("hue ~220: ${grey[0]}", abs(grey[0] - 220f) < 6f)
        assertTrue("barely saturated: ${grey[1]}", grey[1] in 0.10f..0.18f)
    }

    // --- Spectrum ring ---------------------------------------------------------------------------

    @Test
    fun pausedRingCollapsesToDots() {
        repeat(72) { assertEquals(0f, SpectrumMath.level(it, 72, 3.3f, RingMode.Paused), 0f) }
    }

    @Test
    fun playingBarsStayVisibleAndBounded() {
        for (t in listOf(0f, 0.7f, 12.5f, 999f)) repeat(90) {
            val l = SpectrumMath.level(it, 90, t, RingMode.Playing)
            assertTrue("bar $it at $t: $l", l in 0.08f..1f)
        }
    }

    @Test
    fun playingRingMoves() {
        val a = (0 until 72).map { SpectrumMath.level(it, 72, 1f, RingMode.Playing) }
        val b = (0 until 72).map { SpectrumMath.level(it, 72, 1.5f, RingMode.Playing) }
        assertTrue(a != b)
    }

    /**
     * How much a ring pattern drifts clockwise (> 0) or anticlockwise (< 0) between two frames [dt] apart:
     * the correlation of each frame with the next one shifted a bar either way.
     */
    private fun drift(n: Int, dt: Float, level: (Int, Float) -> Float): Double {
        var cw = 0.0
        var ccw = 0.0
        var same = 0.0
        var t = 0f
        repeat(400) {
            val a = (0 until n).map { level(it, t).toDouble() }
            val b = (0 until n).map { level(it, t + dt).toDouble() }
            val ma = a.average()
            val mb = b.average()
            for (i in 0 until n) {
                val x = a[i] - ma
                cw += x * (b[(i + 1) % n] - mb)
                ccw += x * (b[(i - 1 + n) % n] - mb)
                same += x * (b[i] - mb)
            }
            t += 0.137f
        }
        return (cw - ccw) / kotlin.math.abs(same).coerceAtLeast(1e-9)
    }

    @Test
    fun driftMeasureSeesATravellingWave() {
        // The old playing formula: the same shape moving to lower bars, i.e. anticlockwise
        val old = drift(72, 0.19f) { i, t -> 0.5f + 0.5f * kotlin.math.sin(i * 0.42f + t * 2.2f) }
        assertTrue("anticlockwise: $old", old < -0.25)
    }

    @Test
    fun playingRingDoesNotTurn() {
        val d = drift(72, 0.19f) { i, t -> SpectrumMath.level(i, 72, t, RingMode.Playing) }
        assertTrue("no direction: $d", kotlin.math.abs(d) < 0.1)
    }

    @Test
    fun loadingRingPulsesTogetherWithoutTurning() {
        val d = drift(72, 0.05f) { i, t -> SpectrumMath.level(i, 72, t, RingMode.Loading) }
        assertTrue("no direction: $d", kotlin.math.abs(d) < 0.1)
        // All bars rise and fall at once: at the low point every bar is short, at the high point every bar is long
        val low = (0 until 72).map { SpectrumMath.level(it, 72, 0f, RingMode.Loading) }
        val high = (0 until 72).map { SpectrumMath.level(it, 72, 0.7f, RingMode.Loading) }
        assertTrue(low.max() < 0.25f)
        assertTrue(high.min() > 0.75f)
    }

    @Test
    fun playedArcFollowsProgressClockwise() {
        assertEquals(0, (0 until 72).count { SpectrumMath.isPlayed(it, 72, 0f) })
        assertEquals(36, (0 until 72).count { SpectrumMath.isPlayed(it, 72, 0.5f) })
        assertEquals(72, (0 until 72).count { SpectrumMath.isPlayed(it, 72, 1f) })
        assertTrue("starts at the top", SpectrumMath.isPlayed(0, 72, 0.01f))
    }

    // --- FM dial ---------------------------------------------------------------------------------

    private fun station(id: String, freq: String) = RadioStation(id, id, freq, "", "", RadioCategory.ALL)

    @Test
    fun parsesFrequencies() {
        assertEquals(106.2f, RadioDial.parseMhz("106.2 FM")!!, 0.001f)
        assertEquals(91.4f, RadioDial.parseMhz("91,4")!!, 0.001f)
        assertNull("online only", RadioDial.parseMhz("Online"))
        assertNull("outside the FM band", RadioDial.parseMhz("120.5"))
    }

    @Test
    fun dialFractionCoversTheBand() {
        assertEquals(0f, RadioDial.fraction(88f), 0.0001f)
        assertEquals(0.5f, RadioDial.fraction(98f), 0.0001f)
        assertEquals(1f, RadioDial.fraction(108f), 0.0001f)
        assertEquals(1f, RadioDial.fraction(200f), 0.0001f)
    }

    @Test
    fun releasingTheNeedleTunesTheNearestStation() {
        val list = listOf(station("a", "89.1"), station("b", "98.0"), station("web", "Online"), station("c", "107.0"))
        assertEquals("a", RadioDial.nearest(list, 0f)?.id)
        assertEquals("b", RadioDial.nearest(list, 0.52f)?.id)
        assertEquals("c", RadioDial.nearest(list, 1f)?.id)
        assertNull(RadioDial.nearest(listOf(station("web", "Online")), 0.5f))
    }

    // --- Car -------------------------------------------------------------------------------------

    @Test
    fun dockLightsThePlayingSource() {
        assertEquals(CarSource.WAVE, carSourceOf(PlaylistSource.VIBE, isRadio = false))
        assertEquals(CarSource.LIKED, carSourceOf(PlaylistSource.LIBRARY, false))
        assertEquals(CarSource.HITS, carSourceOf(PlaylistSource.TRENDS, false))
        assertEquals(CarSource.RADIO, carSourceOf(PlaylistSource.RADIO, false))
        assertEquals("a radio stream wins over the queue source", CarSource.RADIO, carSourceOf(PlaylistSource.VIBE, true))
        assertNull(carSourceOf(PlaylistSource.SEARCH, false))
        assertNull(carSourceOf(PlaylistSource.NONE, false))
    }

    // --- Car timeline ----------------------------------------------------------------------------

    @Test
    fun timelineFractionFollowsThePosition() {
        assertEquals(0.5f, TimelineMath.fraction(143_000, 286_000), 0.0001f)
        assertEquals("clamped past the end", 1f, TimelineMath.fraction(400_000, 286_000), 0f)
        assertEquals("negative position", 0f, TimelineMath.fraction(-50, 60_000), 0f)
        assertEquals("unknown duration", 0f, TimelineMath.fraction(10_000, 0), 0f)
    }

    @Test
    fun fingerPositionBecomesASeekTarget() {
        assertEquals(0.25f, TimelineMath.fractionAt(150f, 600f), 0.0001f)
        assertEquals("left of the groove", 0f, TimelineMath.fractionAt(-20f, 600f), 0f)
        assertEquals("right of the groove", 1f, TimelineMath.fractionAt(700f, 600f), 0f)
        assertEquals("not laid out yet", 0f, TimelineMath.fractionAt(10f, 0f), 0f)
        assertEquals(71_500L, TimelineMath.seekPosition(0.25f, 286_000))
        assertEquals(286_000L, TimelineMath.seekPosition(1.5f, 286_000))
        assertEquals(0L, TimelineMath.seekPosition(0.5f, 0))
    }

    @Test
    fun onlyLargeChangesAreJumps() {
        // 100 ms of playback on a 30-second track is far below a jump; a new track from the middle is one
        val tick = TimelineMath.fraction(100, 30_000)
        assertTrue(tick < TimelineMath.JUMP)
        assertTrue(TimelineMath.fraction(143_000, 286_000) - TimelineMath.fraction(0, 200_000) > TimelineMath.JUMP)
    }

    @Test
    fun playerReadoutShowsElapsedAndRemaining() {
        assertEquals("1:22" to "−3:24", TimelineMath.readout(82_000, 286_000))
        assertEquals("start of a track", "0:00" to "−4:46", TimelineMath.readout(0, 286_000))
        assertEquals("position past the end is clamped", "4:46" to "−0:00", TimelineMath.readout(300_000, 286_000))
        assertEquals("long mixes get hours", "1:02:05", TimelineMath.clock(3_725_000))
        assertEquals("unknown duration", "0:00" to "−0:00", TimelineMath.readout(5_000, 0))
    }

    @Test
    fun playerQueueSplitsCurrentAndNext() {
        val q = listOf(track("1"), track("2"), track("3"), track("4"))
        val split = com.korenskoy.yandexmusic.presentation.player.queueSplitOf(q, "2")
        assertEquals("2", split.current?.idString)
        assertEquals(listOf("3", "4"), split.next.map { it.idString })
        val unknown = com.korenskoy.yandexmusic.presentation.player.queueSplitOf(q, "9")
        assertEquals("current not in the queue: the whole queue is next", null, unknown.current)
        assertEquals(4, unknown.next.size)
        assertEquals("last track: nothing next", 0, com.korenskoy.yandexmusic.presentation.player.queueSplitOf(q, "4").next.size)
    }

    // --- Wave page shelf -------------------------------------------------------------------------

    private fun track(id: String) = TrackDetailsDto(id = kotlinx.serialization.json.JsonPrimitive(id), title = "T$id")

    @Test
    fun playingWaveShowsWhatComesNext() {
        val state = waveShelfOf(waveActive = true, upcoming = listOf(track("2"), track("3")), preview = listOf(track("9")))
        assertNull("the playing track is shown above, not a start track", state.startsWith)
        assertEquals(listOf("2", "3"), state.shelf.map { it.idString })
        assertFalse(state.loading)
    }

    @Test
    fun playingWaveAtTheEndOfItsBatchKeepsTheShelfAsPlaceholders() {
        val state = waveShelfOf(waveActive = true, upcoming = emptyList(), preview = listOf(track("9")))
        assertTrue(state.loading)
    }

    @Test
    fun idleWavePreviewsWhereItStarts() {
        val state = waveShelfOf(waveActive = false, upcoming = listOf(track("x")), preview = listOf(track("1"), track("2"), track("3")))
        assertEquals("1", state.startsWith?.idString)
        assertEquals("the start track is not repeated on the shelf", listOf("2", "3"), state.shelf.map { it.idString })
        assertFalse(state.loading)
    }

    @Test
    fun idleWaveWithoutAQueueYetShowsPlaceholders() {
        val empty = waveShelfOf(waveActive = false, upcoming = emptyList(), preview = emptyList())
        assertNull(empty.startsWith)
        assertTrue(empty.loading)
        val single = waveShelfOf(waveActive = false, upcoming = emptyList(), preview = listOf(track("1")))
        assertEquals("1", single.startsWith?.idString)
        assertTrue(single.loading)
    }
}

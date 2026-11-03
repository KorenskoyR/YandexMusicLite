package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.presentation.car.CarSource
import com.korenskoy.yandexmusic.presentation.car.carSourceOf
import com.korenskoy.yandexmusic.presentation.car.carTimeReadout
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

/** Pure logic of the Hi-Fi redesign (1.8.0): lite mode, cover tint, spectrum ring, FM dial, car readouts. */
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

    @Test
    fun loadingWaveHasOneBrightHeadThatRuns() {
        fun head(t: Float) = (0 until 72).maxByOrNull { SpectrumMath.level(it, 72, t, RingMode.Loading) }!!
        val levels = (0 until 72).map { SpectrumMath.level(it, 72, 0.5f, RingMode.Loading) }
        assertTrue("bright head", levels.max() > 0.9f)
        assertTrue("quiet rest", levels.count { it < 0.2f } > 50)
        assertTrue("the head moves", head(0.2f) != head(0.8f))
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

    @Test
    fun timeReadoutShowsElapsedAndRemaining() {
        assertEquals("1:22" to "−3:24", carTimeReadout(82_000, 286_000))
        assertEquals("0:00" to "−4:46", carTimeReadout(0, 286_000))
        assertEquals("0:05" to "−0:00", carTimeReadout(5_400, 5_400))
        assertEquals("clamped past the end", "4:46" to "−0:00", carTimeReadout(400_000, 286_000))
        assertEquals("negative position", "0:00" to "−1:00", carTimeReadout(-50, 60_000))
        assertEquals("unknown duration", "0:00" to "−0:00", carTimeReadout(10_000, 0))
        assertEquals("long tracks", "61:01" to "−0:00", carTimeReadout(3_661_000, 3_661_000))
    }
}

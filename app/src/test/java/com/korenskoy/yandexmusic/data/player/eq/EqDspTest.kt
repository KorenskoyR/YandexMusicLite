package com.korenskoy.yandexmusic.data.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** The app's own equalizer, measured on pure tones: each band does what its slider says, nothing overloads. */
class EqDspTest {
    private val rate = 48_000

    private fun gains(vararg pairs: Pair<Int, Float>): List<Float> =
        EqDesign.BANDS.map { hz -> pairs.firstOrNull { it.first == hz }?.second ?: 0f }

    @Test
    fun flatChangesNothing() {
        val s = EqSettings(enabled = true)
        assertTrue(s.isFlat)
        val chain = EqChain.of(s, rate)
        for (hz in listOf(20.0, 100.0, 1000.0, 10_000.0, 19_000.0)) assertEquals(0.0, chain.responseDb(hz), 0.01)
    }

    @Test
    fun oneBandRaisesItsOwnFrequencyByWhatItSays() {
        val chain = EqChain.of(EqSettings(enabled = true, gains = gains(1000 to 6f), noClipping = false), rate)
        assertEquals(6.0, chain.responseDb(1000.0), 0.3)
        assertEquals(0.0, chain.responseDb(62.0), 0.3)
        assertEquals(0.0, chain.responseDb(16_000.0), 0.3)
        // The neighbours an octave away get little of it
        assertTrue(abs(chain.responseDb(500.0)) < 1.0)
    }

    @Test
    fun neighbouringBandsDoNotPileUp() {
        // Set naively, three +6 in a row give about +9 in the middle: the fit keeps it at what the sliders say
        val chain = EqChain.of(EqSettings(enabled = true, gains = gains(500 to 6f, 1000 to 6f, 2000 to 6f), noClipping = false), rate)
        for (hz in listOf(500.0, 1000.0, 2000.0)) assertEquals(6.0, chain.responseDb(hz), 0.5)
    }

    @Test
    fun everySliderPositionIsHitAtItsCentre() {
        val wanted = listOf(4f, 3.5f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4.5f) // «Рок»
        val chain = EqChain.of(EqSettings(enabled = true, gains = wanted, noClipping = false), rate)
        EqDesign.BANDS.forEachIndexed { i, hz -> assertEquals("$hz Hz", wanted[i].toDouble(), chain.responseDb(hz.toDouble()), 0.6) }
    }

    @Test
    fun withoutClippingThePeakIsBroughtDownToZero() {
        val chain = EqChain.of(EqSettings(enabled = true, gains = gains(62 to 9f, 8000 to 6f), bass = 4f), rate)
        assertTrue(chain.preampDb < -8)
        assertTrue(chain.peakDb() + chain.preampDb <= 0.05)
    }

    @Test
    fun theShelvesRaiseTheirEnds() {
        val chain = EqChain.of(EqSettings(enabled = true, bass = 6f, treble = -6f, noClipping = false), rate)
        assertEquals(6.0, chain.responseDb(30.0), 0.5)
        assertEquals(-6.0, chain.responseDb(18_000.0), 0.6)
        assertEquals(0.0, chain.responseDb(1500.0), 0.6)
    }

    @Test
    fun aLowSampleRateKeepsTheTopBandBelowNyquist() {
        val chain = EqChain.of(EqSettings(enabled = true, gains = gains(16_000 to 6f), noClipping = false), 22_050)
        assertTrue(chain.filters.all { !it.b0.isNaN() && !it.a1.isNaN() })
        assertEquals(6.0, chain.responseDb(EqDesign.usable(16_000.0, 22_050)), 1.0)
    }

    @Test
    fun theCurveRunsSmoothlyBetweenTheSliders() {
        val settings = mapOf(
            "Рок" to listOf(4f, 3.5f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4.5f),
            "Басы" to listOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "всё +6" to List(10) { 6f },
            "три +6" to EqDesign.BANDS.map { if (it in listOf(500, 1000, 2000)) 6f else 0f },
            "Мягче верха" to listOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -3f, -5f, -7f)
        )
        for (sampleRate in listOf(44_100, 48_000)) for ((name, g) in settings) {
            val chain = EqChain.of(EqSettings(enabled = true, gains = g, noClipping = false), sampleRate)
            for (i in 0 until g.size - 1) for (k in 1..9) {
                val t = k / 10.0
                val hz = EqDesign.BANDS[i] * Math.pow(EqDesign.BANDS[i + 1].toDouble() / EqDesign.BANDS[i], t)
                val line = g[i] + (g[i + 1] - g[i]) * t
                assertEquals("$name at $hz Hz, $sampleRate", line, chain.responseDb(hz), 0.6)
            }
            // And all the way to the ends: the end bands are shelves
            assertEquals("$name at 20 Hz", g.first().toDouble(), chain.responseDb(20.0), 0.6)
            assertEquals("$name at 19 kHz", g.last().toDouble(), chain.responseDb(19_000.0), 0.8)
        }
    }

    private fun tone(hz: Double, frames: Int, channels: Int = 2, level: Double = 0.1): FloatArray =
        FloatArray(frames * channels) { i -> (level * sin(2 * PI * hz * (i / channels) / rate)).toFloat() }

    private fun rmsDb(x: FloatArray, from: Int): Double {
        var sum = 0.0
        for (i in from until x.size) sum += x[i].toDouble() * x[i]
        return 20 * log10(sqrt(sum / (x.size - from)))
    }

    @Test
    fun theRunnerMakesATone6dBLouderOnlyInItsBand() {
        val s = EqSettings(enabled = true, gains = gains(1000 to 6f), noClipping = false)
        for ((hz, expected) in listOf(1000.0 to 6.0, 125.0 to 0.0)) {
            val runner = EqRunner(2).apply { use(EqChain.of(s, rate), enabled = true); reset() }
            val x = tone(hz, rate)
            val before = rmsDb(x, x.size / 2)
            runner.process(x, rate, rate)
            assertEquals("$hz Hz", expected, rmsDb(x, x.size / 2) - before, 0.4)
        }
    }

    @Test
    fun offIsTheSoundAsItIs() {
        val runner = EqRunner(2).apply { use(EqChain.of(EqSettings(enabled = false, gains = gains(1000 to 6f)), rate), enabled = false); reset() }
        assertTrue(runner.idle)
    }

    @Test
    fun switchingOnFadesInInsteadOfJumping() {
        val runner = EqRunner(1)
        runner.use(EqChain.of(EqSettings(enabled = true, gains = gains(1000 to 12f), noClipping = false), rate), enabled = true)
        val x = tone(1000.0, rate / 10, channels = 1)
        val original = x.copyOf()
        runner.process(x, x.size, rate)
        // The first milliseconds stay close to the original; no step
        for (i in 0 until 24) assertEquals(original[i], x[i], 0.02f)
    }

    @Test
    fun theLimiterBendsPeaksBelowFullScale() {
        assertEquals(0.5, EqRunner.softLimit(0.5), 0.0)
        for (v in listOf(0.95, 1.2, 3.0, 50.0)) {
            val y = EqRunner.softLimit(v)
            assertTrue(y <= 1.0 && y > 0.9)
            assertEquals(-y, EqRunner.softLimit(-v), 1e-12)
        }
        // Continuous at the knee
        assertEquals(EqRunner.softLimit(0.92), EqRunner.softLimit(0.920001), 1e-4)
    }

    private fun processor(s: EqSettings, encoding: Int = C.ENCODING_PCM_16BIT): EqualizerProcessor =
        EqualizerProcessor({ s }).apply {
            configure(AudioProcessor.AudioFormat(rate, 2, encoding))
            flush()
        }

    @Test
    fun theProcessorTakes16BitAndFloatAndLeavesTheRest() {
        assertTrue(processor(EqSettings()).isActive)
        assertTrue(processor(EqSettings(), C.ENCODING_PCM_FLOAT).isActive)
        assertFalse(processor(EqSettings(), C.ENCODING_PCM_24BIT).isActive)
    }

    @Test
    fun theProcessorOffGivesBackTheSameBytes() {
        val p = processor(EqSettings(enabled = false, gains = gains(1000 to 6f)))
        val input = ByteBuffer.allocateDirect(4800 * 4).order(ByteOrder.nativeOrder())
        tone(440.0, 4800).forEach { input.putShort((it * 32767).toInt().toShort()) }
        input.flip()
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        p.queueInput(input)
        val out = p.output
        val got = ByteArray(out.remaining()).also { out.get(it) }
        assertArrayEquals(expected, got)
    }

    @Test
    fun theProcessorRaises16BitSound() {
        val p = processor(EqSettings(enabled = true, gains = gains(1000 to 6f), noClipping = false))
        val x = tone(1000.0, rate)
        val input = ByteBuffer.allocateDirect(x.size * 2).order(ByteOrder.nativeOrder())
        x.forEach { input.putShort((it * 32767).toInt().toShort()) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())
        val y = FloatArray(out.remaining() / 2) { out.getShort() / 32768f }
        assertEquals(x.size, y.size)
        assertEquals(6.0, rmsDb(y, y.size / 2) - rmsDb(x, x.size / 2), 0.4)
        assertTrue(y.all { abs(it) < 1f })
    }
}

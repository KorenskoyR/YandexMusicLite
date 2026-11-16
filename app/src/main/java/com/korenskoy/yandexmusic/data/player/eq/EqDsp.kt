package com.korenskoy.yandexmusic.data.player.eq

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the user set on the app's own equalizer: ten bands an octave apart, quick bass and treble, the preamp.
 * Gains in dB. Immutable: the playback thread reads a whole one at a time.
 */
data class EqSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(EqDesign.BANDS.size) { 0f },
    val bass: Float = 0f,
    val treble: Float = 0f,
    /** The preamp the user set; with [noClipping] the app sets it itself. */
    val preamp: Float = 0f,
    /** The preamp follows the curve's peak, so a raised band never overloads the sound. */
    val noClipping: Boolean = true
) {
    /** Nothing to change in the sound: the processor only copies. */
    val isFlat: Boolean
        get() = gains.all { it == 0f } && bass == 0f && treble == 0f && (noClipping || preamp == 0f)
}

/** The equalizer's layout: an ISO octave graphic equalizer with two shelves. */
object EqDesign {
    val BANDS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MAX_DB = 12f
    const val BASS_HZ = 105.0
    const val TREBLE_HZ = 8000.0

    /**
     * The width of the peaks. With the fit below, 0.9 keeps the curve within 0.4 dB of a straight line between the
     * sliders (measured at 44.1 and 48 kHz over typical settings); the usual octave 1.41 left humps of up to 1.3 dB.
     */
    const val Q = 0.9

    /** The highest centre a filter may have at a sample rate: some way under Nyquist. */
    fun usable(hz: Double, sampleRate: Int): Double = min(hz, sampleRate * 0.45)

    /**
     * Band [index] at [db]: the end bands are shelves, the rest peaks. A peak returns to 0 dB at the ends of the
     * range (at Nyquist always), so "+6 everywhere" sagged towards 20 kHz and below 31 Hz; a shelf holds its level
     * to the end. The shelf's corner sits half an octave past the band, so the slider's value is reached at it.
     */
    fun band(index: Int, centre: Double, db: Double, q: Double, sampleRate: Int): Biquad = when (index) {
        0 -> Biquad.lowShelf(centre * SHELF_CORNER, db, sampleRate)
        BANDS.size - 1 -> Biquad.highShelf(usable(centre / SHELF_CORNER, sampleRate), db, sampleRate)
        else -> Biquad.peaking(centre, db, q, sampleRate)
    }

    private const val SHELF_CORNER = 1.41
}

/** One second-order section (RBJ "Audio EQ Cookbook"), normalized: a0 = 1. */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** |H| in dB at [hz]. */
    fun responseDb(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        return 20 * log10(hypot(nr, ni) / hypot(dr, di))
    }

    companion object {
        private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun peaking(hz: Double, db: Double, q: Double, sampleRate: Int): Biquad {
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return normalized(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowShelf(hz: Double, db: Double, sampleRate: Int): Biquad {
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * hz / sampleRate
            val c = cos(w)
            // Shelf slope 1: alpha = sin(w)/√2, and the cookbook's 2·√A·alpha
            val beta = sqrt(2 * a) * sin(w)
            return normalized(
                a * ((a + 1) - (a - 1) * c + beta), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - beta),
                (a + 1) + (a - 1) * c + beta, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - beta
            )
        }

        fun highShelf(hz: Double, db: Double, sampleRate: Int): Biquad {
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * hz / sampleRate
            val c = cos(w)
            val beta = sqrt(2 * a) * sin(w)
            return normalized(
                a * ((a + 1) + (a - 1) * c + beta), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - beta),
                (a + 1) - (a - 1) * c + beta, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - beta
            )
        }
    }
}

/** The filters for a setting at a sample rate, and the curve they make. Pure; unit-tested. */
class EqChain(val filters: List<Biquad>, val preampDb: Double, val sampleRate: Int) {

    /** The curve of the filters alone (no preamp), dB at [hz]. */
    fun filtersDb(hz: Double): Double = filters.sumOf { it.responseDb(hz, sampleRate) }

    /** What the listener gets at [hz]: filters and preamp. */
    fun responseDb(hz: Double): Double = filtersDb(hz) + preampDb

    companion object {
        private const val FIT_ROUNDS = 5
        private const val MID_WEIGHT = 0.5
        private const val DAMPING = 0.02
        private const val PEAK_POINTS = 160

        /**
         * The chain for [s]. A graphic equalizer's neighbouring bands overlap: set naively, +6 on three bands in a row
         * gives about +9 in the middle. The gains of the filters are fitted a few rounds so the curve goes through
         * what the sliders say at the band centres.
         */
        fun of(s: EqSettings, sampleRate: Int, q: Double = EqDesign.Q): EqChain {
            val centres = EqDesign.BANDS.map { EqDesign.usable(it.toDouble(), sampleRate) }
            // Every filter is always there (at 0 dB it leaves the sound as it is): moving a band from 0 does not
            // change the set of filters, and their state, and so does not click
            val shelves = listOf(
                Biquad.lowShelf(EqDesign.BASS_HZ, s.bass.toDouble(), sampleRate),
                Biquad.highShelf(EqDesign.usable(EqDesign.TREBLE_HZ, sampleRate), s.treble.toDouble(), sampleRate)
            )
            val target = s.gains.map { it.toDouble() }
            fun bands(g: List<Double>) = g.mapIndexed { i, db -> EqDesign.band(i, centres[i], db, q, sampleRate) }
            val gains = if (target.any { it != 0.0 }) fit(target, centres, q, sampleRate) else target
            val filters = bands(gains) + shelves
            val curve = EqChain(filters, 0.0, sampleRate)
            val preamp = if (s.noClipping) -max(0.0, curve.peakDb()) else s.preamp.toDouble()
            return EqChain(filters, preamp, sampleRate)
        }

        /**
         * Least squares over the band centres and the points halfway between them: the curve goes through each slider
         * and runs straight (in dB over octaves) from one to the next, without the ripple of filters set naively, also
         * near the top where the filters crowd towards Nyquist. A filter's curve in dB is close to its gain times its
         * curve at 1 dB, so each round is a small linear solve.
         */
        private fun fit(target: List<Double>, centres: List<Double>, q: Double, sampleRate: Int): List<Double> {
            val n = target.size
            val points = centres + (0 until n - 1).map { sqrt(centres[it] * centres[it + 1]) }
            val wanted = target + (0 until n - 1).map { (target[it] + target[it + 1]) / 2 }
            val weight = DoubleArray(points.size) { if (it < n) 1.0 else MID_WEIGHT }
            val unit = centres.mapIndexed { i, hz -> EqDesign.band(i, hz, 1.0, q, sampleRate) }
            val shape = Array(points.size) { j -> DoubleArray(n) { i -> unit[i].responseDb(points[j], sampleRate) } }
            val gains = target.toDoubleArray()
            repeat(FIT_ROUNDS) {
                val filters = gains.mapIndexed { i, db -> EqDesign.band(i, centres[i], db, q, sampleRate) }
                val residual = DoubleArray(points.size) { j -> wanted[j] - filters.sumOf { it.responseDb(points[j], sampleRate) } }
                // (SᵀWS + λI)·d = SᵀW·r
                val a = Array(n) { r -> DoubleArray(n) { c -> (points.indices).sumOf { j -> shape[j][r] * weight[j] * shape[j][c] } + if (r == c) DAMPING else 0.0 } }
                val b = DoubleArray(n) { r -> points.indices.sumOf { j -> shape[j][r] * weight[j] * residual[j] } }
                val d = solve(a, b)
                for (i in 0 until n) gains[i] = (gains[i] + d[i]).coerceIn(-EqDesign.MAX_DB * 2.0, EqDesign.MAX_DB * 2.0)
            }
            return gains.toList()
        }

        /** Gaussian elimination with partial pivoting: a small, well-conditioned system. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
            val n = b.size
            for (col in 0 until n) {
                val pivot = (col until n).maxBy { abs(a[it][col]) }
                a[col] = a[pivot].also { a[pivot] = a[col] }
                b[col] = b[pivot].also { b[pivot] = b[col] }
                for (r in col + 1 until n) {
                    val k = a[r][col] / a[col][col]
                    for (c in col until n) a[r][c] -= k * a[col][c]
                    b[r] -= k * b[col]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) x[r] = (b[r] - (r + 1 until n).sumOf { a[r][it] * x[it] }) / a[r][r]
            return x
        }
    }

    /** The highest point of the filters' curve, 20 Hz to 20 kHz (or Nyquist). */
    fun peakDb(): Double {
        val top = min(20_000.0, sampleRate * 0.48)
        var peak = Double.NEGATIVE_INFINITY
        for (i in 0..PEAK_POINTS) {
            val hz = 20.0 * (top / 20.0).pow(i / PEAK_POINTS.toDouble())
            peak = max(peak, filtersDb(hz))
        }
        return peak
    }
}

/**
 * Runs a chain over interleaved samples, channel by channel, in double precision (a 31 Hz filter at 48 kHz in floats
 * hisses). The state lives on between buffers; a new chain keeps it, so a change of a band does not click.
 */
class EqRunner(private val channels: Int) {
    private var chain: EqChain? = null
    // The coefficients side by side (b0 b1 b2 a1 a2 per filter): the inner loop touches no objects
    private var coefficients = DoubleArray(0)
    private var count = 0
    // Transposed direct form II: two state values per filter and channel
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)
    private var gain = 1.0
    private var targetGain = 1.0
    private var mix = 0.0
    private var targetMix = 0.0

    fun use(next: EqChain?, enabled: Boolean) {
        val filters = next?.filters.orEmpty()
        if (filters.size != count) {
            z1 = DoubleArray(filters.size * channels)
            z2 = DoubleArray(filters.size * channels)
        }
        count = filters.size
        coefficients = DoubleArray(count * 5).also { c ->
            filters.forEachIndexed { k, q ->
                c[k * 5] = q.b0; c[k * 5 + 1] = q.b1; c[k * 5 + 2] = q.b2; c[k * 5 + 3] = q.a1; c[k * 5 + 4] = q.a2
            }
        }
        chain = next
        targetGain = 10.0.pow((next?.preampDb ?: 0.0) / 20)
        targetMix = if (enabled && next != null) 1.0 else 0.0
    }

    /** True while there is nothing to do: off and faded out. The caller copies the sound as it is. */
    val idle: Boolean
        get() = mix == 0.0 && targetMix == 0.0

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
        gain = targetGain
        mix = targetMix
    }

    /** Processes [frames] frames of [samples] in place, values in -1..1. */
    fun process(samples: FloatArray, frames: Int, sampleRate: Int) {
        val q = coefficients
        val z1 = z1
        val z2 = z2
        val n = count
        // About 20 ms to a new preamp or to on/off: no click
        val step = 1.0 / max(1, sampleRate / RAMPS_PER_SECOND)
        var i = 0
        repeat(frames) {
            if (gain != targetGain) gain = approach(gain, targetGain, step * 4)
            if (mix != targetMix) mix = approach(mix, targetMix, step)
            for (c in 0 until channels) {
                val dry = samples[i].toDouble()
                var y = dry
                var k = 0
                var s = c
                while (k < n) {
                    val o = k * 5
                    val out = q[o] * y + z1[s]
                    z1[s] = q[o + 1] * y - q[o + 3] * out + z2[s]
                    z2[s] = q[o + 2] * y - q[o + 4] * out
                    y = out
                    k++
                    s += channels
                }
                y *= gain
                val wet = dry + (y - dry) * mix
                samples[i] = (if (wet > KNEE || wet < -KNEE) softLimit(wet) else wet).toFloat()
                i++
            }
        }
    }

    private fun approach(value: Double, target: Double, step: Double): Double =
        if (value < target) min(target, value + step) else max(target, value - step)

    companion object {
        private const val RAMPS_PER_SECOND = 50
        private const val KNEE = 0.92

        /** Above the knee the peaks are bent smoothly towards full scale instead of being cut off. */
        fun softLimit(x: Double): Double {
            val a = abs(x)
            if (a <= KNEE) return x
            val bent = KNEE + (1 - KNEE) * (1 - exp(-(a - KNEE) / (1 - KNEE)))
            return if (x < 0) -bent else bent
        }
    }
}

package com.korenskoy.yandexmusic

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.di.PlayerModule
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A measuring tool, not a check (-e measure true): every station's loudness by EBU R128 (integrated, LUFS), from the
 * sound the player decodes, before its volume. The numbers go into the station list, so that with "Выравнивание
 * громкости" the radio plays as loud as the music (the report: louder after switching from the wave to the radio).
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RadioLoudnessInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    /** EBU R128 integrated loudness of 16-bit PCM: K-weighting, 400 ms blocks with 75% overlap, both gates. */
    private class R128 {
        private var rate = 0
        private var channels = 0
        private val stages = mutableListOf<Array<DoubleArray>>() // per channel: two biquads' state
        private var coeffs: List<DoubleArray> = emptyList()
        private val squares = mutableListOf<Double>() // mean square of each 100 ms step, summed over channels
        private var acc = 0.0
        private var count = 0

        fun format(sampleRate: Int, channelCount: Int) {
            if (sampleRate == rate && channelCount == channels) return
            rate = sampleRate
            channels = channelCount
            coeffs = listOf(highShelf(sampleRate.toDouble()), highPass(sampleRate.toDouble()))
            stages.clear()
            repeat(channelCount) { stages += arrayOf(DoubleArray(4), DoubleArray(4)) }
            acc = 0.0
            count = 0
        }

        // RBJ biquads with the BS.1770 parameters (as pyloudnorm): b0 b1 b2 a1 a2, normalized by a0
        private fun highShelf(fs: Double): DoubleArray {
            val g = 3.999843853973347
            val q = 0.7071752369554196
            val fc = 1681.974450955533
            val a = Math.pow(10.0, g / 40.0)
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val sa = 2 * sqrt(a) * alpha
            val a0 = (a + 1) - (a - 1) * c + sa
            return doubleArrayOf(
                a * ((a + 1) + (a - 1) * c + sa) / a0,
                -2 * a * ((a - 1) + (a + 1) * c) / a0,
                a * ((a + 1) + (a - 1) * c - sa) / a0,
                2 * ((a - 1) - (a + 1) * c) / a0,
                ((a + 1) - (a - 1) * c - sa) / a0
            )
        }

        private fun highPass(fs: Double): DoubleArray {
            val q = 0.5003270373238773
            val fc = 38.13547087602444
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return doubleArrayOf((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }

        private fun filter(x: Double, k: DoubleArray, s: DoubleArray): Double {
            // s: x1 x2 y1 y2
            val y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3]
            s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y
            return y
        }

        fun add(buffer: ByteBuffer) {
            if (rate == 0) return
            val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val step = rate / 10
            while (b.remaining() >= 2 * channels) {
                var frame = 0.0
                for (ch in 0 until channels) {
                    val x = b.short / 32768.0
                    val st = stages[ch]
                    val y = filter(filter(x, coeffs[0], st[0]), coeffs[1], st[1])
                    frame += y * y
                }
                acc += frame
                count++
                if (count == step) {
                    squares += acc / step
                    acc = 0.0
                    count = 0
                }
            }
        }

        fun integrated(): Double? {
            if (squares.size < 4) return null
            val blocks = (0..squares.size - 4).map { i -> (squares[i] + squares[i + 1] + squares[i + 2] + squares[i + 3]) / 4 }
            fun lufs(ms: Double) = -0.691 + 10 * log10(ms)
            val absolute = blocks.filter { it > 0 && lufs(it) > -70.0 }
            if (absolute.isEmpty()) return null
            val relativeGate = lufs(absolute.average()) - 10.0
            val gated = absolute.filter { lufs(it) > relativeGate }
            return lufs(gated.average())
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun measure(url: String, seconds: Long): Double? {
        val meter = R128()
        val sink = object : TeeAudioProcessor.AudioBufferSink {
            override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
                if (encoding == C.ENCODING_PCM_16BIT) synchronized(meter) { meter.format(sampleRateHz, channelCount) }
            }
            override fun handleBuffer(buffer: ByteBuffer) = synchronized(meter) { meter.add(buffer) }
        }
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(TeeAudioProcessor(sink))).build()
        }
        val player = onMain {
            ExoPlayer.Builder(context, renderers).setTrackSelector(PlayerModule.bestVariantSelector(context)).build().apply {
                volume = 0f
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        }
        try {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < seconds * 1000 + 10_000) {
                Thread.sleep(500)
                if (onMain { player.playerError } != null) return null
                if (onMain { player.currentPosition } >= seconds * 1000) break
            }
        } finally {
            onMain { player.release() }
        }
        return synchronized(meter) { meter.integrated() }
    }

    /** The meter itself: a full-scale 997 Hz sine in one channel is -3.01 LUFS by the standard. */
    @Test
    fun theMeterReadsTheStandardsSineRight() {
        val meter = R128()
        val rate = 48_000
        meter.format(rate, 1)
        val pcm = ByteBuffer.allocate(rate * 3 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until rate * 3) pcm.putShort((sin(2 * PI * 997 * i / rate) * 32767).toInt().toShort())
        pcm.flip()
        meter.add(pcm)
        val lufs = meter.integrated()!!
        Log.i(TAG, "sine: $lufs")
        org.junit.Assert.assertEquals(-3.01, lufs, 0.3)
    }

    @Test
    fun measureEveryStation() {
        assumeTrue("a measuring tool", InstrumentationRegistry.getArguments().getString("measure") == "true")
        val stations = RadioRepository(context).getBuiltinStations()
        for (station in stations) {
            val lufs = measure(station.streamUrl, SECONDS)
            Log.i(TAG, "LOUDNESS ${station.id} ${lufs?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "none"}")
        }
    }

    private companion object {
        const val TAG = "RadioLoudness"
        const val SECONDS = 20L
    }
}

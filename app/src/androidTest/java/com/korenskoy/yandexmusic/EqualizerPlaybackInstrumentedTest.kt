package com.korenskoy.yandexmusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.eq.EqDesign
import com.korenskoy.yandexmusic.data.player.eq.EqSettings
import com.korenskoy.yandexmusic.data.player.eq.EqualizerProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/**
 * The app's equalizer inside a real player's audio path, on the device: what leaves it for the speaker is measured
 * right after it. Off — the music as it is; +6 dB on the 1 kHz band — a 1 kHz tone 6 dB louder; a hundred quick
 * changes while it plays — no error, the music goes on.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class EqualizerPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val settings = AtomicReference(EqSettings())
    private lateinit var player: ExoPlayer

    /** The level of what came out of the equalizer lately (the last ~300 ms), dB of full scale. */
    private val meter = object : TeeAudioProcessor.AudioBufferSink {
        private val window = ArrayDeque<Double>()
        private var encoding = C.ENCODING_PCM_16BIT

        @Synchronized
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this.encoding = encoding
            window.clear()
        }

        @Synchronized
        override fun handleBuffer(buffer: ByteBuffer) {
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            var sum = 0.0
            var n = 0
            if (encoding == C.ENCODING_PCM_FLOAT) {
                while (b.remaining() >= 4) { val v = b.getFloat().toDouble(); sum += v * v; n++ }
            } else {
                while (b.remaining() >= 2) { val v = b.getShort() / 32768.0; sum += v * v; n++ }
            }
            if (n > 0) window.addLast(sum / n)
            // The sink hands over about 100 ms at a time: the last three are what plays now
            while (window.size > 3) window.removeFirst()
        }

        @Synchronized
        fun levelDb(): Double = if (window.isEmpty()) -200.0 else 10 * log10(window.average())
    }

    private fun toneFile(): Uri {
        val rate = 44_100
        val seconds = 30
        val samples = rate * seconds
        val out = ByteArrayOutputStream(44 + samples * 4)
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + samples * 4); out.write("WAVEfmt ".toByteArray())
        int(16); short(1); short(2); int(rate); int(rate * 4); short(4); short(16)
        out.write("data".toByteArray()); int(samples * 4)
        for (i in 0 until samples) {
            val v = (sin(2 * PI * 1000 * i / rate) * 0.1 * 32767).toInt() and 0xFFFF
            short(v); short(v)
        }
        val file = File(context.cacheDir, "eq_tone.wav").apply { writeBytes(out.toByteArray()) }
        return Uri.fromFile(file)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
        }
    }

    @After
    fun tearDown() {
        if (::player.isInitialized) onMain { player.release() }
    }

    /**
     * The cost on the device: 20 s of stereo 44.1 kHz through all twelve filters. A weak head unit must not notice it:
     * well under a tenth of real time (measured on a phone-class processor it is around a hundredth).
     */
    @Test
    fun theEqualizerCostsLittleOfTheProcessor() {
        val rate = 44_100
        val frames = rate * 20
        val samples = FloatArray(frames * 2) { (sin(2 * PI * 440 * (it / 2) / rate) * 0.3).toFloat() }
        val rock = listOf(4f, 3.5f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4.5f)
        val runner = com.korenskoy.yandexmusic.data.player.eq.EqRunner(2).apply {
            use(com.korenskoy.yandexmusic.data.player.eq.EqChain.of(EqSettings(enabled = true, gains = rock, bass = 3f, treble = 2f), rate), enabled = true)
            reset()
        }
        // Warm up, then in buffers of 100 ms as the sink hands them over
        runner.process(samples.copyOf(rate / 5), rate / 10, rate)
        val chunk = rate / 10 * 2
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        var at = 0
        val buffer = FloatArray(chunk)
        while (at + chunk <= samples.size) {
            System.arraycopy(samples, at, buffer, 0, chunk)
            runner.process(buffer, chunk / 2, rate)
            at += chunk
        }
        val share = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1e9 / 20.0
        android.util.Log.i("EqualizerCost", "%.2f%% of real time".format(share * 100))
        assertTrue("the equalizer took ${"%.1f".format(share * 100)}% of real time", share < 0.10)
    }

    @Test
    fun theEqualizerShapesWhatThePlayerPlays() {
        var error: PlaybackException? = null
        onMain {
            // As the app builds its players (PlayerModule): the equalizer in the sink, here with a meter after it
            val renderers = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                    DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(EqualizerProcessor({ settings.get() }), TeeAudioProcessor(meter)))
                        .build()
            }
            player = ExoPlayer.Builder(context, renderers)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                .build()
            player.volume = 0.05f
            player.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e }
            })
            player.setMediaItem(MediaItem.fromUri(toneFile()))
            player.prepare()
            player.play()
        }
        waitUntil(10_000, "the tone plays") { onMain { player.isPlaying } }
        Thread.sleep(1_500)
        val off = meter.levelDb()
        // A 1 kHz tone at a tenth of full scale: about −23 dB of full scale, untouched with the equalizer off
        assertEquals(-23.0, off, 1.5)

        settings.set(EqSettings(enabled = true, gains = EqDesign.BANDS.map { if (it == 1000) 6f else 0f }, noClipping = false))
        Thread.sleep(1_500)
        assertEquals("+6 dB on the 1 kHz band", 6.0, meter.levelDb() - off, 1.0)

        // Without clipping on: the peak is brought down, the tone at its band ends where it was
        settings.set(EqSettings(enabled = true, gains = EqDesign.BANDS.map { if (it == 1000) 6f else 0f }, noClipping = true))
        Thread.sleep(1_500)
        assertEquals("the preamp takes the boost back", 0.0, meter.levelDb() - off, 1.0)

        // A drag: a hundred changes in two seconds, the music never stops
        repeat(100) { i ->
            val g = (i % 25 - 12).toFloat()
            settings.set(EqSettings(enabled = i % 10 != 0, gains = List(10) { b -> if (b == i % 10) g else 0f }, bass = g / 2, treble = -g / 2))
            Thread.sleep(20)
        }
        Thread.sleep(500)
        assertNull("no playback error", error)
        assertTrue("still playing", onMain { player.isPlaying })
        assertTrue("sound comes out", meter.levelDb() > -60)

        settings.set(EqSettings(enabled = false))
        Thread.sleep(1_000)
        assertEquals("off again: as it was", off, meter.levelDb(), 1.0)
    }

}

package com.korenskoy.yandexmusic

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.PlaybackBuffering
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
import com.korenskoy.yandexmusic.data.player.SoundMode
import com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The real dual-player engine on an uneven "network": a data source that gives a track slower than real time,
 * goes dead for a while like a tunnel, or answers late. Each case is measured with the player's old defaults
 * and with the new buffer and retries; the numbers are written to the log under "SmoothNetwork".
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SmoothNetworkInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val players = mutableListOf<ExoPlayer>()
    private var repo: PlayerRepositoryImpl? = null
    private val journal = PlaybackJournal()

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @After
    fun tearDown() {
        onMain {
            repo?.release()
            players.forEach { it.release() }
        }
    }

    // --- An uneven network, without a network ---

    /** How a "server" gives a track. */
    private data class Line(
        /** Bytes a second; the track itself needs [BYTES_PER_SECOND]. */
        val rate: Int,
        /** Nothing can be opened from [deadFromMs] for [deadForMs] after the first open (a tunnel). */
        val deadFromMs: Long = -1,
        val deadForMs: Long = 0,
        /** Every first open of the track waits this long (a busy server). */
        val firstByteDelayMs: Long = 0
    )

    private inner class JitteryDataSource(private val lines: Map<String, Line>) : BaseDataSource(true) {
        private var bytes = ByteArray(0)
        private var position = 0
        private var end = 0
        private var line = Line(Int.MAX_VALUE)
        private var uri: Uri? = null
        private var startedAt = 0L
        private var openedAt = 0
        private var name = ""

        override fun open(dataSpec: DataSpec): Long {
            transferInitializing(dataSpec)
            name = dataSpec.uri.host.orEmpty()
            line = lines.getValue(name)
            val first = !opened.containsKey(name)
            if (first) {
                opened[name] = System.currentTimeMillis()
                if (line.firstByteDelayMs > 0) Thread.sleep(line.firstByteDelayMs)
            }
            if (inTunnel()) throw IOException("no route to host")
            uri = dataSpec.uri
            bytes = TRACK
            position = dataSpec.position.toInt()
            openedAt = position
            end = bytes.size
            startedAt = System.currentTimeMillis()
            transferStarted(dataSpec)
            return (end - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= end) return C.RESULT_END_OF_INPUT
            // In the tunnel a read hangs until the connection times out, as a real one does
            if (inTunnel()) {
                Thread.sleep(READ_TIMEOUT_MS)
                throw IOException("read timed out")
            }
            val chunk = minOf(length, end - position, 4096)
            // Throttle to the line's rate since this connection was opened
            val sent = (position - openedAt).toLong()
            val due = startedAt + (sent * 1000L / line.rate) - System.currentTimeMillis()
            if (due > 0) Thread.sleep(minOf(due, 200L))
            System.arraycopy(bytes, position, buffer, offset, chunk)
            position += chunk
            bytesTransferred(chunk)
            return chunk
        }

        override fun getUri(): Uri? = uri

        private fun inTunnel(): Boolean {
            if (line.deadFromMs < 0) return false
            val since = System.currentTimeMillis() - (opened[name] ?: return false)
            return since in line.deadFromMs until line.deadFromMs + line.deadForMs
        }

        override fun close() {
            if (uri != null) {
                uri = null
                transferEnded()
            }
        }
    }

    private val opened = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun newPlayer(lines: Map<String, Line>, newRules: Boolean): ExoPlayer {
        val factory = DataSource.Factory { JitteryDataSource(lines) }
        val policy: LoadErrorHandlingPolicy = if (newRules) PlaybackBuffering.PatientLoadErrors() else DefaultLoadErrorHandlingPolicy()
        val load: LoadControl = if (newRules) PlaybackBuffering.loadControl() else DefaultLoadControl()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory).setLoadErrorHandlingPolicy(policy))
            .setLoadControl(load)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .build()
            .also { players += it }
    }

    private fun engine(lines: Map<String, Line>, newRules: Boolean): PlayerRepositoryImpl {
        val okHttp = OkHttpClient()
        val api = retrofit2.Retrofit.Builder().baseUrl("https://localhost/").build().create(YandexMusicApi::class.java)
        val downloads = TrackDownloadManager(context, okHttp)
        val preload = TrackPreloadManager(api, downloads, okHttp, NoAuth(), SoundMode(flowOf(false), {}, { true }))
        return onMain {
            PlayerRepositoryImpl(newPlayer(lines, newRules), newPlayer(lines, newRules), downloads, preload, PlaybackWakeLockHelper(context), journal)
        }.also { repo = it }
    }

    /** Stops after the music was heard, their total length, and whether the player gave up with an error. */
    private data class Run(val stops: Int, val stoppedMs: Long, val failed: Boolean, val positionMs: Long)

    private fun playFor(engine: PlayerRepositoryImpl, id: String, wallMs: Long): Run {
        onMain { engine.playTrack(id, "jitter://$id/a.wav", id, "test", null, false, 0L) }
        // A stop is what a listener hears: the position standing still after the music began
        var lastPosition = -1L
        var movedAt = 0L
        var inStop = false
        var stops = 0
        var stoppedMs = 0L
        var failed = false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < wallMs) {
            val (position, error) = onMain { engine.getPlayer().currentPosition to engine.getPlayer().playerError }
            val now = System.currentTimeMillis()
            if (error != null) failed = true
            if (position > lastPosition + 20) {
                if (inStop) stoppedMs += now - movedAt
                inStop = false
                lastPosition = position
                movedAt = now
            } else if (lastPosition > 0 && !inStop && now - movedAt > STOP_MS) {
                inStop = true
                stops++
            }
            Thread.sleep(50)
        }
        if (inStop) stoppedMs += System.currentTimeMillis() - movedAt
        val position = onMain { engine.getPlayer().currentPosition }
        onMain { engine.stop() }
        return Run(stops, stoppedMs, failed, position)
    }

    @Test
    fun aLineSlowerThanTheMusicNeverStopsMoreOften() {
        val line = mapOf("slow" to Line(rate = BYTES_PER_SECOND * 80 / 100))
        val before = playFor(engine(line, newRules = false), "slow", 60_000)
        onMain { repo?.release() }
        opened.clear()
        val after = playFor(engine(line, newRules = true), "slow", 60_000)
        Log.i(TAG, "slower than real time, 60 s: before $before, after $after")
        // A line slower than the music always runs dry at times; a bigger buffer makes the stops fewer or the
        // same, never more (on an emulator the counts move by one from run to run)
        assertTrue("not more stops: before ${before.stops}, after ${after.stops}", after.stops <= before.stops)
        assertFalse(after.failed)
    }

    @Test
    fun aMinuteLongTunnelIsNotHeard() {
        // A good line for 20 s, then nothing for a minute: more than the old 50 s in hand, less than the new
        val line = mapOf("tunnel" to Line(rate = BYTES_PER_SECOND * 5, deadFromMs = 20_000, deadForMs = 60_000))
        val before = playFor(engine(line, newRules = false), "tunnel", 90_000)
        onMain { repo?.release() }
        opened.clear()
        val after = playFor(engine(line, newRules = true), "tunnel", 90_000)
        Log.i(TAG, "a minute-long tunnel: before $before, after $after")
        assertTrue("the old 50 s ran dry in the tunnel: $before", before.stops > 0 || before.failed)
        assertFalse(after.failed)
        assertEquals("not a single stop with three minutes in hand: $after", 0, after.stops)
        assertTrue("it played on through the tunnel: $after", after.positionMs > 85_000)
    }

    @Test
    fun aNextTrackThatComesLateFadesInInsteadOfSilence() {
        val lines = mapOf("a" to Line(rate = BYTES_PER_SECOND * 10), "b" to Line(rate = BYTES_PER_SECOND * 10, firstByteDelayMs = 5_000))
        val engine = engine(lines, newRules = true)
        onMain {
            engine.setCrossfadeDuration(3_000)
            engine.playTrack("a", "jitter://a/a.wav", "a", "test", null, false, 0L)
        }
        waitUntil(10_000, "a plays") { onMain { engine.getPlayer().isPlaying } }
        // Two seconds left of "a" when the crossfade is asked for; "b" answers only after five
        onMain { engine.seekTo(TRACK_SECONDS * 1000L - 2_500) }
        waitUntil(10_000, "a plays its last seconds") { onMain { engine.getPlayer().isPlaying } }
        onMain { engine.playTrack("b", "jitter://b/a.wav", "b", "test", null, true, 0L) }
        assertTrue("still playing while b loads", onMain { engine.isPlaying.value })
        waitUntil(15_000, "the crossfade happens") { journal.state.value.summary.crossfades == 1 }
        Log.i(TAG, "late next track: ${journal.state.value}")
        assertEquals("a was over before b came", 1, journal.state.value.summary.crossfadesLate)
        Thread.sleep(600)
        assertEquals("b at full volume right after it came", 1f, onMain { engine.getPlayer().volume }, 0.01f)
        assertEquals("only b sounds", 1, onMain { players.count { it.isPlaying } })
    }

    @Test
    fun theSourceSwitchesAtTheSameSecond() {
        val lines = mapOf("stream" to Line(rate = BYTES_PER_SECOND * 2), "copy" to Line(rate = Int.MAX_VALUE))
        val engine = engine(lines, newRules = true)
        onMain { engine.playTrack("t", "jitter://stream/a.wav", "t", "test", null, false, 0L) }
        waitUntil(10_000, "the stream plays") { onMain { engine.getPlayer().isPlaying } }
        Thread.sleep(8_000)
        val before = onMain { engine.getPlayer().currentPosition }
        onMain { engine.switchSource("t", "jitter://copy/a.wav", "t", "test", null) }
        waitUntil(10_000, "the copy plays") { onMain { engine.getPlayer().currentMediaItem?.localConfiguration?.uri?.host == "copy" } }
        Thread.sleep(1_000)
        val after = onMain { engine.getPlayer().currentPosition }
        Log.i(TAG, "switch: $before ms -> $after ms")
        assertTrue("the same second, give or take the switch: $before -> $after", abs(after - before - 1_000) < 2_500)
        assertEquals(1f, onMain { engine.getPlayer().volume }, 0.01f)
        assertEquals("one audible player", 1, onMain { players.count { it.isPlaying } })
    }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(25)
        }
    }

    private companion object {
        const val TAG = "SmoothNetwork"
        const val READ_TIMEOUT_MS = 8_000L
        /** The position standing still this long is a stop one hears. */
        const val STOP_MS = 400L
        const val SAMPLE_RATE = 22_050
        const val BYTES_PER_SECOND = SAMPLE_RATE * 2
        const val TRACK_SECONDS = 180

        /** Three minutes of a quiet tone, 16-bit mono WAV: longer than the old buffer keeps ahead. */
        val TRACK: ByteArray by lazy {
            val samples = SAMPLE_RATE * TRACK_SECONDS
            val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
            data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(BYTES_PER_SECOND).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(samples * 2)
            for (i in 0 until samples) data.putShort((sin(2 * PI * 440 * i / SAMPLE_RATE) * 3000).toInt().toShort())
            data.array()
        }
    }
}

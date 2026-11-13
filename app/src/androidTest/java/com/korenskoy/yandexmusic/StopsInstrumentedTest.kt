package com.korenskoy.yandexmusic

import androidx.test.core.app.ActivityScenario
import android.content.Context
import android.media.AudioAttributes as SystemAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.FocusPolicy
import com.korenskoy.yandexmusic.data.player.LinkRenewal
import com.korenskoy.yandexmusic.data.player.PlaybackBuffering
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
import com.korenskoy.yandexmusic.data.player.SoundMode
import com.korenskoy.yandexmusic.data.player.StopCause
import com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

/**
 * 1.14.7 on a real Android: the stops of the GitHub report that are the app's to prevent.
 *  - The connection breaks mid-track and the link has run out by the time it is picked up: before, the music
 *    stopped with an error; now a fresh link goes on from the same byte and nothing is heard. The real engine,
 *    the real player and the app's own rules; only the "server" is in the test.
 *  - Android's audio focus: a call pauses the music and brings it back; another app taking the sound for good
 *    leaves it paused; a pause is written to the journal with its cause.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class StopsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val players = mutableListOf<ExoPlayer>()
    private var repo: PlayerRepositoryImpl? = null

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

    // --- A link that runs out mid-track ---

    /**
     * A storage server: [OLD] gives the track at twice real time and breaks off at [BREAK_AT]; from then on it
     * answers 403, as an expired link does. [FRESH] gives the same file in full, from any byte.
     */
    private inner class Storage : BaseDataSource(true) {
        private var position = 0
        private var openedAt = 0
        private var startedAt = 0L
        private var uri: Uri? = null

        override fun open(dataSpec: DataSpec): Long {
            transferInitializing(dataSpec)
            if (dataSpec.uri == OLD && oldExpired.get()) {
                throw HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), dataSpec, ByteArray(0))
            }
            opens += "${dataSpec.uri.lastPathSegment}@${dataSpec.position}"
            uri = dataSpec.uri
            position = dataSpec.position.toInt()
            openedAt = position
            startedAt = System.currentTimeMillis()
            transferStarted(dataSpec)
            return (TRACK.size - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= TRACK.size) return C.RESULT_END_OF_INPUT
            if (uri == OLD && position >= BREAK_AT) {
                oldExpired.set(true)
                throw IOException("connection reset by peer")
            }
            val chunk = minOf(length, TRACK.size - position, 4096, if (uri == OLD) BREAK_AT - position else Int.MAX_VALUE)
            val due = startedAt + ((position - openedAt).toLong() * 1000L / (BYTES_PER_SECOND * 2)) - System.currentTimeMillis()
            if (due > 0) Thread.sleep(minOf(due, 200L))
            System.arraycopy(TRACK, position, buffer, offset, chunk)
            position += chunk
            bytesTransferred(chunk)
            return chunk
        }

        override fun getUri(): Uri? = uri

        override fun close() {
            if (uri != null) {
                uri = null
                transferEnded()
            }
        }
    }

    private val oldExpired = AtomicBoolean(false)
    private val opens = CopyOnWriteArrayList<String>()
    private val renewals = AtomicInteger()

    /** The app's player: its buffer and retries, with or without the renewal of links. */
    private fun newPlayer(renewing: Boolean): ExoPlayer {
        val storage = DataSource.Factory { Storage() }
        val sources = if (renewing) LinkRenewal(storage, renew = { FRESH }, onRenewed = { renewals.incrementAndGet() }) else storage
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sources).setLoadErrorHandlingPolicy(PlaybackBuffering.PatientLoadErrors()))
            .setLoadControl(PlaybackBuffering.loadControl())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .build()
            .also { players += it }
    }

    private fun engine(renewing: Boolean, journal: PlaybackJournal = PlaybackJournal()): PlayerRepositoryImpl {
        val okHttp = OkHttpClient()
        val api = retrofit2.Retrofit.Builder().baseUrl("https://localhost/").build().create(YandexMusicApi::class.java)
        val downloads = TrackDownloadManager(context, okHttp)
        val preload = TrackPreloadManager(api, downloads, okHttp, NoAuth(), SoundMode(flowOf(false), {}, { true }))
        return onMain {
            PlayerRepositoryImpl(newPlayer(renewing), newPlayer(renewing), downloads, preload, PlaybackWakeLockHelper(context), journal)
        }.also { repo = it }
    }

    /** What a listener hears over [wallMs]: stops of the sound after it began, and whether the player gave up. */
    private data class Heard(val stops: Int, val failed: Boolean, val positionMs: Long)

    private fun listen(engine: PlayerRepositoryImpl, wallMs: Long): Heard {
        onMain { engine.playTrack(TRACK_ID, OLD.toString(), "Track", "test", null, false, 0L) }
        var last = -1L
        var movedAt = 0L
        var inStop = false
        var stops = 0
        var failed = false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < wallMs) {
            val (position, error) = onMain { engine.getPlayer().currentPosition to engine.getPlayer().playerError }
            val now = System.currentTimeMillis()
            if (error != null) failed = true
            if (position > last + 20) {
                inStop = false
                last = position
                movedAt = now
            } else if (last > 0 && !inStop && now - movedAt > STOP_MS) {
                inStop = true
                stops++
            }
            Thread.sleep(50)
        }
        return Heard(stops, failed, onMain { engine.getPlayer().currentPosition })
    }

    @Test
    fun aLinkThatRanOutMidTrackStoppedTheMusicBefore() {
        val heard = listen(engine(renewing = false), 12_000)
        Log.i(TAG, "without renewal: $heard, opens $opens")
        assertTrue("the expired link ends the track with an error: $heard", heard.failed)
    }

    @Test
    fun aLinkThatRunsOutMidTrackIsRenewedAndNothingIsHeard() {
        val journal = PlaybackJournal()
        val heard = listen(engine(renewing = true, journal), 12_000)
        Log.i(TAG, "with renewal: $heard, opens $opens")
        assertFalse("no error: $heard", heard.failed)
        assertEquals("not a single stop: $heard", 0, heard.stops)
        assertTrue("it played on past the break: $heard", heard.positionMs > 10_000)
        assertEquals(1, renewals.get())
        // The fresh link went on from where the old one broke off, not from the start
        val freshOpen = opens.first { it.startsWith(FRESH.lastPathSegment!!) }
        assertTrue("from the same byte: $opens", freshOpen.substringAfter('@').toInt() >= BREAK_AT - 64 * 1024)
        assertTrue("journal: ${journal.state.value}", journal.state.value.summary.stops == 0)
    }

    // --- The sound shared with other apps, on Android's own audio focus ---

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Another app on the phone: it asks for the sound and gives it back. */
    private inner class Rival(private val gain: Int) {
        private val listener = AudioManager.OnAudioFocusChangeListener { }
        private var request: AudioFocusRequest? = null

        fun take(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val r = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(SystemAudioAttributes.Builder().setUsage(SystemAudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = r
            audio.requestAudioFocus(r)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, gain)
        }

        fun giveBack() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                request?.let(audio::abandonAudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(listener)
            }
        }
    }

    /** The app's rules on Android's real focus, as the service wires them; the "music" is a flag and the journal. */
    private inner class App {
        val journal = PlaybackJournal()
        @Volatile var playing = false
        val pauses = CopyOnWriteArrayList<StopCause>()
        private val listener = AudioManager.OnAudioFocusChangeListener { change -> focus.onFocusChange(change) }
        private var request: AudioFocusRequest? = null

        val focus: FocusPolicy = FocusPolicy(object : FocusPolicy.Actions {
            override fun request(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(SystemAudioAttributes.Builder().setUsage(SystemAudioAttributes.USAGE_MEDIA).build())
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                request = r
                audio.requestAudioFocus(r)
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }

            override fun abandon() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    request?.let(audio::abandonAudioFocusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audio.abandonAudioFocus(listener)
                }
            }

            override fun wantsToPlay(): Boolean = playing
            // The journal first: the test reads it once it sees the music stop or play
            override fun pause(cause: StopCause) {
                journal.expectPause(cause)
                journal.onPaused("Track")
                pauses += cause
                playing = false
                focus.onWantsToPlay(false)
            }
            override fun resume() {
                journal.onPlaying("Track")
                playing = true
                focus.onWantsToPlay(true)
            }
            override fun duck(on: Boolean) = Unit
        })

        fun play() = onMain {
            playing = true
            focus.onWantsToPlay(true)
        }

        fun stop() = onMain {
            playing = false
            focus.onWantsToPlay(false)
        }
    }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(25)
        }
    }

    /**
     * Android 15 gives the sound only to the app on the screen or with a playing service (the app's own service
     * is one while it plays): the app's screen is up for these.
     */
    private fun onScreen(block: () -> Unit) = ActivityScenario.launch(MainActivity::class.java).use { block() }

    @Test
    fun aCallPausesTheMusicAndItsEndBringsItBack() = onScreen {
        val app = App()
        app.play()
        assertTrue(app.focus.hasFocus)
        val call = Rival(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        onMain { call.take() }
        waitUntil(5_000, "paused for the call") { !app.playing }
        assertEquals(listOf(StopCause.FOCUS_TRANSIENT), app.pauses.toList())
        Thread.sleep(500)
        onMain { call.giveBack() }
        waitUntil(5_000, "the music back after the call") { app.playing }
        val back = app.journal.state.value.events.lastOrNull { it.kind == PlaybackJournal.Kind.RESUMED }
        assertNotNull("the journal tells the silence: ${app.journal.state.value.events}", back)
        app.stop()
    }

    @Test
    fun anotherPlayerTakingTheSoundLeavesTheMusicPaused() = onScreen {
        val app = App()
        app.play()
        val video = Rival(AudioManager.AUDIOFOCUS_GAIN)
        onMain { video.take() }
        waitUntil(5_000, "paused for the other player") { !app.playing }
        assertEquals(listOf(StopCause.FOCUS_LOST), app.pauses.toList())
        onMain { video.giveBack() }
        Thread.sleep(1_500)
        assertFalse("not brought back by the other player's end", app.playing)
        assertNull(app.journal.state.value.events.lastOrNull { it.kind == PlaybackJournal.Kind.RESUMED })
        assertEquals(StopCause.FOCUS_LOST, app.journal.state.value.events.last { it.kind == PlaybackJournal.Kind.PAUSED }.cause)
    }

    @Test
    fun aNotificationSoundOnlyMakesTheMusicQuieter() = onScreen {
        val app = App()
        app.play()
        val notification = Rival(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        onMain { notification.take() }
        Thread.sleep(1_000)
        assertTrue("plays on", app.playing)
        assertTrue(app.pauses.isEmpty())
        onMain { notification.giveBack() }
        app.stop()
    }

    private companion object {
        const val TAG = "Stops"
        const val TRACK_ID = "42"
        val OLD: Uri = Uri.parse("https://s3.example/old.wav")
        val FRESH: Uri = Uri.parse("https://s3.example/fresh.wav")
        const val STOP_MS = 400L
        const val SAMPLE_RATE = 22_050
        const val BYTES_PER_SECOND = SAMPLE_RATE * 2
        const val SECONDS = 14

        /** The old link breaks off after four seconds of the track. */
        const val BREAK_AT = 44 + BYTES_PER_SECOND * 4

        val TRACK: ByteArray by lazy {
            val samples = SAMPLE_RATE * SECONDS
            val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
            data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(BYTES_PER_SECOND).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(samples * 2)
            for (i in 0 until samples) data.putShort((sin(2 * PI * 440 * i / SAMPLE_RATE) * 3000).toInt().toShort())
            data.array()
        }
    }
}

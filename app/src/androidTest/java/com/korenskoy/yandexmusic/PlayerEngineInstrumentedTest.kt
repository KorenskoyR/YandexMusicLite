package com.korenskoy.yandexmusic

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
import com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real dual-ExoPlayer engine on a device with live HTTPS radio streams (no Yandex account needed)
 * to verify the crossfade checklist: duration, at most two audible tracks, pause/seek during a crossfade,
 * and playback after stop() with a preloaded standby.
 */
@RunWith(AndroidJUnit4::class)
class PlayerEngineInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var repo: PlayerRepositoryImpl

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false
        )
        .build()

    @Before
    fun setUp() {
        val okHttp = OkHttpClient()
        val api = retrofit2.Retrofit.Builder().baseUrl("https://localhost/").build().create(YandexMusicApi::class.java)
        val downloads = TrackDownloadManager(context, okHttp)
        val preload = TrackPreloadManager(api, downloads, okHttp)
        onMain {
            playerA = newPlayer()
            playerB = newPlayer()
            repo = PlayerRepositoryImpl(playerA, playerB, downloads, preload, PlaybackWakeLockHelper(context))
        }
    }

    @After
    fun tearDown() {
        onMain { repo.release() }
    }

    private fun audiblePlayers(): List<ExoPlayer> = onMain { listOf(playerA, playerB).filter { it.isPlaying } }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(25)
        }
    }

    private fun play(id: String, url: String, crossfade: Boolean) = onMain {
        repo.playTrack(id, url, id, "test", null, crossfade, 0L)
    }

    private fun startAndSettle(id: String, url: String) {
        play(id, url, crossfade = false)
        waitUntil(30_000, "$id starts") { onMain { repo.isPlaying.value && !repo.isBuffering.value } }
        Thread.sleep(1500)
    }

    private fun assertOnlyAudible(id: String) {
        val audible = audiblePlayers()
        assertEquals("exactly one audible player", 1, audible.size)
        assertEquals(id, onMain { audible[0].currentMediaItem?.mediaId })
        assertEquals(1f, onMain { audible[0].volume }, 0.001f)
    }

    @Test
    fun crossfadeLastsThreeSecondsAndEndsWithOneTrack() {
        startAndSettle("A", STREAM_A)
        play("B", STREAM_B, crossfade = true)

        waitUntil(30_000, "incoming track starts rendering") { audiblePlayers().size == 2 }
        val fadeStart = System.currentTimeMillis()
        var maxAudible = 2
        waitUntil(10_000, "crossfade completes") {
            val n = audiblePlayers().size
            maxAudible = maxOf(maxAudible, n)
            n == 1
        }
        val fadeMs = System.currentTimeMillis() - fadeStart
        assertTrue("crossfade took $fadeMs ms, expected ~3000", fadeMs in 2_700L..3_800L)
        assertEquals(2, maxAudible)
        assertOnlyAudible("B")
    }

    @Test
    fun pauseDuringCrossfadeLeavesOnlyNewTrackAtFullVolume() {
        startAndSettle("A", STREAM_A)
        play("B", STREAM_B, crossfade = true)
        waitUntil(30_000, "crossfade running") { audiblePlayers().size == 2 }
        Thread.sleep(1000)

        onMain { repo.pause() }
        Thread.sleep(500)
        assertEquals("nothing plays after pause", 0, audiblePlayers().size)

        onMain { repo.resume() }
        waitUntil(30_000, "resumes") { audiblePlayers().isNotEmpty() }
        Thread.sleep(800)
        assertOnlyAudible("B")
    }

    @Test
    fun seekDuringCrossfadeStopsOutgoingTrack() {
        startAndSettle("A", STREAM_A)
        play("B", STREAM_B, crossfade = true)
        waitUntil(30_000, "crossfade running") { audiblePlayers().size == 2 }
        Thread.sleep(800)

        onMain { repo.seekTo(0) }
        // The outgoing track is silenced at once (a live stream rebuffers after a seek, so B may need a moment)
        assertTrue("outgoing track A must stop", audiblePlayers().none { onMain { it.currentMediaItem?.mediaId } == "A" })
        waitUntil(30_000, "B continues after seek") { audiblePlayers().isNotEmpty() }
        Thread.sleep(500)
        assertOnlyAudible("B")
    }

    @Test
    fun playAfterStopWithPreloadedStandbyProducesSound() {
        startAndSettle("A", STREAM_A)
        onMain { repo.prepareNextTrack("B", STREAM_B, "B", "test", null) }
        Thread.sleep(3000)
        onMain { repo.stop() }

        // Regression: stop() used to keep preloadedTrackId, so this started an empty player in silence
        play("B", STREAM_B, crossfade = true)
        waitUntil(30_000, "B starts after stop") { audiblePlayers().isNotEmpty() }
        Thread.sleep(500)
        assertOnlyAudible("B")
    }

    @Test
    fun rapidSkipsNeverPlayMoreThanTwoTracks() {
        startAndSettle("A", STREAM_A)
        var maxAudible = 0
        for ((id, url) in listOf("B" to STREAM_B, "C" to STREAM_C, "D" to STREAM_D)) {
            play(id, url, crossfade = true)
            repeat(10) {
                maxAudible = maxOf(maxAudible, audiblePlayers().size)
                Thread.sleep(30)
            }
        }
        waitUntil(40_000, "last track settles") {
            maxAudible = maxOf(maxAudible, audiblePlayers().size)
            audiblePlayers().size == 1 && onMain { repo.currentTrackId.value == "D" && !repo.isBuffering.value }
        }
        Thread.sleep(4_000) // let the final crossfade finish
        assertTrue("at most 2 tracks audible, saw $maxAudible", maxAudible <= 2)
        assertOnlyAudible("D")
    }

    private companion object {
        const val STREAM_A = "https://radiorecord.hostingradio.ru/rr_main96.aacp"
        const val STREAM_B = "https://dfm.hostingradio.ru/dfm96.aacp"
        const val STREAM_C = "https://maximum.hostingradio.ru/maximum96.aacp"
        const val STREAM_D = "https://nashe1.hostingradio.ru/nashe-128.mp3"
    }
}

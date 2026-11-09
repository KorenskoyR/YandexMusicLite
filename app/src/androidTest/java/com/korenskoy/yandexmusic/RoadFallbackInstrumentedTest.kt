package com.korenskoy.yandexmusic

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.reserve.RoadReserve
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * The real player with the network really turned off: what plays when, and what happens when it comes
 * back. No account is needed: the tracks in the reserve are generated here.
 */
@RunWith(AndroidJUnit4::class)
class RoadFallbackInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MainViewModel
    private lateinit var reserve: RoadReserve

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun setNetwork(on: Boolean) {
        val word = if (on) "enable" else "disable"
        shell("svc wifi $word")
        shell("svc data $word")
    }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
        }
    }

    private fun track(id: String) = TrackDetailsDto(
        id = JsonPrimitive(id),
        title = "Track $id",
        artists = listOf(ArtistDto(id = JsonPrimitive("1"), name = "Tester")),
        durationMs = 60_000
    )

    /** A minute of a quiet tone: a real file for the real player. */
    private fun addToReserve(vararg tracks: TrackDetailsDto) = runBlocking {
        for (track in tracks) {
            val temp = File(context.cacheDir, "${track.idString}.wav").apply { writeBytes(toneWav(seconds = 60)) }
            assertTrue(reserve.adopt(track, temp, TrackPreloadManager.StreamQuality("mp3", 192)))
        }
    }

    private fun toneWav(seconds: Int, rate: Int = 8_000): ByteArray {
        val samples = seconds * rate
        val out = ByteArrayOutputStream(44 + samples * 2)
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + samples * 2); out.write("WAVEfmt ".toByteArray())
        int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(samples * 2)
        for (i in 0 until samples) short((sin(2 * PI * 220 * i / rate) * 800).toInt() and 0xFFFF)
        return out.toByteArray()
    }

    private fun play(track: TrackDetailsDto, queue: List<TrackDetailsDto>, source: PlaylistSource = PlaylistSource.SEARCH) {
        instrumentation.runOnMainSync { vm.playTrack(track.idString, queue, source) }
    }

    private fun assertPlaysAudibly(trackId: String) {
        waitUntil(15_000, "$trackId is the current track") { vm.currentTrack.value?.idString == trackId }
        waitUntil(15_000, "$trackId is playing") { vm.isPlaying.value && !vm.isBuffering.value }
        val from = vm.progress.value
        waitUntil(8_000, "$trackId moves on: really sounds") { vm.progress.value > from + 300 }
    }

    @Before
    fun setUp() {
        setNetwork(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        // The reserve the player itself plays from (the settings page holds the same singleton)
        val controller = MainViewModel::class.java.getDeclaredField("playbackController").run {
            isAccessible = true
            get(vm)!!
        }
        reserve = controller.javaClass.getDeclaredField("reserve").run {
            isAccessible = true
            get(controller) as RoadReserve
        }
        runBlocking { reserve.clear() }
    }

    @After
    fun tearDown() {
        setNetwork(true)
        instrumentation.runOnMainSync { vm.stopAndClearPlayer() }
        runBlocking { reserve.clear() }
        scenario.close()
    }

    private fun goOffline() {
        setNetwork(false)
        waitUntil(30_000, "the app sees no connection") { vm.linkState.value == LinkState.NONE }
    }

    private fun goOnline() {
        setNetwork(true)
        waitUntil(60_000, "the app sees the connection back") { vm.linkState.value == LinkState.GOOD }
    }

    @Test
    fun noConnectionPlaysTheReserveCopyAndSaysItsQuality() {
        val a = track("road_a")
        addToReserve(a)
        goOffline()

        play(a, listOf(a))

        assertPlaysAudibly(a.idString)
        assertEquals("MP3 · 192 кбит/с", vm.streamQuality.value)
    }

    @Test
    fun noConnectionSkipsWhatIsNotOnTheDeviceAndKeepsTheQueue() {
        val missing = track("road_missing")
        val a = track("road_a")
        val b = track("road_b")
        addToReserve(a, b)
        val queue = listOf(missing, a, b)
        goOffline()

        play(missing, queue)

        assertPlaysAudibly(a.idString)
        assertEquals("the queue stays what it was", queue.map { it.idString }, vm.currentPlaylist.value.map { it.idString })
        assertEquals(PlaylistSource.SEARCH, vm.currentSource.value)

        instrumentation.runOnMainSync { vm.playNext() }
        assertPlaysAudibly(b.idString)
    }

    @Test
    fun withTheQueueOutOfReachTheRestOfTheReservePlaysThenTheQueueComesBack() {
        val x = track("road_x")
        val y = track("road_y")
        val saved = track("road_saved")
        addToReserve(saved)
        goOffline()

        play(x, listOf(x, y))

        assertPlaysAudibly(saved.idString)

        goOnline()
        instrumentation.runOnMainSync { vm.playNext() }

        waitUntil(10_000, "the music is back in its queue, after the track it left") { vm.currentTrack.value?.idString == y.idString }
        assertEquals(PlaylistSource.SEARCH, vm.currentSource.value)
        assertEquals(listOf(x, y).map { it.idString }, vm.currentPlaylist.value.map { it.idString })
    }

    @Test
    fun withNothingOnTheDeviceTheMusicWaitsAndComesBackByItself() {
        val z = track("road_z")
        goOffline()

        play(z, listOf(z))
        waitUntil(10_000, "no spinner stuck") { !vm.isBuffering.value }

        // The next track appears on the device; the connection comes back: the music continues unasked
        addToReserve(z)
        setNetwork(true)
        waitUntil(60_000, "the connection is back") { vm.linkState.value == LinkState.GOOD }
        assertPlaysAudibly(z.idString)
    }

    @Test
    fun aDropMidTrackContinuesFromTheCopyAtTheSameSecond() {
        val a = track("road_stream")
        addToReserve(a)
        // A better stream than the copy: the track starts from the network, as with a good link
        val preload = MainViewModel::class.java.getDeclaredField("playbackController").run {
            isAccessible = true
            get(vm)
        }.let { controller ->
            controller.javaClass.getDeclaredField("preloadManager").run {
                isAccessible = true
                get(controller) as TrackPreloadManager
            }
        }
        preload.seedForTest(a.idString, STREAM, TrackPreloadManager.StreamQuality("mp3", 320))

        play(a, listOf(a))
        waitUntil(30_000, "the stream plays") { vm.isPlaying.value && !vm.isBuffering.value && vm.streamQuality.value?.contains("320") == true }
        Thread.sleep(3_000)
        val beforeDrop = vm.progress.value

        setNetwork(false)

        waitUntil(90_000, "the copy took over") { vm.streamQuality.value?.contains("192") == true }
        val resumedAt = vm.progress.value
        assertTrue("goes on from where it was, not from the start: $beforeDrop -> $resumedAt", resumedAt >= beforeDrop)
        assertPlaysAudibly(a.idString)
    }

    private companion object {
        const val STREAM = "https://nashe1.hostingradio.ru/nashe-128.mp3"
    }
}

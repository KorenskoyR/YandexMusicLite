package com.korenskoy.yandexmusic

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
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
 * The journal now tells an output that stops taking sound and a timer that jumps back (1.14.8: Bluetooth
 * headphones). On an ordinary run of three tracks with their changes it must stay quiet: no false alarms.
 */
@RunWith(AndroidJUnit4::class)
class OutputWatchInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MainViewModel
    private lateinit var reserve: RoadReserve
    private lateinit var journal: PlaybackJournal

    private val queue = listOf("990000201", "990000202", "990000203").map {
        TrackDetailsDto(id = JsonPrimitive(it), title = "Тон $it", artists = listOf(ArtistDto(id = JsonPrimitive("1"), name = "Tester")), durationMs = 20_000)
    }

    private fun toneWav(seconds: Int, rate: Int = 22_050): ByteArray {
        val samples = seconds * rate
        val out = ByteArrayOutputStream(44 + samples * 2)
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + samples * 2); out.write("WAVEfmt ".toByteArray())
        int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(samples * 2)
        for (i in 0 until samples) short((sin(2 * PI * 330 * i / rate) * 900).toInt() and 0xFFFF)
        return out.toByteArray()
    }

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        val controller = MainViewModel::class.java.getDeclaredField("playbackController").run { isAccessible = true; get(vm)!! }
        reserve = controller.javaClass.getDeclaredField("reserve").run { isAccessible = true; get(controller) as RoadReserve }
        journal = controller.javaClass.getDeclaredField("journal").run { isAccessible = true; get(controller) as PlaybackJournal }
        runBlocking {
            reserve.clear()
            for (t in queue) {
                val temp = File(context.cacheDir, "${t.idString}.wav").apply { writeBytes(toneWav(seconds = 20)) }
                assertTrue(reserve.adopt(t, temp, TrackPreloadManager.StreamQuality("mp3", 192)))
            }
        }
        journal.clear()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { vm.stopAndClearPlayer() }
        runBlocking { reserve.clear() }
        scenario.close()
    }

    @Test
    fun threeTracksWithTheirChangesRaiseNoFalseAlarm() {
        instrumentation.runOnMainSync { vm.playTrack(queue[0].idString, queue, PlaylistSource.SEARCH) }
        val start = System.currentTimeMillis()
        while (vm.currentTrack.value?.idString != queue[2].idString && System.currentTimeMillis() - start < 90_000) Thread.sleep(200)
        assertEquals("the third track came", queue[2].idString, vm.currentTrack.value?.idString)
        Thread.sleep(8_000)
        val alarms = journal.state.value.events.filter { it.kind == PlaybackJournal.Kind.OUTPUT_STALL || it.kind == PlaybackJournal.Kind.POSITION_BACK }
        assertEquals("no false alarms: $alarms", 0, alarms.size)
        assertTrue("the music played", journal.state.value.summary.playedMs > 20_000)
    }
}

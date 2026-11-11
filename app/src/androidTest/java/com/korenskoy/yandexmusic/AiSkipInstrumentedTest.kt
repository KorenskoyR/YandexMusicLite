package com.korenskoy.yandexmusic

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.ai.AiMarks
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.CreditDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.reserve.RoadReserve
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
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
 * "Пропускать музыку, созданную ИИ" on the real player: a marked track is passed over on the way (never heard),
 * one whose mark comes after it started is left at once, one the user picked plays, and with the switch off all
 * play. The tracks are generated and played from the cache, so no account and no network are needed; the marks
 * are put where the app keeps the server's answers.
 */
@RunWith(AndroidJUnit4::class)
class AiSkipInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MainViewModel
    private lateinit var reserve: RoadReserve
    private lateinit var marks: AiMarks
    private lateinit var settings: SettingsRepository

    // Numeric like the catalogue's, far from real ones; a request about them would only find nothing
    private val a = track("990000101", "Живая А")
    private val b = track("990000102", "Нейро Б")
    private val c = track("990000103", "Живая В")
    private val queue = listOf(a, b, c)

    private fun track(id: String, title: String) = TrackDetailsDto(
        id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(id = JsonPrimitive("1"), name = "Tester")), durationMs = 60_000
    )

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
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

    /** The server's answer, as if it had come: through the same door. */
    private fun markAsAi(track: TrackDetailsDto, words: String = "Трек полностью создан с использованием ИИ") {
        val remember = AiMarks::class.java.getDeclaredMethod("remember", String::class.java, AiMarks.Answer::class.java).apply { isAccessible = true }
        remember.invoke(marks, track.idString, AiMarks.Answer.Credits(listOf(CreditDto("Использование ИИ", words))))
    }

    private fun forget(vararg tracks: TrackDetailsDto) {
        val remember = AiMarks::class.java.getDeclaredMethod("remember", String::class.java, AiMarks.Answer::class.java).apply { isAccessible = true }
        tracks.forEach { remember.invoke(marks, it.idString, AiMarks.Answer.Credits(emptyList())) }
    }

    private fun play(track: TrackDetailsDto) {
        instrumentation.runOnMainSync { vm.playTrack(track.idString, queue, PlaylistSource.SEARCH) }
    }

    private fun assertPlaysAudibly(trackId: String) {
        waitUntil(15_000, "$trackId is the current track (now ${vm.currentTrack.value?.idString})") { vm.currentTrack.value?.idString == trackId }
        waitUntil(15_000, "$trackId is playing") { vm.isPlaying.value && !vm.isBuffering.value }
        val from = vm.progress.value
        waitUntil(8_000, "$trackId moves on: really sounds") { vm.progress.value > from + 300 }
    }

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        val controller = MainViewModel::class.java.getDeclaredField("playbackController").run { isAccessible = true; get(vm)!! }
        reserve = controller.javaClass.getDeclaredField("reserve").run { isAccessible = true; get(controller) as RoadReserve }
        marks = MainViewModel::class.java.getDeclaredField("ai").run { isAccessible = true; get(vm) as AiMarks }
        settings = MainViewModel::class.java.getDeclaredField("settingsRepository").run { isAccessible = true; get(vm) as SettingsRepository }
        runBlocking {
            reserve.clear()
            for (t in queue) {
                val temp = File(context.cacheDir, "${t.idString}.wav").apply { writeBytes(toneWav(seconds = 60)) }
                assertTrue(reserve.adopt(t, temp, TrackPreloadManager.StreamQuality("mp3", 192)))
            }
        }
        forget(a, b, c)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { vm.stopAndClearPlayer() }
        runBlocking {
            settings.setSkipAi(false)
            reserve.clear()
        }
        forget(a, b, c)
        scenario.close()
    }

    private fun skipAi(on: Boolean) {
        runBlocking { settings.setSkipAi(on) }
        waitUntil(5_000, "the setting is $on") { vm.skipAi.value == on }
    }

    @Test
    fun aMarkedTrackIsPassedOverOnTheWayNeverHeard() {
        skipAi(true)
        markAsAi(b)
        play(a)
        assertPlaysAudibly(a.idString)
        val heard = mutableListOf<String>()
        instrumentation.runOnMainSync { vm.playNext() }
        waitUntil(15_000, "the next unmarked plays") {
            vm.currentTrack.value?.idString?.let { if (heard.lastOrNull() != it) heard += it }
            vm.currentTrack.value?.idString == c.idString
        }
        assertPlaysAudibly(c.idString)
        assertTrue("the marked track never became current: $heard", b.idString !in heard)
    }

    @Test
    fun aMarkThatComesLateTakesTheTrackAwayAtOnce() {
        skipAi(true)
        play(a)
        assertPlaysAudibly(a.idString)
        instrumentation.runOnMainSync { vm.playNext() }
        assertPlaysAudibly(b.idString)
        // Yandex answers only now: the queue reached this track by itself, so it goes
        markAsAi(b)
        waitUntil(10_000, "left for the next one") { vm.currentTrack.value?.idString == c.idString }
        assertPlaysAudibly(c.idString)
    }

    @Test
    fun aTrackTheUserPicksPlaysWhateverItsMark() {
        skipAi(true)
        markAsAi(b)
        play(b)
        assertPlaysAudibly(b.idString)
        Thread.sleep(2_000)
        assertEquals("picked by the user: stays", b.idString, vm.currentTrack.value?.idString)
        assertTrue("the mark is known to the screens", b.idString in vm.aiMarks.value)
    }

    @Test
    fun withTheSwitchOffMarkedTracksPlayAndKeepTheirMark() {
        skipAi(false)
        markAsAi(b)
        play(a)
        assertPlaysAudibly(a.idString)
        instrumentation.runOnMainSync { vm.playNext() }
        assertPlaysAudibly(b.idString)
        assertEquals("Трек полностью создан с использованием ИИ", vm.aiMarks.value[b.idString])
    }

    @Test
    fun theRealServerMarksARealAiTrackAndNotALiveOne() {
        // "SUNO" by V0LT: "Трек полностью создан с использованием ИИ"; Imagine Dragons — no mark
        val ai = "156022399"
        val live = "33311009"
        forget(track(ai, "SUNO"), track(live, "Believer"))
        val clear = AiMarks::class.java.getDeclaredField("entries").run { isAccessible = true; get(marks) as MutableMap<*, *> }
        clear.remove(ai)
        clear.remove(live)
        marks.want(listOf(live, ai))
        waitUntil(20_000, "the server's answer arrives: ${vm.aiMarks.value[ai]}") { ai in vm.aiMarks.value }
        assertEquals("Трек полностью создан с использованием ИИ", vm.aiMarks.value[ai])
        waitUntil(20_000, "the live track was asked too") { clear.containsKey(live) }
        assertTrue(live !in vm.aiMarks.value)
    }

    @Test
    fun aGuessIsNoMark() {
        skipAi(true)
        markAsAi(b, words = "Возможно, трек создан с использованием ИИ")
        play(a)
        assertPlaysAudibly(a.idString)
        instrumentation.runOnMainSync { vm.playNext() }
        assertPlaysAudibly(b.idString)
        assertTrue(b.idString !in vm.aiMarks.value)
    }
}

package com.korenskoy.yandexmusic.data.reserve

import android.app.Application
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.ResolvedStream
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.StreamQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections

/** What gets saved along the way, in what order, and when it stops. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReserveKeeperTest {
    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("audio".toResponseBody("audio/mpeg".toMediaType()))
                .build()
        })
        .build()
    private val reserve = RoadReserve(RuntimeEnvironment.getApplication(), client, Json { ignoreUnknownKeys = true })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolved = Collections.synchronizedList(mutableListOf<String>())
    private val mayFill = MutableStateFlow(true)
    private val limitMb = MutableStateFlow(100)

    private fun track(id: String, radio: Boolean = false) = TrackDetailsDto(id = JsonPrimitive(id), isRadio = radio)

    private fun start(
        upcoming: List<TrackDetailsDto>,
        more: List<TrackDetailsDto> = emptyList(),
        downloaded: Set<String> = emptySet(),
        unavailable: Set<String> = emptySet()
    ) {
        ReserveKeeper(
            scope = scope,
            reserve = reserve,
            limitMb = limitMb,
            mayFill = mayFill,
            resolve = { id ->
                resolved += id
                if (id in unavailable) null else ResolvedStream("https://example.invalid/$id", StreamQuality("mp3", 192))
            },
            streamNeedsTheLine = { false },
            upcoming = { upcoming },
            moreTracks = { more },
            isDownloaded = { it in downloaded },
            fetchMore = { false },
            onStored = {}
        ).start()
    }

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(15_000) { while (!condition()) delay(20) }
    }

    @Before
    fun clean() = runBlocking { reserve.clear() }

    @After
    fun stop() = scope.cancel()

    @Test
    fun whatComesNextGoesFirstThenMoreOfTheWave() {
        start(upcoming = listOf(track("next1"), track("next2")), more = listOf(track("wave1")))

        waitFor { reserve.has("wave1") }

        assertEquals(listOf("next1", "next2", "wave1"), resolved.toList())
    }

    @Test
    fun radioAndDownloadsAreNotCopied() {
        start(upcoming = listOf(track("radio_x", radio = true), track("mine"), track("next")), downloaded = setOf("mine"))

        waitFor { reserve.has("next") }

        assertEquals(listOf("next"), resolved.toList())
    }

    @Test
    fun aTrackWithoutAFullStreamIsNotSaved() {
        start(upcoming = listOf(track("preview_only"), track("next")), unavailable = setOf("preview_only"))

        waitFor { resolved.isNotEmpty() }
        runBlocking { delay(300) }

        assertFalse(reserve.has("preview_only"))
        assertEquals("it is asked for only once", listOf("preview_only"), resolved.toList().filter { it == "preview_only" })
    }

    @Test
    fun nothingIsSavedWhileItMayNotFill() {
        mayFill.value = false
        start(upcoming = listOf(track("next")))

        runBlocking { delay(500) }

        assertTrue(resolved.isEmpty())
        assertFalse(reserve.has("next"))
    }

    @Test
    fun turningItOffEmptiesIt() {
        start(upcoming = listOf(track("next")))
        waitFor { reserve.has("next") }

        limitMb.value = 0

        waitFor { !reserve.has("next") }
        assertEquals(0L, reserve.usedBytes.value)
    }
}

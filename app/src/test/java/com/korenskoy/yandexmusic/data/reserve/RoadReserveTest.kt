package com.korenskoy.yandexmusic.data.reserve

import android.app.Application
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.ResolvedStream
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager.StreamQuality
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The reserve on disk: what is saved plays, survives a restart, and a failed download leaves nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoadReserveTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true }

    private fun client(code: Int = 200, body: String = "audio") = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body.toResponseBody("audio/mpeg".toMediaType()))
                .build()
        })
        .build()

    private fun track(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "Track $id")
    private val stream = ResolvedStream("https://example.invalid/a.mp3", StreamQuality("mp3", 192))

    @Test
    fun aSavedTrackIsThereWithItsQuality() = runBlocking {
        val reserve = RoadReserve(app, client(), json)
        reserve.clear()

        assertTrue(reserve.store(track("1"), stream) { false })

        assertTrue(reserve.has("1"))
        assertEquals("audio", reserve.fileOf("1")?.readText())
        assertEquals(StreamQuality("mp3", 192), reserve.qualityOf("1"))
        assertEquals(5L, reserve.usedBytes.value)
    }

    @Test
    fun aRefusedDownloadLeavesNothing() = runBlocking {
        val reserve = RoadReserve(app, client(code = 403), json)
        reserve.clear()

        assertFalse(reserve.store(track("1"), stream) { false })

        assertFalse(reserve.has("1"))
        assertNull(reserve.fileOf("1"))
        assertEquals(0L, reserve.usedBytes.value)
    }

    @Test
    fun theReserveSurvivesARestart() = runBlocking {
        val first = RoadReserve(app, client(), json)
        first.clear()
        first.store(track("1"), stream) { false }

        val second = RoadReserve(app, client(), json)
        withTimeout(5_000) { while (!second.has("1")) delay(10) }

        assertNotNull(second.fileOf("1"))
        assertEquals("Track 1", second.entries.value["1"]?.track?.title)
    }

    @Test
    fun makingRoomRemovesTheFile() = runBlocking {
        val reserve = RoadReserve(app, client(), json)
        reserve.clear()
        reserve.store(track("1"), stream) { false }
        val file = reserve.fileOf("1")!!

        assertTrue(reserve.makeRoom(limitBytes = 5, incomingBytes = 5, keep = emptySet()))

        assertFalse(file.exists())
        assertFalse(reserve.has("1"))
    }

    @Test
    fun whatIsAboutToPlayIsNotRemovedForTheNext() = runBlocking {
        val reserve = RoadReserve(app, client(), json)
        reserve.clear()
        reserve.store(track("1"), stream) { false }

        assertFalse(reserve.makeRoom(limitBytes = 5, incomingBytes = 5, keep = setOf("1")))
        assertTrue(reserve.has("1"))
    }

    @Test
    fun clearingEmptiesIt() = runBlocking {
        val reserve = RoadReserve(app, client(), json)
        reserve.store(track("1"), stream) { false }

        reserve.clear()

        assertFalse(reserve.has("1"))
        assertEquals(0L, reserve.usedBytes.value)
    }
}

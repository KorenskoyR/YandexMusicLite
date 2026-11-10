package com.korenskoy.yandexmusic.data.download

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Several jobs may want the same track at once (all liked, the like just given, a playlist, the quality
 * upgrade). They used to write the same temporary file together; now the track is fetched once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadOncePerTrackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val music = Random(3).nextBytes(200_000)
    private val requests = AtomicInteger()
    private val atOnce = AtomicInteger()
    private val mostAtOnce = AtomicInteger()

    private val server = Interceptor { chain ->
        requests.incrementAndGet()
        mostAtOnce.accumulateAndGet(atOnce.incrementAndGet(), ::maxOf)
        Thread.sleep(150)
        atOnce.decrementAndGet()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
            .body(music.toResponseBody("audio/mpeg".toMediaType())).build()
    }

    @Before
    fun clean() {
        File(context.filesDir, "tracks").deleteRecursively()
    }

    @Test
    fun theSameTrackIsFetchedOnceHoweverManyJobsAskForIt() = runBlocking {
        val downloads = TrackDownloadManager(context, OkHttpClient.Builder().addInterceptor(server).build())
        val results = (1..4).map { async(Dispatchers.IO) { downloads.downloadTrack("42", "https://example.com/42") } }.awaitAll()

        assertTrue(results.all { it })
        assertEquals("one request for one track", 1, requests.get())
        assertArrayEquals(music, File(context.filesDir, "tracks/42.mp3").readBytes())
    }

    @Test
    fun differentTracksStillDownloadSideBySide() = runBlocking {
        val downloads = TrackDownloadManager(context, OkHttpClient.Builder().addInterceptor(server).build())
        (1..3).map { id -> async(Dispatchers.IO) { downloads.downloadTrack("$id", "https://example.com/$id") } }.awaitAll()
        assertEquals(3, requests.get())
        assertTrue("tracks do not wait for each other", mostAtOnce.get() > 1)
    }
}

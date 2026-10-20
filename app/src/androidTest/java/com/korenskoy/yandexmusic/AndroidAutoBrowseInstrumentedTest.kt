package com.korenskoy.yandexmusic

import android.content.ComponentName
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.MusicService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit

/**
 * Browses MusicService the way Android Auto does (MediaBrowser, no Activity) and starts a radio
 * station from the tree. Radio needs no Yandex account.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoBrowseInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var browser: MediaBrowser

    @Before
    fun connect() {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        browser = MediaBrowser.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
    }

    @After
    fun disconnect() {
        instrumentation.runOnMainSync {
            browser.pause()
            browser.release()
        }
    }

    /** Browser calls must start on the main thread; the result is awaited on the test thread. */
    private fun <T : Any> call(request: MediaBrowser.() -> ListenableFuture<LibraryResult<T>>): LibraryResult<T> {
        lateinit var future: ListenableFuture<LibraryResult<T>>
        instrumentation.runOnMainSync { future = browser.request() }
        return future.get(10, TimeUnit.SECONDS)
    }

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
        }
    }

    @Test
    fun rootListsWaveLikedDownloadedAndRadio() {
        val root = call { getLibraryRoot(null) }.value
        assertNotNull(root)
        val children = call { getChildren(root!!.mediaId, 0, 100, null) }.value.orEmpty()
        assertEquals(listOf("wave", "liked", "downloaded", "playlists", "radio"), children.map { it.mediaId })
        assertEquals(true, children.first().mediaMetadata.isPlayable)
        assertTrue(children.drop(1).all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun radioStationsArePlayableWithArtworkAndPaged() {
        val all = call { getChildren("radio", 0, 1_000, null) }.value.orEmpty()
        assertTrue("stations listed", all.size > 10)
        assertTrue(all.all { it.mediaMetadata.isPlayable == true && it.mediaMetadata.artworkUri != null })

        val secondPage = call { getChildren("radio", 1, 5, null) }.value.orEmpty()
        assertEquals(all.subList(5, 10).map { it.mediaId }, secondPage.map { it.mediaId })

        val unknown = call { getChildren("nope", 0, 10, null) }
        assertTrue("unknown node is an error", unknown.resultCode < 0)
    }

    @Test
    fun pickingAStationInTheCarStartsIt() {
        val station = call { getItem("radio/record") }.value
        assertNotNull(station)
        // Android 15 grants audio focus only to a foreground app. In a car that comes from the Android Auto
        // binding; here the app itself is brought to the front.
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync {
                browser.setMediaItem(station!!)
                browser.prepare()
                browser.play()
            }
            waitUntil(30_000, "station plays") {
                var ok = false
                instrumentation.runOnMainSync {
                    ok = browser.isPlaying && browser.currentMediaItem?.mediaId == "radio_record"
                }
                ok
            }
            instrumentation.runOnMainSync { browser.pause() }
        }
    }

    @Test
    fun voiceSearchForAStationNameStartsThatStation() {
        val voiceRequest = androidx.media3.common.MediaItem.Builder()
            .setRequestMetadata(
                androidx.media3.common.MediaItem.RequestMetadata.Builder().setSearchQuery("radio record").build()
            )
            .build()
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync {
                browser.setMediaItem(voiceRequest)
                browser.prepare()
                browser.play()
            }
            waitUntil(30_000, "station plays") {
                var ok = false
                instrumentation.runOnMainSync {
                    ok = browser.isPlaying && browser.currentMediaItem?.mediaId == "radio_record"
                }
                ok
            }
            instrumentation.runOnMainSync { browser.pause() }
        }
    }
}

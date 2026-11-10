package com.korenskoy.yandexmusic.data.updater

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Closing the update window stops the download: the connection is cut and nothing half-written stays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateCancelTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val cut = AtomicBoolean(false)

    /** An endless slow file; reading fails once the call is cancelled, as a real connection would. */
    private val slowServer = Interceptor { chain ->
        val call = chain.call()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                Thread.sleep(10)
                if (call.isCanceled()) {
                    cut.set(true)
                    throw IOException("Canceled")
                }
                sink.write(ByteArray(1024))
                return 1024
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }
        val body = object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = 50L * 1024 * 1024
            override fun source(): BufferedSource = source.buffer()
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
    }

    @Test
    fun cancellingTheDownloadCutsTheConnection() = runBlocking {
        val manager = GitHubUpdateManager(context, OkHttpClient.Builder().addInterceptor(slowServer).build())
        val download = async(Dispatchers.IO) {
            manager.downloadApk("https://github.com/KorenskoyR/YandexMusicLite/releases/download/v9/app.apk", null) {}
        }
        delay(300)
        download.cancel()
        withTimeout(5_000) { download.join() }

        assertTrue("the connection was cut, not left to run to the end", cut.get())
        assertFalse(File(context.cacheDir, "updates/YandexMusicLite_download.tmp").exists())
    }
}

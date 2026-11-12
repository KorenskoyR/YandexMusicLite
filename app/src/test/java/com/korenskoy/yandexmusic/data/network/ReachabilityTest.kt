package com.korenskoy.yandexmusic.data.network

import android.app.Application
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.LinkTracker
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * "Нет связи" on a good network (GitHub, an Android box): the server check went to the first address of the
 * name only. A network that hands out an IPv6 address with no IPv6 route behind it failed it forever, while the
 * music, through a real HTTP client, went over IPv4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReachabilityTest {

    private lateinit var server: ServerSocket
    @Volatile private var running = true

    @Before
    fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (running) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { socket ->
                        runCatching {
                            val input = socket.getInputStream().bufferedReader()
                            while (input.readLine()?.isNotEmpty() == true) Unit
                            socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            socket.getOutputStream().flush()
                        }
                    }
                }
            }
        }
    }

    @After
    fun stop() {
        running = false
        server.close()
    }

    /** A name that resolves first to an IPv6 address nothing answers on (the discard prefix), then to IPv4. */
    private val brokenIpv6 = Dns { listOf(InetAddress.getByName("100::1"), InetAddress.getByName("127.0.0.1")) }
    private val url get() = "http://music.test:${server.localPort}/"

    @Test
    fun aNetworkWithBrokenIpv6StillReachesTheServer() {
        val client = OkHttpClient.Builder().dns(brokenIpv6).build()
        assertTrue("every address is tried, as the music's own client does", NetworkMonitor.answers(client, url, 4_000))
    }

    @Test
    fun theOldCheckWouldHaveSaidNoServer() {
        // What 1.14.2 did: a bare socket to the first address of the name
        val first = brokenIpv6.lookup("music.test").first()
        val reached = try {
            Socket().use { it.connect(InetSocketAddress(first, server.localPort), 1_500) }
            true
        } catch (e: IOException) {
            false
        }
        assertFalse("the first address is IPv6 with no route: the old check failed here", reached)
    }

    @Test
    fun anErrorPageStillMeansTheServerIsThere() {
        assertTrue(NetworkMonitor.answers(OkHttpClient(), "http://127.0.0.1:${server.localPort}/", 4_000))
    }

    @Test
    fun nothingListeningIsNoServer() {
        val closed = ServerSocket(0).use { it.localPort }
        assertFalse(NetworkMonitor.answers(OkHttpClient(), "http://127.0.0.1:$closed/", 1_500))
    }

    @Test
    fun aStreamThatSoundsEndsNoConnection() {
        val link = LinkTracker { 0L }
        link.onTrouble(reachable = false)
        assertEquals(LinkState.NONE, link.state)
        link.onStreamAudible()
        assertEquals("music is heard: the server is there", LinkState.GOOD, link.state)
    }
}

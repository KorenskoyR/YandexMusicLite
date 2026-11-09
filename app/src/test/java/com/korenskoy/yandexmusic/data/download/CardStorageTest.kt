package com.korenskoy.yandexmusic.data.download

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/** Downloads on the SD card: encrypted on the card, playable from any position, moved both ways losing nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CardStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = Random(7).nextBytes(32)
    private val music = Random(11).nextBytes(150_003) // not a multiple of the 16-byte block on purpose
    private lateinit var cardRoot: File

    @Before
    fun clean() {
        cardRoot = File(context.cacheDir, "fake_card").apply { deleteRecursively(); mkdirs() }
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private val server = Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
            .body(music.toResponseBody("audio/mpeg".toMediaType())).build()
    }
    private val client = OkHttpClient.Builder().addInterceptor(server).build()

    private fun card(wanted: Boolean = true) = testCard(context, cardRoot, key, wanted)

    private fun encrypted(): File {
        val file = File(cardRoot, "x.enc")
        file.outputStream().use { out -> CardStorage.encrypting(key, out).use { it.write(music) } }
        return file
    }

    private fun readThroughPlayer(file: File, position: Long, length: Long = C.LENGTH_UNSET.toLong()): ByteArray {
        val source = EncryptedFileDataSource(card())
        val out = ByteArrayOutputStream()
        source.open(DataSpec.Builder().setUri(Uri.fromFile(file)).setPosition(position).setLength(length).build())
        val buffer = ByteArray(4096)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        source.close()
        return out.toByteArray()
    }

    @Test
    fun theCardFileIsNotTheMusic() {
        val bytes = encrypted().readBytes()
        assertEquals(music.size + 16, bytes.size)
        assertFalse(bytes.copyOfRange(16, 16 + 64).contentEquals(music.copyOfRange(0, 64)))
    }

    @Test
    fun theWholeTrackComesBack() {
        assertArrayEquals(music, readThroughPlayer(encrypted(), 0))
    }

    @Test
    fun seekingLandsOnTheRightBytes() {
        val file = encrypted()
        // Inside a block, on a block edge, near the end: what a seek in the player asks for
        for (position in listOf(1L, 15L, 16L, 17L, 4095L, 70_000L, 149_990L)) {
            assertArrayEquals("from $position", music.copyOfRange(position.toInt(), music.size), readThroughPlayer(file, position))
        }
        assertArrayEquals(music.copyOfRange(100, 1100), readThroughPlayer(file, 100, 1000))
    }

    @Test
    fun aDownloadGoesToTheCardEncryptedWhenChosen() = runBlocking {
        val downloads = TrackDownloadManager(context, client, card(wanted = true))
        assertTrue(downloads.downloadTrack("7", "https://example.com/t", TrackPreloadManager.StreamQuality("mp3", 192)))

        val file = downloads.getLocalTrackFile("7")
        assertNotNull(file)
        assertEquals("enc", file!!.extension)
        assertTrue(file.path.startsWith(cardRoot.path))
        assertArrayEquals(music, readThroughPlayer(file, 0))
        assertTrue(downloads.isKnownDownloaded("7"))
        assertEquals(192, downloads.downloadedQuality("7")?.bitrateKbps)
        assertFalse(File(context.filesDir, "tracks/7.mp3").exists())
    }

    @Test
    fun withoutTheChoiceDownloadsStayInside() = runBlocking {
        val downloads = TrackDownloadManager(context, client, card(wanted = false))
        assertTrue(downloads.downloadTrack("8", "https://example.com/t"))
        assertEquals("mp3", downloads.getLocalTrackFile("8")?.extension)
        assertArrayEquals(music, downloads.getLocalTrackFile("8")!!.readBytes())
    }

    @Test
    fun movingToTheCardAndBackKeepsEveryTrackWhole() = runBlocking {
        val inside = TrackDownloadManager(context, client, card(wanted = false))
        for (id in listOf("1", "2", "3")) inside.downloadTrack(id, "https://example.com/$id", TrackPreloadManager.StreamQuality("mp3", 320))

        val progress = mutableListOf<Pair<Int, Int>>()
        assertTrue(inside.moveAll(toCard = true) { moved, total -> progress += moved to total })
        assertEquals(3 to 3, progress.last())
        for (id in listOf("1", "2", "3")) {
            val file = inside.getLocalTrackFile(id)!!
            assertEquals("enc", file.extension)
            assertArrayEquals(music, readThroughPlayer(file, 0))
            assertEquals(320, inside.downloadedQuality(id)?.bitrateKbps)
        }
        assertTrue(File(context.filesDir, "tracks").listFiles().orEmpty().none { it.extension == "mp3" })

        assertTrue(inside.moveAll(toCard = false) { _, _ -> })
        for (id in listOf("1", "2", "3")) {
            val file = inside.getLocalTrackFile(id)!!
            assertEquals("mp3", file.extension)
            assertArrayEquals(music, file.readBytes())
        }
        assertTrue(cardRoot.walk().none { it.extension == "enc" })
    }

    @Test
    fun removingAndClearingReachTheCard() = runBlocking {
        val downloads = TrackDownloadManager(context, client, card(wanted = true))
        downloads.downloadTrack("5", "https://example.com/5")
        downloads.removeTrack("5")
        assertFalse(downloads.isKnownDownloaded("5"))
        downloads.downloadTrack("6", "https://example.com/6")
        downloads.clearMusicCache()
        assertTrue(cardRoot.walk().none { it.isFile && it.extension != "tmp" && it.name != "tracks" })
    }
}

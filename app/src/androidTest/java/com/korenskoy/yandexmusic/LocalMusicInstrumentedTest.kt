package com.korenskoy.yandexmusic

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.korenskoy.yandexmusic.data.local.LocalArtProvider
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.data.local.LocalTrack
import com.korenskoy.yandexmusic.data.player.MusicService
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * "Музыка с устройства" on a real device: real MP3 files put into Android's own music catalogue — with tags, with a
 * cover inside, with old Windows-1251 tags, without tags, and a short sound that is no song. The list, the covers,
 * playing them with nothing from the network, a file gone in the middle of the queue, and Android Auto's folder.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(AndroidJUnit4::class)
class LocalMusicInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MainViewModel
    private lateinit var repo: LocalMusicRepository
    private val created = mutableListOf<Uri>()

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
        }
    }

    // --- Real files ---

    /** An ID3v2.3 text frame: UTF-16 with its mark, or [raw] bytes as "Latin-1" (how old Russian files were tagged). */
    private fun textFrame(id: String, text: String, raw: Charset? = null): ByteArray {
        val body = ByteArrayOutputStream()
        if (raw != null) {
            body.write(0)
            body.write(text.toByteArray(raw))
        } else {
            body.write(1)
            body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            body.write(text.toByteArray(Charsets.UTF_16LE))
        }
        return frame(id, body.toByteArray())
    }

    private fun pictureFrame(jpeg: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0)
        body.write("image/jpeg".toByteArray()); body.write(0)
        body.write(3) // the front cover
        body.write(0) // no description
        body.write(jpeg)
        return frame("APIC", body.toByteArray())
    }

    private fun frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray())
        out.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
        out.write(byteArrayOf(0, 0))
        out.write(data)
        return out.toByteArray()
    }

    /**
     * [seconds] of silence as MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, mono: every frame a header and zeros, which
     * decodes to silence. [frames] become the ID3 tag in front.
     */
    private fun mp3(seconds: Int, frames: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        if (frames.isNotEmpty()) {
            val tag = frames.fold(ByteArray(0)) { all, f -> all + f }
            val size = tag.size
            out.write("ID3".toByteArray()); out.write(byteArrayOf(3, 0, 0))
            out.write(byteArrayOf(((size shr 21) and 0x7F).toByte(), ((size shr 14) and 0x7F).toByte(), ((size shr 7) and 0x7F).toByte(), (size and 0x7F).toByte()))
            out.write(tag)
        }
        val frame = ByteArray(FRAME_BYTES).also {
            it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0xC0.toByte()
        }
        repeat(seconds * 44_100 / 1_152) { out.write(frame) }
        return out.toByteArray()
    }

    private fun cover(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 60)) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** Put into the catalogue as any music app or a file copied from a computer would be. */
    private fun put(name: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        created += uri
        return uri
    }

    private fun clearOldFiles() {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.RELATIVE_PATH} = ?", arrayOf(FOLDER), null)?.use { c ->
            while (c.moveToNext()) runCatching { resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null) }
        }
    }

    private val ours: List<LocalTrack> get() = vm.localLibrary.value.tracks.filter { it.folder == FOLDER }

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, LocalMusicRepository.PERMISSION)
        clearOldFiles()
        val win = Charset.forName("windows-1251")
        put("01 - tagged.mp3", mp3(40, listOf(textFrame("TIT2", "Тест Один"), textFrame("TPE1", "Тестовый"), textFrame("TALB", "Проверка"), textFrame("TRCK", "1"), pictureFrame(cover()))))
        put("02 - old.mp3", mp3(40, listOf(textFrame("TIT2", "Группа крови", raw = win), textFrame("TPE1", "Кино", raw = win), textFrame("TRCK", "2"))))
        put("03 - Кино - Кукушка.mp3", mp3(40, emptyList()))
        put("beep.mp3", mp3(4, listOf(textFrame("TIT2", "Сигнал"))))

        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        repo = MainViewModel::class.java.getDeclaredField("localMusic").run {
            isAccessible = true
            get(vm) as LocalMusicRepository
        }
        repo.onAccessMaybeChanged()
        repo.refresh()
        // The list outlives a test: wait for this test's own files, not the last test's (already deleted) ones
        val songs = created.dropLast(1).map { it.toString() }.toSet()
        waitUntil(30_000, "the catalogue lists the three songs: ${ours.map { it.title + " " + it.uri }} / $songs") { ours.map { it.uri }.toSet() == songs }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { vm.stopAndClearPlayer() }
        created.forEach { runCatching { resolver.delete(it, null, null) } }
        scenario.close()
    }

    @Test
    fun theDevicesMusicIsFoundAndNamedRight() {
        val byTitle = ours.associateBy { it.title }
        assertEquals("tags", "Тестовый", byTitle["Тест Один"]?.artist)
        assertEquals("the album", "Проверка", byTitle["Тест Один"]?.album)
        assertEquals("old Windows tags read as Russian: ${byTitle.keys}", "Кино", byTitle["Группа крови"]?.artist)
        assertEquals("no tags: named by the file", "Кино", byTitle["Кукушка"]?.artist)
        assertNull("a 4-second sound is no song", byTitle["Сигнал"])
        assertTrue(ours.all { it.source == LocalSource.PHONE && it.format == "MP3" })
        assertTrue("the length is known: ${ours.map { it.durationMs }}", ours.all { it.durationMs in 35_000..45_000 })
        // The folder on the device, and its order (track numbers, then names)
        val folder = vm.localLibrary.value.let { com.korenskoy.yandexmusic.data.local.LocalRules.folders(it.tracks) }.first { it.key == "PHONE:$FOLDER" }
        assertEquals(listOf("Тест Один", "Группа крови", "Кукушка"), folder.tracks.map { it.title })
    }

    @Test
    fun theCoverComesOutOfTheFile() {
        val tagged = ours.first { it.title == "Тест Один" }
        val bitmap = resolver.openInputStream(tagged.art!!.toUri())!!.use { BitmapFactory.decodeStream(it) }
        assertNotNull("the picture inside the file", bitmap)
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertTrue("it is the file's own red: ${Integer.toHexString(pixel)}", Color.red(pixel) > 150 && Color.green(pixel) < 100)
        val bare = ours.first { it.title == "Кукушка" }
        val none = runCatching { resolver.openInputStream(bare.art!!.toUri())?.close() }.exceptionOrNull()
        assertTrue("no picture, no cover: $none", none is FileNotFoundException)
    }

    private fun assertPlaysAudibly(trackId: String) {
        waitUntil(15_000, "$trackId is the current track") { vm.currentTrack.value?.idString == trackId }
        waitUntil(15_000, "$trackId is playing") { vm.isPlaying.value && !vm.isBuffering.value }
        val from = vm.progress.value
        waitUntil(8_000, "$trackId moves on: really sounds") { vm.progress.value > from + 300 }
    }

    @Test
    fun theDevicesMusicPlaysWithoutTheNetworkAndGoesOn() {
        val queue = com.korenskoy.yandexmusic.data.local.LocalRules.inFolderOrder(ours)
        instrumentation.runOnMainSync { vm.playLocal(queue[0], queue) }
        assertPlaysAudibly(queue[0].id)
        assertEquals(PlaylistSource.DEVICE, vm.currentSource.value)
        assertTrue("the quality says where it is from: ${vm.streamQuality.value}", vm.streamQuality.value.orEmpty().contains("с устройства"))
        assertTrue("the player knows the file: ${vm.currentTrack.value?.localUri}", vm.currentTrack.value?.localUri?.startsWith("content://media/") == true)
        // Next: the next file, as the queue goes
        instrumentation.runOnMainSync { vm.playNext() }
        assertPlaysAudibly(queue[1].id)
    }

    @Test
    fun aFileGoneFromTheQueueIsPassedOver() {
        val queue = com.korenskoy.yandexmusic.data.local.LocalRules.inFolderOrder(ours)
        // The middle file goes before it is ever opened (as with a drive taken out). A file the player has open
        // already plays on to its end, as on any Linux, so it goes first here
        resolver.delete(queue[1].uri.toUri(), null, null)
        assertFalse(kotlinx.coroutines.runBlocking { repo.isReachable(queue[1].uri) })
        instrumentation.runOnMainSync { vm.playLocal(queue[0], queue) }
        assertPlaysAudibly(queue[0].id)
        instrumentation.runOnMainSync { vm.playNext() }
        waitUntil(20_000, "the third file plays instead: ${vm.currentTrack.value?.title}") { vm.currentTrack.value?.idString == queue[2].id }
        assertPlaysAudibly(queue[2].id)
        // Nothing left that opens: the music stops and says why, no endless skipping
        resolver.delete(queue[0].uri.toUri(), null, null)
        resolver.delete(queue[2].uri.toUri(), null, null)
        instrumentation.runOnMainSync { vm.playNext() }
        waitUntil(20_000, "the music stops: ${vm.currentTrack.value?.title} playing=${vm.isPlaying.value}") { !vm.isPlaying.value }
    }

    @Test
    fun androidAutoFindsTheDevicesMusicInsideDownloaded() {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val browser = MediaBrowser.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        fun <T : Any> call(request: MediaBrowser.() -> ListenableFuture<LibraryResult<T>>): LibraryResult<T> {
            lateinit var future: ListenableFuture<LibraryResult<T>>
            instrumentation.runOnMainSync { future = browser.request() }
            return future.get(10, TimeUnit.SECONDS)
        }
        try {
            val downloaded = call { getChildren("downloaded", 0, 100, null) }.value.orEmpty()
            assertEquals(listOf("downloaded_app", "device"), downloaded.map { it.mediaId })
            val device = call { getChildren("device", 0, 500, null) }.value.orEmpty()
            assertTrue("the device's folders or tracks are there", device.isNotEmpty())
            // Down to our folder's tracks and play one, as the car would
            val folder = device.firstOrNull { it.mediaMetadata.title == "YMLiteTest" }
            val tracks = if (folder != null) call { getChildren(folder.mediaId, 0, 100, null) }.value.orEmpty() else device
            val kukushka = tracks.first { it.mediaMetadata.title == "Кукушка" }
            assertTrue("the car gets no address of the app's own covers", tracks.none { LocalArtProvider.isOurs(it.mediaMetadata.artworkUri?.toString()) })
            instrumentation.runOnMainSync {
                browser.setMediaItem(kukushka)
                browser.prepare()
                browser.play()
            }
            waitUntil(20_000, "Android Auto's choice plays: ${vm.currentTrack.value?.title}") { vm.currentTrack.value?.title == "Кукушка" }
            assertEquals(PlaylistSource.DEVICE, vm.currentSource.value)
            // The system's media panel and the car get the cover as a picture, never this app's own address
            // (Android 11's SystemUI fell over on it). Checked on the player the session publishes
            val tagged = com.korenskoy.yandexmusic.data.local.LocalRules.inFolderOrder(ours).first { it.title == "Тест Один" }
            instrumentation.runOnMainSync { vm.playLocal(tagged, listOf(tagged)) }
            assertPlaysAudibly(tagged.id)
            val player = playerOfTheSession()
            var art: androidx.media3.common.MediaMetadata? = null
            waitUntil(30_000, "the playing item carries its cover: uri=${art?.artworkUri} data=${art?.artworkData?.size}") {
                instrumentation.runOnMainSync { art = player().currentMediaItem?.mediaMetadata }
                art?.artworkData != null
            }
            assertFalse(LocalArtProvider.isOurs(art?.artworkUri?.toString()))
        } finally {
            instrumentation.runOnMainSync {
                browser.pause()
                browser.release()
            }
        }
    }

    /** The player the media session shows, as the app's dual-player engine has it now. */
    private fun playerOfTheSession(): () -> androidx.media3.common.Player {
        val controller = MainViewModel::class.java.getDeclaredField("playbackController").run { isAccessible = true; get(vm)!! }
        val repository = controller.javaClass.getDeclaredField("playerRepository").run {
            isAccessible = true
            get(controller) as com.korenskoy.yandexmusic.domain.repository.PlayerRepository
        }
        return { repository.getPlayer() }
    }

    private companion object {
        const val FOLDER = "Music/YMLiteTest/"
        /** 144 × 128 000 / 44 100: one frame at 128 kbit/s, without padding. */
        const val FRAME_BYTES = 417
    }
}

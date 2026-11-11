package com.korenskoy.yandexmusic

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.korenskoy.yandexmusic.data.download.StorageAccess
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.presentation.local.LocalMusicPage
import com.korenskoy.yandexmusic.presentation.local.LocalMusicUi
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * "Добавить папку" through the real system window, for head units whose flash drive Android does not list: the
 * window opens on the drive, the folder is chosen and allowed, its music is read file by file (tags, subfolders)
 * and shows once — not twice where the catalogue lists it too. Needs a removable drive (on an emulator:
 * `sm set-virtual-disk true`); skipped without one.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(AndroidJUnit4::class)
class LocalFolderInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val repo: LocalMusicRepository = EntryPointAccessors.fromApplication(context.applicationContext, StorageAccess::class.java).localMusic()
    private val device = UiDevice.getInstance(instrumentation)
    private val created = mutableListOf<Uri>()

    private fun waitFor(timeoutMs: Long = 20_000, message: String = "", condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue("Timed out: $message", condition())
    }

    /** Silence as MPEG-1 Layer III with an ID3v2.3 tag of UTF-16 texts (see LocalMusicInstrumentedTest). */
    private fun mp3(seconds: Int, tags: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        if (tags.isNotEmpty()) {
            val frames = ByteArrayOutputStream()
            for ((id, text) in tags) {
                val data = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
                frames.write(id.toByteArray())
                frames.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte(), 0, 0))
                frames.write(data)
            }
            val size = frames.size()
            out.write("ID3".toByteArray()); out.write(byteArrayOf(3, 0, 0))
            out.write(byteArrayOf(((size shr 21) and 0x7F).toByte(), ((size shr 14) and 0x7F).toByte(), ((size shr 7) and 0x7F).toByte(), (size and 0x7F).toByte()))
            out.write(frames.toByteArray())
        }
        val frame = ByteArray(417).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0xC0.toByte() }
        repeat(seconds * 44_100 / 1_152) { out.write(frame) }
        return out.toByteArray()
    }

    private fun put(volume: String, folder: String, name: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(volume), values)!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        created += uri
    }

    @After
    fun tearDown() {
        repo.addedFolders().filter { "YMLiteFolder" in Uri.decode(it.uri) }.forEach { repo.removeFolder(it.uri) }
        created.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    @Test
    fun aFolderOnTheDriveIsAddedAndItsMusicShowsOnce() {
        val drive: StorageVolume? = context.getSystemService(StorageManager::class.java).storageVolumes
            .firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        assumeTrue("No removable drive on this device", drive != null)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, LocalMusicRepository.PERMISSION)
        val volume = drive!!.mediaStoreVolumeName!!
        // Left over from an earlier run
        repo.addedFolders().filter { "YMLiteFolder" in Uri.decode(it.uri) }.forEach { repo.removeFolder(it.uri) }
        put(volume, "Music/YMLiteFolder/", "01 - tagged.mp3", mp3(40, mapOf("TIT2" to "Тест Флешка", "TPE1" to "Магнитола", "TRCK" to "1")))
        put(volume, "Music/YMLiteFolder/", "Ария - Беспечный ангел.mp3", mp3(40, emptyMap()))
        put(volume, "Music/YMLiteFolder/Рок/", "Кино - Перемен.mp3", mp3(40, emptyMap()))

        compose.setContent {
            YandexMusicTheme {
                val library by repo.library.collectAsState()
                LocalMusicPage(
                    ui = LocalMusicUi(library = library, toTrack = repo::toDto, onAddFolder = { repo.addFolder(it) }, pickerStart = repo::pickerStart),
                    currentTrackId = null, isPlaying = false, contentBottom = 24.dp, onBack = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Добавить папку").performScrollTo().performClick()

        // The system window, opened on the drive: Music › YMLiteFolder, "use this folder", "allow"
        assertNotNull("The folder window did not open on the drive", device.wait(Until.findObject(By.text("Music")), 15_000))
        device.findObject(By.text("Music")).click()
        device.wait(Until.findObject(By.text("YMLiteFolder")), 10_000)!!.click()
        device.wait(Until.findObject(By.text("Рок")), 10_000)
        (device.wait(Until.findObject(By.textContains("USE THIS FOLDER").clickable(true)), 10_000)
            ?: device.wait(Until.findObject(By.textContains("Use this folder")), 5_000))!!.click()
        (device.wait(Until.findObject(By.text("ALLOW")), 10_000) ?: device.wait(Until.findObject(By.text("Allow")), 5_000))!!.click()

        // Kept and read
        waitFor(40_000, "the folder is read: ${repo.library.value.folders}") {
            val now = repo.library.value
            now.folders.any { it.name == "YMLiteFolder" } && !now.readingFolders
        }
        compose.onNodeWithText("YMLiteFolder").performScrollTo()
        val ours = { repo.library.value.tracks.filter { "YMLiteFolder" in it.folder } }
        waitFor(20_000, "the three songs: ${ours().map { it.title }}") { ours().size == 3 }
        assertEquals("each once, though the catalogue lists them too", 3, ours().map { it.title }.distinct().size)
        assertTrue(ours().all { it.source != LocalSource.PHONE })

        // The folder walk itself, without the catalogue: tags, names from the files, the subfolder
        val tree = repo.addedFolders().first { it.name == "YMLiteFolder" }.uri.toUri()
        val scan = repo.javaClass.getDeclaredField("folderScan").run { isAccessible = true; get(repo)!! }
        val walk = scan.javaClass.getDeclaredMethod("scan", Uri::class.java, Function1::class.java, kotlin.coroutines.Continuation::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val found = kotlinx.coroutines.runBlocking {
            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Any?> { c -> walk.invoke(scan, tree, { _: Any? -> }, c) }
        } as List<Any>
        val tracks = found.map { it.javaClass.getMethod("getTrack").invoke(it) as com.korenskoy.yandexmusic.data.local.LocalTrack }
        val byTitle = tracks.associateBy { it.title }
        assertEquals("Магнитола", byTitle["Тест Флешка"]?.artist)
        assertEquals(1, byTitle["Тест Флешка"]?.trackNumber)
        assertEquals("Ария", byTitle["Беспечный ангел"]?.artist)
        assertEquals("Music/YMLiteFolder/Рок/", byTitle["Перемен"]?.folder)
        assertTrue("lengths read from the files: ${tracks.map { it.durationMs }}", tracks.all { it.durationMs in 35_000..45_000 })
        assertTrue(tracks.all { it.uri.startsWith("content://com.android.externalstorage.documents/tree/") })

        // Taken off the list again
        repo.removeFolder(tree.toString())
        waitFor(20_000, "the folder is gone from the list") { repo.library.value.folders.none { it.name == "YMLiteFolder" } }
    }
}

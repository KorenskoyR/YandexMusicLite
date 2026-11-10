package com.korenskoy.yandexmusic.data.download

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager.Place
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Downloads on a flash drive, through the system's documents (a folder picked once): written encrypted,
 * listed, played from any position, moved there and back losing nothing; a drive taken out is noticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UsbStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = Random(7).nextBytes(32)
    private val music = Random(13).nextBytes(120_007)
    private lateinit var cardRoot: File

    private val server = Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
            .body(music.toResponseBody("audio/mpeg".toMediaType())).build()
    }
    private val client = OkHttpClient.Builder().addInterceptor(server).build()
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(FakeDrive.AUTHORITY, "usb:YandexMusicLite")

    @Before
    fun setUp() {
        FakeDrive.root = File(context.cacheDir, "fake_drive").apply { deleteRecursively(); mkdirs() }
        File(FakeDrive.root, "YandexMusicLite").mkdirs()
        cardRoot = File(context.cacheDir, "fake_card").apply { deleteRecursively(); mkdirs() }
        File(context.filesDir, "tracks").deleteRecursively()
        val info = ProviderInfo().apply {
            authority = FakeDrive.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FakeDrive::class.java).create(info)
    }

    private fun usb(wanted: Boolean = true) = UsbStorage(context, flowOf(wanted), flowOf(tree.toString())).also {
        runBlocking { it.folder.first { uri -> uri != null } }
    }

    private fun card() = testCard(context, cardRoot, key, wanted = false)

    private fun readThroughPlayer(uri: Uri, position: Long = 0): ByteArray {
        val source = EncryptedFileDataSource(card())
        val out = ByteArrayOutputStream()
        source.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
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
    fun theFolderIsSeenWithItsNameAndFreeSpace() {
        val drive = usb()
        assertTrue(drive.present)
        assertEquals("USB-накопитель › YandexMusicLite", drive.label())
        assertEquals(FakeDrive.FREE, drive.freeBytes())
    }

    @Test
    fun aDownloadGoesToTheDriveEncryptedAndPlaysFromAnyPosition() = runBlocking {
        val downloads = TrackDownloadManager(context, client, card(), usb())
        assertTrue(downloads.downloadTrack("5", "https://example.com/t", TrackPreloadManager.StreamQuality("mp3", 192)))

        val onDrive = File(FakeDrive.root, "YandexMusicLite/5.enc")
        assertTrue(onDrive.exists())
        assertEquals(music.size + 16, onDrive.length().toInt())
        assertFalse(onDrive.readBytes().copyOfRange(16, 80).contentEquals(music.copyOfRange(0, 64)))
        assertTrue("nothing half-written stays", File(FakeDrive.root, "YandexMusicLite").list()!!.none { it.endsWith(".part") })
        assertFalse(File(context.filesDir, "tracks/5.mp3").exists())
        assertTrue(downloads.isKnownDownloaded("5"))
        assertEquals(192, downloads.downloadedQuality("5")?.bitrateKbps)

        val uri = Uri.parse(downloads.localTrackUri("5"))
        assertEquals("content", uri.scheme)
        assertArrayEquals(music, readThroughPlayer(uri))
        assertArrayEquals(music.copyOfRange(70_001, music.size), readThroughPlayer(uri, 70_001))
    }

    @Test
    fun movingToTheDriveAndBackKeepsEveryTrackWhole() = runBlocking {
        val drive = usb(wanted = false)
        val downloads = TrackDownloadManager(context, client, card(), drive)
        for (id in listOf("1", "2", "3")) downloads.downloadTrack(id, "https://example.com/$id")
        assertTrue(File(context.filesDir, "tracks/1.mp3").exists())

        assertTrue(downloads.moveAll(Place.USB) { _, _ -> })
        for (id in listOf("1", "2", "3")) {
            assertFalse(File(context.filesDir, "tracks/$id.mp3").exists())
            assertArrayEquals(music, readThroughPlayer(Uri.parse(downloads.localTrackUri(id))))
        }
        assertEquals(setOf("1", "2", "3"), downloads.downloadedTrackIds.value)

        assertTrue(downloads.moveAll(Place.INTERNAL) { _, _ -> })
        for (id in listOf("1", "2", "3")) {
            assertArrayEquals(music, File(context.filesDir, "tracks/$id.mp3").readBytes())
            assertFalse(File(FakeDrive.root, "YandexMusicLite/$id.enc").exists())
        }
    }

    @Test
    fun aDriveTakenOutIsNoticedAndNewDownloadsStayInside() = runBlocking {
        val drive = usb()
        val downloads = TrackDownloadManager(context, client, card(), drive)
        assertTrue(downloads.downloadTrack("9", "https://example.com/9"))
        FakeDrive.root.resolve("YandexMusicLite").renameTo(File(FakeDrive.root, "elsewhere"))
        downloads.refresh()

        assertFalse(drive.present)
        assertNull("its tracks wait for the drive", downloads.localTrackUri("9"))
        assertTrue(downloads.downloadTrack("10", "https://example.com/10"))
        assertArrayEquals(music, File(context.filesDir, "tracks/10.mp3").readBytes())
    }

    @Test
    fun onlyLeftoversOfAnEarlierRunAreCleanedAway() {
        File(FakeDrive.root, "YandexMusicLite/7.enc.old1.part").writeText("cut short")
        val drive = usb()
        // A file being written right now by this run (as put() names it) stays
        val mine = drive.files().keys
        assertFalse(File(FakeDrive.root, "YandexMusicLite/7.enc.old1.part").exists())
        assertTrue(mine.isEmpty())
        assertTrue(drive.put("8.enc") { out -> drive.files(); out.write(ByteArray(10)) })
        assertTrue(File(FakeDrive.root, "YandexMusicLite/8.enc").length() == 10L)
    }

    @Test
    fun removingATrackDeletesItFromTheDrive() = runBlocking {
        val downloads = TrackDownloadManager(context, client, card(), usb())
        downloads.downloadTrack("4", "https://example.com/4")
        downloads.removeTrack("4")
        assertFalse(File(FakeDrive.root, "YandexMusicLite/4.enc").exists())
        assertFalse(downloads.isKnownDownloaded("4"))
    }
}

/** A flash drive as the system's documents show it: a folder tree with ids "usb:<path>". */
class FakeDrive : DocumentsProvider() {
    companion object {
        const val AUTHORITY = "test.fake.drive"
        const val FREE = 28_400_000_000L
        lateinit var root: File
    }

    private fun file(id: String) = File(root, id.removePrefix("usb:"))
    private fun id(file: File) = "usb:" + file.relativeTo(root).path.replace('\\', '/')

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_AVAILABLE_BYTES)).apply {
        addRow(arrayOf<Any>("usb", "usb:", "USB-накопитель", FREE))
    }

    private fun row(cursor: MatrixCursor, f: File) {
        cursor.addRow(arrayOf<Any>(id(f), f.name, if (f.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream", f.length(), Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or Document.FLAG_DIR_SUPPORTS_CREATE))
    }

    private fun cursor() = MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor().apply {
        val f = file(documentId)
        if (f.exists()) row(this, f)
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = cursor().apply {
        file(parentDocumentId).listFiles()?.forEach { row(this, it) }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val f = file(documentId)
        if (!f.exists() && !mode.contains('w')) throw java.io.FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val f = File(file(parentDocumentId), displayName)
        f.createNewFile()
        return id(f)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val f = file(documentId)
        val to = File(f.parentFile, displayName)
        check(f.renameTo(to))
        return id(to)
    }

    override fun deleteDocument(documentId: String) {
        file(documentId).delete()
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith(parentDocumentId)
}

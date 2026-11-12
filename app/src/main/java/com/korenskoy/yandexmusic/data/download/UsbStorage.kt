package com.korenskoy.yandexmusic.data.download

import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.CancellationException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.util.Log
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads on a USB flash drive. Android gives an app no direct path to a flash drive, only a folder the
 * user picks once in the system's own window; the permission is kept across restarts. Files there are
 * "<id>.enc", encrypted with the same key as on the SD card, so they play only in this app.
 */
@Singleton
class UsbStorage(
    private val context: Context,
    wantsUsb: Flow<Boolean>,
    folderUri: Flow<String?>
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: SettingsRepository) :
        this(context, settings.downloadsOnUsb, settings.usbFolder)

    /** One file in the folder: where it is and how big. */
    data class Entry(val uri: Uri, val size: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Marks this run's unfinished files ("<name>.<run>.part"): a listing cleans away only those of earlier
     * runs, never a file another download is writing right now.
     */
    private val run = java.lang.Long.toString(System.nanoTime() and 0xFFFFFF, 36)
    private val resolver get() = context.contentResolver

    /** The user's choice; whether the drive is in is checked each time. */
    val wanted: StateFlow<Boolean> = wantsUsb.stateIn(scope, SharingStarted.Eagerly, false)

    /** The folder picked in the system window; null until one was. */
    val folder: StateFlow<Uri?> = folderUri.map { it?.let(Uri::parse) }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The folder as a document, while the drive is in and the permission holds; null otherwise. */
    fun folderDocument(): Uri? {
        val tree = folder.value ?: return null
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            queryDocuments(doc, arrayOf(Document.COLUMN_DOCUMENT_ID))?.use { if (it.moveToFirst()) doc else null }
        }.getOrNull()
    }

    val present: Boolean get() = folderDocument() != null

    /**
     * Where the system window opens: the root of a removable drive that is in, a USB one first (a head unit
     * may have a card too). Null on Android 9 and older, or with nothing plugged in: the window opens as usual.
     */
    fun pickerStart(): Uri? = removableDriveRoot(context)

    /** The folder's name, like "USB-накопитель › YandexMusicLite". */
    fun label(): String? {
        val doc = folderDocument() ?: return null
        val name = runCatching {
            queryDocuments(doc, arrayOf(Document.COLUMN_DISPLAY_NAME))?.use {
                if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) else null
            }
        }.getOrNull()
        // The drive's own name from the system (an app may not read the providers' list of drives)
        val drive = volume()?.getDescription(context) ?: rootRow(Root.COLUMN_TITLE) as? String
        return listOfNotNull(drive, name).distinct().joinToString(" › ").ifBlank { null }
    }

    fun freeBytes(): Long {
        val dir = volumeDirectory()
        if (dir != null) runCatching { return StatFs(dir.path).availableBytes }
        return (rootRow(Root.COLUMN_AVAILABLE_BYTES) as? Long) ?: 0L
    }

    /** The drive the folder is on: a tree id starts with the volume's id ("6266-080C:YandexMusicLite"). */
    private fun volume(): StorageVolume? {
        val tree = folder.value ?: return null
        val uuid = runCatching { DocumentsContract.getTreeDocumentId(tree).substringBefore(':') }.getOrNull() ?: return null
        return context.getSystemService(StorageManager::class.java)?.storageVolumes
            ?.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
    }

    private fun volumeDirectory(): File? {
        val vol = volume() ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory
        else vol.uuid?.let { File("/storage/$it") }
    }

    /** The files in the folder by name; unfinished ones ("*.part") are cleaned away. */
    fun files(): Map<String, Entry> {
        val tree = folder.value ?: return emptyMap()
        val parent = folderDocument() ?: return emptyMap()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val found = mutableMapOf<String, Entry>()
        runCatching {
            queryDocuments(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE))?.use { c ->
                // By name: a provider may give the columns in its own order
                val idColumn = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val sizeColumn = c.getColumnIndex(Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idColumn))
                    val name = c.getString(nameColumn) ?: continue
                    if (name.endsWith(PART)) {
                        // Left by an earlier run that was cut short; this run's own are being written
                        if (!name.endsWith(".$run$PART")) delete(uri)
                        continue
                    }
                    found[name] = Entry(uri, if (sizeColumn < 0 || c.isNull(sizeColumn)) 0L else c.getLong(sizeColumn))
                }
            }
        }.onFailure { Log.w(TAG, "Flash drive not listed", it) }
        return found
    }

    /**
     * Writes a new file [name] through [write]: first as "<name>.<run>.part", renamed once complete, so a drive
     * pulled out half-way never leaves a broken track behind. False when the drive refused.
     */
    fun put(name: String, write: (OutputStream) -> Unit): Boolean {
        val parent = folderDocument() ?: return false
        var part: Uri? = null
        return try {
            files()[name]?.let { delete(it.uri) }
            part = DocumentsContract.createDocument(resolver, parent, MIME, "$name.$run$PART") ?: return false
            requireNotNull(resolver.openOutputStream(part, "w")).use(write)
            DocumentsContract.renameDocument(resolver, part, name) ?: error("Not renamed")
            true
        } catch (e: CancellationException) {
            part?.let(::delete)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Not written to the flash drive: $name", e)
            part?.let(::delete)
            false
        }
    }

    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)

    /** Keeps the permission for a folder picked in the system window across restarts. */
    fun keep(tree: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        true
    }.onFailure { Log.w(TAG, "Folder permission not kept", it) }.getOrDefault(false)

    /** A column of the drive's root (its name, free space): the root whose id the folder's id starts with. */
    private fun rootRow(column: String): Any? {
        val tree = folder.value ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        return runCatching {
            queryDocuments(DocumentsContract.buildRootsUri(tree.authority), arrayOf(Root.COLUMN_DOCUMENT_ID, column))?.use { c ->
                val docColumn = c.getColumnIndexOrThrow(Root.COLUMN_DOCUMENT_ID)
                val wanted = c.getColumnIndex(column).takeIf { it >= 0 } ?: return@use null
                while (c.moveToNext()) {
                    val rootDoc = c.getString(docColumn) ?: continue
                    if (treeId.startsWith(rootDoc.trimEnd('/'))) {
                        return@use when (c.getType(wanted)) {
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(wanted)
                            Cursor.FIELD_TYPE_STRING -> c.getString(wanted)
                            else -> null
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * A documents provider's rows. From Android 8 in the Bundle form, as the providers expect it; Android 7.1 has
     * only the old form, which its providers take.
     */
    private fun queryDocuments(uri: Uri, projection: Array<String>): Cursor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) resolver.query(uri, projection, null as android.os.Bundle?, null)
        else resolver.query(uri, projection, null, null, null)

    companion object {
        private const val TAG = "UsbStorage"
        private const val MIME = "application/octet-stream"
        private const val PART = ".part"
    }
}

/**
 * Where the system's folder window opens: the root of a removable drive that is in, a USB one first (a head unit
 * may have a card too). Null on Android 9 and older, or with nothing plugged in: the window opens as usual.
 */
fun removableDriveRoot(context: Context): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val storage = context.getSystemService(StorageManager::class.java) ?: return null
    return runCatching {
        storage.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .sortedByDescending { it.getDescription(context).contains("USB", ignoreCase = true) }
            .firstNotNullOfOrNull {
                IntentCompat.getParcelableExtra(it.createOpenDocumentTreeIntent(), DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java)
            }
    }.getOrNull()
}

package com.korenskoy.yandexmusic.data.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * The user's music as Android's own catalogue (MediaStore) lists it: the phone, the SD card and the flash
 * drives the system has indexed. A query, not a walk through the storage: 10 000 tracks take a fraction of a
 * second.
 */
internal class LocalCatalogue(private val context: Context) {

    /** A track and its "<volume>:<relative path>" place, to tell it from the same file in an added folder. */
    data class Found(val track: LocalTrack, val place: String)

    private val storage = context.getSystemService(StorageManager::class.java)

    fun scan(): List<Found> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val columns = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.TRACK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.BITRATE)
            // A path: the folder on Android 8–9, the folder's picture where Android still lets read it
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_ALARM} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_PODCAST} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) append(" AND ${MediaStore.Audio.Media.IS_RECORDING} = 0")
        }
        val cursor = try {
            context.contentResolver.query(collection, columns, selection, null, null)
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to the music", e)
            null
        } ?: return emptyList()
        val volumes = VolumeKinds()
        val pictures = FolderPictures()
        return cursor.use { c -> buildList { while (c.moveToNext()) row(c, volumes, pictures)?.let(::add) } }
    }

    private fun row(c: Cursor, volumes: VolumeKinds, pictures: FolderPictures): Found? {
        val id = c.long(MediaStore.Audio.Media._ID) ?: return null
        val fileName = c.string(MediaStore.Audio.Media.DISPLAY_NAME).orEmpty()
        @Suppress("DEPRECATION")
        val path = c.string(MediaStore.Audio.Media.DATA)
        val volume = c.string("volume_name") ?: volumes.volumeOfPath(path)
        val relative = c.string("relative_path") ?: volumes.relativeOfPath(path)
        val duration = c.long(MediaStore.Audio.Media.DURATION)
        if (!LocalRules.isMusic(duration, relative)) return null
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volume != null) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }.toString()
        val folderName = relative.trimEnd('/').substringAfterLast('/').ifEmpty { null }
        val (title, artist) = LocalRules.nameOf(c.string(MediaStore.Audio.Media.TITLE), c.string(MediaStore.Audio.Media.ARTIST), fileName)
        val modified = c.long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0L
        val track = LocalTrack(
            id = "${LocalRules.ID_PREFIX}m$id",
            uri = uri,
            title = title,
            artist = artist,
            album = LocalRules.albumOf(c.string(MediaStore.Audio.Media.ALBUM), folderName),
            durationMs = duration ?: 0L,
            source = volumes.sourceOf(volume, path),
            folder = relative,
            addedAt = c.long(MediaStore.Audio.Media.DATE_ADDED) ?: modified,
            format = LocalRules.formatOf(c.string(MediaStore.Audio.Media.MIME_TYPE), fileName),
            bitrateKbps = c.long("bitrate")?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
            art = LocalArtProvider.uriFor(uri, modified, pictures.of(path)),
            // "1003": disc 1, track 3
            trackNumber = c.long(MediaStore.Audio.Media.TRACK)?.takeIf { it > 0 }?.let { (it % 1000).toInt() }
        )
        return Found(track, LocalRules.placeKey(volume ?: "primary", relative, fileName))
    }

    /** What kind each storage is: asked of the system once per scan. */
    private inner class VolumeKinds {
        private val volumes: List<StorageVolume> = runCatching { storage?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        private val roots: List<Pair<String, StorageVolume?>> = buildList {
            add(Environment.getExternalStorageDirectory().absolutePath to volumes.firstOrNull { it.isPrimary })
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val root = dir.absolutePath.substringBefore("/Android/data/")
                add(root to runCatching { storage?.getStorageVolume(dir) }.getOrNull())
            }
        }.distinctBy { it.first }
        private val kinds = mutableMapOf<String, LocalSource>()
        private val hasCardSlot = volumes.any { !it.isPrimary && LocalRules.sourceOf(false, it.getDescription(context)) == LocalSource.SD }

        fun sourceOf(volume: String?, path: String?): LocalSource {
            val key = volume ?: rootOf(path) ?: return LocalSource.PHONE
            return kinds.getOrPut(key) {
                val found = volumeNamed(volume) ?: path?.let { runCatching { storage?.getStorageVolume(File(it)) }.getOrNull() }
                when {
                    volume == MediaStore.VOLUME_EXTERNAL_PRIMARY -> LocalSource.PHONE
                    found == null -> if (path != null && path.startsWith(roots.first().first)) LocalSource.PHONE else LocalSource.FLASH
                    else -> LocalRules.sourceOf(found.isPrimary, found.getDescription(context), hasCardSlot)
                }
            }
        }

        private fun volumeNamed(name: String?): StorageVolume? {
            if (name == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            return volumes.firstOrNull { volume ->
                val itsName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.mediaStoreVolumeName
                else if (volume.isPrimary) MediaStore.VOLUME_EXTERNAL_PRIMARY else volume.uuid?.lowercase()
                itsName == name
            }
        }

        /** The volume of a file on Android 8–9: "primary", or the card's id ("1234-abcd"). */
        fun volumeOfPath(path: String?): String? {
            val root = rootOf(path) ?: return null
            return if (root == roots.first().first) "primary" else root.substringAfterLast('/').lowercase()
        }

        fun relativeOfPath(path: String?): String {
            if (path == null) return ""
            val root = rootOf(path) ?: return File(path).parent?.let { "$it/" }.orEmpty()
            return File(path).parent?.removePrefix(root)?.trimStart('/')?.let { if (it.isEmpty()) "" else "$it/" }.orEmpty()
        }

        private fun rootOf(path: String?): String? {
            if (path == null) return null
            roots.map { it.first }.filter { path.startsWith("$it/") }.maxByOrNull { it.length }?.let { return it }
            // A drive the app has no folder on yet: "/storage/<id>/…"
            val parts = path.split('/')
            return if (parts.size > 3 && parts[1] == "storage") "/storage/${parts[2]}" else null
        }
    }

    /**
     * A folder's own picture (cover.jpg and the like), for files without one inside. Only where Android still
     * lets the app read other files by their path: up to Android 9, and on 11–12 with the music permission.
     */
    private class FolderPictures {
        private val readable = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2
        private val byFolder = mutableMapOf<String, String?>()

        fun of(path: String?): String? {
            if (!readable || path == null) return null
            val folder = File(path).parentFile ?: return null
            return byFolder.getOrPut(folder.absolutePath) {
                val names = runCatching { folder.list()?.toList() }.getOrNull().orEmpty()
                LocalRules.folderPicture(names)?.let { Uri.fromFile(File(folder, it)).toString() }
            }
        }
    }

    private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun Cursor.long(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private companion object {
        const val TAG = "LocalCatalogue"
    }
}

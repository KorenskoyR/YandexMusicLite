package com.korenskoy.yandexmusic.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The music of a folder the user added: some head units never put their flash drive into Android's catalogue,
 * and the system's folder picker is the only way to reach it. The folder is walked through, and each file is
 * opened once to read its tags — slow, so what was read is kept on disk, and the next walk reads only new or
 * changed files.
 */
internal class LocalFolderScan(private val context: Context) {

    /** A file of the folder before its tags are read. */
    private data class FileEntry(val uri: Uri, val name: String, val mime: String?, val modified: Long, val size: Long, val folder: String, val picture: Uri?)

    @Serializable
    data class Tags(
        val modified: Long,
        val size: Long,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long? = null,
        val bitrate: Int? = null,
        val track: Int? = null,
        val mime: String? = null,
        /** The file would not open as a sound. */
        val unreadable: Boolean = false
    )

    @Serializable
    private data class TagCache(val files: Map<String, Tags> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(context.filesDir, "local_folder_tags.json")
    private var cache: MutableMap<String, Tags>? = null

    /**
     * The tracks of [tree], with their "<volume>:<relative path>" places. [onProgress] gets what is known so far
     * while the tags of new files are still being read.
     */
    suspend fun scan(tree: Uri, onProgress: (List<LocalCatalogue.Found>) -> Unit): List<LocalCatalogue.Found> {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return emptyList()
        val (volume, rootPath) = LocalRules.placeOfDocument(rootId)
        val source = sourceOfVolume(volume)
        val files = walk(tree, rootId, if (rootPath.isEmpty()) "" else "${rootPath.trimEnd('/')}/")
        val tags = loadCache()
        val unread = files.filter { tags[it.uri.toString()]?.let { t -> t.modified != it.modified || t.size != it.size } ?: true }
        fun found(): List<LocalCatalogue.Found> = files.mapNotNull { entry -> toFound(entry, tags[entry.uri.toString()], source, volume) }
        if (unread.isNotEmpty()) onProgress(found())
        unread.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            tags[entry.uri.toString()] = read(entry)
            if ((index + 1) % PROGRESS_EVERY == 0) {
                onProgress(found())
                saveCache()
            }
        }
        if (unread.isNotEmpty()) saveCache()
        return found()
    }

    /** The folder's tree, at most [MAX_DEPTH] deep and [MAX_FILES] files: a drive of backups must not stall the app. */
    private suspend fun walk(tree: Uri, rootId: String, rootPath: String): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        val queue = ArrayDeque(listOf(Triple(rootId, rootPath, 0)))
        while (queue.isNotEmpty() && result.size < MAX_FILES) {
            currentCoroutineContext().ensureActive()
            val (dirId, path, depth) = queue.removeFirst()
            // The folder is not ours any more, or its drive was taken out: what was found so far
            val rows = rowsOf(tree, dirId) ?: return result
            val picture = LocalRules.folderPicture(rows.map { it.name })?.let { name ->
                rows.firstOrNull { it.name == name }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
            }
            for (row in rows.filterNot { it.name.startsWith(".") }) {
                val isFolder = row.mime == Document.MIME_TYPE_DIR
                when {
                    isFolder && depth < MAX_DEPTH -> queue.addLast(Triple(row.id, "$path${row.name}/", depth + 1))
                    !isFolder && LocalRules.isAudioFile(row.mime, row.name) ->
                        result += FileEntry(DocumentsContract.buildDocumentUriUsingTree(tree, row.id), row.name, row.mime, row.modified, row.size, path, picture)
                }
            }
        }
        return result
    }

    private class Row(val id: String, val name: String, val mime: String?, val modified: Long, val size: Long)

    /** What lies in one folder of [tree]; null when it cannot be read. */
    private fun rowsOf(tree: Uri, dirId: String): List<Row>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    add(Row(id, c.getString(1).orEmpty(), c.getString(2), if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4)))
                }
            }
        }.orEmpty()
    }.onFailure { Log.w(TAG, "A folder cannot be read", it) }.getOrNull()

    private fun read(entry: FileEntry): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            runCatching { retriever.setDataSource(context, entry.uri) }.onFailure {
                // Not a sound the device can read (or the drive is gone): no tags; it is passed over
                Log.d(TAG, "Unreadable ${entry.name}: ${it.message}")
                return Tags(entry.modified, entry.size, unreadable = true)
            }
            Tags(
                modified = entry.modified,
                size = entry.size,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }?.takeIf { it > 0 },
                track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun toFound(entry: FileEntry, tags: Tags?, source: LocalSource, volume: String): LocalCatalogue.Found? {
        if (!LocalRules.isMusic(tags?.durationMs, entry.folder)) return null
        // A file that would not open is not a track
        if (tags?.unreadable == true) return null
        val uri = entry.uri.toString()
        val (title, artist) = LocalRules.nameOf(tags?.title, tags?.artist, entry.name)
        val folderName = entry.folder.trimEnd('/').substringAfterLast('/').ifEmpty { null }
        val track = LocalTrack(
            id = "${LocalRules.ID_PREFIX}d${hash(uri)}",
            uri = uri,
            title = title,
            artist = artist,
            album = LocalRules.albumOf(tags?.album, folderName),
            durationMs = tags?.durationMs ?: 0L,
            source = source,
            folder = entry.folder,
            addedAt = entry.modified / 1000,
            format = LocalRules.formatOf(tags?.mime ?: entry.mime, entry.name),
            bitrateKbps = tags?.bitrate,
            art = LocalArtProvider.uriFor(uri, entry.modified, entry.picture?.toString()),
            trackNumber = tags?.track
        )
        return LocalCatalogue.Found(track, LocalRules.placeKey(volume, entry.folder, entry.name))
    }

    private fun sourceOfVolume(volume: String): LocalSource {
        if (volume == "primary") return LocalSource.PHONE
        val storage = context.getSystemService(StorageManager::class.java)
        val volumes = runCatching { storage?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        val found = volumes.firstOrNull { it.uuid.equals(volume, ignoreCase = true) } ?: return LocalSource.FLASH
        val hasCardSlot = volumes.any { !it.isPrimary && LocalRules.sourceOf(false, it.getDescription(context)) == LocalSource.SD }
        return LocalRules.sourceOf(found.isPrimary, found.getDescription(context), hasCardSlot)
    }

    private fun loadCache(): MutableMap<String, Tags> = cache ?: try {
        if (cacheFile.exists()) json.decodeFromString(TagCache.serializer(), cacheFile.readText()).files.toMutableMap() else mutableMapOf()
    } catch (e: IOException) {
        Log.w(TAG, "The tags read before are lost", e)
        mutableMapOf()
    } catch (e: SerializationException) {
        Log.w(TAG, "The tags read before are lost", e)
        mutableMapOf()
    }.also { cache = it }

    private fun saveCache() {
        val tags = cache ?: return
        val part = File(cacheFile.parentFile, cacheFile.name + ".part")
        try {
            part.writeText(json.encodeToString(TagCache.serializer(), TagCache(tags.toMap())))
            part.renameTo(cacheFile)
        } catch (e: IOException) {
            Log.w(TAG, "Cannot keep the tags", e)
        }
    }

    /** Forgets what was read of a folder that is no longer added. */
    fun forget(tree: Uri) {
        val tags = loadCache()
        val prefix = tree.toString()
        if (tags.keys.removeAll { it.startsWith(prefix) }) saveCache()
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        const val TAG = "LocalFolderScan"
        const val MAX_DEPTH = 8
        const val MAX_FILES = 20_000
        const val PROGRESS_EVERY = 100
    }
}

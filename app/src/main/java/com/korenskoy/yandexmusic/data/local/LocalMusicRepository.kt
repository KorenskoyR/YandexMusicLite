package com.korenskoy.yandexmusic.data.local

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Музыка с устройства": the music already on the phone, the SD card and the flash drives, beside what the app
 * downloaded. Read from Android's catalogue, plus the folders the user added for head units whose flash drive the
 * catalogue does not list. The files are only read: never changed, moved or sent anywhere.
 *
 * The list follows the device by itself: a drive put in or taken out, a file added — it is read again.
 */
@Singleton
class LocalMusicRepository internal constructor(
    private val context: Context,
    private val catalogue: () -> List<LocalCatalogue.Found>,
    private val folderScan: LocalFolderScan?,
    private val access: () -> Boolean,
    private val observe: Boolean
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context, LocalCatalogue(context)::scan, LocalFolderScan(context), { hasPermission(context) }, observe = true
    )

    /** A folder the user added through the system's folder picker. */
    data class AddedFolder(val uri: String, val name: String)

    data class Library(
        val tracks: List<LocalTrack> = emptyList(),
        /** The app may read the music in Android's catalogue. */
        val hasAccess: Boolean = false,
        val folders: List<AddedFolder> = emptyList(),
        /** False until the first reading is done: the screen waits instead of saying "nothing". */
        val scanned: Boolean = false,
        /** The added folders are still being read. */
        val readingFolders: Boolean = false
    ) {
        val isEmpty: Boolean get() = tracks.isEmpty()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var byId: Map<String, LocalTrack> = emptyMap()

    init {
        scope.launch {
            while (true) {
                requests.receive()
                // A storage that misbehaves must not take the list (or the app) down: what is known stays
                runCatching { readAll() }.onFailure { e ->
                    Log.w(TAG, "Reading the music failed", e)
                    if (!_library.value.scanned) _library.value = _library.value.copy(scanned = true)
                }
                // Changes come in bursts (a drive mounting reports every file): one reading per burst
                delay(SETTLE_MS)
            }
        }
        refresh()
        if (observe) watchTheDevice()
    }

    /** Reads the music again (soon, and once for any number of calls). */
    fun refresh() {
        requests.trySend(Unit)
    }

    /** The access was granted or taken away in the system's settings: read again if it changed. */
    fun onAccessMaybeChanged() {
        if (access() != _library.value.hasAccess) refresh()
    }

    /** The first reading's result, for those who need the list now (Android Auto, the voice). */
    suspend fun current(): Library = _library.first { it.scanned }

    fun track(id: String): LocalTrack? = byId[id]

    private val _seen by lazy { MutableStateFlow(prefs.getBoolean(KEY_SEEN, false)) }
    /** The page was opened once: the folder's "new" mark goes. */
    val seen: StateFlow<Boolean> get() = _seen

    fun markSeen() {
        if (_seen.value) return
        _seen.value = true
        prefs.edit { putBoolean(KEY_SEEN, true) }
    }

    private suspend fun readAll() {
        val allowed = access()
        val folders = addedFolders()
        val listed = if (allowed) {
            try {
                catalogue()
            } catch (e: SecurityException) {
                Log.w(TAG, "The music cannot be read", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        val listedPlaces = listed.mapTo(mutableSetOf()) { it.place }
        val fromFolders = mutableMapOf<String, List<LocalCatalogue.Found>>()
        fun publish(reading: Boolean) {
            val extra = LocalRules.notListed(fromFolders.values.flatten().map { it.place to it.track }, listedPlaces)
            val tracks = (listed.map { it.track } + extra).distinctBy { it.id }
            byId = tracks.associateBy { it.id }
            _library.value = Library(LocalRules.sorted(tracks, LocalSort.RECENT), allowed, folders, scanned = true, readingFolders = reading)
        }
        publish(reading = folders.isNotEmpty())
        val scan = folderScan ?: return
        for (folder in folders) {
            val tree = folder.uri.toUri()
            fromFolders[folder.uri] = scan.scan(tree) { soFar ->
                fromFolders[folder.uri] = soFar
                publish(reading = true)
            }
        }
        if (folders.isNotEmpty()) publish(reading = false)
    }

    // region Added folders

    fun addedFolders(): List<AddedFolder> = prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty()
        .sorted()
        .map { AddedFolder(it, folderName(it.toUri())) }

    /** Keeps the access to a folder picked in the system's window, across restarts. @return false if refused. */
    fun addFolder(tree: Uri): Boolean {
        try {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "The folder's access cannot be kept", e)
            return false
        }
        prefs.edit { putStringSet(KEY_FOLDERS, prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty() + tree.toString()) }
        refresh()
        return true
    }

    /** Where "Добавить папку" opens the system's window: on the flash drive (or card) that is in. */
    fun pickerStart(): Uri? = com.korenskoy.yandexmusic.data.download.removableDriveRoot(context)

    fun removeFolder(uri: String) {
        prefs.edit { putStringSet(KEY_FOLDERS, prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty() - uri) }
        val tree = uri.toUri()
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            folderScan?.forget(tree)
            refresh()
        }
    }

    /** "Rock" for ".../tree/1234-5678%3AMusic%2FRock". */
    private fun folderName(tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.lastPathSegment.orEmpty()
        val path = LocalRules.placeOfDocument(id).second.trimEnd('/')
        return path.substringAfterLast('/').ifEmpty { context.getString(R.string.local_folder_root) }
    }

    // endregion

    /** Can the file still be opened (a flash drive may have been taken out)? */
    suspend fun isReachable(uri: String): Boolean = withContext(Dispatchers.IO) {
        // Gone, taken out, or no longer allowed: all the same here — it cannot play
        runCatching { context.contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    /** "MP3 · 320 кбит/с · с устройства" for the player's quality line. */
    fun qualityOf(id: String): String {
        val track = byId[id]
        val format = track?.format
        val kbps = track?.bitrateKbps
        return when {
            format != null && kbps != null -> context.getString(R.string.quality_local_known, format, kbps)
            format != null -> context.getString(R.string.quality_local_format, format)
            else -> context.getString(R.string.quality_local)
        }
    }

    /** The track as the player and the lists know tracks. */
    fun toDto(track: LocalTrack): TrackDetailsDto = TrackDetailsDto(
        id = JsonPrimitive(track.id),
        title = track.title,
        artists = listOf(ArtistDto(id = null, name = track.artist ?: context.getString(R.string.local_unknown_artist))),
        coverUri = track.art,
        durationMs = track.durationMs.takeIf { it > 0 },
        localUri = track.uri
    )

    fun toDtos(tracks: List<LocalTrack>): List<TrackDetailsDto> = tracks.map(::toDto)

    /**
     * A drive put in or taken out, a file added or removed: the catalogue tells, and so does the system's
     * broadcast about drives (an added folder on a flash drive is not in the catalogue).
     */
    private fun watchTheDevice() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        runCatching {
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
        val drives = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        // Protected broadcasts: only the system sends them
        ContextCompat.registerReceiver(context, drives, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        private const val TAG = "LocalMusicRepository"
        private const val PREFS = "local_music"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_SEEN = "seen"
        private const val SETTLE_MS = 1_500L

        /** The permission to read music files: its own on Android 13+, the storage one before. */
        val PERMISSION: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}

package com.korenskoy.yandexmusic.data.download

import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlists and albums on the device: the download button, following a playlist's new tracks, and
 * removing only what nothing else keeps. A track stays while it is liked or in any kept playlist or album.
 */
@Singleton
class OfflineDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collections: OfflineCollections,
    private val downloads: TrackDownloadManager,
    private val library: LibraryRepository
) {
    private val workManager get() = WorkManager.getInstance(context)

    val all: Flow<List<OfflineCollection>> = collections.all

    fun keptTrackIds(): Set<String> = collections.keptTrackIds()

    /** True when the file of [trackId] must stay although it is no longer liked. */
    fun keepsFile(trackId: String): Boolean = trackId in collections.keptTrackIds()

    /** Keeps [collection] on the device: saves its list and downloads what is missing. */
    suspend fun download(collection: OfflineCollection) {
        collections.save(collection)
        enqueue(collection.key)
    }

    /**
     * Stops a download or takes a kept playlist off the device. The playlist stays in the library and plays
     * from the network; its files go unless they are liked or in another kept playlist.
     */
    suspend fun remove(key: String) {
        workManager.cancelUniqueWork(workName(key))
        val removed = collections.remove(key) ?: return
        deleteUnkept(removed.trackIds)
    }

    /**
     * The playlist was loaded fresh: if it is kept, its list follows — new tracks are downloaded, removed ones
     * leave the device unless something else keeps them. Nothing happens for a playlist that is not kept.
     */
    suspend fun sync(key: String, title: String, coverUri: String?, fresh: List<TrackDetailsDto>) {
        val saved = collections.get(key) ?: return
        if (fresh.isEmpty() && saved.tracks.isNotEmpty()) return // a failed or empty answer never wipes the copy
        val updated = saved.copy(title = title.ifBlank { saved.title }, coverUri = coverUri ?: saved.coverUri, tracks = fresh)
        if (updated != saved) collections.save(updated)
        val gone = saved.trackIds - fresh.map { it.idString }.toSet()
        if (gone.isNotEmpty()) deleteUnkept(gone)
        if (OfflineRules.missing(updated, downloads.downloadedTrackIds.value).isNotEmpty()) enqueue(key)
    }

    /** Downloads what [key] still misses (a download cut short, tracks that could not be fetched before). */
    fun resume(key: String) {
        val kept = collections.get(key) ?: return
        if (OfflineRules.missing(kept, downloads.downloadedTrackIds.value).isNotEmpty()) enqueue(key)
    }

    /** "Очистить": every download is gone, so no playlist is kept any more. */
    suspend fun forgetAll() {
        collections.all.value.forEach { workManager.cancelUniqueWork(workName(it.key)) }
        collections.clear()
    }

    /** What the button of [key] shows: nothing, "3 из 34", or done with the size on the device. */
    fun state(key: String): Flow<OfflineState> = combine(
        collections.all.map { list -> list.firstOrNull { it.key == key } },
        downloads.downloadedTrackIds,
        workManager.getWorkInfosForUniqueWorkFlow(workName(key))
    ) { collection, downloaded, infos ->
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        val bytes = collection?.let { downloads.sizeOf(it.trackIds) } ?: 0L
        OfflineRules.stateOf(collection, running != null, running?.progress?.getString(CollectionDownloadWorker.KEY_CURRENT), downloaded, bytes)
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    private suspend fun deleteUnkept(candidates: Collection<String>) {
        val liked = library.likedTracks.value.mapTo(mutableSetOf()) { it.idString }
        val stillKept = OfflineRules.kept(collections.all.value, liked)
        OfflineRules.toDelete(candidates, stillKept).forEach { id ->
            runCatching { downloads.removeTrack(id) }.onFailure { Log.w(TAG, "Not removed: $id", it) }
        }
    }

    private fun enqueue(key: String) {
        val request = OneTimeWorkRequestBuilder<CollectionDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setInputData(workDataOf(CollectionDownloadWorker.KEY_COLLECTION to key))
            .build()
        // A running download takes the new tracks too: it reads the list again until nothing is missing
        workManager.enqueueUniqueWork(workName(key), ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val TAG = "OfflineDownloads"
        fun workName(key: String) = "download_collection_$key"
    }
}

/**
 * At start, with a connection: kept playlists are compared with the server, so tracks added on another
 * device are downloaded and removed ones leave; anything still missing (a download cut short) is resumed.
 */
@Singleton
class OfflineSync @Inject constructor(
    private val offline: OfflineDownloads,
    private val collections: OfflineCollections,
    private val playlists: UserPlaylistsRepository
) {
    suspend fun refreshAll() {
        for (kept in collections.all.value) {
            try {
                if (!kept.isAlbum) {
                    val playlist = playlists.playlists.value.firstOrNull { OfflineCollection.keyOf(it) == kept.key }
                    if (playlist != null) offline.sync(kept.key, playlist.title.orEmpty(), playlist.coverUri, playlists.tracks(playlist))
                }
                offline.resume(kept.key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("OfflineSync", "Kept ${kept.key} not refreshed", e)
            }
        }
    }
}

package com.korenskoy.yandexmusic.data.download

import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationChannelCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.abs

/**
 * Downloads a kept playlist or album. Runs in WorkManager: it survives closing the app, waits for a
 * connection and shows progress in a notification. It reads the list again after each pass, so tracks added
 * meanwhile are fetched too. Tracks come as the ordinary stream the service offers this app.
 */
class CollectionDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun offlineCollections(): OfflineCollections
        fun downloadManager(): TrackDownloadManager
        fun preloadManager(): TrackPreloadManager
    }

    private val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
    private val key: String get() = inputData.getString(KEY_COLLECTION).orEmpty()

    override suspend fun doWork(): Result {
        val collections = deps.offlineCollections()
        val downloads = deps.downloadManager()
        while (!isStopped) {
            val collection = collections.get(key) ?: return Result.success()
            val missing = OfflineRules.missing(collection, downloads.downloadedTrackIds.value)
            if (missing.isEmpty()) return Result.success()
            val total = collection.trackIds.distinct().size
            var saved = 0
            for (track in missing) {
                if (isStopped || collections.get(key) == null) return Result.success()
                val done = collection.trackIds.distinct().count { it in downloads.downloadedTrackIds.value }
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_CURRENT to track.idString))
                setForeground(foregroundInfo(collection.title, done, total))
                val stream = deps.preloadManager().resolveOrdinaryStream(track.idString)
                if (stream != null && downloads.downloadTrack(track.idString, stream.url, stream.quality)) saved++
            }
            // Nothing could be fetched in a whole pass (the network fell): WorkManager tries again later
            if (saved == 0) return Result.retry()
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(deps.offlineCollections().get(key)?.title.orEmpty(), 0, 0)

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val context = applicationContext
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.download_channel_name))
                .build()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.offline_notification_title, title))
            .setContentText(if (total > 0) context.getString(R.string.download_progress_count, done, total) else null)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            // Stopped from the playlist's own button: that also takes it off the device, which a notification can't ask
            .build()
        // One notification per playlist: two playlists downloading at once don't take each other's place
        val notificationId = NOTIFICATION_BASE + abs(key.hashCode() % 1000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_COLLECTION = "collection"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        private const val CHANNEL_ID = "yandex_music_lite_downloads"
        private const val NOTIFICATION_BASE = 5000
    }
}

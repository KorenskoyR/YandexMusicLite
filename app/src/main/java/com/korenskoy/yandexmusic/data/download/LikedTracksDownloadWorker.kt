package com.korenskoy.yandexmusic.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.network.DownloadProgressInfo
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.StreamVariants
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Downloads liked tracks for offline listening. Runs in WorkManager, so it survives closing the app and
 * a reboot, honours the "Wi-Fi only" constraint and shows progress in a notification.
 * With [KEY_TRACK_ID] it downloads that single track (auto-download after a like). With [KEY_UPGRADE] it
 * re-downloads already saved tracks the server now offers in a better quality.
 */
class LikedTracksDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun libraryRepository(): LibraryRepository
        fun downloadManager(): TrackDownloadManager
        fun preloadManager(): TrackPreloadManager
    }

    private val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)

    override suspend fun doWork(): Result {
        val downloads = deps.downloadManager()
        val library = deps.libraryRepository()
        library.loadCached()
        val single = inputData.getString(KEY_TRACK_ID)
        val upgrade = inputData.getBoolean(KEY_UPGRADE, false)
        val tracks = library.likedTracks.value
            .filter { single == null || it.idString == single }
            .filter { downloads.isTrackDownloaded(it.idString) == upgrade }
        if (tracks.isEmpty()) return Result.success()

        var failed = 0
        try {
            for ((index, track) in tracks.withIndex()) {
                if (isStopped) break
                val title = track.title.orEmpty()
                val artist = track.artists.firstOrNull()?.name.orEmpty()
                if (single == null) {
                    downloads.setDownloadProgress(DownloadProgressInfo(index, tracks.size, title, artist))
                    setForeground(foregroundInfo(index, tracks.size, title, upgrade))
                }
                val stream = deps.preloadManager().resolveForDownload(track.idString)
                val ok = when {
                    stream == null -> false
                    !upgrade -> downloads.downloadTrack(track.idString, stream.url, stream.quality)
                    else -> {
                        val saved = downloads.downloadedQuality(track.idString) ?: stream.bestMp3
                        saved != null && !StreamVariants.isBetter(stream.quality, saved) ||
                            downloads.downloadTrack(track.idString, stream.url, stream.quality, replace = true)
                    }
                }
                if (!ok) failed++
            }
        } finally {
            if (single == null) downloads.setDownloadProgress(null)
        }
        // Network dropped mid-way: WorkManager retries when the constraint is met again
        return if (failed > 0 && failed == tracks.size) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0, "", inputData.getBoolean(KEY_UPGRADE, false))

    private fun foregroundInfo(done: Int, total: Int, title: String, upgrade: Boolean): ForegroundInfo {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (upgrade) R.string.download_upgrade_title else R.string.download_notification_title))
            .setContentText(if (total > 0) context.getString(R.string.download_progress_count, done, total) + " · " + title else null)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, context.getString(R.string.download_cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
        const val KEY_UPGRADE = "upgrade_quality"
        private const val CHANNEL_ID = "yandex_music_lite_downloads"
        private const val NOTIFICATION_ID = 4201
    }
}

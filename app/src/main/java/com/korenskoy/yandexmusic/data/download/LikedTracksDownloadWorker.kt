package com.korenskoy.yandexmusic.data.download

import androidx.work.workDataOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationChannelCompat
import com.korenskoy.yandexmusic.data.player.SoundMode
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
 * a reboot, waits for a connection and shows progress in a notification.
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
        fun soundMode(): SoundMode
        fun autoDownloadBaseline(): AutoDownloadBaseline
    }

    private val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)

    override suspend fun doWork(): Result {
        val downloads = deps.downloadManager()
        val library = deps.libraryRepository()
        library.loadCached()
        // No likes kept on the device yet (some devices, the first run): asked from the server, or "Скачать всю
        // коллекцию" ended at once and nothing was downloaded
        if (library.likedTracks.value.isEmpty()) runCatching { library.syncLikes() }
        val single = inputData.getString(KEY_TRACK_ID)
        val upgrade = inputData.getBoolean(KEY_UPGRADE, false)
        val newLikesOnly = inputData.getBoolean(KEY_NEW_LIKES, false)
        val baseline = if (newLikesOnly) deps.autoDownloadBaseline().get() ?: return Result.success() else emptySet()
        val tracks = library.likedTracks.value
            .filter { single == null || it.idString == single }
            .filter { !newLikesOnly || it.idString !in baseline }
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
                        // A FLAC file is silent on a device in compatible sound, so the ordinary one is an upgrade
                        val silentFile = saved?.isLossless == true && deps.soundMode().isCompatible()
                        saved != null && !silentFile && !StreamVariants.isBetter(stream.quality, saved) ||
                            downloads.downloadTrack(track.idString, stream.url, stream.quality, replace = true)
                    }
                }
                if (!ok) failed++
            }
        } finally {
            if (single == null) downloads.setDownloadProgress(null)
        }
        // Retried only when the network is gone: a track that cannot be had (gone from the catalogue, not for this
        // region) failed every run, and with the rest done the job waited "for the network" forever on a good
        // connection (608 liked, 599 downloaded, the last 9 «Ожидание сети», 1.14.12)
        if (failed > 0 && DownloadOutcome.shouldRetry(failed, online = isOnline())) return Result.retry()
        return Result.success(workDataOf(KEY_DONE to tracks.size - failed, KEY_FAILED to failed))
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0, "", inputData.getBoolean(KEY_UPGRADE, false))

    private fun foregroundInfo(done: Int, total: Int, title: String, upgrade: Boolean): ForegroundInfo {
        val context = applicationContext
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.download_channel_name))
                .build()
        )
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
        /** Only the likes made after "Скачивать избранное" was switched on. */
        const val KEY_NEW_LIKES = "new_likes"

        /** In the finished job's output: how many were downloaded, how many could not be. */
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
        private const val CHANNEL_ID = "yandex_music_lite_downloads"
        private const val NOTIFICATION_ID = 4201
    }
}

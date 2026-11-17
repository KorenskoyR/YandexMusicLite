package com.korenskoy.yandexmusic.data.download

import kotlinx.coroutines.flow.combine
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** State of the "download all liked" job: idle, waiting for a connection, or running. */
enum class DownloadQueueState { IDLE, WAITING, RUNNING }

/**
 * Maps the work states to one queue state; extracted as a pure function so it is unit-testable. A job queued while
 * there is a connection is about to start, not waiting for the network: "Ожидание сети" flashed for a moment on
 * every press with a good Wi-Fi (1.14.10).
 */
fun queueStateOf(states: List<WorkInfo.State>, connected: Boolean = false): DownloadQueueState = when {
    states.any { it == WorkInfo.State.RUNNING } -> DownloadQueueState.RUNNING
    states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } ->
        if (connected) DownloadQueueState.RUNNING else DownloadQueueState.WAITING
    else -> DownloadQueueState.IDLE
}

/** Schedules offline downloads in WorkManager (they survive the app being closed). */
@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val network: NetworkMonitor
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** True while the "download all liked" job is queued or running. */
    val isDownloadingAll: Flow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(ALL_WORK)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    /**
     * Running vs merely waiting for a connection (or storage). Waiting stays visible and cancellable so a
     * job that cannot start yet (no network) can still be dismissed.
     */
    val downloadAllState: Flow<DownloadQueueState> = combine(
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ALL_WORK),
        network.isConnectedFlow
    ) { infos, connected -> queueStateOf(infos.map { it.state }, connected) }

    /** True while saved tracks are being re-downloaded in a better quality. */
    val isUpgradingQuality: Flow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(UPGRADE_WORK)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    suspend fun downloadAllLiked() {
        val request = OneTimeWorkRequestBuilder<LikedTracksDownloadWorker>()
            .setConstraints(constraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(ALL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** Re-downloads saved liked tracks the server now offers in a better quality. */
    /** "Скачивать избранное": the tracks liked after it was switched on, not yet on the device. */
    fun downloadNewLikes() {
        val request = OneTimeWorkRequestBuilder<LikedTracksDownloadWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(LikedTracksDownloadWorker.KEY_NEW_LIKES to true))
            .build()
        // A like made while it runs is taken by the next run
        workManager.enqueueUniqueWork(NEW_LIKES_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun upgradeDownloadedQuality() {
        val request = OneTimeWorkRequestBuilder<LikedTracksDownloadWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(LikedTracksDownloadWorker.KEY_UPGRADE to true))
            .build()
        workManager.enqueueUniqueWork(UPGRADE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    suspend fun downloadTrack(trackId: String) {
        val request = OneTimeWorkRequestBuilder<LikedTracksDownloadWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(LikedTracksDownloadWorker.KEY_TRACK_ID to trackId))
            .build()
        workManager.enqueueUniqueWork("$TRACK_WORK_PREFIX$trackId", ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(ALL_WORK)
        workManager.cancelUniqueWork(UPGRADE_WORK)
    }

    // Any connection is fine: the app does not police the user's traffic — they know their own data plan.
    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true)
        .build()

    private companion object {
        const val ALL_WORK = "download_liked_tracks"
        const val TRACK_WORK_PREFIX = "download_track_"
        const val UPGRADE_WORK = "upgrade_downloaded_quality"
        const val NEW_LIKES_WORK = "download_new_likes"
    }
}

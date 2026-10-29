package com.korenskoy.yandexmusic.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** State of the "download all liked" job: idle, waiting for its constraints (e.g. Wi-Fi), or running. */
enum class DownloadQueueState { IDLE, WAITING, RUNNING }

/** Maps the work states to one queue state; extracted as a pure function so it is unit-testable. */
fun queueStateOf(states: List<WorkInfo.State>): DownloadQueueState = when {
    states.any { it == WorkInfo.State.RUNNING } -> DownloadQueueState.RUNNING
    states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } -> DownloadQueueState.WAITING
    else -> DownloadQueueState.IDLE
}

/** Schedules offline downloads in WorkManager (survive app close, Wi-Fi-only option). */
@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** True while the "download all liked" job is queued or running. */
    val isDownloadingAll: Flow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(ALL_WORK)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    /**
     * Running vs merely waiting for the job's constraints (Wi-Fi / storage). Waiting must stay visible and
     * cancellable, otherwise a job that can never start (e.g. Wi-Fi-only on mobile data) looks like a hang.
     */
    val downloadAllState: Flow<DownloadQueueState> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(ALL_WORK)
        .map { infos -> queueStateOf(infos.map { it.state }) }

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

    private suspend fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (settingsRepository.downloadOverWifiOnly.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        )
        .setRequiresStorageNotLow(true)
        .build()

    private companion object {
        const val ALL_WORK = "download_liked_tracks"
        const val TRACK_WORK_PREFIX = "download_track_"
        const val UPGRADE_WORK = "upgrade_downloaded_quality"
    }
}

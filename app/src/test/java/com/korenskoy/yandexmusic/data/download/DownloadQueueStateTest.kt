package com.korenskoy.yandexmusic.data.download

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueStateTest {

    @Test
    fun runningTakesPriority() {
        assertEquals(
            DownloadQueueState.RUNNING,
            queueStateOf(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
        )
    }

    @Test
    fun enqueuedButNotRunningIsWaiting() {
        // The reported trap: Wi-Fi-only on mobile data keeps the job enqueued, never running.
        assertEquals(DownloadQueueState.WAITING, queueStateOf(listOf(WorkInfo.State.ENQUEUED)))
    }

    @Test
    fun blockedIsWaiting() {
        assertEquals(DownloadQueueState.WAITING, queueStateOf(listOf(WorkInfo.State.BLOCKED)))
    }

    @Test
    fun noWorkIsIdle() {
        assertEquals(DownloadQueueState.IDLE, queueStateOf(emptyList()))
    }

    @Test
    fun finishedWorkIsIdle() {
        assertEquals(
            DownloadQueueState.IDLE,
            queueStateOf(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED))
        )
    }

    @Test
    fun aQueuedJobWithAConnectionIsNotWaitingForTheNetwork() {
        // "Ожидание сети" flashed for a moment with a good Wi-Fi and nothing seemed to happen
        assertEquals(DownloadQueueState.RUNNING, queueStateOf(listOf(WorkInfo.State.ENQUEUED), connected = true))
        assertEquals(DownloadQueueState.WAITING, queueStateOf(listOf(WorkInfo.State.ENQUEUED), connected = false))
        assertEquals(DownloadQueueState.IDLE, queueStateOf(emptyList(), connected = true))
    }
}

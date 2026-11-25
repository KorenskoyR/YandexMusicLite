package com.korenskoy.yandexmusic.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.AppPresence
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The update looked for in the background twice a day, with a network: a head unit keeps the app running for weeks
 * and its one check at the start, without a network at that second, never offered the update (1.15 feedback). Found
 * while the app is not on the screen, a notification tells of it once per version; the app, opened, offers it.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun updateManager(): GitHubUpdateManager
        fun settings(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        if (!deps.settings().autoCheckUpdates.first()) return Result.success()
        val result = deps.updateManager().checkForUpdates()
        UpdateSchedule.remember(applicationContext, ok = result !is UpdateCheckResult.Error)
        val available = result as? UpdateCheckResult.UpdateAvailable ?: return Result.success()
        // On the screen the app offers it itself; a skipped version and one told of already are not told again
        if (AppPresence.visibleActivities > 0) return Result.success()
        if (available.versionName == deps.settings().skippedUpdateVersion.first()) return Result.success()
        val prefs = applicationContext.getSharedPreferences(UpdateSchedule.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(NOTIFIED_VERSION, null) == available.versionName) return Result.success()
        if (notify(available.versionName)) prefs.edit { putString(NOTIFIED_VERSION, available.versionName) }
        return Result.success()
    }

    private fun notify(version: String): Boolean {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(applicationContext.getString(R.string.update_channel))
                .build()
        )
        val open = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(applicationContext, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.update_notification_title, version))
            .setContentText(applicationContext.getString(R.string.update_notification_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission for the update notification", e)
            false
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "update_check"
        private const val CHANNEL_ID = "yandex_music_lite_update"
        private const val NOTIFICATION_ID = 7303
        private const val NOTIFIED_VERSION = "notified_version"
        private const val EVERY_HOURS = 12L

        /** Twice a day with a network; kept as it is when already planned. */
        fun plan(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }.onFailure { Log.w(TAG, "The update check was not planned", it) }
        }

        /** Stops periodic background update checks when disabled in settings. */
        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }.onFailure { Log.w(TAG, "The update check was not cancelled", it) }
        }
    }
}

/** When the app looks for the update again while it is open. Pure where it can be; unit-tested. */
object UpdateSchedule {
    const val PREFS = "update_check"
    private const val LAST_AT = "last_at"
    private const val LAST_OK = "last_ok"

    /** After a good check, again in this long (the app open for days); after a failed one sooner. */
    const val AFTER_OK_MS = 6 * 60 * 60_000L
    const val AFTER_FAILED_MS = 15 * 60_000L

    fun due(now: Long, lastAt: Long, lastOk: Boolean): Boolean =
        lastAt <= 0L || now < lastAt || now - lastAt >= if (lastOk) AFTER_OK_MS else AFTER_FAILED_MS

    fun remember(context: Context, ok: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(LAST_AT, System.currentTimeMillis())
            putBoolean(LAST_OK, ok)
        }
    }

    fun dueNow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return due(System.currentTimeMillis(), prefs.getLong(LAST_AT, 0L), prefs.getBoolean(LAST_OK, false))
    }

    fun lastFailed(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(LAST_OK, true)
}

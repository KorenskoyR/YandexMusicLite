package com.korenskoy.yandexmusic.data.start

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «Запускать вместе с устройством»: the head unit (or the phone) has started, the app opens by itself. What then
 * plays is «Музыка при запуске», as with any launch. From Android 10 an app opens itself from the background only
 * with «Поверх других приложений» allowed; without it a notification opens it with a tap.
 */
class DeviceStartReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun settings(): SettingsRepository
        fun journal(): com.korenskoy.yandexmusic.data.player.PlaybackJournal
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!WakeRules.isStart(intent.action)) return
        val pending = goAsync()
        handle(context.applicationContext, intent.action.orEmpty()) { pending.finish() }
    }

    companion object {
        private const val TAG = "DeviceStartReceiver"
        private const val CHANNEL_ID = "yandex_music_lite_start"
        private const val NOTIFICATION_ID = 7302
        private const val READ_TIMEOUT_MS = 5_000L

        /** How long after asking to open the app its window must be up, or the notification shows instead. */
        private const val OPEN_CHECK_MS = 3_000L

        @Volatile
        private var lastOpenedAt = 0L

        /** A start or a waking heard by the app's living process ([DeviceWake]). */
        fun onStart(context: Context, signal: String) = handle(context.applicationContext, signal) {}

        private fun handle(app: Context, signal: String, done: () -> Unit) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val deps = EntryPointAccessors.fromApplication(app, Dependencies::class.java)
                    val on = withTimeoutOrNull(READ_TIMEOUT_MS) { deps.settings().autoStartWithDevice.first() } == true
                    val journal = deps.journal()
                    when {
                        !on -> Unit
                        // Already on the screen (a waking with the app in front): nothing to open
                        com.korenskoy.yandexmusic.data.player.AppPresence.visibleActivities > 0 ->
                            journal.record(com.korenskoy.yandexmusic.data.player.PlaybackJournal.Kind.AUTOSTART, detail = "$signal · ${app.getString(R.string.journal_autostart_shown)}")
                        !WakeRules.opens(android.os.SystemClock.elapsedRealtime(), lastOpenedAt) -> Unit
                        else -> {
                            lastOpenedAt = android.os.SystemClock.elapsedRealtime()
                            val result = open(app)
                            journal.record(com.korenskoy.yandexmusic.data.player.PlaybackJournal.Kind.AUTOSTART, detail = "$signal · ${app.getString(result)}")
                        }
                    }
                } finally {
                    done()
                }
            }
        }

        /** Opens the app, or tells with a notification; the words of what happened, for the journal. */
        private suspend fun open(context: Context): Int {
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (mayOpenItself(context)) {
                VisiblePixel.around(context) {
                    try {
                        context.startActivity(open)
                    } catch (e: android.content.ActivityNotFoundException) {
                        Log.w(TAG, "Could not open the app at start", e)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Not let to open the app at start", e)
                    }
                }
                // The system may refuse with no error at all: only the window on the screen tells
                kotlinx.coroutines.delay(OPEN_CHECK_MS)
                if (com.korenskoy.yandexmusic.data.player.AppPresence.visibleActivities > 0) return R.string.journal_autostart_opened
                notifyToOpen(context, open)
                return R.string.journal_autostart_refused
            }
            notifyToOpen(context, open)
            return R.string.journal_autostart_no_overlay
        }

        private fun notifyToOpen(context: Context, open: Intent) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.autostart_channel))
                    .build()
            )
            val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.autostart_title))
                .setContentText(context.getString(R.string.autostart_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "No permission for the start notification", e)
            }
        }

        /** Up to Android 9 always; from 10 only with «Поверх других приложений». */
        fun mayOpenItself(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
    }
}

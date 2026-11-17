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
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in STARTS) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = EntryPointAccessors.fromApplication(app, Dependencies::class.java).settings()
                val on = withTimeoutOrNull(READ_TIMEOUT_MS) { settings.autoStartWithDevice.first() } == true
                if (on) open(app)
            } finally {
                pending.finish()
            }
        }
    }

    private fun open(context: Context) {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (mayOpenItself(context)) {
            try {
                context.startActivity(open)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Could not open the app at start", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Not let to open the app at start", e)
            }
        }
        notifyToOpen(context, open)
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

    companion object {
        private const val TAG = "DeviceStartReceiver"
        private const val CHANNEL_ID = "yandex_music_lite_start"
        private const val NOTIFICATION_ID = 7302
        private const val READ_TIMEOUT_MS = 5_000L

        /** A full start, and the quick ones of some devices (HTC, many head units). */
        private val STARTS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        /** Up to Android 9 always; from 10 only with «Поверх других приложений». */
        fun mayOpenItself(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
    }
}

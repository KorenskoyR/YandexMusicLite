package com.korenskoy.yandexmusic.data.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R

/**
 * The app has just been updated. Installed through a session, Android closes it and shows no «Открыть», as its
 * installer window used to: up to Android 9 the app opens again by itself; from 10 on the system does not let an app
 * open itself from the background, so a notification «Обновлено до …» opens it with a tap.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                context.startActivity(open)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Could not open the updated app", e)
            }
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_done_channel))
                .build()
        )
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_done_title, BuildConfig.VERSION_NAME))
            .setContentText(context.getString(R.string.update_done_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission for the update notification", e)
        }
    }

    private companion object {
        const val TAG = "UpdatedReceiver"
        const val CHANNEL_ID = "yandex_music_lite_updates"
        const val NOTIFICATION_ID = 7301
    }
}

package com.korenskoy.yandexmusic.data.player

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Whether the phone lets the app play on in the background, and the system screen to let it. Phones of some makers
 * close a music app with the screen off to save battery; the journal tells when that happened.
 */
object BackgroundWork {

    /** The phone may close the app in the background: battery optimization is on for it, or its background work is restricted. */
    fun restricted(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val optimized = power?.isIgnoringBatteryOptimizations(context.packageName) == false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val limited = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am?.isBackgroundRestricted == true
        return optimized || limited
    }

    /**
     * The system's own question "let the app work in the background?", or the app's page of the settings when its
     * background work is restricted there. The app plays music: being closed in the background is the very fault.
     */
    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context): Intent {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val limited = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am?.isBackgroundRestricted == true
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val optimized = power?.isIgnoringBatteryOptimizations(context.packageName) == false
        val action = if (optimized && !limited) Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        return Intent(action, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The app's page of the settings: where to go when the question is not offered on this phone. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

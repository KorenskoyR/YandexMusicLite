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

    /**
     * The maker's own switch that closes apps in the background whatever Android allows: Xiaomi's «Автозапуск»,
     * Huawei's and Honor's app launch, Samsung's battery list, OPPO's, realme's, OnePlus's and vivo's start-up
     * managers. A Xiaomi with the background allowed still closed the app while it played (HyperOS, 1.14.12).
     * Tried in order until one opens; empty on other phones. Known screens, as gathered by dontkillmyapp.com.
     */
    fun makerScreens(manufacturer: String = Build.MANUFACTURER): List<Intent> =
        MAKER_SCREENS.filter { (maker, _) -> manufacturer.lowercase() in maker }.map { (_, component) ->
            Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Opens the first maker screen this phone has; false when none opens. */
    fun openMakerScreen(context: Context): Boolean = makerScreens().any { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }

    private val MAKER_SCREENS: List<Pair<Set<String>, android.content.ComponentName>> = listOf(
        setOf("xiaomi", "redmi", "poco") to android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        setOf("huawei") to android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        setOf("huawei") to android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        setOf("honor") to android.content.ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        setOf("honor") to android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        setOf("samsung") to android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        setOf("samsung") to android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        setOf("oppo", "realme", "oneplus") to android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        setOf("oppo", "realme", "oneplus") to android.content.ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        setOf("vivo", "iqoo") to android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        setOf("vivo", "iqoo") to android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
    )

    /** The app's page of the settings: where to go when the question is not offered on this phone. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

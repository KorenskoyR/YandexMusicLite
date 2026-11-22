package com.korenskoy.yandexmusic.data.start

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Запуск с устройством» while the app's process lives on: head units do not boot when the ignition turns on, they
 * wake from a sleep, and no "boot completed" comes then (the start did not work every time, 1.15 feedback). The
 * vendors' own ignition signals reach a listener registered here from Android 8 on, where the system no longer hands
 * them to a closed app; and on a unit with no battery (a head unit, never a phone) the screen coming on after a long
 * sleep is the waking too.
 */
@Singleton
class DeviceWake @Inject constructor(@param:ApplicationContext private val context: Context) {
    private var started = false
    private var screenOffAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOffAt = SystemClock.elapsedRealtime()
                Intent.ACTION_SCREEN_ON -> {
                    val slept = screenOffAt > 0 && SystemClock.elapsedRealtime() - screenOffAt >= WakeRules.LONG_SLEEP_MS
                    if (slept && !batteryPresent(context)) DeviceStartReceiver.onStart(context, "SCREEN_ON")
                }
                else -> DeviceStartReceiver.onStart(context, intent.action.orEmpty())
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val system = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val vendors = IntentFilter().apply { WakeRules.VENDOR_WAKES.forEach(::addAction) }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, system, ContextCompat.RECEIVER_NOT_EXPORTED)
            // Sent by the units' own system apps: from outside the app
            ContextCompat.registerReceiver(context, receiver, vendors, ContextCompat.RECEIVER_EXPORTED)
        }.onFailure { Log.w(TAG, "No listening for the waking", it) }
    }

    private fun batteryPresent(context: Context): Boolean = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
    }.getOrNull() ?: true

    companion object {
        private const val TAG = "DeviceWake"
    }
}

/** The signals of a start and of a waking, and how often they open the app. Pure; unit-tested. */
object WakeRules {
    /** A full start, the quick ones (HTC, MediaTek's quick boot and IPO), the vendors' ignition. */
    val STARTS = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        "android.intent.action.ACTION_BOOT_IPO"
    )

    /** The ignition turned on: FYT (Teyes, Joying and others), GLSX, Microntek (two spellings). */
    val VENDOR_WAKES = setOf(
        "com.fyt.boot.ACCON",
        "com.glsx.boot.ACCON",
        "com.cayboy.action.ACC_ON",
        "com.carboy.action.ACC_ON"
    )

    /** The screen dark at least this long before it came on: a sleep of the unit, not a glance. */
    const val LONG_SLEEP_MS = 2 * 60_000L

    /** One start opens the app once: the boot and the ignition of the same start do not open it twice. */
    const val ONCE_PER_MS = 60_000L

    fun isStart(action: String?): Boolean = action != null && (action in STARTS || action in VENDOR_WAKES || action == "SCREEN_ON")

    /** Whether a start at [now] opens the app, the last having opened it at [lastAt]. */
    fun opens(now: Long, lastAt: Long): Boolean = lastAt == 0L || now - lastAt >= ONCE_PER_MS
}

/**
 * From Android 15 «Поверх других приложений» lets an app open itself only while one of its windows is on the screen:
 * a window of a pixel, invisible and untouchable, for the moment of opening. Without it the opening was refused with
 * no error at all, and the start showed nothing.
 */
internal object VisiblePixel {
    @SuppressLint("InlinedApi")
    fun around(context: Context, open: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || !Settings.canDrawOverlays(context)) {
            open()
            return
        }
        val main = Handler(Looper.getMainLooper())
        main.post {
            val manager = context.getSystemService(WindowManager::class.java)
            val pixel = View(context)
            val added = runCatching {
                manager.addView(
                    pixel,
                    WindowManager.LayoutParams(
                        1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    )
                )
            }.isSuccess
            open()
            if (added) main.postDelayed({ runCatching { manager.removeView(pixel) } }, PIXEL_MS)
        }
    }

    private const val PIXEL_MS = 3_000L
}

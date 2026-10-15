package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WakeLock and WifiLock during track transitions to ensure uninterrupted background
 * playback when the screen is locked, in Doze mode, or under OS battery optimization.
 */
@Singleton
class PlaybackWakeLockHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val wakeLock: PowerManager.WakeLock? = try {
        powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YandexMusicLite:PlaybackWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create WakeLock", e)
        null
    }

    @Suppress("DEPRECATION")
    private val wifiLock: WifiManager.WifiLock? = try {
        wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "YandexMusicLite:PlaybackWifiLock"
        )?.apply {
            setReferenceCounted(false)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create WifiLock", e)
        null
    }

    /**
     * Acquire locks during track switching or buffering so CPU and Wi-Fi chip don't sleep.
     * Automatically times out after [timeoutMs] to prevent accidental battery drain.
     */
    fun acquireTransitionLock(timeoutMs: Long = 30_000L) {
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(timeoutMs)
                }
            }
            wifiLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring transition lock", e)
        }
    }

    /**
     * Release transition locks once playback has successfully started or failed.
     */
    fun releaseTransitionLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing transition lock", e)
        }
    }

    companion object {
        private const val TAG = "PlaybackWakeLockHelper"
    }
}

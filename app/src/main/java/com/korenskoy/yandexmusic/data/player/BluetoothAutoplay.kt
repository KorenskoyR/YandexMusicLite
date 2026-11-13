package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app may start sound right now: Android 12+ forbids starting the player's service from the background. */
object AppPresence {
    @Volatile
    var visibleActivities: Int = 0

    @Volatile
    var playerServiceRunning: Boolean = false

    val canStartPlayback: Boolean
        get() = visibleActivities > 0 || playerServiceRunning
}

/**
 * "Bluetooth of the car": the music starts when a Bluetooth audio output appears (the phone connected to
 * the car). Outputs already connected when the app starts do not count, nor does anything while music plays.
 *
 * Whatever the setting, headphones or a Bluetooth output back within seconds of going away (a car's Bluetooth
 * that drops for a moment) bring back the music their going away paused.
 */
@Singleton
class BluetoothAutoplay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    private val playback: PlaybackController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var baseline: Set<Int>? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val outputs = addedDevices.filter { it.isSink && it.type in PERSONAL_OUTPUTS }
            // The first call lists what is already connected; only what appears later is a new connection
            val known = baseline
            if (known == null) {
                baseline = outputs.map { it.id }.toSet()
                return
            }
            baseline = known + outputs.map { it.id }
            val fresh = outputs.filter { it.id !in known }
            if (fresh.isNotEmpty()) onConnected(bluetooth = fresh.any { it.type in BLUETOOTH_OUTPUTS })
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            baseline = baseline?.minus(removedDevices.map { it.id }.toSet())
        }
    }

    fun start() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())) }
            .onFailure { Log.w(TAG, "Audio devices are not observable here", it) }
    }

    private fun onConnected(bluetooth: Boolean) {
        scope.launch {
            if (auth.getToken().isNullOrEmpty() || playback.isPlaying.value) return@launch
            // Back within seconds of going away: the music it paused plays on
            if (AppPresence.canStartPlayback && playback.resumeAfterOutputBlip()) {
                Log.i(TAG, "The output came back at once: the music plays on")
                return@launch
            }
            if (!bluetooth || !settings.bluetoothAutoplay.first()) return@launch
            if (!AppPresence.canStartPlayback) {
                Log.i(TAG, "Car connected while the app is in the background: Android does not let it start the music")
                return@launch
            }
            playback.awaitRestoredSession()
            playback.play()
        }
    }

    internal companion object {
        const val TAG = "BluetoothAutoplay"
        val BLUETOOTH_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            26, // TYPE_BLE_HEADSET (Android 12)
            27 // TYPE_BLE_SPEAKER (Android 12)
        )

        /** Headphones of any kind and Bluetooth: what going away pauses the music. */
        val PERSONAL_OUTPUTS = BLUETOOTH_OUTPUTS + setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            22 // TYPE_USB_HEADSET (Android 8)
        )
    }
}

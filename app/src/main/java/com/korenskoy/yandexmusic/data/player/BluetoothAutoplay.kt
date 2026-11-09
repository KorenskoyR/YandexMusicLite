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
            val bluetooth = addedDevices.filter { it.isSink && it.type in BLUETOOTH_OUTPUTS }.map { it.id }.toSet()
            // The first call lists what is already connected; only what appears later is a new connection
            val known = baseline
            if (known == null) {
                baseline = bluetooth
                return
            }
            baseline = known + bluetooth
            if ((bluetooth - known).isNotEmpty()) onConnected()
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

    private fun onConnected() {
        scope.launch {
            if (!settings.bluetoothAutoplay.first()) return@launch
            if (auth.getToken().isNullOrEmpty() || playback.isPlaying.value) return@launch
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
    }
}

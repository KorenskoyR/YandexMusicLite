package com.korenskoy.yandexmusic.data.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * When a player last handed sound on towards the output. The app's equalizer sees every buffer on its way, so it
 * marks it; the sink hands a buffer over only when the output has taken the one before.
 */
class AudioFlow {
    @Volatile
    var lastFedAtMs: Long = 0L
        private set

    fun fed(nowMs: Long) {
        lastFedAtMs = nowMs
    }
}

/**
 * Watches the playing track for what the journal could not see (1.14.8 report: with Bluetooth headphones the music
 * fell silent in every track, the timer counted on and jumped back 3–5 s, the journal was empty):
 * - the output stopped taking sound while the track plays ([Finding.OutputStall]);
 * - the position went back without a seek ([Finding.PositionBack]).
 * Fed every [TICK_MS] on the player's thread. Pure; unit-tested.
 */
internal class OutputWatch {
    sealed interface Finding {
        /** The output took nothing for [durationMs] while the track played. */
        data class OutputStall(val startedAtMs: Long, val durationMs: Long) : Finding

        /** The position went back by [byMs] with no seek. */
        data class PositionBack(val byMs: Long) : Finding
    }

    private var stallFrom: Long? = null
    private var lastPosition: Long? = null

    /** A seek, a new track, another player: the position may jump and the output starts afresh. */
    fun reset() {
        stallFrom = null
        lastPosition = null
    }

    fun tick(nowMs: Long, playing: Boolean, lastFedAtMs: Long, positionMs: Long): Finding? {
        if (!playing || lastFedAtMs == 0L) {
            reset()
            return null
        }
        var finding: Finding? = null
        val from = stallFrom
        if (nowMs - lastFedAtMs >= STALL_MS) {
            if (from == null) stallFrom = lastFedAtMs
        } else if (from != null) {
            stallFrom = null
            val duration = lastFedAtMs - from
            if (duration >= STALL_MS) finding = Finding.OutputStall(from, duration)
        }
        val before = lastPosition
        lastPosition = positionMs
        if (finding == null && before != null && positionMs < before - BACK_MS) finding = Finding.PositionBack(before - positionMs)
        return finding
    }

    companion object {
        const val TICK_MS = 250L
        /** Nothing taken for this long while playing is the output standing still, not the sink's rhythm. */
        const val STALL_MS = 1_000L
        /** Smaller jumps back are the clock settling, not a jump. */
        const val BACK_MS = 1_500L
    }
}

/**
 * Where the sound goes now: for the journal and the reports. The device types are plain numbers: a type newer than the
 * device (USB headset before Android 8) never comes, comparing with it is harmless.
 */
@SuppressLint("InlinedApi")
object AudioOutputs {
    private val EXTERNAL = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_HDMI
    ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) else emptySet()

    /** An output a person plugs in or pairs (not the phone's own speaker). */
    fun isExternal(device: AudioDeviceInfo): Boolean = device.isSink && device.type in EXTERNAL

    /** "Bluetooth «WH-1000XM4»", "проводные наушники", "динамик": the most likely route of the music now. */
    fun current(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "?"
        val outputs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        val external = outputs.filter(::isExternal)
        return (external.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: external.firstOrNull())?.let(::describe)
            ?: "встроенный динамик"
    }

    fun describe(device: AudioDeviceInfo): String {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (гарнитура, звонок)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "проводные наушники"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> "линейный выход"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "встроенный динамик"
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET || device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
            ) "Bluetooth LE" else "тип ${device.type}"
        }
        return if (name != null && device.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "$kind «$name»" else kind
    }

    /** "48000 Гц, буфер 240 кадров": the device's own output, for the reports. */
    fun format(context: Context): String? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: return null
        val frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return if (frames != null) "$rate Гц, буфер $frames кадров" else "$rate Гц"
    }
}

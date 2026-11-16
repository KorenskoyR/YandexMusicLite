package com.korenskoy.yandexmusic.data.player.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The app's own equalizer inside the player's audio path: every track, station and file of both players (the
 * crossfade too) goes through it, on any device and Android version — no system effect is needed (a Teyes CC3 has
 * none). [settings] is read at each buffer: a change is heard within a few milliseconds.
 *
 * Takes 16-bit and float PCM (what the sink hands to added processors); anything else passes untouched.
 */
@UnstableApi
class EqualizerProcessor(
    private val settings: () -> EqSettings,
    /** Called with every buffer on its way to the output: the journal sees an output that stopped taking sound. */
    private val onFed: (() -> Unit)? = null
) : BaseAudioProcessor() {
    private var runner: EqRunner? = null
    private var shown: EqSettings? = null
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (supported && inputAudioFormat.channelCount > 0) inputAudioFormat else AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        onFed?.invoke()
        val format = inputAudioFormat
        val fresh = runner == null
        val run = runner ?: EqRunner(format.channelCount).also { runner = it }
        val now = settings()
        if (now !== shown) {
            shown = now
            run.use(if (now.isFlat) null else EqChain.of(now, format.sampleRate), now.enabled && !now.isFlat)
            // A new start (a track, a seek): the setting as it is, no fade in
            if (fresh) run.reset()
        }
        val output = replaceOutputBuffer(remaining)
        if (run.idle) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val count = remaining / if (float) 4 else 2
        if (samples.size < count) samples = FloatArray(count)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (float) {
            for (i in 0 until count) samples[i] = input.getFloat()
        } else {
            for (i in 0 until count) samples[i] = input.getShort() / SHORT_SCALE
        }
        run.process(samples, count / format.channelCount, format.sampleRate)
        output.order(ByteOrder.nativeOrder())
        if (float) {
            for (i in 0 until count) output.putFloat(samples[i])
        } else {
            for (i in 0 until count) output.putShort((samples[i] * SHORT_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        output.flip()
    }

    // A seek or a new format: the filters start clean (and with the channels of the new format)
    override fun onFlush() {
        runner = null
        shown = null
    }

    override fun onReset() {
        runner = null
        shown = null
        samples = FloatArray(0)
    }

    private companion object {
        const val SHORT_SCALE = 32768f
    }
}

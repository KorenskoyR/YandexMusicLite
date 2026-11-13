package com.korenskoy.yandexmusic.presentation.voice

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log

/** What the app's own listening window shows. */
sealed interface Listening {
    /** Listening; [partial] is what is heard so far, [level] how loud (0..1). */
    data class Hearing(val partial: String = "", val level: Float = 0f) : Listening

    /** Heard [text]: it goes to the search. */
    data class Heard(val text: String) : Listening

    /** Nothing said, or nothing understood. */
    data object Missed : Listening

    /** The microphone is not allowed. */
    data object NoMic : Listening

    /** The recognition did not work: [why]. */
    data class Failed(val why: Why) : Listening

    enum class Why { NETWORK, LANGUAGE, OTHER }

    companion object {
        /** A [SpeechRecognizer] error, as the window shows it. Pure. */
        fun ofError(error: Int): Listening = when (error) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> Missed
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> NoMic
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
            ERROR_SERVER_DISCONNECTED -> Failed(Why.NETWORK)
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> Failed(Why.LANGUAGE)
            else -> Failed(Why.OTHER)
        }

        /** How loud, from the service's level in dB (about −2 in silence to 10 aloud), 0..1. Pure. */
        fun levelOf(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)

        // SpeechRecognizer's errors of Android 12 and later, written out
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

/**
 * One listening at a time through the device's speech recognition service. Used on the main thread, as the
 * service asks; [stop] lets the microphone go.
 */
internal class RecognizerSession(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun start(onState: (Listening) -> Unit) {
        stop()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .onFailure { Log.w(TAG, "No speech recognizer", it) }
            .getOrNull()
        if (r == null) {
            onState(Listening.Failed(Listening.Why.OTHER))
            return
        }
        recognizer = r
        var partial = ""
        var level = 0f
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onState(Listening.Hearing(partial, level))
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                level = Listening.levelOf(rmsdB)
                onState(Listening.Hearing(partial, level))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                Log.i(TAG, "Recognition error $error")
                release()
                onState(Listening.ofError(error))
            }
            override fun onResults(results: Bundle?) {
                val text = firstOf(results)
                release()
                onState(if (text.isNullOrBlank()) Listening.Missed else Listening.Heard(text))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                firstOf(partialResults)?.takeIf { it.isNotBlank() }?.let { partial = it }
                onState(Listening.Hearing(partial, level))
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        runCatching { r.startListening(VoiceInput.listeningIntent(context)) }.onFailure {
            Log.w(TAG, "Listening did not start", it)
            release()
            onState(Listening.Failed(Listening.Why.OTHER))
        }
    }

    /** Stops listening and lets the microphone go; nothing more is told. */
    fun stop() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            r.setRecognitionListener(null)
        }
        release()
    }

    private fun release() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private fun firstOf(bundle: Bundle?): String? = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val TAG = "VoiceListening"
    }
}

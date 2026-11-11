package com.korenskoy.yandexmusic.presentation.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.korenskoy.yandexmusic.R

/**
 * The system's voice input: its own window listens and hands back the text, so the app needs no microphone
 * permission. Many head units have no voice input at all; then there is no microphone button.
 */
object VoiceInput {
    fun intent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // The app speaks Russian: a head unit set to English still hears a Russian phrase
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }.getOrDefault(false)

    private const val LANGUAGE = "ru-RU"
}

/**
 * Starts the system's voice input; what was said goes to [onText]. Null when the device has none, so the
 * caller shows no microphone.
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val available = remember(context) { VoiceInput.isAvailable(context) }
    val latest = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { latest.value(it) }
    }
    val prompt = stringResource(R.string.voice_prompt)
    if (!available) return null
    return remember(launcher, prompt) {
        {
            try {
                launcher.launch(VoiceInput.intent(prompt))
            } catch (e: ActivityNotFoundException) {
                Log.w("VoiceInput", "No voice input after all", e)
            }
        }
    }
}

package com.korenskoy.yandexmusic.presentation.voice

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.BackgroundWork
import kotlinx.coroutines.delay

/** A speech recognition service of the device: [id] is its component, [label] what the owner sees. */
data class Recognizer(val id: String, val label: String) {
    fun component(): ComponentName? = ComponentName.unflattenFromString(id)
}

/** How this device can hear what to play. */
enum class VoiceRoute {
    /** The system's voice input window (the "Google" app's): it listens and hands back the text, no permission. */
    SYSTEM_WINDOW,

    /** No such window, but a speech recognition service: the app's own window listens, with the microphone. */
    RECOGNIZER,

    /** Neither: no microphone button; the settings tell what to install. */
    NONE
}

/**
 * Voice input. The system's window first: it hears best and needs no permission. Many head units have none
 * (no "Google" app); some of them still have a speech recognition service, and then the app listens itself.
 */
object VoiceInput {
    fun intent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // The app speaks Russian: a head unit set to English still hears a Russian phrase
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** What the recognition service is asked for when the app listens itself: Russian, words as they come. */
    fun listeningIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

    /** The system's voice input window is there. */
    fun hasSystemWindow(context: Context): Boolean = runCatching {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }.getOrDefault(false)

    fun route(context: Context): VoiceRoute = routeOf(
        window = hasSystemWindow(context),
        recognizer = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    )

    /** The window first, the service second. Pure. */
    fun routeOf(window: Boolean, recognizer: Boolean): VoiceRoute = when {
        window -> VoiceRoute.SYSTEM_WINDOW
        recognizer -> VoiceRoute.RECOGNIZER
        else -> VoiceRoute.NONE
    }

    /** Every speech recognition service installed. */
    fun recognizers(context: Context): List<Recognizer> = runCatching {
        val pm = context.packageManager
        pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).mapNotNull { info ->
            val s = info.serviceInfo ?: return@mapNotNull null
            Recognizer(ComponentName(s.packageName, s.name).flattenToString(), runCatching { pm.getApplicationLabel(s.applicationInfo).toString() }.getOrDefault(s.packageName))
        }
    }.getOrDefault(emptyList())

    /** The saved pick if still installed, else the first, else none. Pure. */
    fun chosen(all: List<Recognizer>, saved: String?): Recognizer? = all.firstOrNull { it.id == saved } ?: all.firstOrNull()

    /** The one after [current], wrapping round. Pure. */
    fun next(all: List<Recognizer>, current: Recognizer?): Recognizer? {
        if (all.isEmpty()) return null
        val i = all.indexOfFirst { it.id == current?.id }
        return all[(i + 1) % all.size]
    }

    private fun prefs(context: Context) = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    fun savedRecognizer(context: Context): String? = prefs(context).getString("recognizer", null)
    fun saveRecognizer(context: Context, id: String) = prefs(context).edit().putString("recognizer", id).apply()

    fun micGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether Play is on the device: the "Google" app is only there. A "market://" link without it opened RuStore,
     * which has no such app and said "нет ссылки" (1.15 feedback).
     */
    fun hasPlayStore(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PLAY_STORE, 0).enabled
    }.getOrDefault(false)

    /** The "Google" app's page in Play itself, never in another store that takes "market://" links. */
    fun installGoogle(context: Context) {
        val inPlay = Intent(Intent.ACTION_VIEW, "market://details?id=$GOOGLE_APP".toUri()).setPackage(PLAY_STORE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(inPlay)
        } catch (e: ActivityNotFoundException) {
            Log.i("VoiceInput", "No Play to open", e)
        }
    }

    /** The app's page of the settings, where the microphone is allowed once it was refused for good. */
    fun openAppSettings(context: Context) {
        runCatching { context.startActivity(BackgroundWork.appDetailsIntent(context)) }
            .onFailure { Log.w("VoiceInput", "No app settings screen", it) }
    }

    private const val LANGUAGE = "ru-RU"
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private const val PLAY_STORE = "com.android.vending"
}

/** How this device can hear, asked again each time the app comes back (the "Google" app installed meanwhile). */
@Composable
fun rememberVoiceRoute(): VoiceRoute {
    val context = LocalContext.current
    var route by remember { mutableStateOf(VoiceInput.route(context)) }
    LifecycleResumeEffect(Unit) {
        route = VoiceInput.route(context)
        onPauseOrDispose { }
    }
    return route
}

/**
 * Starts listening; what was said goes to [onText]. Through the system's window where there is one; otherwise
 * the app's own window listens through the speech recognition service, asking for the microphone the first
 * time. Null when the device can do neither, so the caller shows no microphone.
 */
@Composable
fun rememberVoiceInput(closeWhenUnheard: Boolean = false, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val route = rememberVoiceRoute()
    val latest = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { latest.value(it) }
    }
    val prompt = stringResource(R.string.voice_prompt)

    // The app's own window: null while closed
    var listening by remember { mutableStateOf<Listening?>(null) }
    val session = remember { RecognizerSession(context) }
    DisposableEffect(session) { onDispose { session.stop() } }
    fun listen() {
        listening = Listening.Hearing()
        session.start { state ->
            listening = state
            if (state is Listening.Heard) latest.value(state.text)
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else listening = Listening.NoMic
    }
    fun listenOrAsk() {
        if (VoiceInput.micGranted(context)) listen() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    listening?.let { state ->
        VoiceListeningWindow(
            state = state,
            onRetry = ::listenOrAsk,
            onOpenSettings = {
                listening = null
                VoiceInput.openAppSettings(context)
            },
            onClose = {
                session.stop()
                listening = null
            }
        )
    }
    // What was heard is shown for a moment, then the window goes and the music answers
    LaunchedEffect(listening) {
        if (listening is Listening.Heard) {
            delay(HEARD_SHOWN_MS)
            listening = null
        }
        // From the steering wheel: nothing heard — said so for a moment, and the window goes by itself
        if (closeWhenUnheard && (listening == Listening.Missed || listening is Listening.Failed)) {
            delay(UNHEARD_SHOWN_MS)
            session.stop()
            listening = null
        }
    }

    return when (route) {
        VoiceRoute.NONE -> null
        VoiceRoute.SYSTEM_WINDOW -> remember(launcher, prompt) {
            {
                try {
                    launcher.launch(VoiceInput.intent(prompt))
                } catch (e: ActivityNotFoundException) {
                    Log.w("VoiceInput", "No voice input after all", e)
                }
            }
        }
        VoiceRoute.RECOGNIZER -> ::listenOrAsk
    }
}

private const val HEARD_SHOWN_MS = 900L
private const val UNHEARD_SHOWN_MS = 2_000L

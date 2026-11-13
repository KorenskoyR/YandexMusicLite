package com.korenskoy.yandexmusic

import android.os.ParcelFileDescriptor
import android.os.Build
import android.Manifest
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.presentation.voice.Listening
import com.korenskoy.yandexmusic.presentation.voice.RecognizerSession
import com.korenskoy.yandexmusic.presentation.voice.VoiceInput
import com.korenskoy.yandexmusic.presentation.voice.VoiceRoute
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.14.7 on a real Android: the speech recognition service is seen by the app (Android 11 hides it without a
 * line in the manifest), and the app's own listening reaches it, hears back and lets the microphone go.
 * A voice cannot be spoken to an emulator: what is checked is that the service answers, not what it hears.
 */
@RunWith(AndroidJUnit4::class)
class VoiceListeningInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theRecognitionServiceIsSeenByTheApp() {
        val recognizer = SpeechRecognizer.isRecognitionAvailable(context)
        Log.i(TAG, "window ${VoiceInput.hasSystemWindow(context)}, recognizer $recognizer, route ${VoiceInput.route(context)}")
        assumeTrue("No recognition service on this device", hasService())
        assertTrue("the service is installed, the app must see it", recognizer)
        assertNotEquals(VoiceRoute.NONE, VoiceInput.route(context))
    }

    @Test
    fun theAppsOwnListeningReachesTheServiceAndLetsGo() {
        assumeTrue("No recognition service on this device", hasService())
        // The shell's way, there on Android 7.1 too (UiAutomation.grantRuntimePermission came with Android 9)
        shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
        val states = CopyOnWriteArrayList<Listening>()
        val session = onMain { RecognizerSession(context) }
        instrumentation.runOnMainSync { session.start { states += it } }
        waitUntil(15_000) { states.isNotEmpty() }
        assertTrue("the service answered: $states", states.isNotEmpty())
        // Without a voice the service ends by itself (no speech, no match, no audio); or it is stopped here
        waitUntil(30_000) { states.any { it !is Listening.Hearing } }
        Log.i(TAG, "states: ${states.map { it::class.simpleName }.distinct()} last ${states.last()}")
        instrumentation.runOnMainSync { session.stop() }
        val told = states.size
        Thread.sleep(2_000)
        assertTrue("nothing more after stop: ${states.drop(told)}", states.size == told)
        assertTrue("no permission trouble with the microphone allowed: $states", Listening.NoMic !in states)
    }

    /**
     * A recognition service is installed, whatever the app can see: from Android 11 asked of the system's shell,
     * which sees every package (the app itself sees it only through its manifest's line, which is what is tested).
     */
    private fun hasService(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return SpeechRecognizer.isRecognitionAvailable(context)
        return "name=" in shell("cmd package query-services -a android.speech.RecognitionService")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - start < timeoutMs) Thread.sleep(50)
    }

    private companion object {
        const val TAG = "VoiceListening"
    }
}

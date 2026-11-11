package com.korenskoy.yandexmusic

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.VoicePhrase
import com.korenskoy.yandexmusic.presentation.voice.VoiceInput
import com.korenskoy.yandexmusic.presentation.voice.rememberVoiceInput
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The microphone on a device that has voice input: the system's window is asked for Russian, and what it
 * hands back reaches the app. The window itself is stood in for — a real voice cannot be spoken to an emulator.
 */
@RunWith(AndroidJUnit4::class)
class VoiceInputInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whatWasSaidReachesTheApp() {
        assumeTrue("No voice input on this device", VoiceInput.isAvailable(context))
        var asked: Intent? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked = input as Intent
                dispatchResult(
                    requestCode,
                    Activity.RESULT_OK,
                    Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("Включи Земфира Хочешь"))
                )
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        var heard: String? = null
        var listen: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                listen = rememberVoiceInput { heard = it }
            }
        }
        compose.waitForIdle()
        val start = listen
        assertEquals("the device has voice input, so there is a microphone", true, start != null)
        compose.runOnUiThread { start!!() }
        compose.waitForIdle()

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, asked?.action)
        assertEquals("asked for Russian whatever the device's language", "ru-RU", asked?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("Включи Земфира Хочешь", heard)
        assertEquals("the search gets it without the asking", "Земфира Хочешь", VoicePhrase.query(heard!!))
    }

    @Test
    fun nothingSaidDoesNothing() {
        assumeTrue("No voice input on this device", VoiceInput.isAvailable(context))
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        var heard: String? = null
        var listen: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                listen = rememberVoiceInput { heard = it }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { listen!!() }
        compose.waitForIdle()
        assertEquals("closed without a word: nothing plays", null, heard)
    }
}

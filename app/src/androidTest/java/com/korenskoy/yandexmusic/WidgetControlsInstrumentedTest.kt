package com.korenskoy.yandexmusic

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.MusicService
import com.korenskoy.yandexmusic.presentation.radio.RadioViewModel
import com.korenskoy.yandexmusic.widget.mediaButtonIntent
import com.korenskoy.yandexmusic.widget.skipIntent
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The widget's buttons send the same broadcasts as this test; they must control playback with the
 * app's screen closed, as on the home screen.
 */
@RunWith(AndroidJUnit4::class)
class WidgetControlsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(100)
        }
    }

    @Test
    fun widgetButtonsPauseResumeAndSkip() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var radio: RadioViewModel
        scenario.onActivity { radio = ViewModelProvider(it)[RadioViewModel::class.java] }
        val stations = radio.stations.value
        val start = stations.first { it.id == "record" }
        instrumentation.runOnMainSync { radio.play(start) }

        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        fun playing(): Boolean {
            var value = false
            instrumentation.runOnMainSync { value = controller.isPlaying }
            return value
        }
        fun mediaId(): String? {
            var value: String? = null
            instrumentation.runOnMainSync { value = controller.currentMediaItem?.mediaId }
            return value
        }
        try {
            waitUntil(30_000, "station starts") { playing() }
            scenario.close() // the widget is used from the home screen

            context.sendBroadcast(mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            waitUntil(10_000, "widget pauses") { !playing() }

            context.sendBroadcast(mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            waitUntil(30_000, "widget resumes") { playing() }

            context.sendBroadcast(skipIntent(context, next = true))
            waitUntil(30_000, "widget skips to the next station") { mediaId() != "radio_${start.id}" && playing() }
        } finally {
            instrumentation.runOnMainSync {
                controller.pause()
                MediaController.releaseFuture(future)
            }
        }
    }
}

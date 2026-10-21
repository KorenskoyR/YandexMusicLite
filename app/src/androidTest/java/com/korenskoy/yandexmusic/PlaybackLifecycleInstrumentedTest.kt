package com.korenskoy.yandexmusic

import android.Manifest
import android.content.ComponentName
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.MusicService
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import com.korenskoy.yandexmusic.presentation.radio.RadioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End-to-end regression for "after swiping the app away, notification buttons do nothing and the
 * reopened app looks freshly started". Uses radio, so no Yandex account is required.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun waitUntil(timeoutMs: Long, message: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Timeout: $message")
            Thread.sleep(50)
        }
    }

    private fun viewModelOf(scenario: ActivityScenario<MainActivity>): MainViewModel {
        lateinit var vm: MainViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        return vm
    }

    private fun radioOf(scenario: ActivityScenario<MainActivity>): RadioViewModel {
        lateinit var vm: RadioViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[RadioViewModel::class.java] }
        return vm
    }

    @Test
    fun notificationControlsWorkAfterActivityIsDestroyed() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }

        // 1. Start a station from the UI layer
        val first = ActivityScenario.launch(MainActivity::class.java)
        val vm = viewModelOf(first)
        val radio = radioOf(first)
        val stations = radio.stations.value
        val startIndex = stations.indexOfFirst { it.id == "record" }
        val start = stations[startIndex]
        val next = stations[(startIndex + 1) % stations.size]
        instrumentation.runOnMainSync { radio.play(start) }
        waitUntil(30_000, "station starts") { vm.isPlaying.value && !vm.isBuffering.value }

        // 2. Destroy the Activity and its ViewModel, as swiping the task from recents does
        first.close()
        Thread.sleep(1_000)

        // 3. Press "next" the way the notification / a headset does: through the media session
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { controller.seekToNext() }
            waitUntil(30_000, "next station is loaded and playing") {
                var ok = false
                instrumentation.runOnMainSync {
                    ok = controller.currentMediaItem?.mediaId == "radio_${next.id}" && controller.isPlaying
                }
                ok
            }

            // 4. Reopen the app: the UI must show what is actually playing
            val second = ActivityScenario.launch(MainActivity::class.java)
            val reopenedVm = viewModelOf(second)
            assertEquals("radio_${next.id}", reopenedVm.currentTrack.value?.idString)
            assertTrue("UI must show playing state", reopenedVm.isPlaying.value)
            second.close()
        } finally {
            instrumentation.runOnMainSync {
                controller.pause()
                MediaController.releaseFuture(future)
            }
        }
    }

    /**
     * Second half of a manual two-process check: run after [notificationControlsWorkAfterActivityIsDestroyed]
     * and `am force-stop`. A fresh process must show the last station, paused, without loading anything.
     */
    @Test
    fun sessionIsRestoredPausedAfterProcessDeath() {
        org.junit.Assume.assumeTrue(
            "needs a session saved by a previous process",
            java.io.File(context.filesDir, "playback_state.json").exists()
        )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val vm = viewModelOf(scenario)
        waitUntil(5_000, "session restored") { vm.currentTrack.value != null }
        assertTrue("restored track is radio", vm.currentTrack.value!!.idString.startsWith("radio_"))
        assertTrue("restored session starts paused", !vm.isPlaying.value)
        scenario.close()
    }
}

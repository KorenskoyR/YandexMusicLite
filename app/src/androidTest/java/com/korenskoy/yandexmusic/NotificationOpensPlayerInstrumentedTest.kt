package com.korenskoy.yandexmusic

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.korenskoy.yandexmusic.presentation.auth.AuthViewModel
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tapping the playback notification must open the full-screen player (like Apple Music).
 * Uses a fake token to reach the main screen and radio (no Yandex account needed); logs out at the end.
 */
@RunWith(AndroidJUnit4::class)
class NotificationOpensPlayerInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    /** Exactly one (fake) account: with several, the startup account picker would cover the player. */
    @org.junit.Before
    fun clearAccounts() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
    }

    /** Polls the hierarchy without UiAutomator's wait-for-idle, which never ends while player animations run. */
    private fun hasDesc(desc: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.desc(desc)) != null) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun hasText(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.text(text)) != null) return true
            Thread.sleep(200)
        }
        return false
    }

    @Test
    fun notificationIntentOpensFullScreenPlayer() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var mainVm: MainViewModel? = null
        try {
            scenario.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
            scenario.recreate() // authorized + saved state: straight to the main screen
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            mainVm = vm
            // Radio list snapshot for visual review of the bundled station logos
            if (hasText("FM Радио", 15_000)) device.findObject(By.text("FM Радио")).click()
            Thread.sleep(3_000)
            device.takeScreenshot(java.io.File(context.filesDir, "radio_list.png"))
            val station = vm.radioStations.value.first { it.id == "record" }
            instrumentation.runOnMainSync { vm.playRadioStation(station) }
            hasDesc("Play/Pause", 10_000) // mini player is up
            assertFalse("full player must be closed before the tap", hasDesc("Collapse", 1_000))

            // Same intent MusicService puts into the notification (singleTop -> onNewIntent)
            scenario.onActivity {
                it.startActivity(
                    Intent(it, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                )
            }
            assertTrue("notification tap opens the full-screen player", hasDesc("Collapse", 10_000))
            Thread.sleep(1_500) // let the cover settle
            // Kept for visual review of the opened player (adb shell run-as <pkg> cat files/notification_player.png)
            device.takeScreenshot(java.io.File(context.filesDir, "notification_player.png"))

        } finally {
            // The open full-screen player animates endlessly, so the main thread never goes idle and
            // ActivityScenario calls would wait forever: stop playback and collapse it first
            mainVm?.let { vm -> instrumentation.runOnMainSync { vm.stopAndClearPlayer(); vm.logoutAllAccounts() } }
            device.pressBack()
            scenario.close()
        }
    }
}

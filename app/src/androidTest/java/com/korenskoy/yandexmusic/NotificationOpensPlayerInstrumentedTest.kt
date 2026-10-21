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
import com.korenskoy.yandexmusic.presentation.account.AccountViewModel
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import com.korenskoy.yandexmusic.presentation.radio.RadioViewModel
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

    /** Screenshot for visual review; /data/local/tmp survives the uninstall that wipes the app's files. */
    private fun snap(name: String) {
        device.executeShellCommand("screencap -p /data/local/tmp/$name.png")
    }

    @Test
    fun notificationIntentOpensFullScreenPlayer() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // Save the token, then start a fresh activity: recreate() would restore the auth screen when the
        // short splash has already navigated there, which made this test flaky
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var mainVm: MainViewModel? = null
        var accountVm: AccountViewModel? = null
        try {
            lateinit var vm: MainViewModel
            lateinit var radio: RadioViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[MainViewModel::class.java]
                radio = ViewModelProvider(it)[RadioViewModel::class.java]
                accountVm = ViewModelProvider(it)[AccountViewModel::class.java]
            }
            mainVm = vm
            // Radio list snapshot for visual review of the bundled station logos
            if (hasText("FM Радио", 15_000)) device.findObject(By.text("FM Радио")).click()
            Thread.sleep(3_000)
            snap("radio_list")
            device.findObject(By.text("Dance & Club"))?.click()
            Thread.sleep(2_000)
            snap("radio_dance")
            device.findObject(By.text("Все"))?.click()
            device.findObject(By.clazz("android.widget.EditText"))?.text = "России"
            Thread.sleep(2_000)
            snap("radio_search")
            device.findObject(By.clazz("android.widget.EditText"))?.text = ""
            val station = radio.stations.value.first { it.id == "record" }
            instrumentation.runOnMainSync { radio.play(station) }
            hasDesc("Воспроизведение или пауза", 10_000) // mini player is up
            assertFalse("full player must be closed before the tap", hasDesc("Свернуть плеер", 1_000))

            // Same intent MusicService puts into the notification (singleTop -> onNewIntent)
            scenario.onActivity {
                it.startActivity(
                    Intent(it, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                )
            }
            assertTrue("notification tap opens the full-screen player", hasDesc("Свернуть плеер", 10_000))
            // The sleep timer button sits in the player header
            assertTrue("sleep timer button is shown", hasDesc("Таймер сна", 2_000))
            Thread.sleep(1_500) // let the cover settle
            // Kept for visual review of the opened player
            snap("notification_player")

            device.findObject(By.desc("Таймер сна")).click()
            assertTrue("sleep timer menu opens", hasText("Через 15 мин", 3_000))
            device.findObject(By.text("Через 15 мин")).click()
            assertTrue("remaining time is shown", hasText("Сон через 15 мин", 3_000))
            snap("sleep_timer")

        } finally {
            // The open full-screen player animates endlessly, so the main thread never goes idle and
            // ActivityScenario calls would wait forever: stop playback and collapse it first
            instrumentation.runOnMainSync {
                mainVm?.stopAndClearPlayer()
                accountVm?.logoutAllAccounts()
            }
            device.pressBack()
            scenario.close()
        }
    }
}

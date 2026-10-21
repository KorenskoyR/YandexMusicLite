package com.korenskoy.yandexmusic

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.korenskoy.yandexmusic.presentation.account.AccountViewModel
import com.korenskoy.yandexmusic.presentation.auth.AuthViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The car tab is a full screen of its own: the app's bottom bar must disappear there and come back
 * when the user leaves it. Note: wipes the app's auth storage — run on a test device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class CarModeInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    @After
    fun wipe() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun hasDesc(desc: String) = device.findObject(By.desc(desc)) != null

    @Test
    fun bottomBarIsHiddenInCarModeAndBackAfterExit() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var accounts: AccountViewModel? = null
        try {
            scenario.onActivity { accounts = ViewModelProvider(it)[AccountViewModel::class.java] }
            assertTrue("bottom bar", waitFor(15_000) { hasDesc("В авто") && hasDesc("Моя Музыка") })
            device.findObject(By.desc("В авто")).click()

            assertTrue("car screen", waitFor(10_000) { device.findObject(By.text("Моя Волна")) != null })
            assertTrue("bottom bar hidden in car mode", waitFor(3_000) { !hasDesc("Моя Музыка") && !hasDesc("Главная") })
            device.executeShellCommand("screencap -p /data/local/tmp/car_mode.png")

            device.pressBack()
            assertTrue("bottom bar back after leaving car mode", waitFor(5_000) { hasDesc("Моя Музыка") })
        } finally {
            accounts?.let { vm -> instrumentation.runOnMainSync { vm.logoutAllAccounts() } }
            scenario.close()
        }
    }
}

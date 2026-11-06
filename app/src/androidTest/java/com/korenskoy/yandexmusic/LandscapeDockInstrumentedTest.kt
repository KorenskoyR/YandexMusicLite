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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * On a side the whole app keeps its tabs in one row along the bottom, above the system bars, and they
 * still switch pages. Note: wipes the app's auth storage.
 */
@RunWith(AndroidJUnit4::class)
class LandscapeDockInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    @After
    fun wipe() {
        device.setOrientationNatural()
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

    private fun bounds(desc: String) = device.findObject(By.desc(desc)).visibleBounds

    @Test
    fun theTabsLieInOneRowAlongTheBottomAndWork() {
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
            assertTrue("tabs", waitFor(15_000) { device.findObject(By.desc("Волна")) != null })
            // A head unit is on its side from the start
            if (device.displayWidth < device.displayHeight) device.setOrientationLeft()
            assertTrue("on its side", waitFor(10_000) { device.displayWidth > device.displayHeight && device.findObject(By.desc("В авто")) != null })
            Thread.sleep(800)
            device.executeShellCommand("screencap -p /data/local/tmp/dock_wave.png")

            val tabs = listOf("Волна", "Поиск", "Музыка", "Радио", "В авто").map(::bounds)
            val row = tabs.first().centerY()
            tabs.forEach { assertTrue("all tabs in one row: $tabs", abs(it.centerY() - row) <= 2) }
            assertEquals("from left to right", tabs.sortedBy { it.left }, tabs)
            assertTrue("along the bottom: $row of ${device.displayHeight}", row > device.displayHeight * 0.7)
            assertTrue("on the left", tabs.last().right < device.displayWidth)

            device.findObject(By.desc("Музыка")).click()
            assertTrue("the tab opens", waitFor(5_000) { device.findObject(By.text("Моя музыка")) != null })
            Thread.sleep(800)
            device.executeShellCommand("screencap -p /data/local/tmp/dock_library.png")
        } finally {
            accounts?.let { vm -> instrumentation.runOnMainSync { vm.logoutAllAccounts() } }
            scenario.close()
        }
    }
}

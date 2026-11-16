package com.korenskoy.yandexmusic

import android.Manifest
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.WindowInsets
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
 * 1.14.9 on a real Android with real system bars: on any side (three-button navigation lying down puts its bar on the
 * right or the left, a camera cutout sits on a side), nothing to press and no title of the app lies under a bar —
 * the main screen, «В авто» with its source dock, and the settings opened from it. The Geely Monjaro had its car
 * panel on the left over the settings' title, and a status bar above «В авто» squeezed the source dock.
 * Screenshots go to /data/local/tmp/bars_*.png for a look.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // A bar of its own on a side when lying down: three buttons, not the gesture line
        if (Build.VERSION.SDK_INT >= 29) shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton")
    }

    @After
    fun tearDown() {
        if (seeded) return
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    /** Where the system draws over the app now: its bars on every side and a cutout, as rectangles of the screen. */
    private fun barsOf(activity: Activity): List<Rect> {
        val decor = activity.window.decorView
        val insets = decor.rootWindowInsets ?: return emptyList()
        val (left, top, right, bottom) = if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            listOf(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            listOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        val location = IntArray(2).also { decor.getLocationOnScreen(it) }
        val w = decor.width
        val h = decor.height
        val x = location[0]
        val y = location[1]
        return listOfNotNull(
            Rect(x, y, x + left, y + h).takeIf { left > 0 },
            Rect(x, y, x + w, y + top).takeIf { top > 0 },
            Rect(x + w - right, y, x + w, y + h).takeIf { right > 0 },
            Rect(x, y + h - bottom, x + w, y + h).takeIf { bottom > 0 }
        )
    }

    /** What can be pressed or read on the screen and lies under a bar: none, or the test names it. */
    private fun underBars(bars: List<Rect>, where: String): List<String> {
        val screen = Rect(0, 0, bars.maxOfOrNull { it.right } ?: device.displayWidth, bars.maxOfOrNull { it.bottom } ?: device.displayHeight)
        val nodes = device.findObjects(By.clickable(true)) + device.findObjects(By.textStartsWith(""))
        return nodes.mapNotNull { node ->
            val b = runCatching { node.visibleBounds }.getOrNull() ?: return@mapNotNull null
            // A dimming layer or a background from edge to edge is meant to reach under the bars (the page behind a
            // sheet shows above it as such a strip); buttons and rows always have margins
            if (b.width() * b.height() > screen.width() * screen.height() * 0.6) return@mapNotNull null
            if (b.width() >= screen.width() * 0.95 || b.height() >= screen.height() * 0.95) return@mapNotNull null
            if (b.isEmpty) return@mapNotNull null
            // The system's own bar buttons are not the app's
            if (runCatching { node.applicationPackage }.getOrNull() != context.packageName) return@mapNotNull null
            val hit = bars.firstOrNull { Rect(it).let { r -> r.intersect(b) && r.width() > 2 && r.height() > 2 } }
            hit?.let { "$where: «${runCatching { node.text ?: node.contentDescription }.getOrNull()}» $b under the bar $it" }
        }
    }

    private fun shot(name: String) = device.executeShellCommand("screencap -p /data/local/tmp/bars_$name.png")

    /**
     * The screen as it is turned now: the turn is set from the computer before the run (adb shell settings put system
     * user_rotation 0/1/3) — set from inside, the automation undoes it.
     */
    @Test
    fun nothingLiesUnderTheBars() {
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var accounts: AccountViewModel? = null
        var activity: Activity? = null
        val problems = mutableListOf<String>()
        try {
            scenario.onActivity {
                accounts = ViewModelProvider(it)[AccountViewModel::class.java]
                activity = it
            }
            assertTrue("main screen", waitFor(15_000) { device.findObject(By.desc("В авто")) != null })
            Thread.sleep(1_500)
            val bars = barsOf(activity!!)
            Log.i(TAG, "rotation $rotation, bars $bars")
            shot("r${rotation}_main")
            problems += underBars(bars, "main")

            device.findObject(By.desc("В авто")).click()
            assertTrue("car screen", waitFor(10_000) { device.findObject(By.text("Моя волна")) != null })
            Thread.sleep(1_500)
            shot("r${rotation}_car")
            problems += underBars(bars, "car")
            // The source dock: every source there with its name, none squeezed
            val labels = listOf("Моя волна", "Любимое", "Радио", "Хиты", "Скачанные")
            for (label in labels) {
                val node = device.findObject(By.text(label))
                if (node == null) {
                    problems += "car: the source «$label» is not on the screen"
                    continue
                }
                val b = node.visibleBounds
                if (b.height() < 8) problems += "car: the source «$label» squeezed to $b"
            }

            device.findObject(By.desc("Настройки"))?.click()
            assertTrue("settings", waitFor(10_000) { device.findObject(By.text("Настройки")) != null })
            Thread.sleep(1_500)
            shot("r${rotation}_settings")
            problems += underBars(bars, "settings")
            device.pressBack()
        } finally {
            accounts?.let { vm -> instrumentation.runOnMainSync { vm.logoutAllAccounts() } }
            scenario.close()
        }
        Log.i(TAG, "rotation $rotation problems: $problems")
        assertTrue("rotation $rotation:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Only signs in with the fake token and leaves it so (-e seed true): the side bars are then checked from the
     * computer (scratchpad bars_check.py), which can turn the screen — inside a run the automation holds it upright.
     */
    @Test
    fun seedForTheCheckFromTheComputer() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("seed") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }
        seeded = true
    }

    private companion object {
        const val TAG = "SystemBars"
        /** The seed's account stays: the check from the computer needs it. */
        var seeded = false
    }
}

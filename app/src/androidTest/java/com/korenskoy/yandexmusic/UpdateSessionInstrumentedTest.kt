package com.korenskoy.yandexmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.korenskoy.yandexmusic.data.updater.InstallState
import com.korenskoy.yandexmusic.data.updater.UpdateAccess
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * The update through Android's installer session (a head unit hung on «Обновление…» with the old way): the app's own
 * package is handed over, the system's "install?" window comes up, and a «Отмена» there is heard — the app stops
 * waiting and shows no false failure. Installing for real would replace the app under the test.
 */
@RunWith(AndroidJUnit4::class)
class UpdateSessionInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun theSystemAsksToConfirmAndACancelIsHeard() {
        // As after «Разрешить» in the settings: this app may install
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow").close()
        val installer = EntryPointAccessors.fromApplication(context.applicationContext, UpdateAccess::class.java).installer()
        installer.reset()
        val apk = File(context.cacheDir, "self-update.apk")
        File(context.packageCodePath).copyTo(apk, overwrite = true)

        assertTrue("the session takes the package", runBlocking { installer.install(apk) }.isSuccess)
        waitFor(20_000) { installer.state.value == InstallState.Confirming }

        // The system's own window: «Отмена» / «Cancel»
        val cancel = device.wait(Until.findObject(By.text(Pattern.compile("(?i)(cancel|отмена)"))), 15_000)
        if (cancel != null) cancel.click() else device.pressBack()

        waitFor(20_000) { installer.state.value == null }
        apk.delete()
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue("Timed out", condition())
    }
}

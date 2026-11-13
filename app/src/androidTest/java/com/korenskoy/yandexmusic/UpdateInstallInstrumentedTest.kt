package com.korenskoy.yandexmusic

import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.korenskoy.yandexmusic.data.updater.InstallState
import com.korenskoy.yandexmusic.data.updater.SessionInstaller
import com.korenskoy.yandexmusic.data.updater.UpdateAccess
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 1.14.8 on Android's real installer, the report: after the download several "Обновить?" windows came up, and the
 * installer hung on «Обновление…». The app's own package is handed to the installer (the update itself is not
 * confirmed here: that would replace the app under the test), the way the app's returns and presses did it.
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val packageInstaller: PackageInstaller get() = context.packageManager.packageInstaller
    private lateinit var installer: SessionInstaller
    private lateinit var apk: File

    @Before
    fun setUp() {
        installer = EntryPointAccessors.fromApplication(context.applicationContext, UpdateAccess::class.java).installer()
        // The app may install packages, as the user allows it once
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
        } else {
            shell("settings put secure install_non_market_apps 1")
        }
        apk = File(context.cacheDir, "update-test.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
        clearSessions()
    }

    @After
    fun tearDown() {
        device.pressBack()
        clearSessions()
        installer.reset()
        apk.delete()
    }

    private fun clearSessions() {
        packageInstaller.mySessions.forEach { runCatching { packageInstaller.abandonSession(it.sessionId) } }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    /** The system installer's windows open now, by the activity records of the installer's package. */
    private fun installerWindows(): Int =
        shell("dumpsys activity activities").lines().count { line ->
            // Each window once: its line in the stack ("* Hist #0: ActivityRecord{… packageinstaller …}")
            line.contains("Hist") && line.contains("#") && line.contains("packageinstaller", ignoreCase = true)
        }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test
    fun manyReturnsAndPressesMakeOneSessionAndOneWindow() {
        // The download is done: then five returns to the app or presses of «Установить», as on the head unit
        repeat(5) { runBlocking { installer.install(apk) } }
        assertTrue("the system asks to confirm", waitUntil(15_000) { installer.state.value == InstallState.Confirming })
        Thread.sleep(2_000)
        repeat(5) { runBlocking { installer.install(apk) } }
        Thread.sleep(2_000)
        val sessions = packageInstaller.mySessions.size
        val windows = installerWindows()
        Log.i(TAG, "sessions $sessions, installer windows $windows, state ${installer.state.value}")
        assertEquals("one update, one session", 1, sessions)
        assertTrue("one \"install?\" window, not several: $windows", windows <= 1)
    }

    @Test
    fun aHungInstallIsGivenUpAndANewTryIsOneSessionAgain() {
        runBlocking { installer.install(apk) }
        assertTrue(waitUntil(15_000) { installer.state.value == InstallState.Confirming })
        Thread.sleep(2_000)
        Log.i(TAG, "before giving up: windows ${installerWindows()}, sessions ${packageInstaller.mySessions.size}")
        installer.noAnswer()
        assertEquals(InstallState.NoAnswer, installer.state.value)
        assertTrue("the session is given up", waitUntil(5_000) { packageInstaller.mySessions.isEmpty() })
        // Android leaves its window open (seen on 7.1 and 15); it can no longer install the given-up session
        Log.i(TAG, "after giving up: windows ${installerWindows()}")
        device.pressBack()
        // «Ещё раз» starts anew
        installer.reset()
        runBlocking { installer.install(apk) }
        assertTrue(waitUntil(15_000) { installer.state.value == InstallState.Confirming })
        assertEquals(1, packageInstaller.mySessions.size)
    }

    @Test
    fun sessionsLeftFromEarlierTriesAreClosed() {
        // Two sessions an earlier run left behind
        repeat(2) {
            packageInstaller.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        }
        assertEquals(2, packageInstaller.mySessions.size)
        installer.abandonLeftovers()
        assertTrue(waitUntil(5_000) { packageInstaller.mySessions.isEmpty() })
    }

    private companion object {
        const val TAG = "UpdateInstall"
    }
}

package com.korenskoy.yandexmusic.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods used during a cold start, and — on a device where the app is signed
 * in — while using the main screens, so ART compiles them ahead of time.
 * Run with: ./gradlew :app:generateReleaseBaselineProfile (needs a device or emulator, API 28+).
 * Screens the generator cannot reach are covered by app/src/main/baselineProfiles/screens-baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        // Splash animation, Hilt graph, Compose first frames and the media session connection
        device.waitForIdle()
        Thread.sleep(4_000)
        device.waitForIdle()
    }

    /** Scrolling "Liked", search, opening the player and a playlist; skipped on the sign-in screen. */
    @Test
    fun mainScreens() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = false
    ) {
        pressHome()
        startActivityAndWait()
        if (!device.wait(Until.hasObject(By.desc("Моя Музыка")), 10_000)) return@collect // not signed in

        openTab("Моя Музыка")
        scrollList()
        device.findObject(By.text("Плейлисты"))?.let {
            device.findObjects(By.textContains("трек")).firstOrNull()?.click()
            device.waitForIdle()
            scrollList()
            device.pressBack()
        }

        openTab("Поиск")
        scrollList()

        openTab("FM Радио")
        scrollList()

        // The mini-player opens the full player, if anything is in the queue
        device.findObject(By.desc("Воспроизведение или пауза"))?.let {
            device.findObjects(By.desc("Обложка")).maxByOrNull { it.visibleBounds.top }?.click()
            device.waitForIdle()
            Thread.sleep(1_000)
            device.pressBack()
        }
        openTab("Главная")
    }

    private fun MacrobenchmarkScope.openTab(name: String) {
        device.findObject(By.desc(name))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollList() {
        val list = device.findObject(By.scrollable(true)) ?: return
        list.setGestureMargin(device.displayWidth / 5)
        repeat(2) { list.fling(Direction.DOWN) }
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "YandexMusicLite.KorenskoyR"
    }
}

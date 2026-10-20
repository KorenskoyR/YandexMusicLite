package com.korenskoy.yandexmusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods used during a cold start so ART compiles them ahead of time.
 * Run with: ./gradlew :app:generateReleaseBaselineProfile (needs a device or emulator, API 28+).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "YandexMusicLite.KorenskoyR",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        // Splash animation, Hilt graph, Compose first frames and the media session connection
        device.waitForIdle()
        Thread.sleep(4_000)
        device.waitForIdle()
    }
}

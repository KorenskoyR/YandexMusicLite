package com.korenskoy.yandexmusic.data

import com.korenskoy.yandexmusic.data.start.WakeRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The starts and wakings that open the app with «Запуск с устройством», and how often. */
class WakeRulesTest {

    @Test
    fun theBootsAndTheIgnitionsOfTheUnitsAreStarts() {
        for (action in listOf(
            "android.intent.action.BOOT_COMPLETED", "android.intent.action.QUICKBOOT_POWERON", "android.intent.action.ACTION_BOOT_IPO",
            "com.fyt.boot.ACCON", "com.cayboy.action.ACC_ON", "com.carboy.action.ACC_ON", "com.glsx.boot.ACCON", "SCREEN_ON"
        )) assertTrue(action, WakeRules.isStart(action))
        assertFalse(WakeRules.isStart("android.intent.action.TIME_TICK"))
        assertFalse(WakeRules.isStart(null))
    }

    @Test
    fun oneStartOpensTheAppOnce() {
        // The boot and the ignition of the same start, seconds apart: once
        assertTrue(WakeRules.opens(now = 100_000, lastAt = 0))
        assertFalse(WakeRules.opens(now = 130_000, lastAt = 100_000))
        // The next morning: again
        assertTrue(WakeRules.opens(now = 100_000 + WakeRules.ONCE_PER_MS, lastAt = 100_000))
    }
}

package com.korenskoy.yandexmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayScaleTest {

    @Test
    fun honestScreenIsLeftAtFullScale() {
        // A normal phone reports a physical dpi that matches its density bucket.
        assertEquals(1.0f, DisplayScale.autoScale(reportedDpi = 420, physicalDpi = 432), 0.001f)
    }

    @Test
    fun denseScreenWithLowReportedDpiScalesUp() {
        // 1920×1080 car unit: system says 160 dpi but the panel is physically ~240 dpi.
        assertEquals(1.5f, DisplayScale.autoScale(reportedDpi = 160, physicalDpi = 240), 0.001f)
    }

    @Test
    fun smallMismatchStaysAtFullScale() {
        // Within the 15% trust threshold: do not fiddle with the layout.
        assertEquals(1.0f, DisplayScale.autoScale(reportedDpi = 420, physicalDpi = 460), 0.001f)
    }

    @Test
    fun missingPhysicalDpiFallsBackToFullScale() {
        assertEquals(1.0f, DisplayScale.autoScale(reportedDpi = 160, physicalDpi = 0), 0.001f)
    }

    @Test
    fun extremeRatioIsClampedToMax() {
        assertEquals(2.0f, DisplayScale.autoScale(reportedDpi = 120, physicalDpi = 640), 0.001f)
    }

    @Test
    fun overReportedDensityScalesDown() {
        // Rare opposite case: system density too high, UI too big → scale below 1.0.
        assertEquals(0.75f, DisplayScale.autoScale(reportedDpi = 320, physicalDpi = 240), 0.001f)
    }
}

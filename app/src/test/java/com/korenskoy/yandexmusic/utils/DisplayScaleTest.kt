package com.korenskoy.yandexmusic.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun theMonjaroPanelMisreportingItsDensityIsNotBelieved() {
        // 12.3 inch 1760x720 at 160 dpi says 321 dpi physical: that would be a 5.9 inch screen
        val info = DisplayScale.decide(1760, 720, reportedDpi = 160, physicalDpi = 321)
        assertEquals(1.0f, info.autoScale, 0.001f)
        assertEquals(AutoScaleReason.IMPLAUSIBLE, info.reason)
        assertEquals(5.9f, info.impliedDiagonalIn, 0.05f)
    }

    @Test
    fun aDenseHeadUnitWithAnHonestPanelIsStillScaled() {
        // Issue #5: 1920x720 at 120 dpi, really 200 dpi: a 10 inch screen, believable
        val info = DisplayScale.decide(1920, 720, reportedDpi = 120, physicalDpi = 200)
        assertEquals(1.65f, info.autoScale, 0.001f)
        assertEquals(AutoScaleReason.SCALED, info.reason)
    }

    @Test
    fun autoNeverLeavesTheAppTooSmallToFit() {
        // A believable 8.2 inch 1920x720 panel at 160 dpi, really 250: 155% would leave 464 dp; 150% keeps 480
        val info = DisplayScale.decide(1920, 720, reportedDpi = 160, physicalDpi = 250)
        assertEquals(AutoScaleReason.LIMITED, info.reason)
        assertEquals(1.5f, info.autoScale, 0.001f)
        assertTrue("480 dp kept on the short side", 720 / (160 / 160f * info.autoScale) >= 480f - 0.5f)
    }

    @Test
    fun phonesAreNotJudgedBySize() {
        assertTrue(DisplayScale.plausible(reportedDpi = 420, diagonalIn = 3f))
        assertFalse(DisplayScale.plausible(reportedDpi = 160, diagonalIn = 5.9f))
        assertTrue(DisplayScale.plausible(reportedDpi = 160, diagonalIn = 10f))
    }

    @Test
    fun overReportedDensityScalesDown() {
        // Rare opposite case: system density too high, UI too big → scale below 1.0.
        assertEquals(0.75f, DisplayScale.autoScale(reportedDpi = 320, physicalDpi = 240), 0.001f)
    }
}

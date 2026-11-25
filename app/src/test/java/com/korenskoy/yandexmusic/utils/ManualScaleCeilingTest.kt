package com.korenskoy.yandexmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** The hand-set interface scale stops where the screen would turn shorter than 290 dp. */
class ManualScaleCeilingTest {

    @Test
    fun theMonjaroOfTheUserKeepsItsOneHundredFiftyFivePercent() {
        // 1760×720, «155 % · ≈ 307 dpi» on the user's screen: about 198 dpi reported
        assertEquals(2.0f, DisplayScale.manualCeiling(1760, 720, 198), 0.001f)
    }

    @Test
    fun aDenseLowScreenStopsBeforeThePagesBreak() {
        // 1760×720 at 320 dpi: 720 / (2 * 290) = 1.24 -> 1.2f
        assertEquals(1.2f, DisplayScale.manualCeiling(1760, 720, 320), 0.001f)
    }

    @Test
    fun aPhoneAndATabletHaveTheirOwnCeilings() {
        assertEquals(1.4f, DisplayScale.manualCeiling(1080, 2400, 420), 0.001f)
        assertEquals(2.0f, DisplayScale.manualCeiling(1600, 2560, 320), 0.001f)
    }

    @Test
    fun neverUnderOneHundredPercent() {
        // An old 800×480 at 240 dpi gets 110% at 290 dp floor; tiny 320×240 PiP or 640×480 @ 320 stays at 100%
        assertEquals(1.1f, DisplayScale.manualCeiling(800, 480, 240), 0.001f)
        assertEquals(1.0f, DisplayScale.manualCeiling(320, 240, 160), 0.001f)
        assertEquals(1.0f, DisplayScale.manualCeiling(640, 480, 320), 0.001f)
    }

    @Test
    fun inAWindowTheWindowSetsTheCeiling() {
        // The Monjaro at 150 % by hand: whole screen — kept; in a launcher's lower strip of 288 px — back to 100 %
        val screen = DisplayScaleInfo(1760, 720, 198, 155, autoScale = 1f)
        assertEquals(1.5f, DisplayScale.effectiveScale(false, 1.5f, DisplayScale.inWindow(screen, 1760, 720)), 0.001f)
        assertEquals(1.0f, DisplayScale.effectiveScale(false, 1.5f, DisplayScale.inWindow(screen, 1760, 288)), 0.001f)
        // Half of the screen beside a navigator keeps the height, and the scale with it
        assertEquals(1.5f, DisplayScale.effectiveScale(false, 1.5f, DisplayScale.inWindow(screen, 880, 720)), 0.001f)
    }

    @Test
    fun inAWindowAutoIsLimitedButTheScreenStillDecidesIt() {
        // A dense unit scaled up to 140 % by "Авто": a strip of 300 px holds no more than 100 %
        val screen = DisplayScaleInfo(1280, 720, 160, 224, autoScale = 1.4f, reason = AutoScaleReason.SCALED)
        val strip = DisplayScale.inWindow(screen, 1280, 300)
        assertEquals(1.0f, strip.autoScale, 0.001f)
        assertEquals(AutoScaleReason.LIMITED, strip.reason)
        // Unknown window (before the first layout): the screen's own answer
        assertEquals(screen, DisplayScale.inWindow(screen, 0, 0))
        // Whole screen: as before
        assertEquals(1.4f, DisplayScale.inWindow(screen, 1280, 720).autoScale, 0.001f)
    }
}

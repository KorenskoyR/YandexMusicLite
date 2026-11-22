package com.korenskoy.yandexmusic.presentation

import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.presentation.car.carScaleOf
import com.korenskoy.yandexmusic.presentation.car.carStripOf
import com.korenskoy.yandexmusic.presentation.car.carStripScaleOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «В авто» is as big as its window whatever the interface scale (it got smaller as the scale grew: Geely Monjaro at
 * 150 %, 1.14.12), and a low wide window lies as one strip.
 */
class CarStripTest {

    /** What the eye sees: the design's size in the screen's own pixels, for a screen of [pxW]×[pxH] at [density]. */
    private fun physical(pxW: Float, pxH: Float, density: Float, landscape: Boolean = true): Float =
        // The system's density is 2 in these cases: the rest is the interface scale
        carScaleOf((pxW / density).dp, (pxH / density).dp, landscape, interfaceScale = density / 2f) * density

    @Test
    fun theInterfaceScaleNeverShrinksTheCar() {
        // The Monjaro: 1760×720, the system says density 2; the interface at 100, 150 and 200 %
        val at100 = physical(1760f, 720f, 2f)
        val at150 = physical(1760f, 720f, 3f)
        val at200 = physical(1760f, 720f, 4f)
        assertEquals(at100, at150, 0.01f)
        assertEquals(at100, at200, 0.01f)
        // Standing too (the Geely Galaxy's 1440×1920)
        assertEquals(physical(1440f, 1920f, 2f, landscape = false), physical(1440f, 1920f, 3f, landscape = false), 0.01f)
    }

    @Test
    fun theWholeLyingDesignFitsTheWindow() {
        // Nothing to squeeze: 720 design units are exactly the window's height
        val s = carScaleOf(587.dp, 240.dp, isLandscape = true)
        assertEquals(240f, 720 * s, 0.5f)
    }

    @Test
    fun aLowerFortyPercentWindowIsAStrip() {
        // Carlinkit Ultra2 in CarCar Launcher, 100 % and 155 % of the interface
        assertTrue(carStripOf(1550.dp, 349.dp, wasStrip = false))
        assertTrue(carStripOf(1000.dp, 225.dp, wasStrip = false))
        // A half of a lying 1280×720 unit
        assertTrue(carStripOf(1280.dp, 360.dp, wasStrip = false))
    }

    @Test
    fun theStripGivesTheControlsMoreThanLying() {
        assertTrue(carStripScaleOf(1550.dp, 349.dp) > carScaleOf(1550.dp, 349.dp, isLandscape = true) * 1.4f)
    }

    @Test
    fun wholeScreensKeepTheirLayoutAtAnyScale() {
        for (scale in listOf(1f, 1.5f, 2f)) {
            assertFalse(carStripOf((1280 / scale).dp, (720 / scale).dp, wasStrip = false))
            assertFalse(carStripOf((1920 / scale).dp, (720 / scale).dp, wasStrip = false))
            assertFalse(carStripOf((1760 / scale).dp, (720 / scale).dp, wasStrip = false))
            assertFalse(carStripOf((1280 / scale).dp, (480 / scale).dp, wasStrip = false))
            assertFalse(carStripOf((1024 / scale).dp, (600 / scale).dp, wasStrip = false))
        }
    }

    @Test
    fun aFlatUnitWithItsBarsKeepsItsColumns() {
        // 1760×720 with a status bar and a taskbar left 1760×550; a 1920×720 with a bar below: three columns stay
        assertFalse(carStripOf(1760.dp, 550.dp, wasStrip = false))
        assertFalse(carStripOf(1920.dp, 632.dp, wasStrip = false))
        assertFalse(carStripOf(880.dp, 275.dp, wasStrip = false))
    }

    @Test
    fun theChoiceHoldsNearTheEdge() {
        // ×1.29 bigger in the strip: between LEAVE (1.22) and ENTER (1.3)
        assertFalse(carStripOf(1760.dp, 550.dp, wasStrip = false))
        assertTrue(carStripOf(1760.dp, 550.dp, wasStrip = true))
    }

    @Test
    fun theStripFitsItsDesign() {
        assertEquals(1f, carStripScaleOf(1600.dp, 420.dp), 0.001f)
        assertEquals(0.5f, carStripScaleOf(800.dp, 600.dp), 0.001f)
    }

    @Test
    fun standingInAShortWindowTheControlsStayBig() {
        // Half of a lying 1024×600 unit beside a navigator (Android 7.1 split screen): the whole design came out at
        // 0.5 with 32 dp buttons over each other; the cover gives up all its height instead: 600 / 800
        assertEquals(0.75f, carScaleOf(507.dp, 600.dp, isLandscape = false), 0.001f)
        // A split of 40 % on a 10″ 1280×720: 0.85, not 0.6
        assertEquals(512f / 600f, carScaleOf(512.dp, 720.dp, isLandscape = false), 0.001f)
        // Where the whole design is big enough nothing changes: the Geely's standing 960×1280, a phone
        assertEquals(1280f / 1200f, carScaleOf(960.dp, 1280.dp, isLandscape = false), 0.001f)
        assertEquals(0.6f, carScaleOf(360.dp, 800.dp, isLandscape = false), 0.001f)
    }

    @Test
    fun theControlsComeFirst() {
        // A little bigger than the design always, and the interface scale grows them more (where there is room)
        assertEquals(1.3f, com.korenskoy.yandexmusic.presentation.car.carControlsGrow(1f), 0.001f)
        assertEquals(1.95f, com.korenskoy.yandexmusic.presentation.car.carControlsGrow(1.5f), 0.001f)
        assertEquals(com.korenskoy.yandexmusic.presentation.car.CAR_CONTROLS_MAX, com.korenskoy.yandexmusic.presentation.car.carControlsGrow(3f), 0.001f)
        // A scale under 100 % never makes them smaller than the design's
        assertEquals(1.3f, com.korenskoy.yandexmusic.presentation.car.carControlsGrow(0.8f), 0.001f)
        // Lying, the player's column widens into the cover's, which keeps its least
        val room = 1192f
        assertEquals(652f * 1.3f, com.korenskoy.yandexmusic.presentation.car.carDriverWidth(room, 1.3f), 0.5f)
        assertEquals(room - 40f - 280f, com.korenskoy.yandexmusic.presentation.car.carDriverWidth(room, 2f), 0.5f)
        // Never narrower than designed
        assertEquals(652f, com.korenskoy.yandexmusic.presentation.car.carDriverWidth(800f, 2f), 0.5f)
    }

    @Test
    fun aWindowNotMuchWiderThanHighStands() {
        // Splits of 60 and 70 % on a 1280×720 unit (768 and 896 wide): standing, the controls get the width
        assertFalse(com.korenskoy.yandexmusic.presentation.car.carLyingShape(true, 768.dp, 720.dp))
        assertFalse(com.korenskoy.yandexmusic.presentation.car.carLyingShape(true, 896.dp, 720.dp))
        // The whole unit and a 1024×600 lie
        assertTrue(com.korenskoy.yandexmusic.presentation.car.carLyingShape(true, 1280.dp, 720.dp))
        assertTrue(com.korenskoy.yandexmusic.presentation.car.carLyingShape(true, 1024.dp, 600.dp))
        // A standing window never lies
        assertFalse(com.korenskoy.yandexmusic.presentation.car.carLyingShape(false, 2000.dp, 720.dp))
    }
}

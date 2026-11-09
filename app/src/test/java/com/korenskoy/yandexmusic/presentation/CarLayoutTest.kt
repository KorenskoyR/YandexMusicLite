package com.korenskoy.yandexmusic.presentation

import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.presentation.car.carDockClearance
import com.korenskoy.yandexmusic.presentation.car.carScaleOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The car layout's scale and where a message may sit above the source dock. */
class CarLayoutTest {

    @Test
    fun theDesignSizesScaleToOne() {
        assertEquals(1f, carScaleOf(1280.dp, 720.dp, 720.dp, isLandscape = true), 0.0001f)
        assertEquals(1f, carScaleOf(600.dp, 1200.dp, 1200.dp, isLandscape = false), 0.0001f)
    }

    @Test
    fun standingTheHeightLeftByTheBarsDecides() {
        // The Geely: 960 × 1280 dp, 24 + 48 of system bars
        val s = carScaleOf(960.dp, 1280.dp, 1208.dp, isLandscape = false)
        assertEquals(1208f / 1200f, s, 0.0001f)
    }

    @Test
    fun aMessageSitsAboveTheSources() {
        assertEquals(136f, carDockClearance(1280.dp, 720.dp, 720.dp, isLandscape = true).value, 0.01f)
        assertEquals(124f, carDockClearance(600.dp, 1200.dp, 1200.dp, isLandscape = false).value, 0.01f)
        // Always more than the dock itself (88 standing, 96 lying, plus its bottom margin)
        val phone = carDockClearance(411.dp, 914.dp, 866.dp, isLandscape = false)
        assertTrue(phone.value > (88f + 24f) * carScaleOf(411.dp, 914.dp, 866.dp, false))
    }

    @Test
    fun theFiveStandingControlsFitWithAir() {
        // dislike + previous + ring + next + like, standing; the panel is 544 wide at most of the design
        val controls = 60 + 80 + 196 + 80 + 60
        assertTrue(controls <= 544 - 4 * 16)
    }
}

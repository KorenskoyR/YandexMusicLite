package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.presentation.hifi.Adaptive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One sizing rule for phones, tablets and head units: a tablet gets more and bigger, not a phone's sizes in space. */
class AdaptiveTest {
    @Test
    fun gridsTakeAsManyReadableCardsAsTheWidthHolds() {
        assertEquals("a small phone", 2, Adaptive.gridColumns(320))
        assertEquals("a phone", 2, Adaptive.gridColumns(372))
        assertEquals("a tablet standing (the Galaxy Tab's 800)", 4, Adaptive.gridColumns(760))
        assertEquals("a 12-inch tablet lying", 7, Adaptive.gridColumns(1326))
        assertEquals("never more than eight", 8, Adaptive.gridColumns(3000))
        // Each card stays at least the readable size where there are more than two
        for (w in 400..2000 step 37) {
            val n = Adaptive.gridColumns(w)
            if (n > 2) assertTrue("$w dp: $n columns", (w - (n - 1) * Adaptive.GRID_GAP_DP) / n >= Adaptive.MIN_CELL_DP)
        }
    }

    @Test
    fun theStandingColumnIsAPhonesOnAPhoneAndGrowsOnATablet() {
        assertEquals(560f, Adaptive.standingColumn(412).value, 0.01f)
        assertEquals(640f, Adaptive.standingColumn(800).value, 0.01f)
        assertEquals(680f, Adaptive.standingColumn(1024).value, 0.01f)
    }

    @Test
    fun theWaveKnobGrowsWithTheScreen() {
        assertEquals(168.92f, Adaptive.waveKnob(412f, landscape = false).value, 0.01f)
        assertEquals(300f, Adaptive.waveKnob(800f, landscape = false).value, 0.01f)
        assertEquals(120f, Adaptive.waveKnob(250f, landscape = true).value, 0.01f)
    }

    @Test
    fun theLyingShelfKeepsAHeadUnitsSizeAndGrowsOnATablet() {
        assertEquals(84f, Adaptive.lyingShelfCard(412).value, 0.01f)
        assertEquals(112f, Adaptive.lyingShelfCard(480).value, 0.01f)
        assertEquals(112f, Adaptive.lyingShelfCard(720).value, 0.01f)
        assertEquals(120f, Adaptive.lyingShelfCard(800).value, 0.01f)
        assertEquals(150f, Adaptive.lyingShelfCard(1080).value, 0.01f)
    }
}

package com.korenskoy.yandexmusic.presentation

import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.presentation.car.carScaleOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Масштаб интерфейса: Вручную» sizes «В авто» too, never wider than its window (Carlinkit Ultra2, a 40% window). */
class CarManualScaleTest {

    @Test
    fun aShortWideWindowGrowsWithTheManualScale() {
        assertEquals(1.04f, carScaleOf(2000.dp, 500.dp, isLandscape = true, manual = 1.5f), 0.01f)
    }

    @Test
    fun neverWiderThanTheWindow() {
        assertEquals(1f, carScaleOf(1280.dp, 1000.dp, isLandscape = true, manual = 1.5f), 0.001f)
    }

    @Test
    fun aSmallerScaleShrinksIt() {
        assertEquals(0.8f, carScaleOf(1280.dp, 720.dp, isLandscape = true, manual = 0.8f), 0.001f)
    }

    @Test
    fun autoIsAsBefore() {
        assertEquals(0.6944f, carScaleOf(2000.dp, 500.dp, isLandscape = true), 0.001f)
    }
}

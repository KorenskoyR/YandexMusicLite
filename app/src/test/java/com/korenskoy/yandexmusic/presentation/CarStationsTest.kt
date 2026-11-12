package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.presentation.car.CarRadio
import com.korenskoy.yandexmusic.presentation.car.CarStationsPanel
import com.korenskoy.yandexmusic.presentation.car.Scale
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Станции» in the car: opens on the favourites, a tap plays and closes, the heart keeps or lets go. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class CarStationsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun station(id: String, name: String) = RadioStation(id, name, "100.$id FM", "", "", RadioCategory.POPULAR)
    private val stations = listOf(station("1", "Альфа"), station("2", "Бета"), station("3", "Гамма"))

    @Test
    fun theFavouritesFirstAPlayAndAHeart() {
        val played = mutableListOf<String>()
        var closed = 0
        compose.setContent {
            var favourites by remember { mutableStateOf(setOf("2")) }
            YandexMusicTheme {
                CarStationsPanel(
                    Scale(1f),
                    CarRadio(stations, favourites, onAir = "2", onPlay = { played += it.id }, onToggleFavourite = { id -> favourites = if (id in favourites) favourites - id else favourites + id }),
                    Modifier.fillMaxSize(),
                    onClose = { closed++ }
                )
            }
        }
        // The favourites: only «Бета»
        compose.onNodeWithText("Избранные · 1").assertIsSelected()
        compose.onNodeWithText("Альфа").assertDoesNotExist()
        // All, and «Альфа» becomes a favourite
        compose.onNodeWithText("Все · 3").performClick()
        compose.onNodeWithContentDescription("В избранное: Альфа").performClick()
        compose.onNodeWithText("Избранные · 2").assertExists()
        compose.onNodeWithContentDescription("Убрать из избранного: Альфа").assertExists()
        // A tap plays the station and closes the panel
        compose.onNodeWithContentDescription("Включить Гамма").performClick()
        assertEquals(listOf("3"), played)
        assertEquals(1, closed)
    }

    @Test
    fun withoutFavouritesItOpensOnAll() {
        compose.setContent {
            YandexMusicTheme {
                CarStationsPanel(Scale(1f), CarRadio(stations, emptySet(), onAir = null, onPlay = {}, onToggleFavourite = {}), Modifier.fillMaxSize(), onClose = {})
            }
        }
        compose.onNodeWithText("Все · 3").assertIsSelected()
        compose.onNodeWithText("Альфа").assertExists()
    }
}

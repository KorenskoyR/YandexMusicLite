package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.ChartInfoResponseDto
import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-xxhdpi", application = Application::class)
class TopChartTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chartResponseDtoDeserializesPositionsAndTracks() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val payload = """
            {
              "result": {
                "chart": {
                  "uid": 100,
                  "kind": 1076,
                  "title": "Чарт",
                  "trackCount": 2,
                  "tracks": [
                    {
                      "id": 5001,
                      "chart": { "position": 1, "progress": "same", "shift": 0 },
                      "track": { "id": 5001, "title": "Лидер чарта", "artists": [{"id": 1, "name": "Артист 1"}], "durationMs": 190000 }
                    },
                    {
                      "id": 5002,
                      "chart": { "position": 2, "progress": "up", "shift": 3 },
                      "track": { "id": 5002, "title": "Второй трек", "artists": [{"id": 2, "name": "Артист 2"}], "durationMs": 210000 }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val parsed = json.decodeFromString<ResponseWrapper<ChartInfoResponseDto>>(payload)
        val chart = parsed.result?.chart
        assertNotNull(chart)
        assertEquals(2, chart?.tracks?.size)
        assertEquals(1, chart?.tracks?.get(0)?.chart?.position)
        assertEquals("Лидер чарта", chart?.tracks?.get(0)?.track?.title)
        assertEquals(2, chart?.tracks?.get(1)?.chart?.position)
    }

    @Test
    fun searchPlacesTopChartStrictlyBetweenWeeklyHitsAndInTrend() {
        val sample = (1..4).map { idx ->
            TrackDetailsDto(
                id = JsonPrimitive("id_$idx"),
                title = "Трек $idx",
                artists = listOf(ArtistDto(name = "Исполнитель $idx")),
                durationMs = 200_000L
            )
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme(darkTheme = true) {
                CompositionLocalProvider(LocalArtworkTint provides ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))) {
                    Box(Modifier.fillMaxSize()) {
                        HiFiBackdrop()
                        SearchScreen(
                            query = "",
                            onQueryChange = {},
                            results = emptyList(),
                            weeklyHits = sample,
                            topChart = sample,
                            trendTracks = sample,
                            isSearching = false,
                            currentTrackId = null,
                            isPlaying = false,
                            likedTrackIds = emptySet(),
                            onTrackClick = { _, _ -> },
                            onToggleLike = {}
                        )
                    }
                }
            }
        }
        repeat(15) {
            compose.mainClock.advanceTimeBy(80)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        val hitsNode = compose.onNodeWithText("Хиты недели", ignoreCase = true).assertIsDisplayed().fetchSemanticsNode()
        val chartNode = compose.onNodeWithText("Топ-чарт", ignoreCase = true).assertIsDisplayed().fetchSemanticsNode()
        val trendNode = compose.onNodeWithText("В тренде", ignoreCase = true).fetchSemanticsNode()

        assertTrue("Weekly Hits must be above Top Chart", hitsNode.boundsInRoot.top < chartNode.boundsInRoot.top)
        assertTrue("Top Chart must be above In Trend", chartNode.boundsInRoot.top < trendNode.boundsInRoot.top)
        // Check scrollable rank badges exist in Top Chart
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.onRoot().assertExists()
    }
}

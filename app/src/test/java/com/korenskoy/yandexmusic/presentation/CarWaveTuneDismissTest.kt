package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.car.CarRadio
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.hifi.LocalLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class CarWaveTuneDismissTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showCar(initialTuneOpen: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val stations = RadioRepository(context).getBuiltinStations()
        val radio = CarRadio(stations, emptySet(), onAir = null, onPlay = {}, onToggleFavourite = {})
        val track = TrackDetailsDto(
            id = JsonPrimitive("1"),
            title = "Группа крови",
            artists = listOf(ArtistDto(name = "Кино")),
            durationMs = 240_000L
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalArtworkTint provides ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B)),
                    LocalLyrics provides LyricsUi(known = { LyricsAnswer.Text("Текст песни") }, load = { LyricsAnswer.Text("Текст песни") }),
                    LocalCarClock provides { 1_792_567_800_000L },
                    LocalLinkState provides LinkState.GOOD
                ) {
                    Box(Modifier.fillMaxSize()) {
                        CarScreen(
                            currentTrack = track,
                            isPlaying = true,
                            likedTrackIds = emptySet(),
                            onPlayPauseClick = {},
                            onNextClick = {},
                            onPrevClick = {},
                            onToggleLike = {},
                            onDislikeClick = {},
                            onPlayMyVibe = {},
                            onPlayFavorites = {},
                            onPlayHits = {},
                            onPlayDownloaded = {},
                            onPlayRadio = {},
                            onExit = {},
                            isLandscape = true,
                            source = PlaylistSource.VIBE,
                            waveTuning = WaveTuning(),
                            initialTuneOpen = initialTuneOpen,
                            radio = radio
                        )
                    }
                }
            }
        }
        advanceFrames()
    }

    private fun advanceFrames() {
        repeat(15) {
            compose.mainClock.advanceTimeBy(80)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun tappingOutsideTunePanelDismissesIt() {
        showCar(initialTuneOpen = true)
        compose.onNodeWithText("Настроить волну").assertIsDisplayed()

        // Tap free area on the left (outside the right-side tunePanel).
        compose.onRoot().performTouchInput {
            click(Offset(width * 0.12f, height * 0.25f))
        }
        advanceFrames()

        compose.onNodeWithText("Настроить волну").assertDoesNotExist()
    }

    @Test
    fun closeButtonStillDismissesTunePanel() {
        showCar(initialTuneOpen = true)
        compose.onNodeWithText("Настроить волну").assertIsDisplayed()

        compose.onNodeWithContentDescription("Закрыть").performClick()
        advanceFrames()

        compose.onNodeWithText("Настроить волну").assertDoesNotExist()
    }
}

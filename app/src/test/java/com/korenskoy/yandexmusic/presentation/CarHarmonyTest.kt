package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.car.CAR_CONTROLS_MAX
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.DRIVER_COLUMN
import com.korenskoy.yandexmusic.presentation.car.carControlsGrow
import com.korenskoy.yandexmusic.presentation.car.carDriverWidth
import com.korenskoy.yandexmusic.presentation.car.carStandingGaugeExtra
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Bug-First & regression checks for Car Mode visual harmony (v1.14.12 large cover + NextRow card proportions
 * across CarUnderCover.NEXT and CarUnderCover.QUEUE, single-row controls on standard standing screens, and
 * two-row fallback reserved for narrow split windows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CarHarmonyTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String, title: String, artist: String = "Янка Дягилева") =
        TrackDetailsDto(
            id = JsonPrimitive(id),
            title = title,
            artists = listOf(ArtistDto(name = artist)),
            durationMs = 200_000,
            lyricsAvailable = false
        )

    private val current = track("0", "От большого ума")
    private val upcoming = listOf(
        track("1", "Первый следующий"),
        track("2", "Второй следующий"),
        track("3", "Третий следующий"),
        track("4", "Четвёртый следующий")
    )

    private fun renderCar(
        qualifiers: String,
        landscape: Boolean,
        underCover: CarUnderCover,
        scale: Float = 1f
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale)) {
                YandexMusicTheme {
                    Box(Modifier.fillMaxSize()) {
                        CarScreen(
                            currentTrack = current,
                            isPlaying = false,
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
                            isLandscape = landscape,
                            underCover = underCover,
                            upcoming = upcoming,
                            source = PlaylistSource.VIBE,
                            waveTuning = WaveTuning(),
                            onOpenSettings = {},
                            onVoiceSearch = {}
                        )
                    }
                }
            }
        }
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun SemanticsNode.rect(): Rect = boundsInRoot

    @Test
    fun pureFormulasRestoreV11412AtDefaultScaleAndProtectFarColumn() {
        // Default 1.0x scale does not inflate controls with unconditional 1.3x boost
        assertEquals(1.0f, carControlsGrow(1.0f), 0.001f)
        assertEquals(1.0f, carControlsGrow(0.8f), 0.001f)
        assertEquals(1.5f, carControlsGrow(1.5f), 0.001f)
        assertEquals(CAR_CONTROLS_MAX, carControlsGrow(3.0f), 0.001f)

        // At 1280x720 (roomWidth = 1192f), driver column stays 652f, leaving 500f for the right cover column
        val room = 1192f
        assertEquals(DRIVER_COLUMN, carDriverWidth(room, 1.0f), 0.5f)
        // Even at 1.5x interface scale, right column keeps at least 400f (so 400 cover / 88 NextRow stay readable)
        assertEquals(room - 40f - 400f, carDriverWidth(room, 1.5f), 0.5f)
        // Narrower screens adapt proportionally and never shrink driver below 652f
        assertEquals(DRIVER_COLUMN, carDriverWidth(800f, 1.5f), 0.5f)

        // Standard phone/tablet portrait does not reserve extra two-row control height at 1.0x
        assertEquals(0f, carStandingGaugeExtra(600f, 1.0f), 0.1f)
        assertEquals(0f, carStandingGaugeExtra(393f, 1.0f), 0.1f)
    }

    @Test
    fun landscapeWithNextRowHasLargeCoverAndReadableCards() {
        renderCar("w1280dp-h720dp-land-mdpi", landscape = true, underCover = CarUnderCover.NEXT)
        val app = RuntimeEnvironment.getApplication()
        val coverBounds = compose.onNodeWithContentDescription("От большого ума").fetchSemanticsNode().rect()
        val nextCardBounds = compose.onNodeWithContentDescription(
            app.getString(R.string.car_next_play_cd, "Первый следующий")
        ).fetchSemanticsNode().rect()
        val exitBounds = compose.onNodeWithContentDescription(app.getString(R.string.car_exit)).fetchSemanticsNode().rect()

        // In NEXT mode at 1280x720 (604 dp column height), 88 dp NextRow leaves 353x353 for the square cover
        // (up from crushed 304x304 in v1.16.0) while keeping 4 full-size NextCard thumbnails readable
        assertTrue("Expected large cover >= 350 dp in NEXT mode, got ${coverBounds.width}x${coverBounds.height}", coverBounds.width >= 350f)
        // NextRow cards should be full ~88 dp wide, not crushed to 64 dp
        assertTrue("Expected NextCard width >= 82 dp, got ${nextCardBounds.width}", nextCardBounds.width >= 82f)
        // Header exit button should not be bloated by 1.3x
        assertTrue("Expected compact header button ~56 dp (<= 62 dp), got ${exitBounds.width}", exitBounds.width <= 62f)
    }

    @Test
    fun landscapeWithQueueButtonHas500dpCover() {
        renderCar("w1280dp-h720dp-land-mdpi", landscape = true, underCover = CarUnderCover.QUEUE)
        val coverBounds = compose.onNodeWithContentDescription("От большого ума").fetchSemanticsNode().rect()
        assertTrue("Expected large cover >= 470 dp in QUEUE mode, got ${coverBounds.width}", coverBounds.width >= 470f)
    }

    @Test
    fun standingPhoneKeepsFiveButtonsInOneRowAndShowsNextRow() {
        val app = RuntimeEnvironment.getApplication()
        // Standard phone 393x873 portrait at 1.0x: Play, Prev, Next, Like, Dislike share the same horizontal centerline (1 row)
        renderCar("w393dp-h873dp-port-xxhdpi", landscape = false, underCover = CarUnderCover.NEXT)
        val playPhone = compose.onNodeWithContentDescription(app.getString(R.string.cd_vosproizvedenie_ili_pauza)).fetchSemanticsNode().rect()
        val likePhone = compose.onNodeWithContentDescription(app.getString(R.string.cd_nravitsya)).fetchSemanticsNode().rect()
        val coverPhone = compose.onNodeWithContentDescription("От большого ума").fetchSemanticsNode().rect()
        val densityPhone = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue(
            "Standard phone should keep 5 controls in 1 row: playY=${playPhone.center.y} likeY=${likePhone.center.y}",
            abs(playPhone.center.y - likePhone.center.y) < 16f * densityPhone
        )
        assertTrue(
            "Standing phone cover should be large (>= 160 dp), got ${coverPhone.width / densityPhone}",
            coverPhone.width / densityPhone >= 160f
        )
        // NextRow header must be visible on standard phone when NEXT is chosen
        compose.onNodeWithText("ДАЛЕЕ В ВОЛНЕ ›").fetchSemanticsNode()
    }

    @Test
    fun scaledStandingSplitStacksReactionsBelowPlay() {
        val app = RuntimeEnvironment.getApplication()
        // Narrow split beside navigator (426x720 mdpi) at 1.5x interface scale: reactions move to second row cleanly below Play
        renderCar("w426dp-h720dp-port-mdpi", landscape = false, underCover = CarUnderCover.QUEUE, scale = 1.5f)
        val playSplit = compose.onNodeWithContentDescription(app.getString(R.string.cd_vosproizvedenie_ili_pauza)).fetchSemanticsNode().rect()
        val likeSplit = compose.onNodeWithContentDescription(app.getString(R.string.cd_nravitsya)).fetchSemanticsNode().rect()
        assertTrue(
            "Narrow scaled split should stack reactions below Play: playY=${playSplit.center.y} likeY=${likeSplit.center.y}",
            likeSplit.center.y - playSplit.center.y > 40f
        )
    }
}

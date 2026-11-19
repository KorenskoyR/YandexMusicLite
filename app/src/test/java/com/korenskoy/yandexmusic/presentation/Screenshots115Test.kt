package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.crossfade
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.auth.DeviceSignIn
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.WaveDiversity
import com.korenskoy.yandexmusic.domain.model.WaveLanguage
import com.korenskoy.yandexmusic.domain.model.WaveMood
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.auth.NoWebViewScreen
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInScreen
import com.korenskoy.yandexmusic.presentation.auth.PhoneSignInState
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSnackbar
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import com.korenskoy.yandexmusic.presentation.main.components.DockBottomGap
import com.korenskoy.yandexmusic.presentation.main.components.DockScrim
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassDock
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
import com.korenskoy.yandexmusic.presentation.main.components.WaveTuneSheet
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem

/**
 * 1.15: «В авто» in a window of any shape — the strip in a low wide window (Carlinkit Ultra2 in CarCar Launcher's
 * lower 40 %, a half of a lying head unit), the narrow window beside a navigator, the full screens unchanged; the
 * explicit "E" and the two-line title.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Screenshots115Test {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .coroutineContext(Dispatchers.Unconfined)
                .components {
                    add(Interceptor { chain -> chain.withRequest(chain.request.newBuilder().crossfade(false).build()).proceed() })
                }
                .build()
        )
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    private fun track(id: String, title: String, artist: String = "Исполнитель", explicit: Boolean = false) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000,
            contentWarning = if (explicit) "explicit" else null)

    private val longExplicit = track("1", "Sorry Seems to Be the Hardest Word (Remastered 2014)", "Elton John", explicit = true)
    private val next = (2..7).map { track("$it", "Песня номер $it", "Кино", explicit = it == 3) }
    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalArtworkTint provides tint) {
                    Box(Modifier.fillMaxSize()) {
                        HiFiBackdrop(tint = tint)
                        content()
                    }
                }
            }
        }
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    private fun snap(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", options)

    @Composable
    private fun Car(isLandscape: Boolean = true, wheelOnLeft: Boolean = true) =
        CompositionLocalProvider(LocalCarClock provides { FIXED_TIME_115 }) {
            CarScreen(
                currentTrack = longExplicit, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = wheelOnLeft,
                progress = { 82_000L }, durationMs = 286_000, upcoming = next, source = PlaylistSource.VIBE,
                onOpenSettings = {}, onVoiceSearch = {}
            )
        }

    @Test
    @Config(qualifiers = "w1550dp-h349dp-land-mdpi")
    fun theStripInALowerFortyPercentWindow() {
        show { Car() }
        snap("v115_car_strip_carlinkit")
    }

    @Test
    @Config(qualifiers = "w1280dp-h360dp-land-mdpi")
    fun theStripInAHalfOfALyingUnit() {
        show { Car() }
        snap("v115_car_strip_half")
    }

    @Test
    @Config(qualifiers = "w1280dp-h360dp-land-mdpi")
    fun theStripMirroredForARightWheel() {
        show { Car(wheelOnLeft = false) }
        snap("v115_car_strip_right")
    }

    @Test
    @Config(qualifiers = "w426dp-h720dp-port-mdpi")
    fun aNarrowWindowBesideTheNavigator() {
        show { Car(isLandscape = false) }
        snap("v115_car_narrow_left")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun aWholeLyingScreenStaysAsItWas() {
        show { Car() }
        snap("v115_car_lying")
    }

    @Test
    @Config(qualifiers = "w1920dp-h720dp-land-mdpi")
    fun aFlatUnitKeepsItsThreeColumns() {
        show { Car() }
        snap("v115_car_three")
    }

    @Test
    @Config(qualifiers = "w587dp-h240dp-land-xxhdpi")
    fun theMonjaroAtOneHundredFiftyPercent() {
        // 1760×720 that says density 2, the interface at 150 %: the whole design in the window, nothing squeezed
        show { Car() }
        snap("v115_car_monjaro_150")
    }

    @Composable
    private fun Wave() = com.korenskoy.yandexmusic.presentation.main.HomeScreen(
        waveTrack = longExplicit, isPlaying = true, isLoading = false, isLiked = true, progress = { 70_000L },
        shelf = com.korenskoy.yandexmusic.presentation.main.waveShelfOf(true, next, emptyList()), shimmer = false,
        onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = null, onShelfClick = {}
    )

    @Test
    @Config(qualifiers = "w560dp-h640dp-port-mdpi")
    fun theWaveInASplitWindow() {
        show { Wave() }
        snap("v115_wave_split")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun theExplicitMarkInTheRows() {
        show {
            Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                (listOf(longExplicit) + next.take(3)).forEach { TrackItem(track = it, isPlaying = false, onClick = {}) }
            }
        }
        snap("v115_explicit_rows")
    }
}

private const val FIXED_TIME_115 = 1_763_000_000_000L

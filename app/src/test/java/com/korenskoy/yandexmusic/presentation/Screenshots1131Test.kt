package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.crossfade
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.LinkState
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.updater.UpdateCheckResult
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.AppDialog
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.update.UpdateDialog
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * 1.13.1 references: the update window keeps its buttons on a short head unit, every window keeps the app's
 * scale, and the car has the settings button of "Моя музыка" next to "back".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1131Test {

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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    // --- Windows keep the app's scale ---

    @Test
    fun windowsKeepTheAppsScale() {
        var dialog = 0f
        var question = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(7f, 1f)) {
                AppDialog(onDismissRequest = {}) { dialog = LocalDensity.current.density }
                HiFiAlertDialog(onDismissRequest = {}, title = "?", confirm = { HiFiDialogAction("OK", {}) }) {
                    question = LocalDensity.current.density
                    Text("")
                }
            }
        }
        compose.waitForIdle()
        assertEquals("a window opened from a scaled screen keeps its scale", 7f, dialog, 0.001f)
        assertEquals(7f, question, 0.001f)
    }

    // --- The update window ---

    private val changelog = """
        • 🚗 «В авто»: обложка играющего трека снова крупная, а под ней кнопка «Очередь» — список следующих треков открывается поверх обложки, нажатие включает трек.
        • 🚗 Свайп по экрану «В авто» переключает ровно один трек.
        • 🛠 Магнитолы на Android 9 без аппаратной отрисовки больше не закрываются при запуске; системные панели всегда тёмные.
        • ⬇️ Плейлисты и альбомы можно скачивать целиком: круглая кнопка рядом со «Слушать» и «Перемешать».
        • 💾 Скачанное можно хранить на флешке (USB).
        • 🎛 Эквалайзеры больше не теряют звук после оповещений навигатора и камер.
    """.trimIndent()

    private fun update(name: String, hasPermission: Boolean = false) {
        show {
            UpdateDialog(
                updateInfo = UpdateCheckResult.UpdateAvailable("1.13.1", "Yandex Music Lite v1.13.1", changelog, "https://github.com/x.apk", 8_400_000L),
                downloadProgress = -1f,
                hasInstallPermission = hasPermission,
                onStartDownload = {}, onInstall = {}, onDismiss = {}, onSkip = {}
            )
        }
        snapScreen(name)
    }

    /** A 1920×720 head unit that reports a high density: 960×360 dp — the window was cut under "Размер файла". */
    @Test
    @Config(qualifiers = SHORT_HEAD_UNIT)
    fun updateOnAShortHeadUnit() = update("v1131_update_short_head")

    @Test
    @Config(qualifiers = "w1280dp-h480dp-land-hdpi")
    fun updateOnAHeadUnit() = update("v1131_update_head", hasPermission = true)

    @Test
    fun updateOnAPhone() = update("v1131_update_phone", hasPermission = true)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun updateOnAPhoneOnItsSide() = update("v1131_update_phone_side")

    // --- The car's settings button ---

    private val track = TrackDetailsDto(id = JsonPrimitive("1"), title = "Первая песня", artists = listOf(ArtistDto(name = "Группа")), durationMs = 215_000)

    @Composable
    private fun Car(isLandscape: Boolean, wheelOnLeft: Boolean = true) =
        CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.WEAK) {
            CarScreen(
                currentTrack = track, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = wheelOnLeft,
                progress = { 82_000L }, durationMs = 286_000, upcoming = listOf(track), source = PlaylistSource.VIBE,
                onOpenSettings = {}
            )
        }

    @Test
    @Config(qualifiers = "+land")
    fun carSettingsButton() {
        show { Car(isLandscape = true) }
        snapScreen("v1131_car_settings")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carSettingsButtonRightWheel() {
        show { Car(isLandscape = true, wheelOnLeft = false) }
        snapScreen("v1131_car_settings_right")
    }

    @Test
    @Config(qualifiers = "w1024dp-h600dp-land-mdpi")
    fun oldHeadUnitCarSettingsButton() {
        show { Car(isLandscape = true) }
        snapScreen("v1131_head_old_car_settings")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarSettingsButton() {
        show { Car(isLandscape = false) }
        snapScreen("v1131_geely_car_settings")
    }

    @Test
    fun phoneCarSettingsButton() {
        show { Car(isLandscape = false) }
        snapScreen("v1131_car_portrait_settings")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun carWithSettingsOpen() {
        show {
            Car(isLandscape = true)
            SettingsSheet(state = SettingsUiState(accountName = "Тестовый Аккаунт", hasPlus = true), actions = SettingsActions(), onDismiss = {})
        }
        snapScreen("v1131_car_settings_open")
    }

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
        const val SHORT_HEAD_UNIT = "w960dp-h360dp-land-xhdpi"
        const val GEELY = "w960dp-h1280dp-port-hdpi"
    }
}

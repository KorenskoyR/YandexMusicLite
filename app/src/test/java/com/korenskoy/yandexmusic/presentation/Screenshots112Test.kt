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

/**
 * 1.12.0 references: the wave's settings and the wave by a track (wave page, player, menu, car), the
 * sign-in by code for devices without WebView — on a small phone, a phone, a phone on its side, head
 * units lying down and the Geely's standing 1440 × 1920 screen. Recorded and checked like [ScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots112Test {

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

    private fun track(id: String, title: String, artist: String = "Исполнитель") =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val tracks = listOf(
        track("1", "Первая песня", "Группа"),
        track("2", "Очень длинное название трека, которое не помещается в одну строку", "Исполнитель с длинным именем"),
        track("3", "Третья песня")
    )
    private val shelfTracks = (2..7).map { track("$it", "Песня номер $it", "Кино") }
    private val playlists = listOf(UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 42, revision = 7))
    private val tuned = WaveTuning(WaveDiversity.DISCOVER, WaveMood.CALM, WaveLanguage.FOREIGN)
    private val seed = track("9", "Звезда по имени Солнце", "Кино")
    private val longSeed = track("10", "Очень длинное название трека, по которому запущена волна", "Кино")
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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    // --- The wave page ---

    @Composable
    private fun Wave(tuning: WaveTuning = tuned, seedTrack: TrackDetailsDto? = null, contentBottom: androidx.compose.ui.unit.Dp = 100.dp) {
        HomeScreen(
            waveTrack = tracks[0], isPlaying = true, isLoading = false, isLiked = true, progress = { 70_000L },
            shelf = waveShelfOf(true, shelfTracks, emptyList()), shimmer = false,
            onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {},
            contentBottom = contentBottom, tuning = tuning, trackWaveSeed = seedTrack
        )
    }

    @Composable
    private fun Docked(page: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            page()
            DockScrim(Modifier.align(Alignment.BottomCenter))
            FloatingGlassDock(
                currentRoute = "home", onNavigate = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = DockBottomGap),
                player = {}
            )
        }
    }

    @Test
    fun waveDefaultTuning() {
        show { Wave(tuning = WaveTuning()) }
        snap("v112_wave_default")
    }

    @Test
    fun waveTuned() {
        show { Wave() }
        snap("v112_wave_tuned")
    }

    @Test
    fun waveByTrackWeakLink() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Wave(seedTrack = longSeed) } }
        snap("v112_wave_by_track_weak")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun waveTunedSmallPhone() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Wave() } }
        snap("v112_small_wave_tuned")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun waveByTrackSmallPhone() {
        show { Wave(seedTrack = longSeed) }
        snap("v112_small_wave_by_track")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun headUnitWaveTuned() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Docked { Wave(contentBottom = DockClearance) } } }
        snap("v112_head_wave_tuned")
    }

    @Test
    @Config(qualifiers = OLD_HEAD_UNIT)
    fun oldHeadUnitWaveByTrack() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Docked { Wave(seedTrack = longSeed, contentBottom = DockClearance) } } }
        snap("v112_head_old_wave_by_track")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun phoneOnItsSideWaveByTrack() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Docked { Wave(seedTrack = longSeed, contentBottom = DockClearance) } } }
        snap("v112_phone_side_wave_by_track")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyWave() {
        show { Wave() }
        snap("v112_geely_wave")
    }

    // --- "Настроить волну" ---

    @Test
    fun waveTuneSheet() {
        show { WaveTuneSheet(tuned, onChange = {}, onDismiss = {}) }
        snapScreen("v112_wave_tune_sheet")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun waveTuneSheetSmallPhone() {
        show { WaveTuneSheet(tuned, onChange = {}, onDismiss = {}) }
        snapScreen("v112_small_wave_tune_sheet")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun waveTuneSheetHeadUnit() {
        show { Docked { Wave(contentBottom = DockClearance) }; WaveTuneSheet(tuned, onChange = {}, onDismiss = {}) }
        snapScreen("v112_head_wave_tune_sheet")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun waveTuneSheetPhoneOnItsSide() {
        show { WaveTuneSheet(tuned, onChange = {}, onDismiss = {}) }
        snapScreen("v112_phone_side_wave_tune_sheet")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun waveTuneSheetGeely() {
        show { WaveTuneSheet(tuned, onChange = {}, onDismiss = {}) }
        snapScreen("v112_geely_wave_tune_sheet")
    }

    // --- Player, track menu, snackbar ---

    @Composable
    private fun Player() {
        CompositionLocalProvider(com.korenskoy.yandexmusic.utils.LocalStreamQuality provides "MP3 · 320 кбит/с") {
            FullScreenPlayer(
                track = tracks[1], isPlaying = true, isLiked = true, progress = { 82_000L }, queue = tracks,
                onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
                onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
                sourceLabel = "Моя волна", onTrackWave = {}
            )
        }
    }

    @Test
    fun playerWithTrackWave() {
        show { CompositionLocalProvider(LocalLinkState provides LinkState.WEAK) { Player() } }
        snap("v112_player_track_wave")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun playerWithTrackWaveSmallPhone() {
        show { Player() }
        snap("v112_small_player_track_wave")
    }

    @Test
    @Config(qualifiers = "+land")
    fun playerWithTrackWaveLandscape() {
        show { Player() }
        snap("v112_player_track_wave_land")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun playerWithTrackWaveHeadUnit() {
        show { Player() }
        snap("v112_head_player_track_wave")
    }

    @Test
    fun trackMenuWithTrackWave() {
        show {
            TrackContextMenu(
                track = tracks[0].copy(
                    artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Группа")),
                    albums = listOf(com.korenskoy.yandexmusic.data.dto.TrackAlbumDto(id = JsonPrimitive(2), title = "Группа крови"))
                ), inPlaylist = playlists[0], playlists = playlists, isLiked = false,
                startWithPlaylists = false, onDismiss = {}, onPlayNext = {}, onToggleLike = {}, onAddTo = {},
                onCreatePlaylist = {}, onRemoveFromPlaylist = {}, onOpenArtist = {}, onOpenAlbum = {}, onTrackWave = {}
            )
        }
        snapScreen("v112_track_menu_wave")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun snackbarWithUndo() {
        show {
            Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
                HiFiSnackbar("Волна по треку «Очень длинное название трека, по которому запущена волна»", actionLabel = "Отменить")
                androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                HiFiSnackbar("Волна по треку «Кукушка»", actionLabel = "Отменить")
                androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                HiFiSnackbar("Не удалось сохранить настройки волны")
            }
        }
        snap("v112_snackbar_undo")
    }

    // --- В авто ---

    @Composable
    private fun Car(
        isLandscape: Boolean,
        wheelOnLeft: Boolean = true,
        tuneOpen: Boolean = false,
        seedTrack: TrackDetailsDto? = null,
        clock: Boolean = true,
        tuning: WaveTuning = tuned,
        next: List<TrackDetailsDto> = listOf(tracks[2], tracks[1], tracks[0]),
        loading: Boolean = false,
        source: PlaylistSource = PlaylistSource.VIBE,
        current: TrackDetailsDto = tracks[0]
    ) = CompositionLocalProvider(LocalCarClock provides { FIXED_TIME }, LocalLinkState provides LinkState.WEAK) {
        CarScreen(
            currentTrack = current, isPlaying = true, likedTrackIds = setOf("1"), onPlayPauseClick = {}, onNextClick = {},
            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
            onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = isLandscape, steeringWheelOnLeft = wheelOnLeft,
            progress = { 82_000L }, durationMs = 286_000, upcoming = next, upcomingLoading = loading, source = source,
            showClock = clock, waveTuning = tuning, trackWaveSeed = seedTrack, initialTuneOpen = tuneOpen
        )
    }

    @Test
    @Config(qualifiers = "+land")
    fun carWavePills() {
        show { Car(isLandscape = true) }
        snap("v112_car_pills")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carTuneOpenLeftWheel() {
        show { Car(isLandscape = true, tuneOpen = true) }
        snap("v112_car_tune_left")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carTuneOpenRightWheel() {
        show { Car(isLandscape = true, wheelOnLeft = false, tuneOpen = true) }
        snap("v112_car_tune_right")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carByTrackNoClock() {
        show { Car(isLandscape = true, seedTrack = longSeed, clock = false) }
        snap("v112_car_by_track_no_clock")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carByTrackWithClock() {
        show { Car(isLandscape = true, seedTrack = longSeed) }
        snap("v112_car_by_track")
    }

    @Test
    fun carPortraitTuneOpen() {
        show { Car(isLandscape = false, tuneOpen = true) }
        snap("v112_car_portrait_tune")
    }

    @Test
    fun carPortraitPills() {
        show { Car(isLandscape = false, seedTrack = seed) }
        snap("v112_car_portrait_by_track")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun headUnitCarTuneOpen() {
        show { Car(isLandscape = true, tuneOpen = true) }
        snap("v112_head_car_tune")
    }

    @Test
    @Config(qualifiers = OLD_HEAD_UNIT)
    fun oldHeadUnitCarPills() {
        show { Car(isLandscape = true, seedTrack = longSeed) }
        snap("v112_head_old_car_by_track")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCar() {
        show { Car(isLandscape = false) }
        snap("v112_geely_car")
    }

    /** A standing 768 × 1024 screen (tablet-like head units), with the clock hidden. */
    @Test
    @Config(qualifiers = "w768dp-h1024dp-port-mdpi")
    fun tallHeadUnitCar() {
        show { Car(isLandscape = false, seedTrack = longSeed, clock = false) }
        snap("v112_tall_car_by_track")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhoneCarTuneOpen() {
        show { Car(isLandscape = false, tuneOpen = true) }
        snap("v112_small_car_tune")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarTuneOpen() {
        show { Car(isLandscape = false, tuneOpen = true) }
        snap("v112_geely_car_tune")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carLandscapeScaledTuneOpen() {
        show {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * 1.4f, base.fontScale)) {
                Car(isLandscape = true, tuneOpen = true)
            }
        }
        snap("v112_car_scaled_tune")
    }

    // --- 1.12.2: "Далее" as a row of covers ---

    private val radio = tracks[0].copy(id = JsonPrimitive("radio_europa"), title = "Европа Плюс", isRadio = true)

    @Test
    @Config(qualifiers = "+land")
    fun carNextLoading() {
        show { Car(isLandscape = true, next = emptyList(), loading = true) }
        snap("v1122_car_next_loading")
    }

    @Test
    @Config(qualifiers = "+land")
    fun carOneNextFromAPlaylist() {
        show { Car(isLandscape = true, next = listOf(tracks[1]), source = PlaylistSource.LIBRARY) }
        snap("v1122_car_one_next")
    }

    @Test
    @Config(qualifiers = OLD_HEAD_UNIT)
    fun oldHeadUnitCarNext() {
        show { Car(isLandscape = true, wheelOnLeft = false) }
        snap("v1122_head_old_car_next")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun phoneOnItsSideCarNext() {
        show { Car(isLandscape = true) }
        snap("v1122_phone_side_car_next")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarRadio() {
        show { Car(isLandscape = false, current = radio, next = emptyList(), source = PlaylistSource.RADIO) }
        snap("v1122_geely_car_radio")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun geelyCarNextLoading() {
        show { Car(isLandscape = false, next = emptyList(), loading = true, clock = false) }
        snap("v1122_geely_car_loading")
    }

    // --- Sign-in by code (no WebView) ---

    private fun waiting() = PhoneSignInState.Waiting(
        DeviceSignIn.Code("dev", "6iyqf7ik", "https://ya.ru/device", 5, 300),
        expiresAt = SystemClock.elapsedRealtime() + 272_000L
    )

    @Test
    fun phoneSignIn() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_sign_in_code")
    }

    @Test
    fun phoneSignInLargeFont() {
        show {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) { PhoneSignInScreen(waiting(), onNewCode = {}) }
        }
        snap("v112_sign_in_code_large_font")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun phoneSignInSmallPhone() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_small_sign_in_code")
    }

    @Test
    fun phoneSignInGettingCode() {
        show { PhoneSignInScreen(PhoneSignInState.GettingCode, onNewCode = {}) }
        snap("v112_sign_in_getting_code")
    }

    @Test
    fun phoneSignInExpired() {
        show { PhoneSignInScreen(PhoneSignInState.Expired, onNewCode = {}) }
        snap("v112_sign_in_expired")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun phoneSignInPhoneOnItsSide() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_phone_side_sign_in_code")
    }

    @Test
    @Config(qualifiers = HEAD_UNIT)
    fun phoneSignInHeadUnit() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_head_sign_in_code")
    }

    @Test
    @Config(qualifiers = OLD_HEAD_UNIT)
    fun phoneSignInOldHeadUnit() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_head_old_sign_in_code")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun phoneSignInGeely() {
        show { PhoneSignInScreen(waiting(), onNewCode = {}) }
        snap("v112_geely_sign_in_code")
    }

    @Test
    fun noWebView() {
        show { NoWebViewScreen(note = null, onRetry = {}) }
        snap("v112_no_webview")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun noWebViewSmallPhoneStillMissing() {
        show { NoWebViewScreen(note = "WebView всё ещё недоступен", onRetry = {}) }
        snap("v112_small_no_webview")
    }

    @Test
    @Config(qualifiers = GEELY)
    fun noWebViewGeely() {
        show { NoWebViewScreen(note = null, onRetry = {}) }
        snap("v112_geely_no_webview")
    }

    @Test
    @Config(qualifiers = PHONE_ON_SIDE)
    fun noWebViewPhoneOnItsSide() {
        show { NoWebViewScreen(note = "WebView всё ещё недоступен", onRetry = {}) }
        snap("v112_phone_side_no_webview")
    }

    private companion object {
        const val FIXED_TIME = 1_792_567_800_000L
        const val SMALL_PHONE = "w360dp-h640dp-port-xhdpi"
        const val HEAD_UNIT = "w1280dp-h720dp-land-mdpi"
        const val OLD_HEAD_UNIT = "w1024dp-h600dp-land-mdpi"
        const val PHONE_ON_SIDE = "w800dp-h360dp-land-xhdpi"
        /** The Geely Galaxy L7 head unit: 1440 × 1920 standing, 240 dpi. */
        const val GEELY = "w960dp-h1280dp-port-hdpi"
    }
}

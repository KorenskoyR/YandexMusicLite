package com.korenskoy.yandexmusic

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.TintMath
import com.korenskoy.yandexmusic.presentation.main.HomeScreen
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.SearchScreen
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenu
import com.korenskoy.yandexmusic.presentation.main.components.MainTab
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.TrackMenuLauncher
import com.korenskoy.yandexmusic.presentation.main.waveShelfOf
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import com.korenskoy.yandexmusic.utils.LocalStreamQuality
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The screenshots of the README and the website: the app's real screens, drawn by the real renderer,
 * with invented tracks and generated covers (nobody's artwork gets published). Not a check, it asserts
 * nothing. Run this class on an emulator (`am instrument -e class ...PromoScreenshots`, System UI demo mode
 * for a clean status bar), then pull /data/local/tmp/promo_NAME.png into docs/assets.
 */
@RunWith(AndroidJUnit4::class)
class PromoScreenshots {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val coversDir = File(context.cacheDir, "promo_covers").apply { mkdirs() }

    /** Invented titles and artists, with a generated cover. */
    private fun track(id: Int, title: String, artist: String, style: Int, hue: Float) = TrackDetailsDto(
        id = JsonPrimitive(id.toString()),
        title = title,
        artists = listOf(ArtistDto(name = artist)),
        durationMs = 215_000L + id * 7_000L,
        coverUri = cover("t$id", style, hue)
    )

    private val tracks by lazy {
        listOf(
            track(1, "Северное сияние", "Лунный Берег", 0, 200f),
            track(2, "Ночной экспресс", "Драйв Экспресс", 1, 280f),
            track(3, "Лазурный бриз", "Михаил Бережной", 2, 190f),
            track(4, "Эхо заката", "Soundwave Pro", 0, 20f),
            track(5, "Тайный город", "Северный Ветер", 3, 320f),
            track(6, "Линия горизонта", "Голос Ветра", 4, 170f),
            track(7, "Digital Mirage", "Vex Sound", 1, 45f),
            track(8, "Neon Echoes", "Avenix", 3, 300f),
            track(9, "Silent Orbit", "Kallisto", 2, 230f),
            track(10, "Astral Dawn", "Luminary", 4, 260f),
            track(11, "Гравитация", "V.O.R.T.E.X.", 1, 350f),
            track(12, "Бас на максимум", "MC Гранит", 3, 140f)
        )
    }
    private val liked by lazy { tracks.filterIndexed { i, _ -> i % 3 != 1 }.map { it.idString }.toSet() }
    private val nowPlaying by lazy { tracks[0] }

    /** The app's tint for the cover of [nowPlaying]. */
    private val tint = ArtworkTint(Color(0xFF2F7FA8), Color(0xFF1B2F6B))

    /** The phone screens need a portrait display; the car's a landscape one. */
    private fun phoneOnly() {
        val display = context.resources.displayMetrics
        assumeTrue("needs a portrait display", display.widthPixels < display.heightPixels)
    }

    @Test
    fun wave() = shoot("wave") {
        HomeScreen(
            waveTrack = nowPlaying, isPlaying = true, isLoading = false, isLiked = true, progress = { 82_000L },
            shelf = waveShelfOf(true, tracks.drop(1).take(6), emptyList()), shimmer = false,
            onPlayPause = {}, onLike = {}, onDislike = {}, onOpenPlayer = {}, onShelfClick = {}
        )
        BottomChrome(MainTab.HOME, mini = false)
    }

    @Test
    fun player() = shoot("player", settleMs = 3_500) { Player(queueOpen = false) }

    @Test
    fun queue() = shoot("queue", settleMs = 3_500) { Player(queueOpen = true) }

    @Test
    fun search() = shoot("search") {
        SearchScreen(
            query = "", onQueryChange = {}, results = emptyList(), weeklyHits = tracks.drop(2).take(6), trendTracks = tracks.drop(4),
            isSearching = false, currentTrackId = nowPlaying.idString, isPlaying = true, likedTrackIds = liked,
            onTrackClick = { _, _ -> }, onToggleLike = {}, hasActiveTrack = true
        )
        BottomChrome(MainTab.SEARCH, mini = true)
    }

    @Test
    fun library() = shoot("library") {
        val playlists = listOf(
            UserPlaylistDto(kind = JsonPrimitive(1), title = "В дорогу", trackCount = 42, revision = 1, ogImage = cover("p1", 4, 30f)),
            UserPlaylistDto(kind = JsonPrimitive(2), title = "Спокойное", trackCount = 18, revision = 1, ogImage = cover("p2", 2, 200f))
        )
        LibraryScreen(
            tracks = tracks.drop(1), isLoading = false, currentTrackId = nowPlaying.idString, isPlaying = true, likedTrackIds = liked, downloadedTrackIds = setOf("2", "3", "4", "7"),
            downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
            onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {},
            hasActiveTrack = true, playlists = playlists
        )
        BottomChrome(MainTab.LIBRARY, mini = true)
    }

    @Test
    fun radio() {
        val stations = RadioRepository(context).getBuiltinStations()
        val station = stations.first { it.id == "record" }
        val stationTint = ArtworkTint(
            Color(TintMath.normalize(station.brandColorStart.toInt())),
            Color(TintMath.normalize(station.brandColorEnd.toInt()))
        )
        shoot("radio", appTint = stationTint, backdropCover = null) {
            RadioScreen(
                stations = stations, allStations = stations, selectedCategory = RadioCategory.ALL,
                onCategorySelect = {}, currentStationId = station.id, isPlaying = true,
                favoriteStationIds = stations.take(4).map { it.id }.toSet(),
                onStationClick = {}, onToggleFavorite = {}, onPlayPause = {}, hasActiveTrack = false
            )
            BottomChrome(MainTab.RADIO, mini = false)
        }
    }

    @Test
    fun settings() = shoot("settings") {
        com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet(
            state = com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState(
                accountName = "Алексей", hasPlus = true, crossfadeSeconds = 3, autoDownload = true, cacheSizeBytes = 1_288_000_000L
            ),
            actions = com.korenskoy.yandexmusic.presentation.main.components.SettingsActions(),
            onDismiss = {}
        )
    }

    /**
     * The head unit's view, controls on the driver's side. Needs a landscape display, the way a head unit is:
     * `wm size 2560x1440; wm density 480` before running it, `wm size reset; wm density reset` after.
     */
    @Test
    fun car() {
        val display = context.resources.displayMetrics
        assumeTrue("needs a landscape display", display.widthPixels > display.heightPixels)
        val noon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 45)
        }.timeInMillis
        shoot("car", settleMs = 3_500, backdropCover = null) {
            CompositionLocalProvider(LocalCarClock provides { noon }) {
                CarScreen(
                    currentTrack = nowPlaying, isPlaying = true, likedTrackIds = liked,
                    onPlayPauseClick = {}, onNextClick = {}, onPrevClick = {}, onToggleLike = {}, onDislikeClick = {},
                    onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {}, onPlayDownloaded = {}, onPlayRadio = {}, onExit = {},
                    isLandscape = true, steeringWheelOnLeft = true, progress = { 82_000L }, durationMs = 286_000,
                    upcoming = tracks.drop(1).take(3), source = PlaylistSource.VIBE
                )
            }
        }
    }

    @Composable
    private fun Player(queueOpen: Boolean) {
        FullScreenPlayer(
            track = nowPlaying, isPlaying = true, isLiked = true, progress = { 82_000L }, queue = tracks,
            onPlayPauseClick = {}, onToggleLike = {}, onNextClick = {}, onPrevClick = {}, onSeekTo = {},
            onTrackSelect = {}, onRemoveFromQueue = {}, onCollapse = {}, onCloseAndClear = {}, onDislikeClick = {},
            sourceLabel = "Моя волна", initialQueueOpen = queueOpen
        )
    }

    /** The mini-player and the tab bar, placed as on the main screen. */
    @Composable
    private fun BoxScope.BottomChrome(tab: MainTab, mini: Boolean) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mini) {
                MiniPlayer(
                    track = nowPlaying, isPlaying = true, progress = { 82_000L },
                    onPlayPauseClick = {}, onNextClick = {}, onClick = {}, onDismiss = {}
                )
                Spacer(Modifier.height(10.dp))
            }
            FloatingGlassBottomBar(currentRoute = tab.route, onNavigate = {})
        }
    }

    private fun shoot(
        name: String,
        settleMs: Long = 2_500,
        appTint: ArtworkTint = tint,
        backdropCover: String? = nowPlaying.coverUri,
        content: @Composable BoxScope.() -> Unit
    ) {
        if (name != "car") phoneOnly()
        // Started directly: ActivityScenario waits for the main thread to go idle, and with the spectrum
        // and the timeline animating it never does
        val intent = Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ComponentActivity
        instrumentation.runOnMainSync {
            activity.enableEdgeToEdge()
            activity.setContent {
                YandexMusicTheme {
                    CompositionLocalProvider(
                        LocalArtworkTint provides appTint,
                        LocalHiFiLite provides false,
                        // A quality every account gets from the regular stream
                        LocalStreamQuality provides "MP3 · 192 кбит/с",
                        LocalTrackMenu provides TrackMenuLauncher { _, _ -> }
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            HiFiBackdrop(coverUrl = backdropCover)
                            content()
                        }
                    }
                }
            }
        }
        Thread.sleep(settleMs)
        instrumentation.uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/promo_$name.png")
            .use { fd -> FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
        instrumentation.runOnMainSync { activity.finish() }
        Thread.sleep(600)
    }

    /** A generated cover (nobody's artwork), saved once; returns its file:// address. */
    private fun cover(name: String, style: Int, hue: Float): String {
        val file = File(coversDir, "$name.png")
        if (!file.exists()) file.outputStream().use { drawCover(style, hue, name.hashCode()).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://${file.absolutePath}"
    }

    private fun color(hue: Float, saturation: Float, value: Float): Int =
        android.graphics.Color.HSVToColor(floatArrayOf((hue + 360f) % 360f, saturation, value))

    private fun drawCover(style: Int, hue: Float, seed: Int): Bitmap {
        val size = 1000f
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(seed)
        paint.shader = LinearGradient(0f, 0f, size * 0.4f, size, color(hue, 0.75f, 0.95f), color(hue + 50f, 0.85f, 0.25f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, paint)
        paint.shader = null
        when (style) {
            // A low sun over layered waves
            0 -> {
                paint.shader = RadialGradient(size / 2, size * 0.52f, size * 0.28f, color(hue + 40f, 0.25f, 1f), color(hue + 20f, 0.7f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(size / 2, size * 0.52f, size * 0.26f, paint)
                paint.shader = null
                for (layer in 0 until 4) {
                    val top = size * (0.6f + layer * 0.1f)
                    val path = Path().apply {
                        moveTo(0f, size)
                        var x = 0f
                        while (x <= size) {
                            lineTo(x, top + sin(x / (120f + layer * 30f) + layer * 1.3f).toFloat() * 26f)
                            x += 10f
                        }
                        lineTo(size, size)
                        close()
                    }
                    paint.color = color(hue + 60f + layer * 8f, 0.8f, 0.5f - layer * 0.1f)
                    canvas.drawPath(path, paint)
                }
            }
            // Concentric rings, off-centre
            1 -> {
                paint.style = Paint.Style.STROKE
                val cx = size * (0.3f + random.nextFloat() * 0.4f)
                val cy = size * (0.3f + random.nextFloat() * 0.4f)
                for (ring in 1..14) {
                    paint.strokeWidth = 8f + ring * 1.6f
                    paint.color = color(hue + ring * 7f, 0.55f, 1f)
                    paint.alpha = 240 - ring * 14
                    canvas.drawCircle(cx, cy, ring * 66f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.alpha = 255
            }
            // Soft overlapping lights
            2 -> repeat(7) {
                val x = random.nextFloat() * size
                val y = random.nextFloat() * size
                val r = size * (0.22f + random.nextFloat() * 0.3f)
                val tone = color(hue + random.nextFloat() * 90f - 30f, 0.65f, 1f)
                paint.shader = RadialGradient(x, y, r, tone, tone and 0x00FFFFFF, Shader.TileMode.CLAMP)
                canvas.drawCircle(x, y, r, paint)
                paint.shader = null
            }
            // A rosette of petals
            3 -> {
                val petals = 12
                for (i in 0 until petals) {
                    val angle = i * 2 * Math.PI / petals
                    val x = size / 2 + cos(angle).toFloat() * size * 0.24f
                    val y = size / 2 + sin(angle).toFloat() * size * 0.24f
                    paint.color = color(hue + i * 9f, 0.6f, 1f)
                    paint.alpha = 150
                    canvas.drawCircle(x, y, size * 0.2f, paint)
                }
                paint.alpha = 255
                paint.color = color(hue + 180f, 0.3f, 0.12f)
                canvas.drawCircle(size / 2, size / 2, size * 0.07f, paint)
            }
            // Mountain ridges
            else -> for (layer in 0 until 5) {
                val base = size * (0.42f + layer * 0.12f)
                val path = Path().apply {
                    moveTo(0f, size)
                    var x = 0f
                    while (x <= size + 90f) {
                        lineTo(x, base - random.nextFloat() * 130f)
                        x += 60f + random.nextFloat() * 70f
                    }
                    lineTo(size, size)
                    close()
                }
                paint.color = color(hue + layer * 12f, 0.5f + layer * 0.07f, 0.8f - layer * 0.14f)
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}

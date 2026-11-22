package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * How big the buttons of «В авто» really are, in millimetres, on the screens people complain from: the Monjaro at
 * 100/150/200 %, a 10″ 1280×720 unit full screen and split 50/50, 40/60, 30/70, a 9″ 1024×600. Not a check: a
 * report (build/carsize/report.txt and a picture of each), to compare layouts by numbers. Runs with -PcarSizeReport.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CarSizeReport(
    private val name: String,
    private val widthPx: Int,
    private val heightPx: Int,
    private val systemDpi: Int,
    private val realDpi: Float,
    private val scale: Float
) {
    private val density = systemDpi / 160f
    private val qualifiers = "w${(widthPx / density).toInt()}dp-h${(heightPx / density).toInt()}dp-" +
        (if (widthPx >= heightPx) "land" else "port") + "-${systemDpi}dpi"

    @get:Rule(order = 0)
    val size = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                RuntimeEnvironment.setQualifiers(qualifiers)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun report() {
        if (System.getProperty("carSizeReport") != "true") return
        val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))
        val track = TrackDetailsDto(id = JsonPrimitive("2"), title = "Растафарай", artists = listOf(ArtistDto(name = "VALID, Вектор А")), durationMs = 215_000)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density * scale, system.fontScale)) {
                YandexMusicTheme {
                    CompositionLocalProvider(LocalArtworkTint provides tint, LocalCarClock provides { 1_792_567_800_000L }) {
                        Box(Modifier.fillMaxSize()) {
                            HiFiBackdrop(tint = tint)
                            val landscape = widthPx > heightPx
                            CarScreen(
                                currentTrack = track, isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
                                onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                                onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = landscape, progress = { 82_000L },
                                durationMs = 215_000, onOpenSettings = {}, onVoiceSearch = {}
                            )
                        }
                    }
                }
            }
        }
        repeat(25) {
            compose.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }).fetchSemanticsNodes()
        fun mm(px: Float) = px / realDpi * 25.4f
        val wanted = linkedMapOf(
            "Пуск" to "Воспроизведение или пауза", "Назад" to "Предыдущий трек", "Вперёд" to "Следующий трек",
            "Лайк" to "Нравится", "Дизлайк" to "Не нравится", "Настройки" to "Настройки", "Выход" to "Выйти из режима В авто"
        )
        val line = StringBuilder("$name ${widthPx}x$heightPx @${systemDpi}dpi (real ${realDpi.toInt()}) ×$scale:")
        for ((short, label) in wanted) {
            val node = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == label }
            line.append("  $short ").append(node?.let { "%.1f".format(mm(minOf(it.size.width, it.size.height).toFloat())) } ?: "—")
        }
        // The sources: the first of the dock
        val source = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == "Любимое" }
        line.append("  Источник ").append(source?.let { "%.1f×%.1f".format(mm(it.size.width.toFloat()), mm(it.size.height.toFloat())) } ?: "—")
        line.append(" мм")
        val dir = File("build/carsize").apply { mkdirs() }
        File(dir, "report.txt").appendText(line.toString() + "\n")
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage(
            "build/carsize/$name.png",
            com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record)
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // Geely Monjaro 12.3″ 1760×720, ≈155 dpi real; the system says 320 (scale "auto" makes it ≈198)
            arrayOf("monjaro_100", 1760, 720, 160, 155f, 1f),
            arrayOf("monjaro_150", 1760, 720, 160, 155f, 1.5f),
            arrayOf("monjaro_200", 1760, 720, 160, 155f, 2f),
            // A 10″ 1280×720 unit (the VW Golf's Q8MAX and most aftermarket ones), ≈147 dpi
            arrayOf("unit10_full", 1280, 720, 160, 147f, 1f),
            arrayOf("unit10_full_150", 1280, 720, 160, 147f, 1.5f),
            arrayOf("unit10_split50", 640, 720, 160, 147f, 1f),
            arrayOf("unit10_split40", 512, 720, 160, 147f, 1f),
            arrayOf("unit10_split60", 768, 720, 160, 147f, 1f),
            arrayOf("unit10_split30", 384, 720, 160, 147f, 1f),
            arrayOf("unit10_split70", 896, 720, 160, 147f, 1f),
            // A 9″ 1024×600, ≈132 dpi
            arrayOf("unit9_full", 1024, 600, 160, 132f, 1f),
            arrayOf("unit9_full_150", 1024, 600, 160, 132f, 1.5f),
            // A phone in the holder, 1080×2400 at 420, 6.5″ ≈ 405 dpi real
            arrayOf("phone_side", 2400, 1080, 420, 405f, 1f)
        )
    }
}

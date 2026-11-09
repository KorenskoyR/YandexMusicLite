package com.korenskoy.yandexmusic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Glass controls on a real (hardware) renderer, where shadows are drawn — Robolectric screenshots don't
 * draw them. Under a translucent glass surface a platform elevation shadow showed through as a dark band
 * along the bottom edge. Each surface sits on a flat background here, so inside the shape there must be
 * exactly the glass fill composited over that background, at the top edge as well as at the bottom.
 */
@RunWith(AndroidJUnit4::class)
class GlassSurfacesInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val track = TrackDetailsDto(id = JsonPrimitive("1"), title = "Первая песня", artists = listOf(ArtistDto(name = "Группа")), durationMs = 215_000)
    private val ground = Color(0xFF2B3550)

    @Test
    fun glassSurfacesHaveNoShadowBandInside() {
        compose.setContent {
            YandexMusicTheme {
                Column(
                    Modifier.fillMaxSize().background(ground).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Box(Modifier.testTag("mini")) {
                        MiniPlayer(track = track, isPlaying = false, progress = { 0L }, onPlayPauseClick = {}, onNextClick = {}, onClick = {}, onDismiss = {})
                    }
                    // "Волна" selected: its highlight stays at the left end, away from the point measured at 42 %
                    Box(Modifier.testTag("bar")) { FloatingGlassBottomBar(currentRoute = "home", onNavigate = {}) }
                    Box(Modifier.testTag("track").fillMaxWidth()) { TrackItem(track = track, isPlaying = false, isLiked = true, onClick = {}) }
                    Box(Modifier.testTag("round")) {
                        GlassCircleButton(onClick = {}, contentDescription = "x", size = 56.dp) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.Transparent)
                        }
                    }
                    Box(Modifier.testTag("segmented").width(300.dp).height(44.dp)) {
                        HiFiSegmented(options = listOf("", ""), selectedIndex = 0, onSelect = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "glass").apply { mkdirs() }
        // What the glass must look like over the flat ground: its fill composited, nothing else
        val barGlass = composite(HiFi.BarFill, ground)
        val roundGlass = composite(HiFi.GlassFill, ground)
        for ((tag, expected) in listOf("mini" to barGlass, "bar" to barGlass, "round" to roundGlass)) {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            File(dir, "$tag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Just inside the top and the bottom edge, left of the middle (away from text, icons and corners)
            val x = (bitmap.width * 0.42f).toInt()
            for (y in listOf((bitmap.height * 0.12f).toInt(), (bitmap.height * 0.88f).toInt())) {
                val c = bitmap.getPixel(x, y)
                val diff = maxOf(abs(((c shr 16) and 0xFF) - expected[0]), abs(((c shr 8) and 0xFF) - expected[1]), abs((c and 0xFF) - expected[2]))
                assertTrue("$tag at y=$y: ${Integer.toHexString(c)} instead of the glass colour — a shadow shows through", diff <= 2)
            }
        }
    }

    private fun composite(fill: Color, under: Color): IntArray {
        val a = fill.alpha
        fun ch(f: Float, u: Float) = Math.round((f * a + u * (1 - a)) * 255f)
        return intArrayOf(ch(fill.red, under.red), ch(fill.green, under.green), ch(fill.blue, under.blue))
    }
}

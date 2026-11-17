package com.korenskoy.yandexmusic.utils

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VW Golf (Q8MAX, 1280×720), 1.14.10: from a third of the screen to the whole of it, the lying layout showed, and
 * half a second later the standing one on the lying screen — the unit sent a late configuration that said
 * "portrait". Lying or standing is the window's own size now; the configuration only before the window has one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LandscapeWindowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun window(width: Int, height: Int) = object : WindowInfo {
        override val isWindowFocused = true
        override val containerSize = IntSize(width, height)
    }

    private fun show(windowWidth: Int, windowHeight: Int, orientation: Int) {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { this.orientation = orientation }
            CompositionLocalProvider(LocalConfiguration provides config, LocalWindowInfo provides window(windowWidth, windowHeight)) {
                Text(if (isLandscapeWindow()) "lying" else "standing")
            }
        }
    }

    @Test
    fun aLateSaysPortraitDoesNotStandTheLyingScreenUp() {
        show(1280, 720, Configuration.ORIENTATION_PORTRAIT)
        compose.onNodeWithText("lying").assertExists()
    }

    @Test
    fun aThirdOfTheScreenStands() {
        // Beside the navigator: a narrow tall window on a lying screen
        show(426, 720, Configuration.ORIENTATION_LANDSCAPE)
        compose.onNodeWithText("standing").assertExists()
    }

    @Test
    fun beforeTheWindowHasASizeTheConfigurationSays() {
        show(0, 0, Configuration.ORIENTATION_LANDSCAPE)
        compose.onNodeWithText("lying").assertExists()
    }
}

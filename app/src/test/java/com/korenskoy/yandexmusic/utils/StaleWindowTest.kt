package com.korenskoy.yandexmusic.utils

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A VW Golf unit (Q8MAX, Android 15, 1280×720) with left-hand drive: its launcher keeps the app in a small standing
 * card, and a second after the app is opened full screen the system speaks of that card again. The pages follow the
 * size the app really draws in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h720dp-port-mdpi")
class StaleWindowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun drawnLyingWhileTheSystemSaysStandingIsLying() {
        var landscape: Boolean? = null
        var size: WindowSizeDp? = null
        compose.setContent {
            // The system: the 420×720 card; the app: drawn on the whole 1280×720
            CompositionLocalProvider(LocalAppWindowSize provides IntSize(1280, 720)) {
                landscape = isLandscapeWindow()
                size = currentWindowSizeDp()
            }
        }
        compose.waitForIdle()
        assertTrue(landscape!!)
        assertEquals(WindowSizeDp(1280, 720), size)
    }

    @Test
    fun withoutTheRootTheSystemsWordStands() {
        var landscape: Boolean? = null
        compose.setContent { landscape = isLandscapeWindow() }
        compose.waitForIdle()
        assertFalse(landscape!!)
    }
}

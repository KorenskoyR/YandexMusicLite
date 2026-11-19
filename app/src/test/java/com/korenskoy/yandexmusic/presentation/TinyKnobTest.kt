package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A free window dragged down to its smallest squeezes the knobs for a moment to a pixel or two: drawn, they are
 * not a crash ("ending radius must be > 0", Android 15, a free window of 386×352).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class TinyKnobTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aKnobOfAlmostNothingIsDrawnWithoutACrash() {
        compose.setContent {
            Row {
                for (size in listOf(0.5f, 1f, 1.5f, 2f, 3f, 4f, 48f)) HiFiKnob(onClick = {}, contentDescription = "k$size", size = size.dp) {}
            }
        }
        compose.onRoot().captureToImage()
    }
}

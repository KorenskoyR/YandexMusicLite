package com.korenskoy.yandexmusic.presentation

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiPalette
import com.korenskoy.yandexmusic.presentation.hifi.applyWindowSystemBars
import com.korenskoy.yandexmusic.ui.theme.HiFiAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UiDesignAndFullscreenTest {

    private fun isAccentBlue(color: androidx.compose.ui.graphics.Color): Boolean =
        color.blue > color.red + 0.15f && color.blue > color.green + 0.10f

    private fun mainJavaDir(): File {
        val candidates = listOf(
            File("src/main/java/com/korenskoy/yandexmusic"),
            File("app/src/main/java/com/korenskoy/yandexmusic")
        )
        return candidates.first { it.isDirectory }
    }

    @Test
    fun paletteAndSourcesHaveNoBlueAccent() {
        assertFalse("HiFi.Accent must be monochrome, not blue", isAccentBlue(HiFi.Accent))
        assertFalse("Dark.accent must be monochrome, not blue", isAccentBlue(HiFiPalette.Dark.accent))
        assertFalse("Light.accent must be monochrome, not blue", isAccentBlue(HiFiPalette.Light.accent))
        assertFalse("HiFiAccent must be monochrome, not blue", isAccentBlue(HiFiAccent))

        val forbidden = listOf("0xFF0A6CFF", "0xFF5C9DFF", "0xFF8FB6FF", "0xFF9CC3FF")
        val hits = mutableListOf<String>()
        mainJavaDir().walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RadioRepository.kt" }
            .forEach { file ->
                val text = file.readText()
                forbidden.forEach { hex ->
                    if (text.contains(hex, ignoreCase = true)) {
                        hits += "${file.name}:$hex"
                    }
                }
            }
        assertEquals("Blue UI hex colors found in sources: $hits", emptyList<String>(), hits)
    }

    @Test
    fun hifiMachinedHasNoRectangularBottomUnderline() {
        val headerFile = File(mainJavaDir(), "presentation/hifi/HiFiPageHeader.kt")
        val text = headerFile.readText()
        assertFalse(
            "HiFiPageHeader.hifiMachined must not draw rectangular lipHighlight underline",
            text.contains("lipHighlight") || text.contains("drawRect(lipHighlight")
        )
    }

    @Test
    fun dialogAndSheetSyncFullscreenMode() {
        val dialogText = File(mainJavaDir(), "presentation/hifi/HiFiDialog.kt").readText()
        val menuText = File(mainJavaDir(), "presentation/main/components/TrackContextMenu.kt").readText()
        assertTrue("AppDialog must call SyncDialogWindowSystemBars()", dialogText.contains("SyncDialogWindowSystemBars()"))
        assertTrue("TrackContextMenu sheet must call SyncDialogWindowSystemBars()", menuText.contains("SyncDialogWindowSystemBars()"))
    }

    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun applyWindowSystemBarsTogglesFlagsOnApi28() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window
        val hideMask = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        applyWindowSystemBars(window, darkTheme = true, fullscreen = true, inMultiWindow = false)
        assertEquals(hideMask, window.decorView.systemUiVisibility and hideMask)

        applyWindowSystemBars(window, darkTheme = false, fullscreen = false, inMultiWindow = false)
        assertEquals(0, window.decorView.systemUiVisibility and hideMask)

        applyWindowSystemBars(window, darkTheme = true, fullscreen = true, inMultiWindow = true)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, window.decorView.systemUiVisibility and hideMask)
    }

    @Test
    fun applyWindowSystemBarsRunsCleanlyOnDefaultSdk() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        applyWindowSystemBars(activity.window, darkTheme = true, fullscreen = true, inMultiWindow = false)
        applyWindowSystemBars(activity.window, darkTheme = false, fullscreen = false, inMultiWindow = false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

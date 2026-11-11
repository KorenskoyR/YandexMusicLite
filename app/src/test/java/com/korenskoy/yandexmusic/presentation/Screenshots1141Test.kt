package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.main.components.PlaybackJournalDialog
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
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
 * 1.14.1 references: "Не прерывать музыку" with the cache, the warning without one, and the playback journal
 * that tells a stutter of the link from one of the track or the device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1141Test {
    private companion object {
        const val TALL = "w412dp-h1900dp-port-xxhdpi"
    }

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
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
        settle()
    }

    private fun settle() = repeat(20) {
        compose.mainClock.advanceTimeBy(100)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) = captureScreenRoboImage("src/test/screenshots/$name.png", options)

    private fun playback(state: SettingsUiState) = show {
        SettingsSheet(state = state, actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.PLAYBACK)
    }

    // A tall screen shows the whole page: scrolling waits for an idle screen the endless backdrop never gives
    @Test
    @Config(qualifiers = TALL)
    fun smoothPlaybackWithTheCache() {
        playback(SettingsUiState(smoothPlayback = true, reserveMb = 100, reserveUsedBytes = 64L * 1024 * 1024, crossfadeSeconds = 3))
        snapScreen("v1141_smooth_cache")
    }

    @Test
    @Config(qualifiers = TALL)
    fun smoothPlaybackWithoutACacheWarns() {
        playback(SettingsUiState(smoothPlayback = true, reserveMb = 0, crossfadeSeconds = 3))
        snapScreen("v1141_smooth_no_cache")
    }


    private val at = 1_794_380_000_000L

    private val journal = PlaybackJournal.Snapshot(
        PlaybackJournal.Summary(playedMs = 58 * 60_000L, stalls = 1, stalledMs = 2_000, copySwitches = 3, lightTracks = 4, crossfades = 17, crossfadesLate = 1),
        listOf(
            PlaybackJournal.Event(at, PlaybackJournal.Kind.UNEVEN),
            PlaybackJournal.Event(at + 5_000, PlaybackJournal.Kind.COPY, "Хочешь?", 82_000),
            PlaybackJournal.Event(at + 180_000, PlaybackJournal.Kind.LIGHT, "Группа крови"),
            PlaybackJournal.Event(at + 540_000, PlaybackJournal.Kind.STALL, "Кукушка", 2_000),
            PlaybackJournal.Event(at + 600_000, PlaybackJournal.Kind.CROSSFADE_LATE, "Звезда по имени Солнце"),
            PlaybackJournal.Event(at + 900_000, PlaybackJournal.Kind.STEADY)
        )
    )

    @Test
    fun theJournal() {
        show { PlaybackJournalDialog(journal, onShare = {}, onClear = {}, onDismiss = {}) }
        snapScreen("v1141_journal")
    }

    @Test
    fun theJournalInAbout() {
        show {
            SettingsSheet(state = SettingsUiState(journal = journal), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.ABOUT)
        }
        snapScreen("v1141_about_journal")
    }
}

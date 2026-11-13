package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.presentation.voice.VoiceRoute
import com.korenskoy.yandexmusic.presentation.voice.VoiceListeningWindow
import com.korenskoy.yandexmusic.presentation.voice.Listening
import com.korenskoy.yandexmusic.presentation.main.components.VoiceSearchRows
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import com.korenskoy.yandexmusic.data.player.PlaybackJournal.Kind
import com.korenskoy.yandexmusic.data.player.ProcessExits
import com.korenskoy.yandexmusic.data.player.Recovery
import com.korenskoy.yandexmusic.data.player.SoundTaker
import com.korenskoy.yandexmusic.data.player.StopCause
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.main.components.PlaybackJournalDialog
import com.korenskoy.yandexmusic.presentation.main.components.journalText
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 1.14.7 references: the playback journal tells every stop with its cause (another app, the headphones, a
 * button, an error, Android closing the app) and offers to let the app work in the background when the phone
 * closed it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class Screenshots1147Test {

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

    private val at = 1_794_380_000_000L

    private val journal = PlaybackJournal.Snapshot(
        PlaybackJournal.Summary(
            playedMs = 41 * 60_000L, stalls = 1, stalledMs = 700, crossfades = 9, crossfadesLate = 0,
            pausesNotAsked = 3, errors = 1, kills = 1, renewedLinks = 1, waveOffered = 23, waveOfferedHeard = 3
        ),
        listOf(
            PlaybackJournal.Event(at, Kind.KILLED, valueMs = at - 600_000, detail = ProcessExits.SYSTEM),
            PlaybackJournal.Event(at + 60_000, Kind.PAUSED, "Подруга моя", count = StopCause.FOCUS_TRANSIENT.ordinal, detail = SoundTaker.CALL),
            PlaybackJournal.Event(at + 150_000, Kind.RESUMED, "Подруга моя", valueMs = 90_000, count = StopCause.FOCUS_TRANSIENT.ordinal),
            PlaybackJournal.Event(at + 400_000, Kind.PAUSED, "Кукушка", count = StopCause.OUTPUT_GONE.ordinal),
            PlaybackJournal.Event(at + 403_000, Kind.RESUMED, "Кукушка", valueMs = 3_000, count = StopCause.OUTPUT_GONE.ordinal),
            PlaybackJournal.Event(at + 900_000, Kind.LINK_RENEWED),
            PlaybackJournal.Event(at + 1_200_000, Kind.ERROR, "Группа крови", 61_000, Recovery.RETRY.ordinal, "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"),
            PlaybackJournal.Event(at + 1_500_000, Kind.PAUSED, "Звезда по имени Солнце", count = StopCause.FOCUS_LOST.ordinal, detail = SoundTaker.APP),
            PlaybackJournal.Event(at + 1_800_000, Kind.PAUSED, "Пачка сигарет", count = StopCause.USER_DEVICE.ordinal, detail = "com.android.bluetooth")
        )
    )

    @Test
    fun theJournalTellsEveryStopAndItsCause() {
        show { PlaybackJournalDialog(journal, onShare = {}, onClear = {}, onDismiss = {}, backgroundRestricted = true) }
        snapScreen("v1147_journal")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun theJournalOnASmallPhone() {
        show { PlaybackJournalDialog(journal, onShare = {}, onClear = {}, onDismiss = {}, backgroundRestricted = true) }
        snapScreen("v1147_journal_small")
    }

    @Test
    @Config(qualifiers = "w1280dp-h480dp-land-mdpi")
    fun theJournalOnAHeadUnit() {
        show { PlaybackJournalDialog(journal, onShare = {}, onClear = {}, onDismiss = {}, backgroundRestricted = true) }
        snapScreen("v1147_journal_head_unit")
    }

    @Test
    fun theHintIsGoneOnceTheAppMayWorkInTheBackground() {
        show { PlaybackJournalDialog(journal, onShare = {}, onClear = {}, onDismiss = {}, backgroundRestricted = false) }
        snapScreen("v1147_journal_allowed")
    }

    // --- Voice search where there is no voice input window ---

    private fun listening(state: Listening) = show { VoiceListeningWindow(state, onRetry = {}, onOpenSettings = {}, onClose = {}) }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theCarListens() {
        listening(Listening.Hearing("земфира хоч", 0.6f))
        snapScreen("v1147_voice_car_hearing")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theCarDidNotHear() {
        listening(Listening.Missed)
        snapScreen("v1147_voice_car_missed")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theCarHasNoMicrophone() {
        listening(Listening.NoMic)
        snapScreen("v1147_voice_car_no_mic")
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun theCarHeard() {
        listening(Listening.Heard("земфира хочешь"))
        snapScreen("v1147_voice_car_heard")
    }

    @Test
    @Config(qualifiers = "w1280dp-h480dp-land-mdpi")
    fun aLowHeadUnitListens() {
        listening(Listening.Missed)
        snapScreen("v1147_voice_low_missed")
    }

    @Test
    fun thePhoneListens() {
        listening(Listening.Hearing("земфира хоч", 0.4f))
        snapScreen("v1147_voice_phone_hearing")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun aSmallPhoneHasNoMicrophone() {
        listening(Listening.NoMic)
        snapScreen("v1147_voice_small_no_mic")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun aPhoneLyingDownFailed() {
        listening(Listening.Failed(Listening.Why.LANGUAGE))
        snapScreen("v1147_voice_landscape_failed")
    }

    @Test
    @Config(qualifiers = "w412dp-h1100dp-port-xxhdpi")
    fun theSettingsTellHowTheDeviceHears() {
        show {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((route, mic) in listOf(VoiceRoute.NONE to false, VoiceRoute.RECOGNIZER to false, VoiceRoute.RECOGNIZER to true, VoiceRoute.SYSTEM_WINDOW to false)) {
                    Column(Modifier.fillMaxWidth().hifiGlass(RoundedCornerShape(20.dp))) {
                        VoiceSearchRows(route, mic, onInstall = {}, onAllowMic = {})
                    }
                }
            }
        }
        snapScreen("v1147_voice_settings")
    }

    @Test
    fun theSharedTextNamesEachCause() {
        val r = RuntimeEnvironment.getApplication().resources
        val text = journalText(r, journal, SimpleDateFormat("HH:mm", Locale.US))
        listOf(
            "пауза: звук на время занят — звонок",
            "Музыка снова играет через 90 с",
            "пауза: отключились наушники или Bluetooth",
            "Ссылка на поток обновлена без остановки",
            "ошибка ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: повтор с той же секунды",
            "пауза: звук занят насовсем — другое приложение со звуком",
            "команда извне — Bluetooth-устройство (наушники, машина)",
            "Android закрыл приложение во время музыки: система телефона",
            "Паузы не по вашей команде: 3"
        ).forEach { assertTrue("$it\n---\n$text", it in text) }
    }
}

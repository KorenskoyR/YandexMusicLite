package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import com.korenskoy.yandexmusic.utils.Donations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** «Поддержать разработчика»: from «О приложении», the page opens the fundraiser and copies the wallet; Back returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-xxhdpi", application = Application::class)
class SupportPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun fromAboutToSupportAndBack() {
        compose.setContent {
            YandexMusicTheme { SettingsSheet(state = SettingsUiState(), actions = SettingsActions(), onDismiss = {}, initialPage = SettingsPage.ABOUT) }
        }
        compose.onNodeWithText("Неофициальный клиент", substring = true).assertExists()
        compose.onNodeWithText("Поддержать разработчика").performScrollTo().performClick()
        compose.onNodeWithText("Сеть TRON (TRC20)").assertExists()

        // The fundraiser, by the button and by the code itself
        compose.onNodeWithText("Открыть").performScrollTo().performClick()
        assertEquals(Donations.YOOMONEY_URL, Shadows.shadowOf(app).nextStartedActivity.let { it.action to it.dataString }.also { assertEquals(Intent.ACTION_VIEW, it.first) }.second)
        compose.onNodeWithContentDescription("QR-код сбора на ЮMoney", substring = true).performClick()
        assertEquals(Donations.YOOMONEY_URL, Shadows.shadowOf(app).nextStartedActivity.dataString)

        // The wallet goes to the clipboard, and the page says so
        compose.onNodeWithText("Скопировать").performScrollTo().performClick()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(Donations.USDT_TRC20, clip.getItemAt(0).text.toString())
        compose.onNodeWithText("Адрес скопирован").assertExists()

        // Back: to «О приложении», not out of the settings
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Поддержать разработчика").assertExists()
        compose.onNodeWithText("Сеть TRON (TRC20)").assertDoesNotExist()
    }
}

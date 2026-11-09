package com.korenskoy.yandexmusic

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.main.LibraryScreen
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.TrackContextMenu
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A window opened from another one closes back to it: Settings stay open behind the accounts window,
 * the playlist picker steps back to the track's actions, "new playlist" steps back to the picker.
 */
@RunWith(AndroidJUnit4::class)
class StepBackNavigationInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val track = TrackDetailsDto(id = JsonPrimitive("1"), title = "Первая песня", artists = listOf(ArtistDto(name = "Группа")), durationMs = 215_000)

    @Test
    fun settingsStayOpenBehindTheAccountsWindow() {
        compose.setContent {
            YandexMusicTheme {
                var accounts by remember { mutableStateOf(false) }
                LibraryScreen(
                    tracks = listOf(track), isLoading = false,
                    currentTrackId = null, isPlaying = false, likedTrackIds = emptySet(), downloadedTrackIds = emptySet(),
                    downloadProgress = null, onTrackClick = {}, onPlayAllClick = {}, onShuffleClick = {},
                    onDownloadAllClick = {}, onCancelDownload = {}, onToggleLike = {},
                    settings = SettingsUiState(accountName = "Тестовый Аккаунт"),
                    settingsActions = SettingsActions(onAccountManagement = { accounts = true })
                )
                // Stands in for the accounts window, which the app shows the same way: a dialog over Settings
                if (accounts) {
                    HiFiAlertDialog(
                        onDismissRequest = { accounts = false },
                        title = "Аккаунты",
                        confirm = { HiFiDialogAction("Готово", { accounts = false }) }
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitForIdle()
        // The account card on the settings' root opens the account page
        compose.onNodeWithText("Тестовый Аккаунт").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Профиль при запуске").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Аккаунты").assertExists()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Аккаунты").assertDoesNotExist()
        compose.onNodeWithText("Профиль при запуске").assertExists()

        // Back steps out of the page to the root list, then closes the settings
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Профиль при запуске").assertDoesNotExist()
        compose.onNodeWithText("Воспроизведение").assertExists()
    }

    @Test
    fun trackMenuStepsBackFromThePickerAndFromNewPlaylist() {
        var dismissed = 0
        compose.setContent {
            YandexMusicTheme {
                TrackContextMenu(
                    track = track, inPlaylist = null,
                    playlists = listOf(UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 4, revision = 1)),
                    isLiked = false, startWithPlaylists = false,
                    onDismiss = { dismissed++ }, onPlayNext = {}, onToggleLike = {}, onAddTo = {},
                    onCreatePlaylist = {}, onRemoveFromPlaylist = {}
                )
            }
        }
        compose.onNodeWithText("Добавить в плейлист").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("В дорогу").assertExists()

        // "New playlist" → Cancel: back to the picker
        compose.onNodeWithText("Новый плейлист").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Отмена").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("В дорогу").assertExists()
        assertEquals(0, dismissed)

        // Back from the picker: the track's actions again, the menu stays
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("В дорогу").assertDoesNotExist()
        compose.onNodeWithText("Добавить в плейлист").assertExists()
        assertEquals(0, dismissed)

        // Back from the actions closes the menu
        Espresso.pressBack()
        compose.waitUntil(3_000) { dismissed == 1 }
    }
}

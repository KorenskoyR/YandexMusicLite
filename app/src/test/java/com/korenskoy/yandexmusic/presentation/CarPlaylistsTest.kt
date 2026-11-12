package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.car.CarPlaylists
import com.korenskoy.yandexmusic.presentation.car.CarPlaylistsPanel
import com.korenskoy.yandexmusic.presentation.car.Scale
import com.korenskoy.yandexmusic.presentation.car.canAddToPlaylist
import com.korenskoy.yandexmusic.presentation.main.QuickAdd
import com.korenskoy.yandexmusic.presentation.main.QuickMark
import com.korenskoy.yandexmusic.presentation.main.QuickNote
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «В плейлист» in the car: a tap adds, the row says so and adds no second time, a new playlist takes the track along. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class CarPlaylistsTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = TrackDetailsDto(id = JsonPrimitive("222"), title = "Хочешь?", artists = listOf(ArtistDto(name = "Земфира")))
    private val rock = UserPlaylistDto(kind = JsonPrimitive(1003), title = "Рок", trackCount = 118)
    private val calm = UserPlaylistDto(kind = JsonPrimitive(1004), title = "Спокойное", trackCount = 23)

    @Test
    fun aTapAddsOnceAndTheRowSaysSo() {
        val opened = mutableListOf<String>()
        val added = mutableListOf<String>()
        compose.setContent {
            var state by remember { mutableStateOf(QuickAdd()) }
            YandexMusicTheme {
                CarPlaylistsPanel(
                    Scale(1f),
                    CarPlaylists(
                        listOf(rock, calm), state,
                        onOpen = { opened += it.idString; state = QuickAdd(it.idString, mapOf("1004" to QuickMark.ALREADY)) },
                        onAdd = { playlist, t ->
                            added += playlist.kindString
                            state = state.copy(marks = state.marks + (playlist.kindString to QuickMark.ADDED), note = QuickNote("Добавлено в «${playlist.title}»", 1))
                        },
                        onCreate = { _, _ -> }
                    ),
                    track, Modifier.fillMaxSize(), onClose = {}
                )
            }
        }
        compose.waitForIdle()
        assertEquals("the panel asks which playlists have the track", listOf("222"), opened)
        compose.onNodeWithText("Хочешь? · Земфира").assertExists()
        // «Спокойное» has it already: no «+» there
        compose.onNodeWithText("Уже здесь").assertExists()
        compose.onNodeWithContentDescription("Добавить в «Спокойное»").assertDoesNotExist()

        compose.onNodeWithContentDescription("Добавить в «Рок»").performClick()
        compose.onNodeWithText("Добавлен").assertExists()
        compose.onNodeWithText("Добавлено в «Рок»").assertExists()
        // The row has its mark: a second tap adds nothing
        compose.onNodeWithText("Рок").performClick()
        assertEquals(listOf("1003"), added)
    }

    @Test
    fun aNewPlaylistTakesTheTrack() {
        val created = mutableListOf<Pair<String, String>>()
        compose.setContent {
            YandexMusicTheme {
                CarPlaylistsPanel(
                    Scale(1f),
                    CarPlaylists(emptyList(), QuickAdd(), onOpen = {}, onAdd = { _, _ -> }, onCreate = { name, t -> created += name to t.idString }),
                    track, Modifier.fillMaxSize(), onClose = {}
                )
            }
        }
        compose.onNodeWithText("Своих плейлистов пока нет — создайте первый").assertExists()
        compose.onNodeWithText("Новый плейлист").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Для дачи")
        compose.onNodeWithText("Создать").performClick()
        assertEquals(listOf("Для дачи" to "222"), created)
    }

    @Test
    fun onlyACatalogueTrackGoesIntoAPlaylist() {
        assertTrue(canAddToPlaylist(track))
        assertFalse(canAddToPlaylist(track.copy(isRadio = true)))
        assertFalse(canAddToPlaylist(null))
    }
}

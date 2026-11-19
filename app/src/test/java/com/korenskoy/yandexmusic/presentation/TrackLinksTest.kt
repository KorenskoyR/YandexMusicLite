package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackAlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.LocalTrackLinks
import com.korenskoy.yandexmusic.presentation.hifi.TrackLinks
import com.korenskoy.yandexmusic.presentation.hifi.toAlbum
import com.korenskoy.yandexmusic.presentation.hifi.toArtist
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** From the player and the wave: the artist's name opens the artist, the title the album; not for a station or a file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackLinksTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = TrackDetailsDto(
        id = JsonPrimitive("10"),
        title = "Песня",
        artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Исполнитель")),
        albums = listOf(TrackAlbumDto(id = JsonPrimitive(3), title = "Альбом"))
    )

    private fun show(shown: TrackDetailsDto, links: TrackLinks?) {
        compose.setContent {
            CompositionLocalProvider(LocalTrackLinks provides links) {
                Column {
                    Text(shown.title.orEmpty(), Modifier.toAlbum(shown))
                    Text(shown.artists.first().name.orEmpty(), Modifier.toArtist(shown))
                }
            }
        }
    }

    @Test
    fun theNamesLeadToTheirPages() {
        val opened = mutableListOf<String>()
        show(track, TrackLinks(onArtist = { opened += "artist:" + it.idString }, onAlbum = { opened += "album:" + it.idString }))

        compose.onNodeWithText("Исполнитель").performClick()
        compose.onNodeWithText("Песня").performClick()

        assertEquals(listOf("artist:10", "album:10"), opened)
    }

    @Test
    fun aStationHasNoPagesToGoTo() {
        show(track.copy(isRadio = true), TrackLinks({}, {}))
        compose.onNodeWithText("Исполнитель").assertHasNoClickAction()
        compose.onNodeWithText("Песня").assertHasNoClickAction()
    }

    @Test
    fun withoutAWayNothingIsTappable() {
        // The car and the screenshots give no links
        show(track, null)
        compose.onNodeWithText("Исполнитель").assertHasNoClickAction()
    }

    @Test
    fun aTrackOfTheServiceIsTappable() {
        show(track, TrackLinks({}, {}))
        compose.onNodeWithText("Исполнитель").assertHasClickAction()
    }

    @Test
    fun inTheCarTooTheNamesLeadToTheirPages() {
        val opened = mutableListOf<String>()
        val car = track.copy(artists = listOf(ArtistDto(id = JsonPrimitive(7), name = "Группа Крови")), title = "Звезда")
        compose.setContent {
            CompositionLocalProvider(LocalTrackLinks provides TrackLinks({ opened += "artist" }, { opened += "album" })) {
                com.korenskoy.yandexmusic.presentation.car.CarScreen(
                    currentTrack = car, isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
                    onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                    onPlayDownloaded = {}, onPlayRadio = {}, onExit = {}, isLandscape = false
                )
            }
        }
        compose.onNodeWithText("Группа Крови").performClick()
        compose.onNodeWithText("Звезда").performClick()
        assertEquals(listOf("artist", "album"), opened)
    }
}

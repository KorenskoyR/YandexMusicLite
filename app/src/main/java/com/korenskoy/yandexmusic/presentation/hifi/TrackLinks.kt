package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.serialization.json.JsonPrimitive

/**
 * The way from a playing track to its artist's and album's pages: the player and the wave take it from here, the
 * main screen gives it (the same pages as the track menu's «Исполнитель» and «Альбом»). Null where there is no such
 * way (the car: no extra steps while driving; screenshots).
 */
class TrackLinks(val onArtist: (TrackDetailsDto) -> Unit, val onAlbum: (TrackDetailsDto) -> Unit)

val LocalTrackLinks = staticCompositionLocalOf<TrackLinks?> { null }

/** A track of the service with an artist page to go to (not a station, not the user's own file). */
fun TrackDetailsDto.hasArtistPage(): Boolean = !isRadio && !isLocal && artists.any { it.idString != null }

/** A track of the service with an album page to go to. */
fun TrackDetailsDto.hasAlbumPage(): Boolean = !isRadio && !isLocal && albums.firstOrNull()?.let { it.id is JsonPrimitive } == true

/** The artist's name, tapped: the artist's page (with several, a short choice first). */
@Composable
fun Modifier.toArtist(track: TrackDetailsDto): Modifier {
    val links = LocalTrackLinks.current ?: return this
    if (!track.hasArtistPage()) return this
    val label = stringResource(R.string.track_link_artist)
    return clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = label) { links.onArtist(track) }
}

/** The track's title, tapped: its album's page. */
@Composable
fun Modifier.toAlbum(track: TrackDetailsDto): Modifier {
    val links = LocalTrackLinks.current ?: return this
    if (!track.hasAlbumPage()) return this
    val label = stringResource(R.string.track_link_album)
    return clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = label) { links.onAlbum(track) }
}

/** A track of several artists: whose page to open. */
@Composable
fun ArtistChoiceDialog(track: TrackDetailsDto, onPick: (id: String, name: String) -> Unit, onDismiss: () -> Unit) {
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.track_link_which_artist),
        content = {
            Column {
                track.artists.forEach { artist ->
                    val id = artist.idString ?: return@forEach
                    Text(
                        artist.name.orEmpty(),
                        color = Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onPick(id, artist.name.orEmpty()) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirm = { HiFiDialogAction(stringResource(R.string.settings_cancel), onDismiss, HiFiDialogActionKind.Quiet) }
    )
}

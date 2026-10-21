package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.utils.russianQuantityString
import com.korenskoy.yandexmusic.utils.toCoverUrl

/** Opens the track menu. MainScreen provides it, so every [TrackItem] shows the "⋮" button. */
fun interface TrackMenuLauncher {
    fun open(track: TrackDetailsDto, playlist: UserPlaylistDto?)
}

val LocalTrackMenu = staticCompositionLocalOf<TrackMenuLauncher?> { null }

/** The playlist whose tracks are listed, so the menu can offer "remove from playlist". */
val LocalTrackMenuPlaylist = staticCompositionLocalOf<UserPlaylistDto?> { null }

/**
 * Track actions, and the "add to playlist" picker ([startWithPlaylists] opens it directly, as the
 * player's button does). "New playlist" asks for a name and adds the track to the new playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: TrackDetailsDto,
    inPlaylist: UserPlaylistDto?,
    playlists: List<UserPlaylistDto>,
    isLiked: Boolean,
    startWithPlaylists: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLike: () -> Unit,
    onAddTo: (UserPlaylistDto) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null
) {
    var picking by remember { mutableStateOf(startWithPlaylists) }
    var naming by remember { mutableStateOf(false) }

    if (naming) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { name ->
                onCreatePlaylist(name)
                onDismiss()
            },
            onDismiss = onDismiss
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = track.title ?: stringResource(R.string.bez_nazvaniya),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
            )
            Text(
                text = if (picking) stringResource(R.string.track_context_menu_dobavit_v_pleylist)
                else track.artists.joinToString { it.name.orEmpty() },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )

            if (picking) {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item(key = "new") {
                        ContextMenuItem(icon = Icons.Default.Add, text = stringResource(R.string.playlist_new)) { naming = true }
                    }
                    items(playlists.filter { it.kindString != inPlaylist?.kindString }, key = { it.kindString }) { playlist ->
                        PlaylistPickerRow(playlist) {
                            onAddTo(playlist)
                            onDismiss()
                        }
                    }
                }
            } else {
                ContextMenuItem(Icons.Default.SkipNext, stringResource(R.string.track_context_menu_igrat_sleduyuschim)) {
                    onPlayNext()
                    onDismiss()
                }
                ContextMenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.track_context_menu_dobavit_v_pleylist)) {
                    picking = true
                }
                if (inPlaylist != null) {
                    ContextMenuItem(Icons.Default.PlaylistRemove, stringResource(R.string.track_menu_remove_from_playlist)) {
                        onRemoveFromPlaylist()
                        onDismiss()
                    }
                }
                if (onOpenArtist != null) {
                    ContextMenuItem(Icons.Default.Person, stringResource(R.string.track_menu_artist, track.artists.first { it.idString != null }.name.orEmpty())) {
                        onOpenArtist()
                        onDismiss()
                    }
                }
                if (onOpenAlbum != null) {
                    ContextMenuItem(Icons.Default.Album, stringResource(R.string.track_menu_album, track.albums.firstOrNull()?.title.orEmpty()).removeSuffix(": ")) {
                        onOpenAlbum()
                        onDismiss()
                    }
                }
                ContextMenuItem(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(if (isLiked) R.string.track_menu_unlike else R.string.track_menu_like)
                ) {
                    onToggleLike()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerRow(playlist: UserPlaylistDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF3A2A4A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
            AsyncImage(
                model = playlist.coverUri.toCoverUrl("100x100"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(playlist.title.orEmpty(), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

package com.korenskoy.yandexmusic.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.main.components.DeletePlaylistDialog
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenuPlaylist
import com.korenskoy.yandexmusic.presentation.main.components.PlaylistNameDialog
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString
import com.korenskoy.yandexmusic.utils.toCoverUrl

/** Horizontal row of the user's playlists in the library header. */
@Composable
fun PlaylistsRow(
    playlists: List<UserPlaylistDto>,
    onOpen: (UserPlaylistDto) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { onCreate(it); naming = false },
            onDismiss = { naming = false }
        )
    }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_playlists),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "new") {
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { naming = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.playlist_new),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            items(playlists, key = { it.kindString }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(playlist) }
                ) {
                    PlaylistCover(playlist, Modifier.size(104.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playlist.title.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCover(playlist: UserPlaylistDto, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2A4A), Color(0xFF22222C)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(36.dp)
        )
        AsyncImage(
            model = playlist.coverUri.toCoverUrl("400x400"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Opened playlist: cover, play/shuffle and the track list. Back closes it. */
@Composable
fun PlaylistScreen(
    opened: OpenedPlaylist,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    contentBottom: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onPlay: (shuffle: Boolean) -> Unit,
    onTrackClick: (String) -> Unit,
    onToggleLike: (com.korenskoy.yandexmusic.data.dto.TrackDetailsDto) -> Unit,
    onRetry: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onReorder: (shownOrder: List<String>, newOrder: List<String>) -> Unit = { _, _ -> },
    onSetPublic: (Boolean) -> Unit = {}
) {
    val playlist = opened.playlist
    // Track order being edited (null — normal view); saved as one change with "Done"
    var editedOrder by remember(opened.tracks) { mutableStateOf<List<com.korenskoy.yandexmusic.data.dto.TrackDetailsDto>?>(null) }
    BackHandler { if (editedOrder != null) editedOrder = null else onBack() }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_rename),
            confirmLabel = stringResource(R.string.playlist_save),
            initialName = playlist.title.orEmpty(),
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (deleting) {
        DeletePlaylistDialog(
            playlistTitle = playlist.title.orEmpty(),
            onConfirm = { onDelete(); deleting = false },
            onDismiss = { deleting = false }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1F1626), Color(0xFF151219), Color(0xFF111115))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x352A2A38))
                    .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_nazad),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            editedOrder?.let { order ->
                Text(
                    text = stringResource(R.string.playlist_reorder_done),
                    color = Color(0xFFFF3366),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onReorder(opened.tracks.map { it.idString }, order.map { it.idString })
                            editedOrder = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (editedOrder == null) Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x352A2A38))
                        .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_playlist_menu),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; renaming = true }
                    )
                    if (opened.tracks.size > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_reorder)) },
                            leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                            onClick = { showMenu = false; editedOrder = opened.tracks }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (playlist.isPublic) R.string.playlist_make_private else R.string.playlist_make_public)) },
                        leadingIcon = { Icon(if (playlist.isPublic) Icons.Default.Lock else Icons.Default.Public, contentDescription = null) },
                        onClick = { showMenu = false; onSetPublic(!playlist.isPublic) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showMenu = false; deleting = true }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(playlist, Modifier.size(112.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = playlist.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = russianQuantityString(
                        if (opened.isLoading) playlist.trackCount else opened.tracks.size,
                        R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                if (playlist.isPublic) {
                    Text(
                        text = stringResource(R.string.playlist_public_badge),
                        color = Color(0xFFFF9900),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (editedOrder == null) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val enabled = opened.tracks.isNotEmpty()
            PlaylistAction(Icons.Default.PlayArrow, stringResource(R.string.cd_slushat), primary = true, enabled = enabled,
                modifier = Modifier.weight(1f)) { onPlay(false) }
            PlaylistAction(Icons.Default.Shuffle, stringResource(R.string.cd_peremeshat), primary = false, enabled = enabled,
                modifier = Modifier.weight(1f)) { onPlay(true) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // The menu of a track listed here also offers "remove from playlist"
        CompositionLocalProvider(LocalTrackMenuPlaylist provides playlist) {
            val order = editedOrder
            when {
                order != null -> ReorderList(
                    tracks = order,
                    contentBottom = contentBottom,
                    onMove = { from, to -> editedOrder = order.toMutableList().apply { add(to, removeAt(from)) } }
                )
                opened.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                opened.failed || opened.tracks.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentBottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(if (opened.failed) R.string.playlist_load_failed else R.string.playlist_empty),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp
                    )
                    if (opened.failed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(stringResource(R.string.playlist_retry), color = Color.White) }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = contentBottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(8.dp)
                ) {
                    items(opened.tracks, key = { it.idString }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = isPlaying && track.idString == currentTrackId,
                            isLiked = likedTrackIds.contains(track.idString),
                            isDownloaded = downloadedTrackIds.contains(track.idString),
                            onToggleLike = { onToggleLike(track) },
                            onClick = { onTrackClick(track.idString) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Track order editing: drag a row by its handle; TalkBack users get "Up" and "Down" actions instead.
 * The list keeps the dragged row under the finger while neighbours swap places.
 */
@Composable
private fun ReorderList(
    tracks: List<com.korenskoy.yandexmusic.data.dto.TrackDetailsDto>,
    contentBottom: androidx.compose.ui.unit.Dp,
    onMove: (from: Int, to: Int) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The drag handler outlives recompositions: it must move within the current order, not the first one
    val currentOnMove by androidx.compose.runtime.rememberUpdatedState(onMove)
    val upLabel = stringResource(R.string.action_move_up)
    val downLabel = stringResource(R.string.action_move_down)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottom),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0x1AFFFFFF))
            .padding(8.dp)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.idString }) { index, track ->
            val dragged = track.idString == draggedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dragged) Color(0xFF2A2A36) else Color.Transparent)
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(upLabel) { onMove(index, index - 1); true }.takeIf { index > 0 },
                            CustomAccessibilityAction(downLabel) { onMove(index, index + 1); true }.takeIf { index < tracks.lastIndex }
                        )
                    }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title.orEmpty(), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artists.joinToString { it.name.orEmpty() },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .pointerInput(track.idString) {
                            detectDragGestures(
                                onDragStart = { draggedId = track.idString; dragOffset = 0f },
                                onDragEnd = { draggedId = null; dragOffset = 0f },
                                onDragCancel = { draggedId = null; dragOffset = 0f }
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val items = listState.layoutInfo.visibleItemsInfo
                                val current = items.firstOrNull { it.key == track.idString } ?: return@detectDragGestures
                                val center = current.offset + current.size / 2 + dragOffset
                                val target = items.firstOrNull {
                                    it.index != current.index && center.toInt() in it.offset until it.offset + it.size
                                } ?: return@detectDragGestures
                                currentOnMove(current.index, target.index)
                                // The row now sits in the target slot: keep it under the finger
                                dragOffset += current.offset - target.offset
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun PlaylistAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(
                if (primary) Brush.linearGradient(listOf(Color(0xFFFF3366), Color(0xFFFF7722), Color(0xFFFF9900)))
                else Brush.verticalGradient(listOf(Color(0x352A2A38), Color(0x201E1E28)))
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (primary) 0.6f else 0.25f)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

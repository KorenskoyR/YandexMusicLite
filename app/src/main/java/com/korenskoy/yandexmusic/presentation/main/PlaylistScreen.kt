package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.presentation.hifi.HiFiWideHeader
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import com.korenskoy.yandexmusic.presentation.hifi.appearOnce
import com.korenskoy.yandexmusic.presentation.hifi.rememberAppearance
import com.korenskoy.yandexmusic.presentation.hifi.HiFiEmptyState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiErrorState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.predictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.rememberPredictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.main.components.DeletePlaylistDialog
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenuPlaylist
import com.korenskoy.yandexmusic.presentation.main.components.PlaylistNameDialog
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString

@Composable
internal fun PlaylistCover(playlist: UserPlaylistDto, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(36.dp)
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).cover(playlist.coverUri, CoverSize.Medium).build(),
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
    // Back leaves the playlist (the page shrinks under the gesture); while reordering it only cancels that
    val back = rememberPredictiveBack(enabled = true, onBack = onBack)
    BackHandler(enabled = editedOrder != null) { editedOrder = null }
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
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val backButton: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(40.dp)
                .hifiGlass(CircleShape)
                .hifiPress(onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_nazad),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
    val menu: @Composable () -> Unit = {
            editedOrder?.let { order ->
                Text(
                    text = stringResource(R.string.playlist_reorder_done),
                    color = Color.White,
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
    // While it loads or after a failure the list says nothing, and the playlist itself knows its size
    val trackCount = if (opened.isLoading || opened.failed) playlist.trackCount else opened.tracks.size
    val details: @Composable () -> Unit = {
        Text(
            text = russianQuantityString(trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        if (playlist.isPublic) {
            Text(
                text = stringResource(R.string.playlist_public_badge).uppercase(),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.1.em
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .predictiveBack(back)
    ) {
        if (isLandscape) {
            HiFiWideHeader(
                title = playlist.title.orEmpty(),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                back = backButton,
                cover = { size -> PlaylistCover(playlist, Modifier.size(size)) },
                details = { details() },
                actions = {
                    if (editedOrder == null) {
                        val enabled = opened.tracks.isNotEmpty()
                        PlaylistAction(Icons.Default.PlayArrow, stringResource(R.string.cd_slushat), primary = true, enabled = enabled,
                            modifier = Modifier.weight(1f)) { onPlay(false) }
                        PlaylistAction(Icons.Default.Shuffle, stringResource(R.string.cd_peremeshat), primary = false, enabled = enabled,
                            modifier = Modifier.weight(1f)) { onPlay(true) }
                    }
                },
                trailing = {
                    Spacer(modifier = Modifier.width(12.dp))
                    menu()
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                backButton()
                Spacer(modifier = Modifier.weight(1f))
                menu()
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
                        style = HiFiType.LargeTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    details()
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
        }

        // The menu of a track listed here also offers "remove from playlist"
        CompositionLocalProvider(LocalTrackMenuPlaylist provides playlist) {
            val order = editedOrder
            val appearance = rememberAppearance(playlist.kindString)
            when {
                order != null -> ReorderList(
                    tracks = order,
                    contentBottom = contentBottom,
                    onMove = { from, to -> editedOrder = order.toMutableList().apply { add(to, removeAt(from)) } }
                )
                opened.isLoading -> HiFiSkeletonRows(Modifier.padding(top = 8.dp), count = 6)
                opened.failed -> HiFiErrorState(
                    onRetry = onRetry,
                    message = stringResource(R.string.playlist_load_failed) + opened.failReason?.let { "\n" + stringResource(R.string.playlist_error_code, it) }.orEmpty(),
                    modifier = if (isLandscape) Modifier.padding(bottom = contentBottom) else Modifier
                )
                opened.tracks.isEmpty() -> HiFiEmptyState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.playlist_empty)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = contentBottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        // Rows lie right on the backdrop, like on the album page (no lighter panel)
                        .padding(horizontal = 12.dp)
                ) {
                    itemsIndexed(opened.tracks, key = { _, t -> t.idString }, contentType = { _, _ -> "track" }) { index, track ->
                        Box(Modifier.appearOnce(appearance, track.idString, index)) {
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
            .padding(horizontal = 12.dp)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.idString }) { index, track ->
            val dragged = track.idString == draggedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dragged) HiFi.GlassFill else Color.Transparent)
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

/** "Слушать" is the Hi-Fi metal capsule, "Перемешать" glass — the same pair as on "Моя музыка". */
@Composable
private fun PlaylistAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (primary) MetalPillButton(label, onClick, modifier, icon = icon, height = 48.dp, enabled = enabled)
    else GlassPillButton(label, onClick, modifier, icon = icon, height = 48.dp, enabled = enabled)
}

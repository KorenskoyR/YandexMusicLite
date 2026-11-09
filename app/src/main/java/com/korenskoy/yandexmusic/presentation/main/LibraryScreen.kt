package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.landscapeColumns
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import androidx.compose.foundation.layout.navigationBars
import com.korenskoy.yandexmusic.presentation.hifi.rememberHeaderCollapse
import com.korenskoy.yandexmusic.presentation.hifi.fadeUnderHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiWellButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFiCollapsingTitle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.FavoriteBorder
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.hifi.appearOnce
import com.korenskoy.yandexmusic.presentation.hifi.rememberAppearance
import com.korenskoy.yandexmusic.presentation.hifi.predictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.rememberPredictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.HiFiEmptyState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiErrorState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import com.korenskoy.yandexmusic.presentation.hifi.LoadState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.download.DownloadQueueState
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.network.DownloadProgressInfo
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLargeTitle
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.presentation.main.components.DownloadProgressModal
import com.korenskoy.yandexmusic.presentation.main.components.PlaylistNameDialog
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString

private enum class LibrarySection { LIKED, DOWNLOADED, PLAYLISTS }

/** The list starts to show under the header; below it, nothing is cut by a hard line. */
private val LibraryFadeStart = 56.dp
private val LibraryFadeLength = 22.dp

/**
 * "Моя музыка": play/shuffle, the three library sections (each opens its own page) and recently
 * added covers. Settings — the only entry to the account — sit behind the button in the header.
 */
@Composable
fun LibraryScreen(
    tracks: List<TrackDetailsDto>,
    isLoading: Boolean,
    loadFailed: Boolean = false,
    downloadState: DownloadQueueState = DownloadQueueState.IDLE,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    downloadProgress: DownloadProgressInfo?,
    onTrackClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadAllClick: () -> Unit,
    onCancelDownload: () -> Unit,
    settings: SettingsUiState = SettingsUiState(),
    settingsActions: SettingsActions = SettingsActions(),
    onToggleLike: (TrackDetailsDto) -> Unit,
    onRefresh: () -> Unit = {},
    hasActiveTrack: Boolean = false,
    playlists: List<UserPlaylistDto> = emptyList(),
    openedPlaylist: OpenedPlaylist? = null,
    onOpenPlaylist: (UserPlaylistDto) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
    onPlayPlaylist: (shuffle: Boolean) -> Unit = {},
    onPlaylistTrackClick: (String) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onRenamePlaylist: (UserPlaylistDto, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (UserPlaylistDto) -> Unit = {},
    onReorderPlaylist: (UserPlaylistDto, List<String>, List<String>) -> Unit = { _, _, _ -> },
    onSetPlaylistPublic: (UserPlaylistDto, Boolean) -> Unit = { _, _ -> }
) {
    var showSettings by remember { mutableStateOf(false) }
    var showProgressModal by remember { mutableStateOf(false) }
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sectionName?.let { LibrarySection.valueOf(it) }
    val back = rememberPredictiveBack(enabled = section != null && openedPlaylist == null) { sectionName = null }

    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val contentStart = 20.dp
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = if (isLandscape) DockClearance + navigationBar else (if (hasActiveTrack) 168.dp else 100.dp)
    val columns = if (isLandscape) landscapeColumns(com.korenskoy.yandexmusic.utils.currentWindowSizeDp().widthDp) else 2
    val downloaded = remember(tracks, downloadedTrackIds) { tracks.filter { downloadedTrackIds.contains(it.idString) } }

    if (openedPlaylist != null) {
        PlaylistScreen(
            opened = openedPlaylist,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            likedTrackIds = likedTrackIds,
            downloadedTrackIds = downloadedTrackIds,
            // The page keeps above the navigation bar itself
            contentBottom = if (isLandscape) DockClearance else contentBottom,
            onBack = onClosePlaylist,
            onPlay = onPlayPlaylist,
            onTrackClick = onPlaylistTrackClick,
            onToggleLike = onToggleLike,
            onRetry = { onOpenPlaylist(openedPlaylist.playlist) },
            onRename = { onRenamePlaylist(openedPlaylist.playlist, it) },
            onDelete = { onDeletePlaylist(openedPlaylist.playlist) },
            onReorder = { shown, new -> onReorderPlaylist(openedPlaylist.playlist, shown, new) },
            onSetPublic = { onSetPlaylistPublic(openedPlaylist.playlist, it) }
        )
        return
    }

    AnimatedContent(
        targetState = section,
        transitionSpec = {
            (fadeIn(HiFiMotion.standard()) + slideInVertically(HiFiMotion.standard()) { it / 40 }) togetherWith fadeOut(HiFiMotion.quick())
        },
        label = "library_section"
    ) { shown ->
        val listPadding = PaddingValues(start = contentStart, end = 20.dp, bottom = contentBottom)
        when (shown) {
            null -> Box(Modifier.fillMaxSize()) {
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val density = LocalDensity.current
            val list = rememberLazyListState()
            val collapse by rememberHeaderCollapse(list)
            LazyColumn(
                state = list,
                modifier = Modifier
                    .fillMaxSize()
                    .fadeUnderHeader({ with(density) { (statusBar + LibraryFadeStart).toPx() } }, with(density) { LibraryFadeLength.toPx() }),
                contentPadding = PaddingValues(start = contentStart, end = 20.dp, top = statusBar + LibraryFadeStart + LibraryFadeLength + 2.dp, bottom = contentBottom)
            ) {
                item(key = "buttons") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetalPillButton(stringResource(R.string.library_listen), onPlayAllClick, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                        GlassPillButton(stringResource(R.string.library_shuffle), onShuffleClick, Modifier.weight(1f), icon = Icons.Rounded.Shuffle)
                    }
                }
                item(key = "sections") {
                    Column(Modifier.padding(top = 14.dp)) {
                        SectionRow(Icons.Rounded.Favorite, stringResource(R.string.library_liked), tracks.size) { sectionName = LibrarySection.LIKED.name }
                        SectionRow(Icons.Rounded.DownloadDone, stringResource(R.string.library_downloaded), downloaded.size) { sectionName = LibrarySection.DOWNLOADED.name }
                        SectionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.library_playlists_row), playlists.size) { sectionName = LibrarySection.PLAYLISTS.name }
                    }
                }
                when (LoadState.of(tracks, tracks.isEmpty(), isLoading, loadFailed)) {
                    LoadState.Loading -> item(key = "loading") { HiFiSkeletonRows(Modifier.padding(top = 28.dp), count = 4) }
                    LoadState.Failed -> item(key = "failed") { HiFiErrorState(onRetry = onRefresh) }
                    LoadState.Empty -> item(key = "empty") {
                        HiFiEmptyState(
                            icon = Icons.Rounded.FavoriteBorder,
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.library_empty_message),
                            actionLabel = stringResource(R.string.library_obnovit_mediateku),
                            onAction = onRefresh
                        )
                    }
                    is LoadState.Ready -> {
                        item(key = "recent_title") {
                            HiFiSectionHeader(stringResource(R.string.library_recent), Modifier.padding(top = 26.dp, bottom = 12.dp))
                        }
                        coverGrid(
                            tracks = tracks.take(if (isLandscape) columns * 2 else 6),
                            columns = columns,
                            onClick = { onTrackClick(it.idString) }
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = contentStart, end = 20.dp, top = statusBar + 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HiFiCollapsingTitle(stringResource(R.string.library_title), collapse = { collapse }, modifier = Modifier.weight(1f))
                if (downloadState == DownloadQueueState.RUNNING) {
                    HiFiWellButton(onClick = { showProgressModal = true }, contentDescription = stringResource(R.string.download_progress_modal_zagruzka_kollektsii)) {
                        HiFiActivityIndicator(size = 18.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                HiFiWellButton(onClick = { showSettings = true }, contentDescription = stringResource(R.string.library_settings)) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(20.dp))
                }
            }
            }
            LibrarySection.LIKED, LibrarySection.DOWNLOADED -> {
                val list = if (shown == LibrarySection.LIKED) tracks else downloaded
                val appearance = rememberAppearance(shown)
                LazyColumn(modifier = Modifier.fillMaxSize().predictiveBack(back).statusBarsPadding(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = contentBottom)) {
                    item(key = "header") {
                        SectionHeader(
                            title = stringResource(if (shown == LibrarySection.LIKED) R.string.library_liked else R.string.library_downloaded),
                            count = list.size,
                            onBack = { sectionName = null }
                        )
                    }
                    if (shown == LibrarySection.LIKED && list.isNotEmpty()) {
                        item(key = "buttons") {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetalPillButton(stringResource(R.string.library_listen), onPlayAllClick, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                                GlassPillButton(stringResource(R.string.library_shuffle), onShuffleClick, Modifier.weight(1f), icon = Icons.Rounded.Shuffle)
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item(key = "empty") {
                            HiFiEmptyState(
                                icon = if (shown == LibrarySection.DOWNLOADED) Icons.Rounded.DownloadDone else Icons.Rounded.FavoriteBorder,
                                title = stringResource(if (shown == LibrarySection.DOWNLOADED) R.string.library_downloaded_empty else R.string.library_mne_nravitsya)
                            )
                        }
                    } else if (isLandscape) {
                        items(list.chunked(2), key = { row -> row.joinToString("|") { it.idString } }, contentType = { "track_row" }) { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                rowItems.forEach { track ->
                                    Box(Modifier.weight(1f)) { LibraryTrack(track, currentTrackId, isPlaying, likedTrackIds, downloadedTrackIds, onToggleLike, onTrackClick) }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        itemsIndexed(list, key = { _, t -> t.idString }, contentType = { _, _ -> "track" }) { index, track ->
                            Box(Modifier.appearOnce(appearance, track.idString, index)) {
                                LibraryTrack(track, currentTrackId, isPlaying, likedTrackIds, downloadedTrackIds, onToggleLike, onTrackClick)
                            }
                        }
                    }
                }
            }
            LibrarySection.PLAYLISTS -> PlaylistsPage(
                modifier = Modifier.predictiveBack(back),
                playlists = playlists,
                columns = columns,
                contentPadding = listPadding,
                onBack = { sectionName = null },
                onOpen = onOpenPlaylist,
                onCreate = onCreatePlaylist
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            state = settings,
            actions = settingsActions.copy(onDownloadAll = {
                onDownloadAllClick()
                showProgressModal = true
            }),
            onDismiss = { showSettings = false }
        )
    }

    if (showProgressModal && downloadState != DownloadQueueState.IDLE) {
        DownloadProgressModal(
            state = downloadState,
            progressInfo = downloadProgress,
            onDismiss = { showProgressModal = false },
            onCancel = {
                onCancelDownload()
                showProgressModal = false
            }
        )
    }
}

@Composable
private fun LibraryTrack(
    track: TrackDetailsDto,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onTrackClick: (String) -> Unit
) {
    TrackItem(
        track = track,
        isPlaying = isPlaying && track.idString == currentTrackId,
        isLiked = likedTrackIds.contains(track.idString),
        isDownloaded = downloadedTrackIds.contains(track.idString),
        onToggleLike = { onToggleLike(track) },
        onClick = { onTrackClick(track.idString) }
    )
}

/** One library section row: metal plaque, title, count, chevron, hairline below. */
@Composable
private fun SectionRow(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .hifiPress(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(icon, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, style = HiFiType.RowTitle, modifier = Modifier.weight(1f))
        Text(count.toString(), color = HiFi.TextTertiary, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
    }
    Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(1.dp).background(HiFi.Hairline))
}

/** Back button, page title and count — the header of every library sub-page. */
@Composable
private fun SectionHeader(title: String, count: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(onClick = onBack, contentDescription = stringResource(R.string.cd_nazad)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HiFiType.LargeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                russianQuantityString(count, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                style = HiFiType.Caption
            )
        }
    }
}

/** Covers in rows of [columns]; each row is one lazy item so long grids stay cheap. */
private fun LazyListScope.coverGrid(tracks: List<TrackDetailsDto>, columns: Int, onClick: (TrackDetailsDto) -> Unit) {
    items(tracks.chunked(columns), key = { row -> "grid_" + row.joinToString("|") { it.idString } }, contentType = { "cover_row" }) { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { track -> CoverCard(track, Modifier.weight(1f)) { onClick(track) } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun CoverCard(track: TrackDetailsDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        // Only the top corners are rounded: a fully rounded clip would shave the first letter off the caption
        modifier = modifier
            .hifiPress(onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Medium).crossfade(true).build(),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .hifiSoftShadow(RoundedCornerShape(16.dp), 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HiFi.Placeholder)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(9.dp))
        Text(track.title ?: stringResource(R.string.bez_nazvaniya), style = HiFiType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artists.joinToString { it.name ?: "" }, style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistsPage(
    modifier: Modifier,
    playlists: List<UserPlaylistDto>,
    columns: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (UserPlaylistDto) -> Unit,
    onCreate: (String) -> Unit
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
    val cells: List<UserPlaylistDto?> = listOf<UserPlaylistDto?>(null) + playlists
    LazyColumn(modifier = modifier.fillMaxSize().statusBarsPadding(), contentPadding = contentPadding) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(onClick = onBack, contentDescription = stringResource(R.string.cd_nazad)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.library_playlists_row), style = HiFiType.LargeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp))
            }
        }
        items(cells.chunked(columns), key = { row -> "pl_" + row.joinToString("|") { it?.kindString ?: "new" } }, contentType = { "playlist_row" }) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { playlist ->
                    Column(
                        Modifier
                            .weight(1f)
                            .hifiPress({ if (playlist == null) naming = true else onOpen(playlist) })
                    ) {
                        if (playlist == null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(9.dp))
                            Text(stringResource(R.string.playlist_new), style = HiFiType.CardTitle, maxLines = 1)
                        } else {
                            PlaylistCover(playlist, Modifier.fillMaxWidth().aspectRatio(1f).hifiSoftShadow(RoundedCornerShape(16.dp), 18.dp).clip(RoundedCornerShape(16.dp)))
                            Spacer(Modifier.height(9.dp))
                            Text(playlist.title ?: "", style = HiFiType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                                style = HiFiType.Caption
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

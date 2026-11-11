package com.korenskoy.yandexmusic.presentation.main

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import com.korenskoy.yandexmusic.data.download.OfflineState
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
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.ColumnScope
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

private enum class LibrarySection { LIKED, DOWNLOADED, PLAYLISTS, ALBUMS }

/** The list starts to show under the header; below it, nothing is cut by a hard line. */
private val LibraryFadeStart = 56.dp
private val LibraryFadeLength = 22.dp

/**
 * "Моя музыка": play/shuffle, the library sections (each opens its own page) and recently
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
    onSetPlaylistPublic: (UserPlaylistDto, Boolean) -> Unit = { _, _ -> },
    openedOffline: OfflineState = OfflineState.None,
    onDownloadPlaylist: () -> Unit = {},
    onRemovePlaylistDownload: () -> Unit = {},
    /** Everything on the device; null: the liked tracks that are downloaded. */
    downloadedTracks: List<TrackDetailsDto>? = null,
    offlineCollections: List<OfflineCollection> = emptyList(),
    onDownloadedTrackClick: (String) -> Unit = onTrackClick,
    onOpenCollection: (OfflineCollection) -> Unit = {},
    /** Other people's playlists saved to the collection in the official app. */
    savedPlaylists: List<UserPlaylistDto> = emptyList(),
    /** Albums in the collection. */
    albums: List<AlbumDto> = emptyList(),
    onOpenAlbum: (AlbumDto) -> Unit = {},
    /** A section page opened: the library catches up with the server unless it just did. */
    onSectionShown: () -> Unit = {},
    /** A section open from the start (screenshots): LIKED, DOWNLOADED, PLAYLISTS or ALBUMS. */
    initialSection: String? = null
) {
    var showSettings by remember { mutableStateOf(false) }
    var showProgressModal by remember { mutableStateOf(false) }
    var sectionName by rememberSaveable { mutableStateOf(initialSection) }
    val section = sectionName?.let { LibrarySection.valueOf(it) }
    val back = rememberPredictiveBack(enabled = section != null && openedPlaylist == null) { sectionName = null }

    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val contentStart = 20.dp
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = if (isLandscape) DockClearance + navigationBar else (if (hasActiveTrack) 168.dp else 100.dp)
    val columns = if (isLandscape) landscapeColumns(com.korenskoy.yandexmusic.utils.currentWindowSizeDp().widthDp) else 2
    val downloaded = downloadedTracks ?: remember(tracks, downloadedTrackIds) { tracks.filter { downloadedTrackIds.contains(it.idString) } }
    val keptKeys = remember(offlineCollections) { offlineCollections.mapTo(mutableSetOf()) { it.key } }
    LaunchedEffect(section) { if (section != null) onSectionShown() }

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
            onSetPublic = { onSetPlaylistPublic(openedPlaylist.playlist, it) },
            offline = openedOffline,
            onDownload = onDownloadPlaylist,
            onRemoveDownload = onRemovePlaylistDownload
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
                        SectionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.library_playlists_row), playlists.size + savedPlaylists.size) { sectionName = LibrarySection.PLAYLISTS.name }
                        SectionRow(Icons.Rounded.Album, stringResource(R.string.library_albums_row), albums.size) { sectionName = LibrarySection.ALBUMS.name }
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
                    if (shown == LibrarySection.DOWNLOADED && offlineCollections.isNotEmpty()) {
                        item(key = "kept") {
                            KeptRow(
                                liked = tracks.filter { it.idString in downloadedTrackIds },
                                kept = offlineCollections,
                                downloadedTrackIds = downloadedTrackIds,
                                onOpenLiked = { sectionName = LibrarySection.LIKED.name },
                                onOpen = onOpenCollection
                            )
                        }
                        item(key = "all_title") {
                            HiFiSectionHeader(stringResource(R.string.offline_all_downloaded), Modifier.padding(start = 12.dp, top = 22.dp, bottom = 6.dp))
                        }
                    }
                    val onRowClick: (String) -> Unit = if (shown == LibrarySection.DOWNLOADED) onDownloadedTrackClick else onTrackClick
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
                                    Box(Modifier.weight(1f)) { LibraryTrack(track, currentTrackId, isPlaying, likedTrackIds, downloadedTrackIds, onToggleLike, onRowClick) }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        itemsIndexed(list, key = { _, t -> t.idString }, contentType = { _, _ -> "track" }) { index, track ->
                            Box(Modifier.appearOnce(appearance, track.idString, index)) {
                                LibraryTrack(track, currentTrackId, isPlaying, likedTrackIds, downloadedTrackIds, onToggleLike, onRowClick)
                            }
                        }
                    }
                }
            }
            LibrarySection.PLAYLISTS -> PlaylistsPage(
                modifier = Modifier.predictiveBack(back),
                playlists = playlists,
                saved = savedPlaylists,
                keptKeys = keptKeys,
                columns = columns,
                contentPadding = listPadding,
                onBack = { sectionName = null },
                onOpen = onOpenPlaylist,
                onCreate = onCreatePlaylist
            )
            LibrarySection.ALBUMS -> AlbumsPage(
                modifier = Modifier.predictiveBack(back),
                albums = albums,
                keptKeys = keptKeys,
                columns = columns,
                contentPadding = listPadding,
                onBack = { sectionName = null },
                onOpen = onOpenAlbum
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

/**
 * "Плейлисты на устройстве": «Мне нравится» and the kept playlists and albums as covers, each with the
 * "downloaded" mark the track rows use.
 */
@Composable
private fun KeptRow(
    liked: List<TrackDetailsDto>,
    kept: List<OfflineCollection>,
    downloadedTrackIds: Set<String>,
    onOpenLiked: () -> Unit,
    onOpen: (OfflineCollection) -> Unit
) {
    Column {
        HiFiSectionHeader(stringResource(R.string.offline_on_device), Modifier.padding(start = 12.dp, top = 6.dp, bottom = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (liked.isNotEmpty()) item(key = "liked") {
                KeptCard(stringResource(R.string.library_liked), liked.size, cover = null, isLiked = true, onClick = onOpenLiked)
            }
            items(kept, key = { it.key }) { collection ->
                KeptCard(collection.title, collection.trackIds.count { it in downloadedTrackIds }, collection.coverUri, isLiked = false, isAlbum = collection.isAlbum) { onOpen(collection) }
            }
        }
    }
}

@Composable
private fun KeptCard(title: String, count: Int, cover: String?, isLiked: Boolean, isAlbum: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(132.dp)
            .clip(shape)
            .hifiPress(onClick)
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(shape)
                .background(if (isLiked) Brush.linearGradient(listOf(Color(0xFF7A2E3A), HiFi.Love)) else Brush.linearGradient(listOf(HiFi.Placeholder, HiFi.Placeholder)))
        ) {
            if (isLiked || cover == null) {
                Icon(
                    when {
                        isLiked -> Icons.Rounded.Favorite
                        isAlbum -> Icons.Rounded.Album
                        else -> Icons.AutoMirrored.Rounded.QueueMusic
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (isLiked) 1f else 0.6f),
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).cover(cover, CoverSize.Medium).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x9E0A0B0F)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DownloadDone, contentDescription = stringResource(R.string.cd_skachan), tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text(title, style = HiFiType.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        Text(
            russianQuantityString(count, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
            style = HiFiType.Caption, maxLines = 1
        )
    }
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
    saved: List<UserPlaylistDto>,
    keptKeys: Set<String>,
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
            PageHeader(stringResource(R.string.library_playlists_row), onBack)
        }
        // The groups are named only when there is a second one
        if (saved.isNotEmpty()) item(key = "mine_title") { HiFiSectionHeader(stringResource(R.string.library_playlists_mine), Modifier.padding(bottom = 12.dp)) }
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
                            PlaylistCard(playlist, kept = OfflineCollection.keyOf(playlist) in keptKeys)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (saved.isNotEmpty()) {
            item(key = "saved_title") { HiFiSectionHeader(stringResource(R.string.library_playlists_saved), Modifier.padding(top = 8.dp, bottom = 12.dp)) }
            items(saved.chunked(columns), key = { row -> "saved_" + row.joinToString("|") { UserPlaylistsRepository.savedKey(it) } }, contentType = { "playlist_row" }) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { playlist ->
                        Column(Modifier.weight(1f).hifiPress({ onOpen(playlist) })) {
                            PlaylistCard(playlist, kept = OfflineCollection.keyOf(playlist) in keptKeys)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(onClick = onBack, contentDescription = stringResource(R.string.cd_nazad)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = HiFiType.LargeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp))
    }
}

@Composable
private fun ColumnScope.PlaylistCard(playlist: UserPlaylistDto, kept: Boolean) {
    Box {
        PlaylistCover(playlist, Modifier.fillMaxWidth().aspectRatio(1f).hifiSoftShadow(RoundedCornerShape(16.dp), 18.dp).clip(RoundedCornerShape(16.dp)))
        if (kept) KeptBadge(Modifier.align(Alignment.BottomEnd))
    }
    Spacer(Modifier.height(9.dp))
    Text(playlist.title ?: "", style = HiFiType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
        style = HiFiType.Caption
    )
}

/** On the device: the same mark as on a kept playlist in "Скачанные". */
@Composable
private fun KeptBadge(modifier: Modifier) {
    Box(
        modifier
            .padding(8.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0x9E0A0B0F)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.DownloadDone, contentDescription = stringResource(R.string.cd_skachan), tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Albums in the collection: marked in the official app or with the heart on an album page. */
@Composable
private fun AlbumsPage(
    modifier: Modifier,
    albums: List<AlbumDto>,
    keptKeys: Set<String>,
    columns: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (AlbumDto) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize().statusBarsPadding(), contentPadding = contentPadding) {
        item(key = "header") { PageHeader(stringResource(R.string.library_albums_row), onBack) }
        if (albums.isEmpty()) {
            item(key = "empty") {
                HiFiEmptyState(icon = Icons.Rounded.Album, title = stringResource(R.string.library_albums_empty_title), message = stringResource(R.string.library_albums_empty_message))
            }
        }
        items(albums.chunked(columns), key = { row -> "al_" + row.joinToString("|") { it.idString.orEmpty() } }, contentType = { "album_row" }) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { album ->
                    Column(Modifier.weight(1f).hifiPress({ onOpen(album) })) {
                        val shape = RoundedCornerShape(16.dp)
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).hifiSoftShadow(shape, 18.dp).clip(shape).background(HiFi.Placeholder)) {
                            if (album.coverUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).cover(album.coverUri, CoverSize.Medium).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).size(40.dp))
                            }
                            if (album.idString?.let(OfflineCollection::albumKey) in keptKeys) KeptBadge(Modifier.align(Alignment.BottomEnd))
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(album.title.orEmpty(), style = HiFiType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(albumCaption(album), style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "Исполнитель · 2019". */
internal fun albumCaption(album: AlbumDto): String =
    listOfNotNull(album.artists.mapNotNull { it.name }.joinToString(", ").ifBlank { null }, album.year?.toString()).joinToString(" · ")

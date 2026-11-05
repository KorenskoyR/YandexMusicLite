package com.korenskoy.yandexmusic.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.presentation.account.AccountViewModel
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogViewModel
import com.korenskoy.yandexmusic.presentation.crash.CrashLogViewModel
import com.korenskoy.yandexmusic.presentation.radio.RadioScreen
import com.korenskoy.yandexmusic.presentation.radio.RadioViewModel
import com.korenskoy.yandexmusic.presentation.update.UpdateViewModel

/**
 * What every tab needs to know about playback. Each tab collects the rest of its state itself, so a
 * change on one tab (search results, a download's progress) recomposes only that tab, not the app.
 */
@Immutable
data class NowPlaying(
    val track: TrackDetailsDto?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val source: PlaylistSource,
    val likedTrackIds: Set<String>
) {
    val trackId: String? get() = track?.idString
    val hasTrack: Boolean get() = track != null
    /** The wave is what plays (or is paused) right now. */
    val waveActive: Boolean get() = source == PlaylistSource.VIBE && track != null
}

/** The tracks after the current one (at most [limit]). Pure; unit-tested. */
fun upcomingOf(queue: List<TrackDetailsDto>, currentId: String?, limit: Int = 10): List<TrackDetailsDto> {
    val index = queue.indexOfFirst { it.idString == currentId }
    return if (index < 0) emptyList() else queue.drop(index + 1).take(limit)
}

@Composable
internal fun HomeTab(
    viewModel: MainViewModel,
    now: NowPlaying,
    progress: () -> Long,
    isLandscape: Boolean,
    contentBottom: Dp,
    onOpenPlayer: (() -> Unit)?
) {
    val queue by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val wavePreview by viewModel.wavePreview.collectAsStateWithLifecycle()
    val isFetchingWave by viewModel.isFetchingMoreQueue.collectAsStateWithLifecycle()
    val upcoming = remember(queue, now.trackId) { upcomingOf(queue, now.trackId) }
    val shelf = remember(now.waveActive, upcoming, wavePreview) { waveShelfOf(now.waveActive, upcoming, wavePreview) }
    val waveActive = now.waveActive
    HomeScreen(
        waveTrack = if (waveActive) now.track else null,
        isPlaying = now.isPlaying,
        isLoading = now.isBuffering && now.source == PlaylistSource.VIBE,
        isLiked = now.trackId?.let { it in now.likedTrackIds } == true,
        progress = progress,
        shelf = shelf,
        shimmer = isFetchingWave || now.isBuffering,
        // Like Yandex Music: the knob controls the wave; from any other context it starts the wave
        onPlayPause = { if (waveActive) viewModel.togglePlayPause() else viewModel.playMyVibe() },
        onLike = { now.track?.let { viewModel.toggleLike(it) } },
        onDislike = { now.track?.let { viewModel.dislikeTrack(it) } },
        onOpenPlayer = onOpenPlayer,
        onShelfClick = {
            if (waveActive) viewModel.playTrack(it.idString, queue, PlaylistSource.VIBE)
            else viewModel.playWaveFrom(it.idString)
        },
        contentBottom = if (waveActive && !isLandscape) 100.dp else contentBottom
    )
}

@Composable
internal fun SearchTab(
    viewModel: MainViewModel,
    searchVm: SearchViewModel,
    catalogVm: CatalogViewModel,
    now: NowPlaying
) {
    val query by searchVm.query.collectAsStateWithLifecycle()
    val results by searchVm.results.collectAsStateWithLifecycle()
    val isSearching by searchVm.isSearching.collectAsStateWithLifecycle()
    val found by searchVm.found.collectAsStateWithLifecycle()
    val history by searchVm.history.collectAsStateWithLifecycle()
    val weeklyHits by searchVm.weeklyHits.collectAsStateWithLifecycle()
    val trendTracks by searchVm.trendTracks.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        onQueryChange = { searchVm.onQueryChange(it) },
        results = results,
        weeklyHits = weeklyHits,
        trendTracks = trendTracks,
        isSearching = isSearching,
        currentTrackId = now.trackId,
        isPlaying = now.isPlaying,
        likedTrackIds = now.likedTrackIds,
        onTrackClick = { id, playlist ->
            searchVm.rememberQuery()
            viewModel.playTrack(id, playlist, PlaylistSource.SEARCH)
        },
        onToggleLike = { track -> viewModel.toggleLike(track) },
        onLoadMoreHits = { searchVm.loadMoreHits() },
        onLoadMoreTrends = { searchVm.loadMoreTrends() },
        hasActiveTrack = now.hasTrack,
        artists = found.artists,
        albums = found.albums,
        history = history,
        onArtistClick = { artist ->
            val id = artist.idString ?: return@SearchScreen
            searchVm.rememberQuery()
            catalogVm.openArtist(id, artist.name.orEmpty())
        },
        onAlbumClick = { album ->
            val id = album.idString ?: return@SearchScreen
            searchVm.rememberQuery()
            catalogVm.openAlbum(id, album.title.orEmpty())
        },
        onRemoveFromHistory = { searchVm.removeFromHistory(it) },
        onClearHistory = { searchVm.clearHistory() },
        onSubmit = { searchVm.rememberQuery() }
    )
}

@Composable
internal fun LibraryTab(
    viewModel: MainViewModel,
    accountVm: AccountViewModel,
    playlistsVm: PlaylistsViewModel,
    updateVm: UpdateViewModel,
    crashVm: CrashLogViewModel,
    now: NowPlaying,
    onOpenAccounts: () -> Unit,
    onOpenCrashLog: () -> Unit,
    onAddAccount: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.libraryLoadFailed.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadAllState.collectAsStateWithLifecycle()
    val autoDownloadEnabled by viewModel.autoDownloadEnabled.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val steeringWheelOnLeft by viewModel.steeringWheelOnLeft.collectAsStateWithLifecycle()
    val uiScaleAuto by viewModel.uiScaleAuto.collectAsStateWithLifecycle()
    val uiScaleManual by viewModel.uiScaleManual.collectAsStateWithLifecycle()
    val roadReserveMb by viewModel.roadReserveMb.collectAsStateWithLifecycle()
    val reserveUsedBytes by viewModel.reserveUsedBytes.collectAsStateWithLifecycle()
    val activeAccount by accountVm.activeAccount.collectAsStateWithLifecycle()
    val accountName by accountVm.accountName.collectAsStateWithLifecycle()
    val accountAvatarUrl by accountVm.accountAvatarUrl.collectAsStateWithLifecycle()
    val defaultAccountName by accountVm.defaultAccountName.collectAsStateWithLifecycle()
    val updateStatusMessage by updateVm.statusMessage.collectAsStateWithLifecycle()
    val crashReports by crashVm.reports.collectAsStateWithLifecycle()
    val userPlaylists by playlistsVm.playlists.collectAsStateWithLifecycle()
    val openedPlaylist by playlistsVm.opened.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (viewModel.tracks.value.isEmpty()) viewModel.loadLikedTracks()
    }

    LibraryScreen(
        tracks = tracks,
        isLoading = isLoading,
        loadFailed = loadFailed,
        downloadState = downloadState,
        autoDownloadEnabled = autoDownloadEnabled,
        currentTrackId = now.trackId,
        isPlaying = now.isPlaying,
        likedTrackIds = now.likedTrackIds,
        downloadedTrackIds = downloadedTrackIds,
        cacheSizeBytes = cacheSizeBytes,
        downloadProgress = downloadProgress,
        onTrackClick = { id -> viewModel.playTrack(id, tracks, PlaylistSource.LIBRARY) },
        onPlayAllClick = { viewModel.playAllLikedTracks() },
        onShuffleClick = { viewModel.shuffleLikedTracks() },
        onDownloadAllClick = { viewModel.downloadAllLikedTracks() },
        onEnableAutoDownload = { enabled ->
            if (enabled) viewModel.enableAutoDownloadAndStart() else viewModel.setAutoDownloadLikedTracks(false)
        },
        crossfadeSeconds = crossfadeSeconds,
        onCrossfadeChange = { viewModel.setCrossfadeSeconds(it) },
        steeringWheelOnLeft = steeringWheelOnLeft,
        onSteeringWheelChange = { viewModel.setSteeringWheelOnLeft(it) },
        uiScaleAuto = uiScaleAuto,
        onUiScaleAutoChange = { viewModel.setUiScaleAuto(it) },
        uiScaleManual = uiScaleManual,
        onUiScaleManualChange = { viewModel.setUiScaleManual(it) },
        onUpgradeDownloads = { viewModel.upgradeDownloadedQuality() },
        reserveMb = roadReserveMb,
        onReserveChange = { viewModel.setRoadReserveMb(it) },
        reserveUsedBytes = reserveUsedBytes,
        onClearReserve = { viewModel.clearRoadReserve() },
        crashReports = crashReports.size,
        onOpenCrashLog = onOpenCrashLog,
        onCancelDownload = { viewModel.cancelDownloadAll() },
        onClearCache = { viewModel.clearMusicCache() },
        onToggleLike = { track -> viewModel.toggleLike(track) },
        onRefresh = { viewModel.loadLikedTracks() },
        hasActiveTrack = now.hasTrack,
        accountName = accountName,
        avatarUrl = accountAvatarUrl,
        hasPlus = activeAccount?.hasPlus == true,
        defaultAccountName = defaultAccountName,
        updateStatus = updateStatusMessage,
        onCheckForUpdates = { updateVm.checkForUpdates(silent = false) },
        onAccountManagementClick = onOpenAccounts,
        onAddAccount = onAddAccount,
        onLogout = { accountVm.logoutCurrentAccount() },
        playlists = userPlaylists,
        openedPlaylist = openedPlaylist,
        onOpenPlaylist = { playlistsVm.open(it) },
        onClosePlaylist = { playlistsVm.close() },
        onPlayPlaylist = { playlistsVm.play(it) },
        onPlaylistTrackClick = { playlistsVm.playTrack(it) },
        onCreatePlaylist = { playlistsVm.create(it) },
        onRenamePlaylist = { playlist, title -> playlistsVm.rename(playlist, title) },
        onDeletePlaylist = { playlistsVm.delete(it) },
        onReorderPlaylist = { playlist, shown, new -> playlistsVm.reorder(playlist, shown, new) },
        onSetPlaylistPublic = { playlist, public -> playlistsVm.setPublic(playlist, public) }
    )
}

@Composable
internal fun RadioTab(viewModel: MainViewModel, radioVm: RadioViewModel, now: NowPlaying) {
    val stations by radioVm.stations.collectAsStateWithLifecycle()
    val favoriteStationIds by radioVm.favoriteStationIds.collectAsStateWithLifecycle()
    var selectedCategory by rememberSaveable { mutableStateOf(RadioCategory.ALL) }
    val filtered = remember(stations, favoriteStationIds, selectedCategory) {
        stations.filter {
            selectedCategory == RadioCategory.ALL ||
                (selectedCategory == RadioCategory.FAVORITES && it.id in favoriteStationIds) ||
                it.category == selectedCategory
        }
    }
    RadioScreen(
        stations = filtered,
        allStations = stations,
        selectedCategory = selectedCategory,
        onCategorySelect = { selectedCategory = it },
        currentStationId = now.track?.takeIf { it.isRadio }?.idString?.removePrefix("radio_"),
        isPlaying = now.isPlaying,
        favoriteStationIds = favoriteStationIds,
        onStationClick = { radioVm.play(it) },
        onToggleFavorite = { radioVm.toggleFavorite(it) },
        onPlayPause = { viewModel.togglePlayPause() },
        hasActiveTrack = now.hasTrack
    )
}

@Composable
internal fun CarTab(
    viewModel: MainViewModel,
    radioVm: RadioViewModel,
    now: NowPlaying,
    progress: () -> Long,
    isLandscape: Boolean,
    onExit: () -> Unit
) {
    val queue by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val steeringWheelOnLeft by viewModel.steeringWheelOnLeft.collectAsStateWithLifecycle()
    val track = now.track
    val nextTrack = remember(queue, now.trackId) { if (track?.isRadio == true) null else upcomingOf(queue, now.trackId, 1).firstOrNull() }
    CarScreen(
        currentTrack = track,
        isPlaying = now.isPlaying,
        likedTrackIds = now.likedTrackIds,
        onPlayPauseClick = { viewModel.togglePlayPause() },
        onNextClick = { viewModel.playNext() },
        onPrevClick = { viewModel.playPrev() },
        onToggleLike = { viewModel.toggleLike(it) },
        onDislikeClick = { viewModel.dislikeTrack(it) },
        onPlayMyVibe = { viewModel.playMyVibe() },
        onPlayFavorites = { viewModel.playAllLikedTracks() },
        onPlayHits = { viewModel.playHits(shuffle = true) },
        onPlayDownloaded = { viewModel.playDownloaded() },
        onPlayRadio = { radioVm.playFirst() },
        onExit = onExit,
        isLandscape = isLandscape,
        steeringWheelOnLeft = steeringWheelOnLeft,
        progress = progress,
        durationMs = track?.durationMs ?: 0L,
        onSeek = { viewModel.seekTo(it) },
        nextTrack = nextTrack,
        source = now.source,
        isBuffering = now.isBuffering
    )
}

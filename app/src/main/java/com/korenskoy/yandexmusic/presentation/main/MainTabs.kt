package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val tuning by viewModel.waveTuning.collectAsStateWithLifecycle()
    val seed by viewModel.waveSeed.collectAsStateWithLifecycle()
    var tuneOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (tuneOpen) {
        com.korenskoy.yandexmusic.presentation.main.components.WaveTuneSheet(tuning, onChange = viewModel::setWaveTuning, onDismiss = { tuneOpen = false })
    }
    HomeScreen(
        waveTrack = if (waveActive) now.track else null,
        isPlaying = now.isPlaying,
        isLoading = now.isBuffering && now.source == PlaylistSource.VIBE,
        isLiked = now.trackId?.let { it in now.likedTrackIds } == true,
        progress = progress,
        shelf = shelf,
        shimmer = isFetchingWave || now.isBuffering,
        // Like Yandex Music: the knob controls the wave; from any other context it starts the wave
        onPlayPause = { viewModel.waveKnob(waveActive) },
        onLike = { now.track?.let { viewModel.toggleLike(it) } },
        onDislike = { now.track?.let { viewModel.dislikeTrack(it) } },
        onOpenPlayer = onOpenPlayer,
        onShelfClick = {
            if (waveActive) viewModel.playTrack(it.idString, queue, PlaylistSource.VIBE)
            else viewModel.playWaveFrom(it.idString)
        },
        contentBottom = if (waveActive && !isLandscape) 100.dp else contentBottom,
        tuning = tuning,
        trackWaveSeed = seed?.takeIf { waveActive },
        onTune = { tuneOpen = true },
        onBackToMyWave = viewModel::returnToMyWave
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
    onAddAccount: () -> Unit,
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.libraryLoadFailed.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadAllState.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
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

    val settings = settingsState.copy(
        accountName = accountName,
        avatarUrl = accountAvatarUrl,
        hasPlus = activeAccount?.hasPlus == true,
        defaultAccountName = defaultAccountName,
        updateStatus = updateStatusMessage,
        crashReports = crashReports.size
    )
    val settingsActions = remember(settingsVm, accountVm, updateVm, viewModel) {
        SettingsActions(
            onAccountManagement = onOpenAccounts,
            onAddAccount = onAddAccount,
            onLogout = { accountVm.logoutCurrentAccount() },
            onCheckForUpdates = { updateVm.checkForUpdates(silent = false) },
            onOpenCrashLog = onOpenCrashLog,
            onCompatibleSoundChange = settingsVm::setCompatibleSound,
            onCrossfadeChange = settingsVm::setCrossfade,
            onNormalizeVolumeChange = settingsVm::setNormalizeVolume,
            onOpenEqualizer = settingsVm::openEqualizer,
            onStartMusicChange = settingsVm::setStartMusic,
            onBluetoothAutoplayChange = settingsVm::setBluetoothAutoplay,
            onSkipExplicitChange = settingsVm::setSkipExplicit,
            onReserveChange = settingsVm::setReserveMb,
            onClearReserve = settingsVm::clearReserve,
            onAutoDownloadChange = settingsVm::setAutoDownload,
            onDownloadAll = { viewModel.downloadAllLikedTracks() },
            onUpgradeDownloads = settingsVm::upgradeDownloads,
            onDownloadsOnCardChange = settingsVm::setDownloadsOnCard,
            onClearDownloads = { viewModel.clearMusicCache() },
            onStartPageChange = settingsVm::setStartPage,
            onExpandPlayerChange = settingsVm::setExpandPlayer,
            onSwipeToCloseChange = settingsVm::setSwipeToClose,
            onSteeringWheelChange = settingsVm::setSteeringWheelOnLeft,
            onCarClockChange = settingsVm::setCarClock,
            onUiScaleAutoChange = settingsVm::setUiScaleAuto,
            onUiScaleManualChange = settingsVm::setUiScaleManual,
            onAnimationModeChange = settingsVm::setAnimationMode
        )
    }

    LibraryScreen(
        tracks = tracks,
        isLoading = isLoading,
        loadFailed = loadFailed,
        downloadState = downloadState,
        currentTrackId = now.trackId,
        isPlaying = now.isPlaying,
        likedTrackIds = now.likedTrackIds,
        downloadedTrackIds = downloadedTrackIds,
        downloadProgress = downloadProgress,
        onTrackClick = { id -> viewModel.playTrack(id, tracks, PlaylistSource.LIBRARY) },
        onPlayAllClick = { viewModel.playAllLikedTracks() },
        onShuffleClick = { viewModel.shuffleLikedTracks() },
        onDownloadAllClick = { viewModel.downloadAllLikedTracks() },
        onCancelDownload = { viewModel.cancelDownloadAll() },
        settings = settings,
        settingsActions = settingsActions,
        onToggleLike = { track -> viewModel.toggleLike(track) },
        onRefresh = { viewModel.loadLikedTracks() },
        hasActiveTrack = now.hasTrack,
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
    val showClock by viewModel.carClock.collectAsStateWithLifecycle()
    val waveTuning by viewModel.waveTuning.collectAsStateWithLifecycle()
    val waveSeed by viewModel.waveSeed.collectAsStateWithLifecycle()
    val skipExplicit by viewModel.skipExplicit.collectAsStateWithLifecycle()
    val track = now.track
    // What will really play next, by the player's own rule (explicit passed over, a playlist goes on from its start)
    val upcoming = remember(queue, now.trackId, now.source, skipExplicit, track?.isRadio) {
        if (track == null || track.isRadio) emptyList()
        else PlaybackQueue.upcoming(queue, queue.indexOfFirst { it.idString == now.trackId }, now.source, 3) { skipExplicit && it.isExplicit }
    }
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
        upcoming = upcoming,
        // The wave with nothing fetched yet ahead: placeholders keep the row's place
        upcomingLoading = now.source == PlaylistSource.VIBE && track != null && !track.isRadio && upcoming.isEmpty(),
        onUpcomingClick = { viewModel.playTrack(it.idString, queue, now.source, byUser = false) },
        source = now.source,
        isBuffering = now.isBuffering,
        showClock = showClock,
        waveTuning = waveTuning,
        trackWaveSeed = waveSeed,
        onWaveTuningChange = viewModel::setWaveTuning,
        onTrackWave = viewModel::startTrackWave,
        onBackToMyWave = viewModel::returnToMyWave
    )
}

package com.korenskoy.yandexmusic.presentation.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassNavigationRail
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.components.MainTab
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.account.AccountManagementDialog
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit = {}
) {
    val tracks by viewModel.tracks.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDownloadingAll by viewModel.isDownloadingAll.collectAsState()
    val autoDownloadEnabled by viewModel.autoDownloadEnabled.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsState()
    val favoriteStationIds by viewModel.radioFavoriteStationIds.collectAsState()
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val accountName by viewModel.accountName.collectAsState()

    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()
    val isFetchingMoreQueue by viewModel.isFetchingMoreQueue.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentTrackId by viewModel.playerRepository.currentTrackId.collectAsState(null)
    val isPlaying by viewModel.playerRepository.isPlaying.collectAsState(false)
    val progress by viewModel.playerRepository.progress.collectAsState(0L)
    
    val weeklyHits by viewModel.weeklyHits.collectAsState()
    val trendTracks by viewModel.trendTracks.collectAsState()
    val popularTracks by viewModel.popularTracks.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadLikedTracks()
        viewModel.loadPopularTracks()
    }

    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.LIBRARY && tracks.isEmpty()) {
            viewModel.loadLikedTracks()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: Full-bleed Edge-to-Edge Content Area
        // In landscape, offset content to the right to avoid NavigationRail overlap
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    isPlaying = isPlaying,
                    isWavePlaying = currentSource == MainViewModel.PlaylistSource.VIBE,
                    onPlayPauseVibe = {
                        if (currentSource == MainViewModel.PlaylistSource.VIBE) {
                            if (isPlaying) viewModel.playerRepository.pause()
                            else viewModel.playerRepository.resume()
                        } else {
                            viewModel.playMyVibe(forceRestart = true)
                        }
                    }
                )
                MainTab.SEARCH -> SearchScreen(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    results = searchResults,
                    weeklyHits = weeklyHits,
                    trendTracks = trendTracks,
                    isSearching = isSearching,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    likedTrackIds = likedTrackIds,
                    onTrackClick = { id, playlist -> viewModel.playTrack(id, playlist) },
                    onToggleLike = { track -> viewModel.toggleLike(track) },
                    onLoadMoreHits = { viewModel.loadMoreHits() },
                    onLoadMoreTrends = { viewModel.loadMoreTrends() },
                    hasActiveTrack = currentTrack != null
                )
                MainTab.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    isLoading = isLoading,
                    isDownloadingAll = isDownloadingAll,
                    autoDownloadEnabled = autoDownloadEnabled,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    likedTrackIds = likedTrackIds,
                    downloadedTrackIds = downloadedTrackIds,
                    cacheSizeBytes = cacheSizeBytes,
                    downloadProgress = downloadProgress,
                    onTrackClick = { id -> viewModel.playTrack(id, tracks) },
                    onPlayAllClick = { viewModel.playAllLikedTracks() },
                    onShuffleClick = { viewModel.shuffleLikedTracks() },
                    onDownloadAllClick = { viewModel.downloadAllLikedTracks() },
                    onEnableAutoDownload = { viewModel.enableAutoDownloadAndStart() },
                    onCancelDownload = { viewModel.cancelDownloadAll() },
                    onClearCache = { viewModel.clearMusicCache() },
                    onToggleLike = { track -> viewModel.toggleLike(track) },
                    onRefresh = { viewModel.loadLikedTracks() },
                    hasActiveTrack = currentTrack != null,
                    accountName = accountName,
                    updateStatus = updateStatusMessage,
                    onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
                    onAccountManagementClick = { showAccountDialog = true },
                    onLogout = { showAccountDialog = true }
                )
                MainTab.RADIO -> {
                    val radioStations by viewModel.radioStations.collectAsState()
                    val radioSearchQuery by remember { mutableStateOf("") }
                    var radioQueryState by remember { mutableStateOf("") }
                    var selectedCategory by remember { mutableStateOf(com.korenskoy.yandexmusic.domain.model.RadioCategory.ALL) }
                    val favoriteStationIds by viewModel.radioFavoriteStationIds.collectAsState()
                    
                    val filteredStations = radioStations.filter {
                        (selectedCategory == com.korenskoy.yandexmusic.domain.model.RadioCategory.ALL || 
                         (selectedCategory == com.korenskoy.yandexmusic.domain.model.RadioCategory.FAVORITES && favoriteStationIds.contains(it.id)) ||
                         it.category == selectedCategory) &&
                        (radioQueryState.isEmpty() || it.name.contains(radioQueryState, ignoreCase = true) || it.category.title.contains(radioQueryState, ignoreCase = true) || it.frequency.contains(radioQueryState, ignoreCase = true))
                    }
                    
                    com.korenskoy.yandexmusic.presentation.radio.RadioScreen(
                        stations = filteredStations,
                        searchQuery = radioQueryState,
                        onQueryChange = { radioQueryState = it },
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        currentStationId = currentTrack?.idString?.removePrefix("radio_"),
                        isPlaying = isPlaying,
                        favoriteStationIds = favoriteStationIds,
                        onStationClick = { viewModel.playRadioStation(it) },
                        onToggleFavorite = { viewModel.toggleRadioFavorite(it) },
                        hasActiveTrack = currentTrack != null
                    )
                }
                MainTab.CAR -> CarScreen(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    likedTrackIds = likedTrackIds,
                    onPlayPauseClick = {
                        if (isPlaying) viewModel.playerRepository.pause()
                        else viewModel.playerRepository.resume()
                    },
                    onNextClick = { viewModel.playNext() },
                    onPrevClick = { viewModel.playPrev() },
                    onToggleLike = { track ->
                        if (track.isRadio) {
                            viewModel.toggleRadioFavorite(track.idString.removePrefix("radio_"))
                        } else {
                            viewModel.toggleLike(track)
                        }
                    },
                    onDislikeClick = { track ->
                        if (track.isRadio) {
                            viewModel.removeRadioFavorite(track.idString.removePrefix("radio_"))
                        } else {
                            viewModel.dislikeTrack(track)
                        }
                    },
                    onPlayMyVibe = { viewModel.playMyVibe(forceRestart = true) },
                    onPlayFavorites = { viewModel.playAllLikedTracks() },
                    onPlayHits = { viewModel.playHits(shuffle = true) },
                    onPlayDownloaded = {
                        val downloaded = tracks.filter { downloadedTrackIds.contains(it.idString) }
                        if (downloaded.isNotEmpty()) {
                            viewModel.playTrack(downloaded.first().idString, downloaded)
                        }
                    },
                    onPlayRadio = {
                        val stations = viewModel.radioStations.value
                        if (stations.isNotEmpty()) {
                            viewModel.playRadioStation(stations.first())
                        }
                    },
                    onExit = { currentTab = MainTab.HOME },
                    isLandscape = isLandscape,
                    progress = progress,
                    durationMs = currentTrack?.durationMs ?: 0L,
                    onSeek = { viewModel.seekTo(it) }
                )
            }
        }

        // Layer 1 & 2: Floating Navigation and MiniPlayer Overlays
        if (isLandscape) {
            // Landscape Navigation Rail (hidden in CAR mode)
            if (currentTab != MainTab.CAR) {
                FloatingGlassNavigationRail(
                    currentRoute = currentTab.route,
                    onNavigate = { currentTab = it },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp, top = 16.dp, bottom = 16.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            // Landscape MiniPlayer in Bottom Right (hidden in CAR mode)
            if (currentTrack != null && currentTab != MainTab.CAR) {
                val isMiniLiked = if (currentTrack?.isRadio == true) {
                    favoriteStationIds.contains(currentTrack!!.idString.removePrefix("radio_"))
                } else {
                    likedTrackIds.contains(currentTrack!!.idString)
                }
                MiniPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    isLiked = isMiniLiked,
                    progress = progress,
                    onPlayPauseClick = {
                        if (isPlaying) viewModel.playerRepository.pause()
                        else viewModel.playerRepository.resume()
                    },
                    onToggleLike = {
                        if (currentTrack?.isRadio == true) {
                            viewModel.toggleRadioFavorite(currentTrack!!.idString.removePrefix("radio_"))
                        } else {
                            viewModel.toggleLike(currentTrack!!)
                        }
                    },
                    onClick = {
                        isPlayerExpanded = true
                    },
                    onDismiss = {
                        viewModel.stopAndClearPlayer()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .widthIn(max = 580.dp)
                        .fillMaxWidth()
                )
            }
        } else {
            // Portrait Unified Bottom Overlay: MiniPlayer stacked cleanly above FloatingGlassBottomBar
            if (currentTab != MainTab.CAR) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentTrack != null) {
                        val isMiniLiked = if (currentTrack?.isRadio == true) {
                            favoriteStationIds.contains(currentTrack!!.idString.removePrefix("radio_"))
                        } else {
                            likedTrackIds.contains(currentTrack!!.idString)
                        }
                        MiniPlayer(
                            track = currentTrack!!,
                            isPlaying = isPlaying,
                            isLiked = isMiniLiked,
                            progress = progress,
                            onPlayPauseClick = {
                                if (isPlaying) viewModel.playerRepository.pause()
                                else viewModel.playerRepository.resume()
                            },
                            onToggleLike = {
                                if (currentTrack?.isRadio == true) {
                                    viewModel.toggleRadioFavorite(currentTrack!!.idString.removePrefix("radio_"))
                                } else {
                                    viewModel.toggleLike(currentTrack!!)
                                }
                            },
                            onClick = {
                                isPlayerExpanded = true
                            },
                            onDismiss = {
                                viewModel.stopAndClearPlayer()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    FloatingGlassBottomBar(
                        currentRoute = currentTab.route,
                        onNavigate = { currentTab = it }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentTrack != null) {
                val isPlayerLiked = if (currentTrack?.isRadio == true) {
                    favoriteStationIds.contains(currentTrack!!.idString.removePrefix("radio_"))
                } else {
                    likedTrackIds.contains(currentTrack!!.idString)
                }
                FullScreenPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    isLiked = isPlayerLiked,
                    progress = progress,
                    queue = currentPlaylist,
                    onPlayPauseClick = {
                        if (isPlaying) viewModel.playerRepository.pause()
                        else viewModel.playerRepository.resume()
                    },
                    onToggleLike = {
                        if (currentTrack?.isRadio == true) {
                            viewModel.toggleRadioFavorite(currentTrack!!.idString.removePrefix("radio_"))
                        } else {
                            viewModel.toggleLike(currentTrack!!)
                        }
                    },
                    onNextClick = { viewModel.playNext() },
                    onPrevClick = { viewModel.playPrev() },
                    onSeekTo = { position -> viewModel.playerRepository.seekTo(position) },
                    onTrackSelect = { id -> viewModel.playTrack(id, currentPlaylist) },
                    onRemoveFromQueue = { id -> viewModel.removeFromQueue(id) },
                    onCollapse = { isPlayerExpanded = false },
                    onCloseAndClear = { 
                        isPlayerExpanded = false
                        viewModel.stopAndClearPlayer()
                    },
                    onDislikeClick = {
                        if (currentTrack?.isRadio == true) {
                            viewModel.removeRadioFavorite(currentTrack!!.idString.removePrefix("radio_"))
                        } else {
                            viewModel.dislikeTrack(currentTrack!!)
                        }
                    },
                    onLoadMoreQueue = { viewModel.loadMoreQueue() },
                    isFetchingMoreQueue = isFetchingMoreQueue
                )
            }
        }

        // In-App Update Dialog
        if (updateCheckResult is com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable) {
            val updateAvailable = updateCheckResult as com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable
            com.korenskoy.yandexmusic.presentation.update.UpdateDialog(
                updateInfo = updateAvailable,
                downloadProgress = updateDownloadProgress,
                statusMessage = updateStatusMessage,
                onStartDownload = {
                    viewModel.startUpdateDownload(updateAvailable.apkDownloadUrl)
                },
                onInstall = {
                    viewModel.installDownloadedUpdate()
                },
                onDismiss = {
                    viewModel.dismissUpdateDialog()
                }
            )
        }

        // Account Management Dialog
        if (showAccountDialog) {
            AccountManagementDialog(
                accounts = allAccounts,
                activeAccount = activeAccount,
                onSwitchAccount = { uid -> viewModel.switchAccount(uid) },
                onRemoveAccount = { uid -> viewModel.removeAccount(uid) },
                onAddNewAccount = { onNavigateToAuth() },
                onLogoutCurrent = { viewModel.logoutCurrentAccount() },
                onLogoutAll = { viewModel.logoutAllAccounts() },
                onDismiss = { showAccountDialog = false }
            )
        }
    }
}

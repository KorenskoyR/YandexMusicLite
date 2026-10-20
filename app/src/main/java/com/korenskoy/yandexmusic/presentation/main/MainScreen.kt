package com.korenskoy.yandexmusic.presentation.main

import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.runtime.State
import com.korenskoy.yandexmusic.data.player.PlaylistSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit = {},
    openPlayerSignal: Int = 0
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
    val accountAvatarUrl by viewModel.accountAvatarUrl.collectAsState()
    val defaultAccountUid by viewModel.defaultAccountUid.collectAsState()
    val defaultAccountName by viewModel.defaultAccountName.collectAsState()

    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()
    val isSignatureMismatch by viewModel.isSignatureMismatch.collectAsState()
    val isFetchingMoreQueue by viewModel.isFetchingMoreQueue.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val streamQuality by viewModel.streamQuality.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val openedPlaylist by viewModel.openedPlaylist.collectAsState()
    val downloadOverWifiOnly by viewModel.downloadOverWifiOnly.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    // Stable local copy: allows smart casts instead of `!!` in the blocks below
    val shownTrack = currentTrack
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    // Progress ticks 10x per second: keep the State and read it only inside WithProgress,
    // so the whole screen (lists included) is not recomposed on every tick.
    val progressState = viewModel.progress.collectAsState()
    
    val weeklyHits by viewModel.weeklyHits.collectAsState()
    val trendTracks by viewModel.trendTracks.collectAsState()
    val popularTracks by viewModel.popularTracks.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && viewModel.currentTrack.value != null) isPlayerExpanded = true
    }
    var showAccountDialog by remember { mutableStateOf(false) }
    var hasHandledStartupAccountPicker by rememberSaveable { mutableStateOf(false) }
    val showStartupAccountPicker = !hasHandledStartupAccountPicker && allAccounts.size > 1 && defaultAccountUid == null

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (updateDownloadProgress >= 2f && viewModel.canRequestPackageInstalls) {
                    viewModel.installDownloadedUpdate()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.LIBRARY && tracks.isEmpty()) {
            viewModel.loadLikedTracks()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    androidx.compose.runtime.CompositionLocalProvider(com.korenskoy.yandexmusic.utils.LocalStreamQuality provides streamQuality) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 0: Full-bleed Edge-to-Edge Content Area
            // In landscape, offset content to the right to avoid NavigationRail overlap
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentTab) {
                    MainTab.HOME -> HomeScreen(
                        isPlaying = isPlaying,
                        isWavePlaying = currentSource == PlaylistSource.VIBE,
                        isLoading = isBuffering && currentSource == PlaylistSource.VIBE,
                        onPlayPauseVibe = {
                            // Like Yandex Music: the wave button controls the wave; from any other context it starts the wave.
                            if (currentSource == PlaylistSource.VIBE && currentTrack != null) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.playMyVibe()
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
                        onTrackClick = { id, playlist -> viewModel.playTrack(id, playlist, PlaylistSource.SEARCH) },
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
                        onTrackClick = { id -> viewModel.playTrack(id, tracks, PlaylistSource.LIBRARY) },
                        onPlayAllClick = { viewModel.playAllLikedTracks() },
                        onShuffleClick = { viewModel.shuffleLikedTracks() },
                        onDownloadAllClick = { viewModel.downloadAllLikedTracks() },
                        onEnableAutoDownload = { enabled ->
                            if (enabled) viewModel.enableAutoDownloadAndStart() else viewModel.setAutoDownloadLikedTracks(false)
                        },
                        crossfadeSeconds = crossfadeSeconds,
                        onCrossfadeChange = { viewModel.setCrossfadeSeconds(it) },
                        wifiOnly = downloadOverWifiOnly,
                        onWifiOnlyChange = { viewModel.setDownloadOverWifiOnly(it) },
                        onCancelDownload = { viewModel.cancelDownloadAll() },
                        onClearCache = { viewModel.clearMusicCache() },
                        onToggleLike = { track -> viewModel.toggleLike(track) },
                        onRefresh = { viewModel.loadLikedTracks() },
                        hasActiveTrack = currentTrack != null,
                        accountName = accountName,
                        avatarUrl = accountAvatarUrl,
                        defaultAccountName = defaultAccountName,
                        updateStatus = updateStatusMessage,
                        onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
                        onAccountManagementClick = { showAccountDialog = true },
                        onLogout = { showAccountDialog = true },
                        playlists = userPlaylists,
                        openedPlaylist = openedPlaylist,
                        onOpenPlaylist = { viewModel.openPlaylist(it) },
                        onClosePlaylist = { viewModel.closePlaylist() },
                        onPlayPlaylist = { viewModel.playOpenedPlaylist(it) },
                        onPlaylistTrackClick = { viewModel.playOpenedPlaylistTrack(it) }
                    )
                    MainTab.RADIO -> {
                        val radioStations by viewModel.radioStations.collectAsState()
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
                    MainTab.CAR -> WithProgress(progressState) { progress ->
                        CarScreen(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            likedTrackIds = likedTrackIds,
                            onPlayPauseClick = {
                                viewModel.togglePlayPause()
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
                            onPlayMyVibe = { viewModel.playMyVibe() },
                            onPlayFavorites = { viewModel.playAllLikedTracks() },
                            onPlayHits = { viewModel.playHits(shuffle = true) },
                            onPlayDownloaded = {
                                val downloaded = tracks.filter { downloadedTrackIds.contains(it.idString) }
                                if (downloaded.isNotEmpty()) {
                                    viewModel.playTrack(downloaded.first().idString, downloaded, PlaylistSource.LIBRARY)
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
                if (shownTrack != null && currentTab != MainTab.CAR) {
                    val isMiniLiked = if (currentTrack?.isRadio == true) {
                        favoriteStationIds.contains(shownTrack.idString.removePrefix("radio_"))
                    } else {
                        likedTrackIds.contains(shownTrack.idString)
                    }
                    WithProgress(progressState) { progress ->
                        MiniPlayer(
                            track = shownTrack,
                            isPlaying = isPlaying,
                            isLiked = isMiniLiked,
                            progress = progress,
                            onPlayPauseClick = {
                                viewModel.togglePlayPause()
                            },
                            onToggleLike = {
                                if (currentTrack?.isRadio == true) {
                                    viewModel.toggleRadioFavorite(shownTrack.idString.removePrefix("radio_"))
                                } else {
                                    viewModel.toggleLike(shownTrack)
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
                        if (shownTrack != null) {
                            val isMiniLiked = if (currentTrack?.isRadio == true) {
                                favoriteStationIds.contains(shownTrack.idString.removePrefix("radio_"))
                            } else {
                                likedTrackIds.contains(shownTrack.idString)
                            }
                            WithProgress(progressState) { progress ->
                                MiniPlayer(
                                    track = shownTrack,
                                    isPlaying = isPlaying,
                                    isLiked = isMiniLiked,
                                    progress = progress,
                                    onPlayPauseClick = {
                                        viewModel.togglePlayPause()
                                    },
                                    onToggleLike = {
                                        if (currentTrack?.isRadio == true) {
                                            viewModel.toggleRadioFavorite(shownTrack.idString.removePrefix("radio_"))
                                        } else {
                                            viewModel.toggleLike(shownTrack)
                                        }
                                    },
                                    onClick = {
                                        isPlayerExpanded = true
                                    },
                                    onDismiss = {
                                        viewModel.stopAndClearPlayer()
                                    }
                                )
                            }
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
                if (shownTrack != null) {
                    val isPlayerLiked = if (currentTrack?.isRadio == true) {
                        favoriteStationIds.contains(shownTrack.idString.removePrefix("radio_"))
                    } else {
                        likedTrackIds.contains(shownTrack.idString)
                    }
                    WithProgress(progressState) { progress ->
                        FullScreenPlayer(
                            track = shownTrack,
                            isPlaying = isPlaying,
                            isLiked = isPlayerLiked,
                            progress = progress,
                            queue = currentPlaylist,
                            onPlayPauseClick = {
                                viewModel.togglePlayPause()
                            },
                            onToggleLike = {
                                if (currentTrack?.isRadio == true) {
                                    viewModel.toggleRadioFavorite(shownTrack.idString.removePrefix("radio_"))
                                } else {
                                    viewModel.toggleLike(shownTrack)
                                }
                            },
                            onNextClick = { viewModel.playNext() },
                            onPrevClick = { viewModel.playPrev() },
                            onSeekTo = { position -> viewModel.seekTo(position) },
                            onTrackSelect = { id -> viewModel.playTrack(id, currentPlaylist) },
                            onRemoveFromQueue = { id -> viewModel.removeFromQueue(id) },
                            onCollapse = { isPlayerExpanded = false },
                            onCloseAndClear = { 
                                isPlayerExpanded = false
                                viewModel.stopAndClearPlayer()
                            },
                            onDislikeClick = {
                                if (currentTrack?.isRadio == true) {
                                    viewModel.removeRadioFavorite(shownTrack.idString.removePrefix("radio_"))
                                } else {
                                    viewModel.dislikeTrack(shownTrack)
                                }
                            },
                            onLoadMoreQueue = { viewModel.loadMoreQueue() },
                            isFetchingMoreQueue = isFetchingMoreQueue,
                            sleepTimer = sleepTimer,
                            onSleepTimer = { viewModel.setSleepTimer(it) },
                            onSleepAtEndOfTrack = { viewModel.setSleepAtEndOfTrack() },
                            onCancelSleepTimer = { viewModel.cancelSleepTimer() }
                        )
                    }
                }
            }

            // Playback errors ("no connection", "station unavailable"), above the mini-player and bottom bar
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            val messageLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            LaunchedEffect(messageLifecycleOwner) {
                // Collect only while visible, so a message raised in the background falls back to a toast
                messageLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    viewModel.playbackMessages.collect { snackbarHostState.showSnackbar(it) }
                }
            }
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (shownTrack != null) 160.dp else 90.dp)
            )

            // In-App Update Dialog
            if (updateCheckResult is com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable) {
                val updateAvailable = updateCheckResult as com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable
                com.korenskoy.yandexmusic.presentation.update.UpdateDialog(
                    updateInfo = updateAvailable,
                    downloadProgress = updateDownloadProgress,
                    statusMessage = updateStatusMessage,
                    hasInstallPermission = viewModel.canRequestPackageInstalls,
                    isSignatureMismatch = isSignatureMismatch,
                    onRequestPermission = { viewModel.openInstallPermissionSettings() },
                    onStartDownload = {
                        viewModel.startUpdateDownload(updateAvailable.apkDownloadUrl)
                    },
                    onInstall = {
                        viewModel.installDownloadedUpdate(forceSystemInstall = true)
                    },
                    onShareOrSave = {
                        viewModel.shareOrSaveDownloadedApk()
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
                    defaultAccountUid = defaultAccountUid,
                    onSetDefaultAccount = { uid -> viewModel.setDefaultAccount(uid) },
                    onSwitchAccount = { uid -> viewModel.switchAccount(uid) },
                    onRemoveAccount = { uid -> viewModel.removeAccount(uid) },
                    onAddNewAccount = { onNavigateToAuth() },
                    onLogoutCurrent = { viewModel.logoutCurrentAccount() },
                    onLogoutAll = { viewModel.logoutAllAccounts() },
                    onDismiss = { showAccountDialog = false }
                )
            }

            // Startup Account Picker Dialog (shown when multiple accounts exist and no default user set)
            if (showStartupAccountPicker) {
                com.korenskoy.yandexmusic.presentation.account.StartupAccountPickerDialog(
                    accounts = allAccounts,
                    activeAccount = activeAccount,
                    onSelectAccount = { uid, rememberAsDefault ->
                        viewModel.switchAccount(uid)
                        if (rememberAsDefault) {
                            viewModel.setDefaultAccount(uid)
                        }
                        hasHandledStartupAccountPicker = true
                    },
                    onAddNewAccount = {
                        hasHandledStartupAccountPicker = true
                        onNavigateToAuth()
                    }
                )
            }
        }
    }
}

/** Reads the progress state in its own recomposition scope so only [content] recomposes on each tick. */
@Composable
private fun WithProgress(progress: State<Long>, content: @Composable (Long) -> Unit) {
    content(progress.value)
}

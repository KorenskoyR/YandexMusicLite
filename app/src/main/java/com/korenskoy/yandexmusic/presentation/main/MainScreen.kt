package com.korenskoy.yandexmusic.presentation.main

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import com.korenskoy.yandexmusic.presentation.main.components.DockScrim
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import com.korenskoy.yandexmusic.presentation.hifi.LocalLinkState
import com.korenskoy.yandexmusic.utils.CoverSize
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.account.AccountViewModel
import com.korenskoy.yandexmusic.presentation.catalog.CatalogActions
import com.korenskoy.yandexmusic.presentation.catalog.CatalogPageScreen
import com.korenskoy.yandexmusic.presentation.catalog.CatalogViewModel
import com.korenskoy.yandexmusic.presentation.crash.CrashLogViewModel
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.TintMath
import com.korenskoy.yandexmusic.presentation.hifi.rememberArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.rememberHiFiLite
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassBottomBar
import com.korenskoy.yandexmusic.presentation.main.components.DockBottomGap
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import com.korenskoy.yandexmusic.presentation.main.components.FloatingGlassDock
import com.korenskoy.yandexmusic.presentation.main.components.LocalTrackMenu
import com.korenskoy.yandexmusic.presentation.main.components.MainTab
import com.korenskoy.yandexmusic.presentation.main.components.MiniPlayer
import com.korenskoy.yandexmusic.presentation.main.components.TrackMenuLauncher
import com.korenskoy.yandexmusic.presentation.player.FullScreenPlayer
import com.korenskoy.yandexmusic.presentation.player.QualityDetailsDialog
import com.korenskoy.yandexmusic.presentation.player.playerSourceLabelRes
import com.korenskoy.yandexmusic.presentation.radio.RadioViewModel
import com.korenskoy.yandexmusic.presentation.update.UpdateViewModel
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.utils.LocalStreamQuality
import com.korenskoy.yandexmusic.utils.toCoverUrl
import kotlinx.coroutines.flow.merge

private const val PLAYER_COVER_KEY = "player_cover"

/**
 * The app's frame: the tab content, the floating mini-player and tab bar, the full-screen player and
 * the dialogs. Only what all of them share is collected here (the playing track, play state, likes);
 * every tab collects its own state, so a change on one tab does not recompose the others.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit = {},
    openPlayerSignal: Int = 0,
    accountVm: AccountViewModel = hiltViewModel(),
    searchVm: SearchViewModel = hiltViewModel(),
    playlistsVm: PlaylistsViewModel = hiltViewModel(),
    radioVm: RadioViewModel = hiltViewModel(),
    updateVm: UpdateViewModel = hiltViewModel(),
    catalogVm: CatalogViewModel = hiltViewModel(),
    crashVm: CrashLogViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
    val likedTrackIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    val favoriteStationIds by radioVm.favoriteStationIds.collectAsStateWithLifecycle()
    val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()
    val catalogStack by catalogVm.stack.collectAsStateWithLifecycle()
    val radioStations by radioVm.stations.collectAsStateWithLifecycle()
    // Progress ticks 10× a second: the State is handed out as `{ progressState.value }` and read only where
    // it is drawn, so nothing here recomposes on a tick
    val progressState = viewModel.progress.collectAsStateWithLifecycle()
    val progress = remember(progressState) { { progressState.value } }

    val now = NowPlaying(currentTrack, isPlaying, isBuffering, currentSource, likedTrackIds)

    // The tab chosen in this session; before any choice, the one the settings open on
    var chosenTab by rememberSaveable { mutableStateOf<MainTab?>(null) }
    val startTab by viewModel.startTab.collectAsStateWithLifecycle()
    val currentTab = chosenTab ?: startTab ?: MainTab.HOME
    val tabKnown = chosenTab != null || startTab != null
    val currentTabState = androidx.compose.runtime.rememberUpdatedState(currentTab)
    LaunchedEffect(currentTab, tabKnown) { if (tabKnown) viewModel.rememberTab(currentTab) }
    LaunchedEffect(Unit) { viewModel.applyStartMusic() }
    val swipeToClose by viewModel.swipeToCloseMiniPlayer.collectAsStateWithLifecycle()
    val animationMode by viewModel.animationMode.collectAsStateWithLifecycle()
    var isPlayerExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && viewModel.currentTrack.value != null) isPlayerExpanded = true
    }
    // "Open the player when the music starts": only for music the user started, never in the car
    LaunchedEffect(Unit) {
        viewModel.openPlayerRequests.collect { if (currentTabState.value != MainTab.CAR) isPlayerExpanded = true }
    }
    // Nothing to show: a stale "expanded" would open an empty player with the next track
    LaunchedEffect(currentTrack == null) { if (currentTrack == null) isPlayerExpanded = false }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var startupPickerHandled by rememberSaveable { mutableStateOf(false) }
    var qualityDetails by remember { mutableStateOf<PlaybackController.QualityDetails?>(null) }
    var trackMenu by remember { mutableStateOf<TrackMenuRequest?>(null) }
    val trackMenuLauncher = remember {
        TrackMenuLauncher { track, playlist -> trackMenu = TrackMenuRequest(track, playlist, pickPlaylist = false) }
    }
    val catalogActions = remember(catalogVm, viewModel) {
        CatalogActions(
            onBack = { catalogVm.back() },
            onRetry = { catalogVm.retry() },
            onOpenArtist = { id, name -> catalogVm.openArtist(id, name) },
            onOpenAlbum = { id, title -> catalogVm.openAlbum(id, title) },
            onPlay = { tracks, trackId, shuffle -> catalogVm.play(tracks, trackId, shuffle) },
            onToggleLike = { viewModel.toggleLike(it) }
        )
    }
    // Another tab: the catalog pages belong to where they were opened
    LaunchedEffect(currentTab) { catalogVm.closeAll() }
    InstallDownloadedUpdateOnResume(updateVm)
    PrefetchCovers(viewModel, currentTrack)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // One tint for the whole app: the playing cover, or the station's brand colours on air
    val autoLite = rememberHiFiLite()
    val lite = when (animationMode) {
        AnimationMode.FULL -> false
        AnimationMode.MINIMUM -> true
        else -> autoLite
    }
    val stationTint = remember(currentTrack?.idString, radioStations) {
        val track = currentTrack
        if (track?.isRadio != true) null
        else radioStations.firstOrNull { it.id == track.idString.removePrefix("radio_") }?.let {
            ArtworkTint(Color(TintMath.normalize(it.brandColorStart.toInt())), Color(TintMath.normalize(it.brandColorEnd.toInt())))
        }
    }
    val coverForTint = if (currentTrack?.isRadio == true) null else currentTrack?.coverUri.toCoverUrl(CoverSize.Small)
    val appTint = rememberArtworkTint(coverForTint, stationTint)
    val tabStates = rememberSaveableStateHolder()
    val contentBottom = if (isLandscape) DockClearance else if (currentTrack != null) 168.dp else 100.dp
    // On a side a notch or a three-button bar sits next to the page, and the pages run edge to edge
    val sideInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)
    // The mini-player stays out of the way on the wave page, which already shows the playing wave
    val showMini = currentTrack != null && currentTab != MainTab.CAR && !(currentTab == MainTab.HOME && now.waveActive)
    val snackbar = remember { SnackbarHostState() }
    val openPlayer = { isPlayerExpanded = true }

    CompositionLocalProvider(
        LocalStreamQuality provides streamQuality,
        LocalLinkState provides linkState,
        LocalTrackMenu provides trackMenuLauncher,
        LocalHiFiLite provides lite,
        LocalArtworkTint provides appTint
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            Box(modifier = Modifier.fillMaxSize()) {
                // One living background under every tab: it does not blink when tabs change
                if (currentTab != MainTab.CAR) {
                    HiFiBackdrop(coverUrl = coverForTint)
                }
                // Nothing until the start tab is known: no other tab flashes on the way
                if (tabKnown) AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(HiFiMotion.standard()) + slideInVertically(HiFiMotion.standard()) { it / 100 }) togetherWith
                            fadeOut(HiFiMotion.quick())
                    },
                    label = "tabs",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    tabStates.SaveableStateProvider(tab.route) {
                        // An artist or album page covers the tab until Back (or another tab) closes it. Pages
                        // slide in from the right as they open and back out to the right on Back.
                        val depth = if (tab == MainTab.CAR) 0 else catalogStack.size
                        AnimatedContent(
                            targetState = depth to catalogStack.lastOrNull()?.takeIf { depth > 0 },
                            contentKey = { (_, page) -> page?.page },
                            transitionSpec = { catalogTransition(forward = targetState.first > initialState.first, lite = lite) },
                            label = "catalog_page",
                            modifier = if (isLandscape && tab != MainTab.CAR) Modifier.windowInsetsPadding(sideInsets) else Modifier
                        ) { (_, catalogTop) ->
                        if (catalogTop != null) {
                            CatalogPageScreen(
                                state = catalogTop,
                                currentTrackId = now.trackId,
                                isPlaying = isPlaying,
                                likedTrackIds = likedTrackIds,
                                contentBottom = contentBottom,
                                actions = catalogActions
                            )
                        } else when (tab) {
                            MainTab.HOME -> HomeTab(
                                viewModel, now, progress, isLandscape, contentBottom,
                                onOpenPlayer = if (currentTrack != null) openPlayer else null
                            )
                            MainTab.SEARCH -> SearchTab(viewModel, searchVm, catalogVm, now)
                            MainTab.LIBRARY -> LibraryTab(
                                viewModel, accountVm, playlistsVm, updateVm, crashVm, now,
                                onOpenAccounts = { showAccountDialog = true },
                                onOpenCrashLog = { showCrashLog = true },
                                onAddAccount = onNavigateToAuth
                            )
                            MainTab.RADIO -> RadioTab(viewModel, radioVm, now)
                            MainTab.CAR -> CarTab(viewModel, radioVm, now, progress, isLandscape, onExit = { chosenTab = MainTab.HOME })
                        }
                        }
                    }
                }

                // Floating navigation and mini-player (hidden in the car)
                if (currentTab != MainTab.CAR) {
                    val mini: @Composable () -> Unit = {
                        val track = currentTrack
                        if (showMini && track != null) {
                            AnimatedVisibility(
                                visible = !isPlayerExpanded,
                                enter = fadeIn(HiFiMotion.standard()),
                                exit = fadeOut(HiFiMotion.quick())
                            ) {
                                MiniPlayer(
                                    track = track,
                                    isPlaying = isPlaying,
                                    progress = progress,
                                    onPlayPauseClick = { viewModel.togglePlayPause() },
                                    onNextClick = { viewModel.playNext() },
                                    onClick = openPlayer,
                                    onDismiss = { viewModel.stopAndClearPlayer() },
                                    swipeToDismiss = swipeToClose,
                                    coverModifier = Modifier.sharedCover(sharedScope, this, enabled = !lite && !track.isRadio)
                                )
                            }
                        }
                    }
                    if (isLandscape) {
                        DockScrim(Modifier.align(Alignment.BottomCenter))
                        FloatingGlassDock(
                            currentRoute = currentTab.route,
                            onNavigate = { chosenTab = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(sideInsets)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = DockBottomGap),
                            player = mini
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (showMini) {
                                mini()
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            FloatingGlassBottomBar(currentRoute = currentTab.route, onNavigate = { chosenTab = it })
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isPlayerExpanded,
                    enter = fadeIn(HiFiMotion.large()) + slideInVertically(HiFiMotion.large()) { it / 6 },
                    exit = fadeOut(HiFiMotion.standard()) + slideOutVertically(HiFiMotion.standard()) { it / 6 },
                    modifier = Modifier.fillMaxSize()
                ) {
                    val track = currentTrack
                    if (track != null) {
                        PlayerHost(
                            track = track,
                            now = now,
                            favoriteStationIds = favoriteStationIds,
                            progress = progress,
                            viewModel = viewModel,
                            radioVm = radioVm,
                            onCollapse = { isPlayerExpanded = false },
                            onAddToPlaylist = { trackMenu = TrackMenuRequest(track, null, pickPlaylist = true) },
                            onQualityClick = { qualityDetails = viewModel.qualityDetails() },
                            coverModifier = Modifier.sharedCover(sharedScope, this, enabled = !lite && !track.isRadio)
                        )
                    }
                }

                // Playback and playlist errors ("no connection", "station unavailable"), above the mini-player
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    // Collected only while visible, so a message raised in the background falls back to a toast
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        merge(viewModel.playbackMessages, playlistsVm.messages).collect { snackbar.showSnackbar(it) }
                    }
                }
                // A wave by a track has started: say so, and offer to undo it
                val resources = androidx.compose.ui.platform.LocalResources.current
                val undoLabel = stringResource(com.korenskoy.yandexmusic.R.string.action_undo)
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.trackWaveStarted.collect { seed ->
                            launch {
                                val result = snackbar.showSnackbar(
                                    message = resources.getString(com.korenskoy.yandexmusic.R.string.wave_by_track_started, seed.title.orEmpty()),
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.undoTrackWave()
                            }
                        }
                    }
                }
                // In the car the message sits right above the big source dock, never over it
                val bars = WindowInsets.systemBars.asPaddingValues()
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val snackBottom = when {
                    currentTab == MainTab.CAR -> com.korenskoy.yandexmusic.presentation.car.carDockClearance(
                        maxWidth, maxHeight, maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding(), isLandscape
                    )
                    isLandscape -> DockClearance
                    currentTrack != null -> 160.dp
                    else -> 90.dp
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = snackBottom)
                ) { data ->
                    com.korenskoy.yandexmusic.presentation.hifi.HiFiSnackbar(
                        message = data.visuals.message,
                        actionLabel = data.visuals.actionLabel,
                        onAction = { data.performAction() }
                    )
                }
                }

                if (showCrashLog) CrashLogHost(crashVm) { showCrashLog = false }
                qualityDetails?.let { details -> QualityDetailsDialog(details) { qualityDetails = null } }
                trackMenu?.let { request ->
                    TrackMenuHost(
                        request, viewModel, playlistsVm, catalogVm, likedTrackIds, snackbar,
                        onDismiss = { trackMenu = null },
                        onLeavePlayer = { isPlayerExpanded = false }
                    )
                }
                UpdateHost(updateVm)
                AccountHost(
                    accountVm,
                    showManagement = showAccountDialog,
                    onCloseManagement = { showAccountDialog = false },
                    startupPickerHandled = startupPickerHandled,
                    onStartupPickerHandled = { startupPickerHandled = true },
                    onAddAccount = onNavigateToAuth
                )
            }
        }
    }
}

/** A catalog page slides a fifth of the way in from the right and fades in; Back mirrors it. */
private fun catalogTransition(forward: Boolean, lite: Boolean): androidx.compose.animation.ContentTransform =
    if (lite) {
        fadeIn(HiFiMotion.quick()) togetherWith fadeOut(HiFiMotion.quick())
    } else {
        val dir = if (forward) 1 else -1
        (androidx.compose.animation.slideInHorizontally(HiFiMotion.large()) { it * dir / 5 } + fadeIn(HiFiMotion.standard())) togetherWith
            (androidx.compose.animation.slideOutHorizontally(HiFiMotion.standard()) { -it * dir / 5 } + fadeOut(HiFiMotion.quick()))
    }

/** The full-screen player wired to the view models. */
// coverModifier is the shared cover transition handed through to the player's cover
@Suppress("ModifierParameter")
@Composable
private fun PlayerHost(
    track: TrackDetailsDto,
    now: NowPlaying,
    favoriteStationIds: Set<String>,
    progress: () -> Long,
    viewModel: MainViewModel,
    radioVm: RadioViewModel,
    onCollapse: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQualityClick: () -> Unit,
    coverModifier: Modifier
) {
    val queue by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val isFetchingMoreQueue by viewModel.isFetchingMoreQueue.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val stationId = track.idString.removePrefix("radio_")
    FullScreenPlayer(
        track = track,
        isPlaying = now.isPlaying,
        isLiked = if (track.isRadio) stationId in favoriteStationIds else track.idString in now.likedTrackIds,
        progress = progress,
        queue = queue,
        sourceLabel = playerSourceLabelRes(now.source, track.isRadio)?.let { stringResource(it) }.orEmpty(),
        onPlayPauseClick = { viewModel.togglePlayPause() },
        onToggleLike = { viewModel.toggleLike(track) },
        onNextClick = { viewModel.playNext() },
        onPrevClick = { viewModel.playPrev() },
        onSeekTo = { viewModel.seekTo(it) },
        onTrackSelect = { id -> viewModel.playTrack(id, queue) },
        onRemoveFromQueue = { viewModel.removeFromQueue(it) },
        onMoveInQueue = { id, target -> viewModel.moveInQueue(id, target) },
        onCollapse = onCollapse,
        onCloseAndClear = {
            onCollapse()
            viewModel.stopAndClearPlayer()
        },
        onDislikeClick = { viewModel.dislikeTrack(track) },
        onTrackWave = { viewModel.startTrackWave(track) },
        onLoadMoreQueue = { viewModel.loadMoreQueue() },
        isFetchingMoreQueue = isFetchingMoreQueue,
        sleepTimer = sleepTimer,
        onSleepTimer = { viewModel.setSleepTimer(it) },
        onSleepAtEndOfTrack = { viewModel.setSleepAtEndOfTrack() },
        onCancelSleepTimer = { viewModel.cancelSleepTimer() },
        onAddToPlaylist = onAddToPlaylist,
        onQualityClick = onQualityClick,
        coverModifier = coverModifier
    )
}

/** The cover shared between the mini-player and the full player (off in lite mode and for radio). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedCover(scope: SharedTransitionScope, visibility: AnimatedVisibilityScope, enabled: Boolean): Modifier =
    if (!enabled) this else with(scope) {
        this@sharedCover.sharedElement(rememberSharedContentState(PLAYER_COVER_KEY), visibility)
    }

/**
 * Covers are fetched before they are needed: the playing track and the next two (player and small
 * size), and the wave's "next up" cards — opening the player or moving on finds them ready.
 */
@Composable
private fun PrefetchCovers(viewModel: MainViewModel, track: TrackDetailsDto?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val queue by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val wavePreview by viewModel.wavePreview.collectAsStateWithLifecycle()
    val ahead = remember(queue, track?.idString) { listOfNotNull(track) + upcomingOf(queue, track?.idString, limit = 2) }
    LaunchedEffect(ahead) {
        com.korenskoy.yandexmusic.utils.prefetchCovers(context, ahead, listOf(CoverSize.Large, CoverSize.Small))
    }
    LaunchedEffect(wavePreview) {
        com.korenskoy.yandexmusic.utils.prefetchCovers(context, wavePreview.take(5), listOf(CoverSize.Medium))
    }
}

/** Back from the system installer's permission screen: continue the update that was waiting for it. */
@Composable
private fun InstallDownloadedUpdateOnResume(updateVm: UpdateViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && updateVm.downloadProgress.value >= 2f && updateVm.canRequestPackageInstalls) {
                updateVm.installDownloaded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

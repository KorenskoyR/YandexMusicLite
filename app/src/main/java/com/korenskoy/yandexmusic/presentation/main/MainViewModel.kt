package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.domain.model.WaveTuning
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import com.korenskoy.yandexmusic.presentation.main.components.MainTab
import com.korenskoy.yandexmusic.domain.model.StartPage
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import com.korenskoy.yandexmusic.data.player.PlayRequests
import com.korenskoy.yandexmusic.data.player.WaveKnob
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.account.AccountSessionManager
import com.korenskoy.yandexmusic.data.download.DownloadQueueState
import com.korenskoy.yandexmusic.data.download.DownloadScheduler
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.SleepTimerState
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Playback and the "Liked" library with its downloads and settings. Accounts, search, playlists, radio
 * and updates have their own ViewModels; data and the network live in the repositories.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val session: AccountSessionManager,
    private val playbackController: PlaybackController,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadManager: TrackDownloadManager,
    private val downloadScheduler: DownloadScheduler,
    networkMonitor: com.korenskoy.yandexmusic.data.network.NetworkMonitor,
    private val reactions: com.korenskoy.yandexmusic.data.player.TrackReactions,
    private val playRequests: PlayRequests
) : ViewModel() {

    val isAuthorized = authRepository.isAuthorized()
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.getToken() != null)

    // region Library

    // Library and charts live in the process-wide LibraryRepository (single-flight, incremental sync)
    val tracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.likedTracks
    val likedTrackIds: StateFlow<Set<String>> = libraryRepository.likedIds
    val popularTracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.popularTracks
    val isLoading: StateFlow<Boolean> = libraryRepository.isSyncingLikes
    /** The likes could not be loaded; they are retried by themselves as soon as the network is back. */
    val libraryLoadFailed: StateFlow<Boolean> = libraryRepository.likesSyncFailed

    init {
        networkMonitor.isConnectedFlow
            .onEach { online -> if (online && libraryRepository.likesSyncFailed.value) loadLikedTracks() }
            .launchIn(viewModelScope)
        // Once music has sounded, the wave knob's play is a resume (see waveKnob)
        playbackController.isPlaying
            .onEach { if (it) playRequests.markPlayed() }
            .launchIn(viewModelScope)
    }

    val isDownloadingAll: StateFlow<Boolean> = downloadScheduler.isDownloadingAll
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val downloadAllState: StateFlow<DownloadQueueState> = downloadScheduler.downloadAllState
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadQueueState.IDLE)
    val downloadedTrackIds: StateFlow<Set<String>> = downloadManager.downloadedTrackIds
    val downloadProgress = downloadManager.downloadProgress

    val steeringWheelOnLeft: StateFlow<Boolean> = settingsRepository.steeringWheelOnLeft
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_STEERING_WHEEL_ON_LEFT)
    val linkState = playbackController.linkState

    // endregion

    // region Playback state

    val currentSource: StateFlow<PlaylistSource> = playbackController.currentSource
    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = playbackController.currentPlaylist
    val currentTrack: StateFlow<TrackDetailsDto?> = playbackController.currentTrack
    val currentTrackId: StateFlow<String?> = playbackController.currentTrack
        .map { it?.idString }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playbackController.currentTrack.value?.idString)
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying
    val isBuffering: StateFlow<Boolean> = playbackController.isBuffering
    val progress: StateFlow<Long> = playbackController.progress
    val isFetchingMoreQueue: StateFlow<Boolean> = playbackController.isFetchingMoreQueue
    val wavePreview: StateFlow<List<com.korenskoy.yandexmusic.data.dto.TrackDetailsDto>> = playbackController.wavePreview
    val streamQuality: StateFlow<String?> = playbackController.streamQuality
    val sleepTimer: StateFlow<SleepTimerState?> = playbackController.sleepTimer
    val playbackMessages: SharedFlow<String> = playbackController.messages

    // endregion

    init {
        // The controller outlives this ViewModel; give it the library for "play liked" fallbacks.
        tracks.onEach { playbackController.updateLibrary(it) }.launchIn(viewModelScope)

        viewModelScope.launch { session.refreshProfileIfIncomplete() }

        // Cached library instantly, then exactly one network sync per authorization
        viewModelScope.launch {
            session.loadCached()
            authRepository.isAuthorized().collect { isAuth ->
                if (isAuth) session.syncAll()
            }
        }
    }

    // region Library actions

    /** Pull-to-refresh and the Library tab: sync likes (joins a sync already running). */
    fun loadLikedTracks() {
        viewModelScope.launch { session.syncLikes() }
    }

    /** The heart: a track's like, or a station's favourite (shared with the player in the notification). */
    fun toggleLike(track: TrackDetailsDto) = reactions.toggleLike(track)

    /** "Не нравится": a track leaves the wave and the next one plays; a station leaves the favourites. */
    fun dislikeTrack(track: TrackDetailsDto) = reactions.dislike(track)

    /** Offline downloads run in WorkManager: they continue after the app is closed. */
    fun downloadAllLikedTracks() {
        viewModelScope.launch { downloadScheduler.downloadAllLiked() }
    }

    fun cancelDownloadAll() {
        downloadScheduler.cancelAll()
        downloadManager.setDownloadProgress(null)
    }

    /** What plays now and what the server offered, for the quality details in the player. */
    fun qualityDetails() = playbackController.qualityDetails()

    /** Deletes the active user's downloads (except tracks other saved accounts liked) and reloads the lists. */
    fun clearMusicCache() = session.launch { clearMusicCache() }

    // endregion

    // region Playback: delegated to the process-wide PlaybackController

    fun playMyVibe() = playbackController.playMyVibe()
    fun playWaveFrom(trackId: String) {
        playRequests.userStarted()
        playbackController.playWaveFrom(trackId)
    }

    /**
     * The big knob on the wave page. It starts the wave, or plays the session restored at launch: both count
     * as switching music on (the full player may open). A play after the user's own pause is a resume and
     * opens nothing; so are the mini-player's buttons.
     */
    fun waveKnob(waveActive: Boolean) {
        val press = WaveKnob.press(waveActive, playbackController.isPlaying.value, playRequests.hasPlayed)
        if (press.opensPlayer) playRequests.userStarted()
        when (press.action) {
            WaveKnob.Action.START_WAVE -> playbackController.playMyVibe()
            WaveKnob.Action.TOGGLE -> playbackController.togglePlayPause()
        }
    }

    fun loadMoreQueue() = playbackController.loadMoreQueue()

    fun playAllLikedTracks() {
        playRequests.userStarted()
        playbackController.playAllLikedTracks()
    }

    /** The liked tracks that are on the device, in the library's order. */
    fun playDownloaded() {
        val downloaded = tracks.value.filter { it.idString in downloadedTrackIds.value }
        downloaded.firstOrNull()?.let {
            playRequests.userStarted()
            playbackController.playTrack(it.idString, downloaded, PlaylistSource.LIBRARY)
        }
    }

    fun shuffleLikedTracks() {
        playRequests.userStarted()
        playbackController.playAllLikedTracks(shuffle = true)
    }

    fun playHits(shuffle: Boolean = true) {
        playRequests.userStarted()
        playbackController.playHits(libraryRepository.weeklyHits.value, shuffle)
    }

    /** @param byUser the user chose this track (a list, a card); false when the app moves on by itself. */
    fun playTrack(trackId: String, playlist: List<TrackDetailsDto>, source: PlaylistSource? = null, byUser: Boolean = true) {
        if (byUser) playRequests.userStarted()
        playbackController.playTrack(trackId, playlist, source)
    }

    // region My Wave: tuning and a wave by a track

    val waveTuning: StateFlow<WaveTuning> = playbackController.waveTuning
    val waveSeed: StateFlow<TrackDetailsDto?> = playbackController.waveSeed
    val trackWaveStarted: Flow<TrackDetailsDto> = playbackController.trackWaveStarted

    fun setWaveTuning(tuning: WaveTuning) = playbackController.setWaveTuning(tuning)
    fun startTrackWave(track: TrackDetailsDto) {
        playRequests.userStarted()
        playbackController.startTrackWave(track)
    }
    fun returnToMyWave() = playbackController.returnToMyWave()
    fun undoTrackWave() = playbackController.undoTrackWave()

    // endregion

    // region Launch and the look of things

    /** Opens the full player when the user starts music, if the setting asks for it. */
    val openPlayerRequests: Flow<Unit> = playRequests.userStarted
        .filter { settingsRepository.expandPlayerOnPlay.first() }

    /** "Без ненормативной лексики": what is shown as coming next leaves out what will be passed over. */
    val skipExplicit: StateFlow<Boolean> = settingsRepository.skipExplicit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val swipeToCloseMiniPlayer: StateFlow<Boolean> = settingsRepository.swipeToCloseMiniPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val carClock: StateFlow<Boolean> = settingsRepository.carClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val animationMode: StateFlow<AnimationMode?> = settingsRepository.animationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The tab the app opens on; null until the setting has been read, so no other tab flashes first. */
    val startTab: StateFlow<MainTab?> = combine(settingsRepository.startPage, settingsRepository.lastTab) { page, last ->
        when (page) {
            StartPage.WAVE -> MainTab.HOME
            StartPage.CAR -> MainTab.CAR
            StartPage.LAST -> MainTab.entries.firstOrNull { it.route == last } ?: MainTab.HOME
        }
    }.take(1).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun rememberTab(tab: MainTab) {
        viewModelScope.launch { settingsRepository.setLastTab(tab.route) }
    }

    /** "Music on launch": once per process, and never over music that already plays. */
    fun applyStartMusic() {
        if (!isAuthorized.value || !playRequests.claimLaunchStart()) return
        viewModelScope.launch {
            val mode = settingsRepository.startMusic.first()
            if (mode == StartMusic.OFF) return@launch
            playbackController.awaitRestoredSession()
            if (playbackController.isPlaying.value) return@launch
            when (mode) {
                StartMusic.CONTINUE -> playbackController.play()
                StartMusic.WAVE -> if (playbackController.currentSource.value == PlaylistSource.VIBE && playbackController.currentTrack.value != null) {
                    playbackController.play()
                } else {
                    playbackController.playMyVibe()
                }
                StartMusic.OFF -> Unit
            }
        }
    }

    // endregion

    fun playNext() = playbackController.playNext()

    fun playPrev() = playbackController.playPrev()

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun removeFromQueue(trackId: String) = playbackController.removeFromQueue(trackId)
    fun moveInQueue(trackId: String, targetId: String) = playbackController.moveInQueue(trackId, targetId)

    fun enqueueNext(track: TrackDetailsDto) = playbackController.enqueueNext(track)

    fun stopAndClearPlayer() = playbackController.stopAndClear()

    fun setSleepTimer(minutes: Int) = playbackController.setSleepTimer(minutes)

    fun setSleepAtEndOfTrack() = playbackController.setSleepAtEndOfTrack()

    fun cancelSleepTimer() = playbackController.cancelSleepTimer()

    // endregion
}

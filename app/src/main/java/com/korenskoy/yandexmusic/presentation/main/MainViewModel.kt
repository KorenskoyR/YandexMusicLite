package com.korenskoy.yandexmusic.presentation.main

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
    private val roadReserve: com.korenskoy.yandexmusic.data.reserve.RoadReserve,
    private val soundMode: com.korenskoy.yandexmusic.data.player.SoundMode
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
    }

    val isDownloadingAll: StateFlow<Boolean> = downloadScheduler.isDownloadingAll
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val downloadAllState: StateFlow<DownloadQueueState> = downloadScheduler.downloadAllState
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadQueueState.IDLE)
    val downloadedTrackIds: StateFlow<Set<String>> = downloadManager.downloadedTrackIds
    val cacheSizeBytes: StateFlow<Long> = downloadManager.cacheSizeBytes
    val downloadProgress = downloadManager.downloadProgress

    val autoDownloadEnabled: StateFlow<Boolean> = settingsRepository.autoDownloadLikedTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val steeringWheelOnLeft: StateFlow<Boolean> = settingsRepository.steeringWheelOnLeft
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_STEERING_WHEEL_ON_LEFT)
    val uiScaleAuto: StateFlow<Boolean> = settingsRepository.uiScaleAuto
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_UI_SCALE_AUTO)
    val uiScaleManual: StateFlow<Float> = settingsRepository.uiScaleManual
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_UI_SCALE)
    val crossfadeSeconds: StateFlow<Int> = settingsRepository.crossfadeSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_CROSSFADE_SECONDS)
    val roadReserveMb: StateFlow<Int> = settingsRepository.roadReserveMb
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_ROAD_RESERVE_MB)
    val reserveUsedBytes: StateFlow<Long> = roadReserve.usedBytes
    val compatibleSound: StateFlow<Boolean> = soundMode.compatible
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

    fun enableAutoDownloadAndStart() {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadLikedTracks(true)
            downloadScheduler.downloadAllLiked()
        }
    }

    fun setAutoDownloadLikedTracks(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDownloadLikedTracks(enabled) }
    }

    fun setSteeringWheelOnLeft(onLeft: Boolean) {
        viewModelScope.launch { settingsRepository.setSteeringWheelOnLeft(onLeft) }
    }

    fun setUiScaleAuto(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUiScaleAuto(enabled) }
    }

    fun setUiScaleManual(scale: Float) {
        viewModelScope.launch { settingsRepository.setUiScaleManual(scale) }
    }

    /** Re-downloads saved tracks the server now offers in a better quality (in the background). */
    fun upgradeDownloadedQuality() {
        viewModelScope.launch { downloadScheduler.upgradeDownloadedQuality() }
    }

    /** What plays now and what the server offered, for the quality details in the player. */
    fun qualityDetails() = playbackController.qualityDetails()

    fun setCompatibleSound(enabled: Boolean) {
        viewModelScope.launch {
            soundMode.choose(enabled)
            playbackController.onSoundModeChanged(enabled)
        }
    }

    fun setRoadReserveMb(megabytes: Int) {
        viewModelScope.launch { settingsRepository.setRoadReserveMb(megabytes) }
    }

    fun clearRoadReserve() {
        viewModelScope.launch { roadReserve.clear() }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setCrossfadeSeconds(seconds) }
    }

    /** Deletes the active user's downloads (except tracks other saved accounts liked) and reloads the lists. */
    fun clearMusicCache() = session.launch { clearMusicCache() }

    // endregion

    // region Playback: delegated to the process-wide PlaybackController

    fun playMyVibe() = playbackController.playMyVibe()
    fun playWaveFrom(trackId: String) = playbackController.playWaveFrom(trackId)

    fun loadMoreQueue() = playbackController.loadMoreQueue()

    fun playAllLikedTracks() = playbackController.playAllLikedTracks()

    /** The liked tracks that are on the device, in the library's order. */
    fun playDownloaded() {
        val downloaded = tracks.value.filter { it.idString in downloadedTrackIds.value }
        downloaded.firstOrNull()?.let { playbackController.playTrack(it.idString, downloaded, PlaylistSource.LIBRARY) }
    }

    fun shuffleLikedTracks() = playbackController.playAllLikedTracks(shuffle = true)

    fun playHits(shuffle: Boolean = true) = playbackController.playHits(libraryRepository.weeklyHits.value, shuffle)

    fun playTrack(trackId: String, playlist: List<TrackDetailsDto>, source: PlaylistSource? = null) =
        playbackController.playTrack(trackId, playlist, source)

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

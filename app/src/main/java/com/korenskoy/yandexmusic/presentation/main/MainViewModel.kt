package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.domain.model.MiniOnWave
import com.korenskoy.yandexmusic.presentation.hifi.LyricsUi
import com.korenskoy.yandexmusic.data.lyrics.Lyrics
import android.content.res.Resources
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import android.util.Log
import com.korenskoy.yandexmusic.data.player.VoiceSearch
import com.korenskoy.yandexmusic.data.ai.AiMarks
import com.korenskoy.yandexmusic.data.player.VoicePhrase
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.local.LocalRules
import com.korenskoy.yandexmusic.data.local.LocalSource
import com.korenskoy.yandexmusic.data.local.LocalTrack
import com.korenskoy.yandexmusic.presentation.car.CAR_DEVICE_FOLDERS
import com.korenskoy.yandexmusic.presentation.car.CarDeviceFolder
import com.korenskoy.yandexmusic.presentation.car.CarDeviceMusic
import android.net.Uri
import com.korenskoy.yandexmusic.R
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.korenskoy.yandexmusic.data.download.RemoveOutcome
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import com.korenskoy.yandexmusic.data.download.OfflineDownloads
import com.korenskoy.yandexmusic.data.download.OfflineRules
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import com.korenskoy.yandexmusic.data.player.TrackReactions
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
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
import com.korenskoy.yandexmusic.data.account.LibrarySyncPolicy
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.repository.LikedAlbumsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    networkMonitor: NetworkMonitor,
    private val reactions: TrackReactions,
    private val playRequests: PlayRequests,
    private val offline: OfflineDownloads,
    private val voiceSearch: VoiceSearch,
    albums: LikedAlbumsRepository,
    private val localMusic: LocalMusicRepository,
    private val ai: AiMarks,
    lyrics: Lyrics
) : ViewModel() {

    val isAuthorized = authRepository.isAuthorized()
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.getToken() != null)

    // region Library

    // Library and charts live in the process-wide LibraryRepository (single-flight, incremental sync)
    val tracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.likedTracks
    val likedTrackIds: StateFlow<Set<String>> = libraryRepository.likedIds

    /** Albums in the collection (marked here or in the official app). */
    val likedAlbums: StateFlow<List<AlbumDto>> = albums.albums
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

    /** Playlists and albums kept on the device. */
    val offlineCollections: StateFlow<List<OfflineCollection>> = offline.all
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "Скачанные": liked tracks on the device, then the kept playlists' and albums'. */
    val downloadedTracks: StateFlow<List<TrackDetailsDto>> = combine(libraryRepository.likedTracks, offline.all, downloadManager.downloadedTrackIds) { liked, kept, ids ->
        OfflineRules.downloadedTracks(liked, kept, ids)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // region Music on the device

    /** "Музыка с устройства": the phone's, the SD card's and the flash drives' own music. */
    val localLibrary: StateFlow<LocalMusicRepository.Library> = localMusic.library
    val localSeen: StateFlow<Boolean> = localMusic.seen

    /** "Скачанные" in the car: with music on the device the tile opens a choice of what to play. */
    val carDeviceMusic: StateFlow<CarDeviceMusic?> = combine(localMusic.library, downloadedTracks) { library, downloaded ->
        if (library.tracks.isEmpty()) {
            null
        } else {
            CarDeviceMusic(
                downloadedCount = downloaded.size,
                deviceCount = library.tracks.size,
                sources = LocalSource.entries.filter { s -> library.tracks.any { it.source == s } },
                folders = LocalRules.carFolders(library.tracks, CAR_DEVICE_FOLDERS)
                    .takeIf { it.size > 1 }.orEmpty()
                    .map { CarDeviceFolder(it.key, it.name, it.tracks.size, it.source) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun localTrack(track: LocalTrack): TrackDetailsDto = localMusic.toDto(track)

    /** A track of the device's own music, with [queue] after it. */
    fun playLocal(track: LocalTrack, queue: List<LocalTrack>) {
        playRequests.userStarted()
        playbackController.playOnDevice(track.id, localMusic.toDtos(queue))
    }

    /** "Слушать" / "Перемешать" for what the page shows. */
    fun playLocalAll(queue: List<LocalTrack>, shuffle: Boolean) {
        val order = if (shuffle) queue.shuffled() else queue
        val first = order.firstOrNull() ?: return
        playLocal(first, order)
    }

    /**
     * The car: all the music on the device, folder by folder as it lies on the drive, or one folder by its key.
     * @return false when there is none.
     */
    fun playDevice(folderKey: String? = null, only: LocalSource? = null): Boolean {
        val tracks = localMusic.library.value.tracks.filter { only == null || it.source == only }
        val folders = LocalRules.folders(tracks)
        val queue = if (folderKey != null) folders.firstOrNull { it.key == folderKey }?.tracks.orEmpty() else folders.flatMap { it.tracks }
        val first = queue.firstOrNull() ?: return false
        playLocal(first, queue)
        return true
    }

    fun onLocalAccessChanged() = localMusic.onAccessMaybeChanged()
    fun markLocalSeen() = localMusic.markSeen()
    fun removeLocalFolder(uri: String) = localMusic.removeFolder(uri)
    fun localPickerStart(): Uri? = localMusic.pickerStart()

    /** A folder picked in the system's window: kept, read, and said so. */
    fun addLocalFolder(uri: Uri) {
        val ok = localMusic.addFolder(uri)
        val name = uri.let { runCatching { android.provider.DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
            ?.let { LocalRules.placeOfDocument(it).second.trimEnd('/').substringAfterLast('/') }.orEmpty()
        _trackMessages.tryEmit(if (ok) UiMessage(R.string.local_folder_added, listOf(name)) else UiMessage(R.string.local_folder_failed))
    }

    // endregion

    val steeringWheelOnLeft: StateFlow<Boolean> = settingsRepository.steeringWheelOnLeft
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_STEERING_WHEEL_ON_LEFT)
    val linkState = playbackController.linkState

    // endregion

    // region Playback state

    val currentSource: StateFlow<PlaylistSource> = playbackController.currentSource
    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = playbackController.currentPlaylist
    val currentTrack: StateFlow<TrackDetailsDto?> = playbackController.currentTrack

    /** The playing track on the device: the player's download button. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTrackOffline: StateFlow<TrackOfflineState> = playbackController.currentTrack
        .flatMapLatest { track -> if (track == null || track.isRadio || track.isLocal) flowOf(TrackOfflineState.NONE) else offline.trackState(track.idString) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackOfflineState.NONE)

    /** A track's place on the device, for its menu. */
    fun trackOffline(trackId: String): Flow<TrackOfflineState> = offline.trackState(trackId)

    private fun voiceFailed(e: Exception) {
        Log.w("MainViewModel", "Voice search failed", e)
        _trackMessages.tryEmit(UiMessage(R.string.voice_failed))
    }

    /** A message for the snackbar: a text and what goes into it (a track's title, a phrase). */
    data class UiMessage(@param:StringRes val text: Int, val args: List<String> = emptyList()) {
        fun text(resources: Resources): String = when (args.size) {
            0 -> resources.getString(text)
            1 -> resources.getString(text, args[0])
            2 -> resources.getString(text, args[0], args[1])
            else -> resources.getString(text, args[0], args[1], args[2])
        }
    }

    private val _trackMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val trackMessages: SharedFlow<UiMessage> = _trackMessages

    /** "Скачать" for one track: in the background, then among the downloads. */
    fun downloadTrack(track: TrackDetailsDto) {
        viewModelScope.launch {
            offline.downloadTrack(track)
            _trackMessages.tryEmit(UiMessage(R.string.track_download_started, listOf(track.title.orEmpty())))
        }
    }

    /** "Убрать с устройства" for one track, and why it stays when something else keeps it. */
    fun removeTrackFromDevice(track: TrackDetailsDto) {
        viewModelScope.launch {
            val text = when (offline.removeTrackFromDevice(track.idString)) {
                RemoveOutcome.REMOVED -> R.string.track_removed
                RemoveOutcome.KEPT_BY_COLLECTION -> R.string.track_kept_by_collection
                RemoveOutcome.KEPT_BY_AUTO_DOWNLOAD -> R.string.track_kept_by_auto_download
            }
            _trackMessages.tryEmit(UiMessage(text, listOf(track.title.orEmpty())))
        }
    }

    /**
     * The car's microphone: what was said plays at once, without a list to pick from while driving — a named
     * station, or what the search thinks was meant; a message says what plays.
     */
    fun playByVoice(phrase: String) {
        viewModelScope.launch {
            val pick = try {
                voiceSearch.pick(phrase)
            } catch (e: IOException) {
                voiceFailed(e)
                return@launch
            } catch (e: HttpException) {
                voiceFailed(e)
                return@launch
            } catch (e: SerializationException) {
                voiceFailed(e)
                return@launch
            }
            when (pick) {
                is VoiceSearch.Pick.Station -> {
                    playRequests.userStarted()
                    playbackController.playRadioStation(pick.station)
                    _trackMessages.tryEmit(UiMessage(R.string.voice_station, listOf(pick.query, pick.station.name)))
                }
                is VoiceSearch.Pick.Tracks -> {
                    val first = pick.tracks.first()
                    playRequests.userStarted()
                    playbackController.playTrack(first.idString, pick.tracks, PlaylistSource.SEARCH)
                    val more = pick.tracks.size - 1
                    _trackMessages.tryEmit(
                        if (more > 0) UiMessage(R.string.voice_playing_more, listOf(pick.query, first.title.orEmpty(), more.toString()))
                        else UiMessage(R.string.voice_playing, listOf(pick.query, first.title.orEmpty()))
                    )
                }
                is VoiceSearch.Pick.Nothing -> _trackMessages.tryEmit(UiMessage(R.string.voice_nothing, listOf(pick.query)))
                is VoiceSearch.Pick.Offline -> playOffline(pick)
            }
        }
    }
    private fun playOffline(pick: VoiceSearch.Pick.Offline) {
        val phrase = pick.query
        when (pick.target) {
            VoicePhrase.Offline.DOWNLOADED -> if (downloadedTracks.value.isNotEmpty()) {
                playDownloaded()
                _trackMessages.tryEmit(UiMessage(R.string.voice_downloaded, listOf(phrase)))
            } else {
                _trackMessages.tryEmit(UiMessage(R.string.car_downloads_in_app_none))
            }
            else -> {
                val (only, what) = when (pick.target) {
                    VoicePhrase.Offline.PHONE -> LocalSource.PHONE to R.string.voice_device_phone
                    VoicePhrase.Offline.SD -> LocalSource.SD to R.string.voice_device_sd
                    VoicePhrase.Offline.FLASH -> LocalSource.FLASH to R.string.voice_device_flash
                    else -> null to R.string.voice_device_all
                }
                if (playDevice(only = only)) {
                    _trackMessages.tryEmit(UiMessage(what, listOf(phrase)))
                } else {
                    _trackMessages.tryEmit(UiMessage(R.string.voice_device_none, listOf(phrase)))
                }
            }
        }
    }

    val currentTrackId: StateFlow<String?> = playbackController.currentTrack
        .map { it?.idString }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playbackController.currentTrack.value?.idString)
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying
    val isBuffering: StateFlow<Boolean> = playbackController.isBuffering
    val progress: StateFlow<Long> = playbackController.progress
    val isFetchingMoreQueue: StateFlow<Boolean> = playbackController.isFetchingMoreQueue
    val wavePreview: StateFlow<List<TrackDetailsDto>> = playbackController.wavePreview
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

    /** Pull-to-refresh: the whole library now — likes, playlists, albums (joins a sync already running). */
    fun loadLikedTracks() {
        session.syncLibraryNow()
    }

    /** The library or one of its pages opened: refreshed unless it just was. */
    fun onLibraryShown() = session.syncLibraryIfStale(LibrarySyncPolicy.ON_SHOW_MS)

    private var whileShown: Job? = null

    /**
     * The app is on the screen: the library catches up with changes made in the official app, then keeps up
     * every few minutes (a head unit never closes the app). Nothing runs while the app is hidden.
     */
    fun onAppShown() {
        whileShown?.cancel()
        whileShown = viewModelScope.launch {
            session.syncLibraryIfStale(LibrarySyncPolicy.ON_SHOW_MS)
            while (true) {
                delay(LibrarySyncPolicy.WHILE_SHOWN_MS)
                session.syncLibraryIfStale(LibrarySyncPolicy.WHILE_SHOWN_MS)
            }
        }
    }

    fun onAppHidden() {
        whileShown?.cancel()
        whileShown = null
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

    /** Everything on the device: liked tracks in the library's order, then kept playlists and albums. */
    fun playDownloaded() {
        val downloaded = downloadedTracks.value
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

    /** Tracks Yandex marks as made with AI: id → its words. Shown always; passed over when [skipAi] is on. */
    val aiMarks: StateFlow<Map<String, String>> = ai.marks

    /** The words of tracks for the player and the car. */
    val lyricsUi = LyricsUi(known = lyrics::known, load = lyrics::of)
    val skipAi: StateFlow<Boolean> = settingsRepository.skipAi
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun wantAiMark(trackId: String) = ai.want(trackId)
    fun unwantAiMark(trackId: String) = ai.unwant(trackId)

    /** "Без ненормативной лексики": what is shown as coming next leaves out what will be passed over. */
    val skipExplicit: StateFlow<Boolean> = settingsRepository.skipExplicit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val swipeToCloseMiniPlayer: StateFlow<Boolean> = settingsRepository.swipeToCloseMiniPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val carClock: StateFlow<Boolean> = settingsRepository.carClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val carUnderCover: StateFlow<CarUnderCover> = settingsRepository.carUnderCover
        .stateIn(viewModelScope, SharingStarted.Eagerly, CarUnderCover.QUEUE)
    val animationMode: StateFlow<AnimationMode?> = settingsRepository.animationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val miniOnWave: StateFlow<MiniOnWave> = settingsRepository.miniOnWave
        .stateIn(viewModelScope, SharingStarted.Eagerly, MiniOnWave.LANDSCAPE)

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

    /** "Перемешано" in the car's "Любимое". */
    val shuffled: StateFlow<Boolean> = playbackController.shuffled
    fun toggleShuffle() = playbackController.toggleShuffle()

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

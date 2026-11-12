package com.korenskoy.yandexmusic.data.player

import com.korenskoy.yandexmusic.data.repository.RadioRules
import android.os.SystemClock
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.withContext
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import kotlinx.coroutines.flow.distinctUntilChanged
import com.korenskoy.yandexmusic.utils.prefetchCovers
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.data.ai.AiMarks
import com.korenskoy.yandexmusic.data.local.LocalArtProvider
import com.korenskoy.yandexmusic.data.local.LocalMusicRepository
import com.korenskoy.yandexmusic.data.reserve.RoadReserve
import com.korenskoy.yandexmusic.data.reserve.ReservePolicy
import com.korenskoy.yandexmusic.data.reserve.ReserveKeeper
import com.korenskoy.yandexmusic.utils.toCoverUrl
import com.korenskoy.yandexmusic.R
import androidx.core.net.toUri
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.cache.PlaybackSnapshot
import com.korenskoy.yandexmusic.data.cache.PlaybackStateStore
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.domain.repository.PlaybackFailure
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the play queue and every playback decision (next/previous, My Wave refill, error recovery,
 * session persistence). It is a process-wide singleton, so notification, headset and track-end events
 * keep working after the Activity and its ViewModel are destroyed (e.g. the task is swiped away).
 */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val downloadManager: TrackDownloadManager,
    private val preloadManager: TrackPreloadManager,
    private val cacheManager: PlaylistCacheManager,
    private val radioRepository: RadioRepository,
    private val stateStore: PlaybackStateStore,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val reserve: RoadReserve,
    private val journal: PlaybackJournal,
    private val localMusic: LocalMusicRepository,
    private val aiMarks: AiMarks
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Unhandled playback coroutine error", e)
        }
    )

    private val _currentSource = MutableStateFlow(PlaylistSource.NONE)
    val currentSource: StateFlow<PlaylistSource> = _currentSource.asStateFlow()

    /** The favourite stations: "next" and "previous" on the radio go round them (see [RadioRules.step]). */
    private val favouriteStations: StateFlow<Set<String>> =
        radioRepository.favoriteStationIds.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _currentPlaylist = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = _currentPlaylist.asStateFlow()

    /** "Перемешано": the queue after the current track is in a random order; [orderBeforeShuffle] is the way back. */
    private val _shuffled = MutableStateFlow(false)
    val shuffled: StateFlow<Boolean> = _shuffled.asStateFlow()
    private var orderBeforeShuffle: List<TrackDetailsDto>? = null

    private val _currentTrack = MutableStateFlow<TrackDetailsDto?>(null)
    val currentTrack: StateFlow<TrackDetailsDto?> = _currentTrack.asStateFlow()

    /** Playback was requested but the stream URL is still being resolved. */
    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val sleep = SleepTimer(
        scope = scope,
        fadeOutAndPause = { playerRepository.fadeOutAndPause(it) },
        onFinished = { persistSession() }
    )
    val sleepTimer: StateFlow<SleepTimerState?> get() = sleep.state

    /** Real stream quality of the current track ("MP3 · 320 кбит/с"), null for radio or while unknown. */
    private val _streamQuality = MutableStateFlow<String?>(null)
    val streamQuality: StateFlow<String?> = _streamQuality.asStateFlow()

    /** Position of a restored (not yet loaded) session, shown until the player takes over. */
    private val restoredPositionMs = MutableStateFlow(0L)

    /** Play/Pause icon state: true while playing, buffering, or resolving the next stream. */
    val isPlaying: StateFlow<Boolean> = combine(playerRepository.isPlaying, _isStarting) { playing, starting ->
        playing || starting
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isBuffering: StateFlow<Boolean> = combine(playerRepository.isBuffering, _isStarting, playerRepository.soundingTrackId) { buffering, starting, sounding ->
        buffering || (starting && sounding == null)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val progress: StateFlow<Long> = combine(
        playerRepository.progress,
        playerRepository.currentTrackId,
        restoredPositionMs
    ) { position, loadedId, restored ->
        if (loadedId == null) restored else position
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    private val recentPlayedTrackIds = RecentlyPlayed(RECENT_LIMIT)

    // Declared before the wave and the init blocks below: both may read them at once
    private val skipExplicit = settingsRepository.skipExplicit.stateIn(scope, SharingStarted.Eagerly, false)
    private val skipAi = settingsRepository.skipAi.stateIn(scope, SharingStarted.Eagerly, false)
    /** The current track was picked by the user, not reached by the queue: a mark never takes it away. */
    private var currentChosen = true
    /** A track already left because its mark came after it started: never left twice. */
    private var leftForMark: String? = null
    private val smoothOn = settingsRepository.smoothPlayback.stateIn(scope, SharingStarted.Eagerly, true)

    /** Whether the link is uneven: the music waited for data mid-track. */
    private val steadiness = LinkSteadiness(SystemClock::elapsedRealtime)
    /** The track that has been heard: only its stops count, not the wait before a track starts. */
    private var soundedTrackId: String? = null
    private var stallLastedJob: Job? = null
    /** Light streams already asked for while the link is uneven, so a prepared track is not asked twice. */
    private val lightStreams = mutableMapOf<String, Pair<TrackPreloadManager.ResolvedStream, Long>>()

    private val _waveSeed = MutableStateFlow<TrackDetailsDto?>(null)
    /** The track a "wave by track" was started from; null while My Wave (or anything else) plays. */
    val waveSeed: StateFlow<TrackDetailsDto?> = _waveSeed.asStateFlow()

    private val _waveTuning = MutableStateFlow(WaveTuning())
    /** The current station's character, mood and language. */
    val waveTuning: StateFlow<WaveTuning> = _waveTuning.asStateFlow()

    private val _trackWaveStarted = MutableSharedFlow<TrackDetailsDto>(extraBufferCapacity = 1)
    /** A wave by a track has just started: the screen offers to undo it. */
    val trackWaveStarted: SharedFlow<TrackDetailsDto> = _trackWaveStarted

    private data class BeforeTrackWave(
        val source: PlaylistSource,
        val playlist: List<TrackDetailsDto>,
        val trackId: String?,
        val positionMs: Long,
        val seed: TrackDetailsDto?
    )

    private var beforeTrackWave: BeforeTrackWave? = null

    /** The server has said the account has no Plus: nothing plays (the app shows how to get it). */
    val plusMissing: StateFlow<Boolean> = authRepository.activeAccount
        .map { it?.plusMissing == true }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, authRepository.getActiveAccount()?.plusMissing == true)

    /** Every way in to the music asks this first. */
    private fun blocked(): Boolean = plusMissing.value.also { if (it) Log.i(TAG, "No Plus: nothing plays") }

    init {
        // No Plus: what plays stops and the queue goes; a Play from a headset, the car or a widget finds nothing
        plusMissing.onEach { if (it) stopAndClear() }.launchIn(scope)
        combine(plusMissing, playerRepository.isPlaying) { missing, playing -> missing && playing }
            .onEach { if (it) playerRepository.pause() }
            .launchIn(scope)
        // Volume normalization: the gains of the queued tracks, applied as each one plays
        combine(_currentPlaylist, settingsRepository.normalizeVolume) { queue, on ->
            if (!on) emptyMap() else queue.mapNotNull { track -> track.r128?.integrated?.let { track.idString to Loudness.gainFor(it) } }.toMap()
        }.distinctUntilChanged().onEach { playerRepository.setTrackGains(it) }.launchIn(scope)
    }

    /** "Моя волна": its own list, preview and fetching; new tracks join the queue while the wave plays. */
    private val wave = WaveQueue(
        api = api,
        cacheManager = cacheManager,
        preloadManager = preloadManager,
        scope = scope,
        recent = recentPlayedTrackIds,
        isOffline = ::isOffline,
        userFolder = { activeUserFolder },
        keep = { !(skipExplicit.value && it.isExplicit) && !(skipAi.value && aiMarks.isAi(it.idString)) },
        // Appended to the queue as it is now: tracks the user removed or moved stay that way
        onFresh = { fresh ->
            if (_currentSource.value == PlaylistSource.VIBE) {
                _currentPlaylist.value = (_currentPlaylist.value + fresh).distinctBy { it.idString }
            }
        }
    )
    val isFetchingMoreQueue: StateFlow<Boolean> get() = wave.isFetching

    /** What the wave will play when it is started; shown on the wave page while the wave is not playing. */
    val wavePreview: StateFlow<List<TrackDetailsDto>> get() = wave.preview
    private var libraryTracks: List<TrackDetailsDto> = emptyList()

    private val link = LinkTracker(SystemClock::elapsedRealtime)
    private val _linkState = MutableStateFlow(LinkState.GOOD)
    /** How the music gets through right now: the "Слабая связь" and "Нет связи" labels. */
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    /** The track now coming from a stream: only its sound proves the link is good again. */
    private var streamingTrackId: String? = null
    private var playingCopyId: String? = null
    /** Nothing on the device to play: the music continues by itself once the connection is back. */
    private var waitingForConnection = false
    /** What the music left for a mix of what is on the device, to go back to once the connection is good. */
    private var returnPoint: ReturnPoint? = null

    private class ReturnPoint(val source: PlaylistSource, val queue: List<TrackDetailsDto>, val leftTrackId: String?)

    private val reserveKeeper = ReserveKeeper(
        scope = scope,
        reserve = reserve,
        limitMb = settingsRepository.roadReserveMb,
        mayFill = combine(
            playerRepository.isPlaying,
            _linkState,
            _currentTrack,
            authRepository.isAuthorized()
        ) { playing, state, track, signedIn ->
            // The user's own music needs no network: the cache does not spend traffic while it plays
            playing && signedIn && state == LinkState.GOOD && track?.isRadio != true && track?.isLocal != true
        },
        resolve = preloadManager::resolveOrdinaryStream,
        // The playing track comes first: the cache fills only while it has enough in hand
        streamNeedsTheLine = {
            isBuffering.value || playerRepository.inTransition || progress.value < TRACK_SETTLE_MS ||
                playerRepository.bufferedAheadMs < RESERVE_HEADROOM_MS
        },
        upcoming = { upcomingTracks() },
        moreTracks = { wave.tracks.value },
        isDownloaded = preloadManager::hasPlayableCopy,
        fetchMore = { wave.fetchMoreAwait() },
        onStored = { prefetchCovers(context, listOf(it), listOf(CoverSize.Small, CoverSize.Medium, CoverSize.Large)) }
    )

    private var currentPlayJob: Job? = null
    private var bufferingWatchdog: Job? = null
    private var stallWatchdog: Job? = null
    /** Incremented on every explicit start/stop so stale async callbacks can tell they were superseded. */
    private var startRequestId = 0L
    private var retriedTrackId: String? = null
    private var consecutiveFailedTracks = 0
    /** Completes once the saved session (if any) has been loaded from disk. */
    private val sessionRestored = CompletableDeferred<Unit>()

    private val activeUserFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    init {
        playerRepository.trackEndedEvent.onEach { onTrackEnded() }.launchIn(scope)
        playerRepository.prepareNextTrackEvent.onEach { prepareNextTrackInQueue() }.launchIn(scope)
        playerRepository.skipNextEvent.onEach { playNext() }.launchIn(scope)
        playerRepository.skipPrevEvent.onEach { playPrev() }.launchIn(scope)
        playerRepository.playbackErrorEvent.onEach { onPlaybackFailure(it) }.launchIn(scope)
        settingsRepository.crossfadeSeconds
            .onEach { playerRepository.setCrossfadeDuration(it * 1000L) }
            .launchIn(scope)

        // A track that actually renders audio resets error recovery.
        combine(playerRepository.soundingTrackId, playerRepository.isBuffering, playerRepository.currentTrackId) { sounding, buffering, current ->
            sounding != null && sounding == current && !buffering
        }
            .distinctUntilChanged()
            .onEach { audible ->
                if (audible) {
                    _isStarting.value = false
                    consecutiveFailedTracks = 0
                    retriedTrackId = null
                    soundedTrackId = playerRepository.currentTrackId.value
                    if (streamingTrackId != null && streamingTrackId == _currentTrack.value?.idString) {
                        updateLink(LinkReason.STREAM_SOUNDS) { onStreamAudible() }
                    }
                }
            }
            .launchIn(scope)

        // The player stopped wanting to play with a track still loaded (focus loss during a call, a Bluetooth
        // unplug, a manual pause): no start is in flight any more. Clearing this flag stops the "loading" spinner
        // from sticking and lets play()/resume() run again afterwards (it early-returns while a start is pending).
        playerRepository.isPlaying
            .onEach { wantsToPlay ->
                if (!wantsToPlay && playerRepository.currentTrackId.value != null) _isStarting.value = false
            }
            .launchIn(scope)

        // Watchdog: a stream that never finishes buffering (a dead link that reports no error) would spin forever.
        // After a timeout, treat it as a failure so the usual retry/skip recovery kicks in instead of a stuck UI.
        isBuffering
            .onEach { buffering ->
                bufferingWatchdog?.cancel()
                stallWatchdog?.cancel()
                if (buffering) {
                    val trackId = _currentTrack.value?.idString
                    stallWatchdog = scope.launch {
                        delay(LINK_STALL_MS)
                        val stalled = _currentTrack.value?.takeIf { it.idString == trackId && it.isStreamed }
                        if (isBuffering.value && stalled != null) onStreamStalled(stalled)
                    }
                    bufferingWatchdog = scope.launch {
                        delay(BUFFERING_TIMEOUT_MS)
                        if (isBuffering.value && _currentTrack.value?.idString == trackId && trackId != null) {
                            Log.w(TAG, "Buffering watchdog fired for $trackId")
                            onPlaybackFailure(PlaybackFailure(trackId, progress.value, "BUFFERING_TIMEOUT"))
                        }
                    }
                }
            }
            .launchIn(scope)

        watchTheSteadiness()
        watchAiMarks()
        keepWaveShelfFilled()
        watchTheLink()
        reserveKeeper.start()

        // Persist the position while playing so a killed process resumes close to where it stopped.
        scope.launch {
            while (true) {
                delay(SNAPSHOT_INTERVAL_MS)
                if (playerRepository.isPlaying.value) persistSession()
            }
        }

        scope.launch {
            try {
                loadUserState(activeUserFolder, restoreSession = true)
            } finally {
                sessionRestored.complete(Unit)
            }
        }
    }

    // region Public API

    fun updateLibrary(tracks: List<TrackDetailsDto>) {
        libraryTracks = tracks
    }

    fun playTrack(
        trackId: String,
        playlist: List<TrackDetailsDto>,
        source: PlaylistSource? = null,
        crossfade: Boolean = true,
        startPositionMs: Long = 0L,
        /** Picked by the user (a row, a search, the voice); false when the queue moves on by itself. */
        chosen: Boolean = true
    ) {
        if (blocked()) return
        val track = playlist.find { it.idString == trackId } ?: return
        if (!chosen && skippedOnTheWay(track)) {
            // Reached by the queue though it is to be passed over (its mark came late): the next that is not
            val index = playlist.indexOf(track)
            val next = PlaybackQueue.nextMatching(playlist, index) { !skippedOnTheWay(it) }
            if (next != null && playlist[next].idString != trackId) {
                playTrack(playlist[next].idString, playlist, source, crossfade, 0L, chosen = false)
                return
            }
        }
        currentChosen = chosen
        startRequestId++
        waitingForConnection = false
        // A context chosen on purpose: there is nothing to go back to any more
        source?.let {
            _currentSource.value = it
            returnPoint = null
            _shuffled.value = false
            orderBeforeShuffle = null
        }
        _currentPlaylist.value = playlist
        _currentTrack.value = track
        restoredPositionMs.value = startPositionMs

        if (!track.isLocal) {
            recordTrackPlayed(trackId)
            reserve.markPlayed(trackId)
        }

        val currentIndex = playlist.indexOfFirst { it.idString == trackId }
        // The user's own files need no links
        val upcoming = playlist.drop(currentIndex + 1).take(3).filterNot { it.isLocal || it.isRadio }.map { it.idString }
        if (upcoming.isNotEmpty()) preloadManager.preloadTracks(upcoming)
        playlist.getOrNull(currentIndex + 1)?.let { prepareStandbyWhenSettled(it) }

        // Continuous queue auto-refill: fewer than 20 tracks left in My Wave
        if (_currentSource.value == PlaylistSource.VIBE && playlist.size - currentIndex <= 20) {
            wave.fetchMore()
        }
        if (_currentSource.value == PlaylistSource.VIBE && !track.isRadio) {
            sendRotorFeedback("trackStarted", trackId)
        }

        resolveAndPlay(track, crossfade, startPositionMs)
        scope.launch { persistSession() }
    }

    /** Starts the wave from a track of its preview (the wave page's "next up" shelf). */
    fun playWaveFrom(trackId: String) {
        if (blocked()) return
        val waveList = wave.tracks.value
        if (waveList.none { it.idString == trackId }) {
            playMyVibe()
            return
        }
        _currentSource.value = PlaylistSource.VIBE
        playTrack(trackId, waveList, PlaylistSource.VIBE)
    }

    /** Returns once the saved session (track, queue, position) is back, so "continue" has something to continue. */
    suspend fun awaitRestoredSession() = sessionRestored.await()

    fun playMyVibe() {
        if (blocked()) return
        if (isOffline()) {
            playOfflineMix(ReturnPoint(PlaylistSource.VIBE, emptyList(), null))
            return
        }
        _currentSource.value = PlaylistSource.VIBE
        val waveList = wave.tracks.value
        val unplayed = wave.firstUnplayed()
        if (unplayed != null) {
            playTrack(unplayed.idString, waveList, PlaylistSource.VIBE, chosen = false)
            if (waveList.size < 30) wave.fetchMore()
            return
        }

        // Nothing cached yet (first launch): show the loading state and start once tracks arrive.
        val request = ++startRequestId
        _isStarting.value = true
        wave.fetchMore { _ ->
            if (request != startRequestId) return@fetchMore
            val list = wave.tracks.value
            if (list.isNotEmpty()) {
                playTrack(list.first().idString, list, PlaylistSource.VIBE, chosen = false)
            } else {
                _isStarting.value = false
                showMessage(context.getString(R.string.msg_wave_load_failed))
            }
        }
    }

    fun playAllLikedTracks(shuffle: Boolean = false) {
        val playlist = if (shuffle) libraryTracks.shuffled() else libraryTracks
        if (playlist.isEmpty()) return
        playTrack(playlist.first().idString, playlist, PlaylistSource.LIBRARY)
        // Shuffled from the start: "Перемешано" is on, and turning it off gives the list's own order
        if (shuffle) {
            orderBeforeShuffle = libraryTracks
            _shuffled.value = true
        }
    }

    /**
     * "Перемешать" / back to the order: the current track plays on, what follows it is shuffled — or put back in
     * the order it had, with the current track at its own place there.
     */
    fun toggleShuffle() {
        val currentId = _currentTrack.value?.takeIf { !it.isRadio }?.idString ?: return
        if (_shuffled.value) {
            val original = orderBeforeShuffle
            if (original != null) updateQueueKeepingStandby { queue -> PlaybackQueue.restored(queue, original) { it.idString } }
            orderBeforeShuffle = null
            _shuffled.value = false
        } else {
            orderBeforeShuffle = _currentPlaylist.value
            updateQueueKeepingStandby { queue -> PlaybackQueue.shuffledAround(queue, currentId, { it.idString }) }
            _shuffled.value = true
        }
        scope.launch { persistSession() }
    }

    fun playHits(hits: List<TrackDetailsDto>, shuffle: Boolean = true) {
        val hitsList = if (shuffle) hits.shuffled() else hits
        if (hitsList.isEmpty()) return
        val trackToPlay = hitsList.firstOrNull { it.idString !in recentPlayedTrackIds } ?: hitsList.first()
        playTrack(trackToPlay.idString, hitsList, PlaylistSource.TRENDS)
    }

    /** The user's own music ("Музыка с устройства"): the queue is [playlist], nothing of it goes to the network. */
    fun playOnDevice(trackId: String, playlist: List<TrackDetailsDto>) {
        if (playlist.none { it.idString == trackId }) return
        playTrack(trackId, playlist, PlaylistSource.DEVICE)
    }

    fun playRadioStation(station: RadioStation) {
        val radioTrack = radioTrackFor(station)
        playTrack(radioTrack.idString, listOf(radioTrack), PlaylistSource.RADIO)
        scope.launch { radioRepository.setLastStation(station.id) }
    }

    fun togglePlayPause() {
        if (isPlaying.value) pause() else play()
    }

    fun play() {
        if (blocked()) return
        val current = _currentTrack.value
        if (current == null) {
            playMyVibe()
            return
        }
        // A start is already in flight (e.g. a second Play from a headset): don't restart it
        if (_isStarting.value) return
        if (playerRepository.currentTrackId.value == current.idString && playerRepository.resume()) return
        // Nothing loaded (restored session or stopped player): start the current track where it was left.
        resolveAndPlay(current, crossfade = false, startPositionMs = restoredPositionMs.value)
    }

    fun pause() {
        waitingForConnection = false
        if (_isStarting.value) {
            // Cancel a start that hasn't produced sound yet.
            startRequestId++
            currentPlayJob?.cancel()
            _isStarting.value = false
        }
        playerRepository.pause()
        scope.launch { persistSession() }
    }

    fun seekTo(positionMs: Long) {
        if (playerRepository.currentTrackId.value == null) {
            restoredPositionMs.value = positionMs
        } else {
            playerRepository.seekTo(positionMs)
        }
    }

    fun playNext(isAuto: Boolean = false, sendFeedback: Boolean = true) {
        val current = _currentTrack.value
        if (current == null) {
            val playlist = _currentPlaylist.value
            if (playlist.isNotEmpty()) playTrack(playlist.first().idString, playlist) else playAllLikedTracks()
            return
        }
        if (current.isRadio) {
            stepRadioStation(current, +1)
            return
        }
        if (returnFromOfflineMix()) return

        val currentId = current.idString
        if (_currentSource.value == PlaylistSource.VIBE && sendFeedback) {
            val playedSeconds = progress.value / 1000.0
            sendRotorFeedback(if (isAuto) "trackFinished" else "skip", currentId, playedSeconds)
        }

        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == currentId }
        when (val action = PlaybackQueue.nextAction(playlist, currentIndex, _currentSource.value, ::skippedOnTheWay)) {
            is NextAction.PlayIndex -> playTrack(playlist[action.index].idString, playlist, chosen = false)
            NextAction.FetchMoreWave -> {
                if (isOffline()) {
                    skipToNextOnDevice(current)
                    return
                }
                // End of the wave playlist: fetch more instead of stopping.
                val request = ++startRequestId
                wave.fetchMore { _ ->
                    if (request != startRequestId) return@fetchMore
                    val updated = _currentPlaylist.value
                    val newIndex = updated.indexOfFirst { it.idString == currentId }
                    val following = PlaybackQueue.nextMatching(updated, newIndex) { !skippedOnTheWay(it) }
                        ?: (newIndex + 1).takeIf { newIndex != -1 && it < updated.size }
                    when {
                        following != null -> playTrack(updated[following].idString, updated, chosen = false)
                        updated.isNotEmpty() -> playTrack(updated.first().idString, updated, chosen = false)
                        else -> playAllLikedTracks()
                    }
                }
            }
            NextAction.FallbackLibrary -> playAllLikedTracks()
        }
    }

    fun playPrev() {
        val current = _currentTrack.value ?: return
        if (current.isRadio) {
            stepRadioStation(current, -1)
            return
        }
        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == current.idString }
        val prevIndex = PlaybackQueue.previousIndex(currentIndex, progress.value)
        if (prevIndex == null) seekTo(0) else playTrack(playlist[prevIndex].idString, playlist)
    }

    fun loadMoreQueue() {
        if (_currentSource.value == PlaylistSource.VIBE) wave.fetchMore()
    }

    fun reorderQueue(from: Int, to: Int) {
        val list = _currentPlaylist.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _currentPlaylist.value = list
    }

    /** Puts [track] right after the current one; with nothing (or radio) playing it just starts. */
    fun enqueueNext(track: TrackDetailsDto) {
        val current = _currentTrack.value
        if (current == null || current.isRadio) {
            playTrack(track.idString, listOf(track))
            return
        }
        if (current.idString == track.idString) return
        val queue = _currentPlaylist.value.filterNot { it.idString == track.idString }.toMutableList()
        queue.add(queue.indexOfFirst { it.idString == current.idString } + 1, track)
        _currentPlaylist.value = queue
    }

    fun removeFromQueue(trackId: String) {
        updateQueueKeepingStandby { queue -> queue.filterNot { it.idString == trackId } }
    }

    /** Drag in the queue: [trackId] takes the place of [targetId]. The current track never moves. */
    fun moveInQueue(trackId: String, targetId: String) {
        val currentId = _currentTrack.value?.idString
        if (trackId == currentId || targetId == currentId) return
        updateQueueKeepingStandby { queue ->
            PlaybackQueue.moved(queue, queue.indexOfFirst { it.idString == trackId }, queue.indexOfFirst { it.idString == targetId })
        }
    }

    /** Changes the queue; when what follows the current track changed, that track is prepared instead. */
    private fun updateQueueKeepingStandby(change: (List<TrackDetailsDto>) -> List<TrackDetailsDto>) {
        val before = _currentPlaylist.value
        val after = change(before)
        if (after === before) return
        _currentPlaylist.value = after
        val currentId = _currentTrack.value?.idString ?: return
        val nextBefore = before.getOrNull(before.indexOfFirst { it.idString == currentId } + 1)
        val nextAfter = after.getOrNull(after.indexOfFirst { it.idString == currentId } + 1)
        if (nextAfter != null && nextAfter.idString != nextBefore?.idString) prepareStandby(nextAfter)
    }

    /** Dislike behaves like Yandex Music: the current track is skipped right away. */
    fun onTrackDisliked(trackId: String) {
        wave.remove(trackId)
        if (_currentTrack.value?.idString == trackId) playNext()
        removeFromQueue(trackId)
    }

    fun stopAndClear() {
        startRequestId++
        currentPlayJob?.cancel()
        _isStarting.value = false
        playerRepository.stop()
        _currentTrack.value = null
        _currentPlaylist.value = emptyList()
        _shuffled.value = false
        orderBeforeShuffle = null
        _currentSource.value = PlaylistSource.NONE
        restoredPositionMs.value = 0L
        waitingForConnection = false
        returnPoint = null
        scope.launch { stateStore.clear() }
    }

    /** Account switch/logout: drop the session and load the other user's wave and history. */
    fun resetForAccount(userFolder: String?) {
        stopAndClear()
        // The reserve was saved under this account's subscription
        scope.launch { reserve.clear() }
        recentPlayedTrackIds.clear()
        wave.switchTo(WaveQueue.MY_WAVE)
        _waveSeed.value = null
        _waveTuning.value = WaveTuning()
        libraryTracks = emptyList()
        if (userFolder != null) scope.launch { loadUserState(userFolder, restoreSession = false) }
    }

    fun clearWaveAndHistory() {
        wave.clear()
        recentPlayedTrackIds.clear()
    }

    /** Silent My Wave refresh on launch: append fresh unplayed tracks and warm their URLs. */
    fun primeWave() {
        wave.fetchMore()
        loadWaveTuning()
    }

    // region Wave tuning and a wave by a track

    private fun loadWaveTuning() {
        val station = wave.station
        if (authRepository.getToken().isNullOrEmpty() || isOffline()) return
        scope.launch(Dispatchers.IO) {
            val settings = runCatching { api.getStationInfo(station).result?.firstOrNull()?.settings2 }
                .onFailure { Log.w(TAG, "Wave settings not loaded", it) }
                .getOrNull() ?: return@launch
            if (wave.station == station) _waveTuning.value = WaveTuning.fromWire(settings.diversity, settings.moodEnergy, settings.language)
        }
    }

    /**
     * Saves the station's settings on the server, then the wave goes on with tracks that fit them: the
     * current track plays on, what comes after it is fetched anew.
     */
    fun setWaveTuning(tuning: WaveTuning) {
        val before = _waveTuning.value
        if (tuning == before) return
        _waveTuning.value = tuning
        val station = wave.station
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    api.setStationSettings(
                        station,
                        kotlinx.serialization.json.buildJsonObject {
                            put("diversity", JsonPrimitive(tuning.diversity.wire))
                            put("moodEnergy", JsonPrimitive(tuning.mood.wire))
                            put("language", JsonPrimitive(tuning.language.wire))
                            put("type", JsonPrimitive("rotor"))
                        }
                    )
                }.onFailure { Log.w(TAG, "Wave settings not saved", it) }.isSuccess
            }
            if (!saved) {
                if (_waveTuning.value == tuning) _waveTuning.value = before
                showMessage(context.getString(R.string.wave_tuning_failed))
                return@launch
            }
            if (wave.station == station) restartWaveAfterCurrent()
        }
    }

    /** The wave starts over after the current track: same station, fresh tracks. */
    private fun restartWaveAfterCurrent() {
        wave.switchTo(wave.station)
        if (_currentSource.value == PlaylistSource.VIBE) {
            _currentPlaylist.value = listOfNotNull(_currentTrack.value?.takeIf { !it.isRadio })
            playerRepository.dropStandby()
        }
        wave.fetchMore()
    }

    /**
     * A wave by [track]: the track plays (on, if it already does) and similar ones follow. What played
     * before is kept, so [undoTrackWave] can go back to it.
     */
    fun startTrackWave(track: TrackDetailsDto) {
        // The user's own file is not in the service: there is no wave by it
        if (track.isLocal) return
        if (track.isRadio || isOffline()) {
            if (isOffline()) showMessage(context.getString(R.string.msg_offline_no_downloads))
            return
        }
        val current = _currentTrack.value
        beforeTrackWave = BeforeTrackWave(_currentSource.value, _currentPlaylist.value, current?.idString, progress.value, _waveSeed.value)
        wave.switchTo(WaveQueue.trackStation(track.idString))
        _waveSeed.value = track
        _waveTuning.value = WaveTuning()
        if (current?.idString == track.idString) {
            _currentSource.value = PlaylistSource.VIBE
            returnPoint = null
            _currentPlaylist.value = listOf(current)
            playerRepository.dropStandby()
            wave.fetchMore()
            scope.launch { persistSession() }
        } else {
            playTrack(track.idString, listOf(track), PlaylistSource.VIBE)
        }
        loadWaveTuning()
        _trackWaveStarted.tryEmit(track)
    }

    /** Back to My Wave; the current track plays on. */
    fun returnToMyWave() {
        if (_waveSeed.value == null) return
        _waveSeed.value = null
        beforeTrackWave = null
        val current = _currentTrack.value?.takeIf { !it.isRadio }
        scope.launch {
            val saved = cacheManager.loadWaveQueue(activeUserFolder).filterNot { it.idString == current?.idString }
            wave.switchTo(WaveQueue.MY_WAVE, saved)
            _waveTuning.value = WaveTuning()
            loadWaveTuning()
            if (_currentSource.value == PlaylistSource.VIBE) {
                _currentPlaylist.value = (listOfNotNull(current) + saved).distinctBy { it.idString }
                playerRepository.dropStandby()
                prepareNextTrackInQueue()
            }
            if (saved.size < 10) wave.fetchMore()
            persistSession()
        }
    }

    /** "Undo" right after a wave by a track started: what played before comes back. */
    fun undoTrackWave() {
        val before = beforeTrackWave ?: return
        beforeTrackWave = null
        if (before.seed != null) {
            // It was a wave by another track: go back to that one
            startTrackWave(before.seed)
            beforeTrackWave = null
            return
        }
        _waveSeed.value = null
        val current = _currentTrack.value
        scope.launch {
            wave.switchTo(WaveQueue.MY_WAVE, cacheManager.loadWaveQueue(activeUserFolder))
            _waveTuning.value = WaveTuning()
            loadWaveTuning()
            val previous = before.trackId
            if (previous != null && previous != current?.idString && before.playlist.any { it.idString == previous }) {
                // Started from a menu on another track: the track that played before comes back where it was
                playTrack(previous, before.playlist, before.source, startPositionMs = before.positionMs)
            } else {
                _currentSource.value = before.source
                _currentPlaylist.value = before.playlist.ifEmpty { listOfNotNull(current) }
                playerRepository.dropStandby()
                prepareNextTrackInQueue()
                persistSession()
            }
        }
    }

    // endregion

    fun setSleepTimer(minutes: Int) = sleep.start(minutes)

    fun setSleepAtEndOfTrack() = sleep.atEndOfTrack()

    fun cancelSleepTimer() = sleep.cancel()

    /** "Without explicit lyrics": such tracks are passed over on the way; a track the user picks still plays. */
    private fun skippedOnTheWay(track: TrackDetailsDto): Boolean =
        (skipExplicit.value && track.isExplicit) || (skipAi.value && aiMarks.isAi(track.idString))

    /**
     * Marks are asked for what plays and what comes next, and for what the wave will play; when a mark arrives for
     * a track that has started by itself, it is left at once (the queue's own next, no "skip" sent to the wave).
     */
    private fun watchAiMarks() {
        combine(_currentTrack, _currentPlaylist, wave.preview) { track, queue, preview ->
            val index = queue.indexOfFirst { it.idString == track?.idString }
            val upcoming = if (index < 0) emptyList() else queue.drop(index + 1).take(AI_LOOK_AHEAD)
            // The last wish is asked first: the playing track, then the next ones, then the wave's preview
            (preview.take(AI_LOOK_AHEAD) + upcoming.reversed() + listOfNotNull(track)).filterNot { it.isLocal || it.isRadio }.map { it.idString }
        }.distinctUntilChanged().onEach { aiMarks.want(it) }.launchIn(scope)
        combine(aiMarks.marks, skipAi, _currentTrack) { marks, on, track -> Triple(marks, on, track) }
            .onEach { (marks, on, track) ->
                // Only a track the queue reached by itself, marked while it has just begun, and never twice
                val marked = track?.takeIf { on && marks.containsKey(it.idString) } ?: return@onEach
                if (currentChosen || leftForMark == marked.idString || progress.value > AI_LATE_LEAVE_MS) return@onEach
                leftForMark = marked.idString
                playNext(sendFeedback = false)
            }.launchIn(scope)
    }

    private fun onTrackEnded() {
        // With crossfade the end is announced a few seconds early: the timer fades out over that tail
        if (sleep.takeEndOfTrack(PlayerRepositoryImpl.CROSSFADE_DURATION_MS)) return
        playNext(isAuto = true)
    }

    fun persistNow() {
        scope.launch { persistSession() }
    }

    /**
     * Playback resumption (headset/Bluetooth Play or the system media card after the process died):
     * waits for the saved session, optionally starts it, and returns its metadata for the system UI.
     */
    suspend fun prepareResumption(startPlayback: Boolean): Pair<MediaItem, Long>? {
        sessionRestored.await()
        if (blocked()) return null
        val track = _currentTrack.value ?: return null
        if (startPlayback) {
            play()
            withTimeoutOrNull(RESUMPTION_START_TIMEOUT_MS) { playerRepository.isPlaying.first { it } }
        }
        val cover = coverUrlFor(track)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists.firstOrNull()?.name)
            // The cover of the user's own file is this app's alone: never its address to the system
            .apply { if (!LocalArtProvider.isOurs(cover)) setArtworkUri(cover?.toUri()) }
            .build()
        val item = MediaItem.Builder()
            .setMediaId(track.idString)
            .setMediaMetadata(metadata)
            .build()
        return item to progress.value
    }

    // endregion

    private fun resolveAndPlay(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
        if (blocked()) return
        currentPlayJob?.cancel()
        val trackId = track.idString
        val title = track.title
        val artist = track.artists.firstOrNull()?.name
        val coverUrl = coverUrlFor(track)
        _isStarting.value = true
        _streamQuality.value = null

        if (track.isRadio) {
            val station = stationFor(trackId)
            if (station == null) {
                _isStarting.value = false
                return
            }
            playerRepository.playTrack(trackId, station.streamUrl, title, artist, coverUrl, crossfade, 0L)
            return
        }
        if (track.isLocal) {
            playLocalFile(track, crossfade, startPositionMs)
            return
        }

        val downloaded = preloadManager.hasPlayableCopy(trackId)
        if (downloaded) {
            publishQuality(trackId)
            streamingTrackId = null
            playingCopyId = null
            playerRepository.playTrack(trackId, "", title, artist, coverUrl, crossfade, startPositionMs)
            return
        }
        when (RoadPlan.originFor(currentLink(), link.mayTryStream(), reserve.has(trackId))) {
            TrackOrigin.UNAVAILABLE -> {
                _isStarting.value = false
                skipToNextOnDevice(track)
                return
            }
            TrackOrigin.COPY -> {
                playCopy(track, crossfade, startPositionMs)
                return
            }
            TrackOrigin.STREAM -> Unit
        }
        when (smoothOrigin(trackId)) {
            SmoothOrigin.COPY -> {
                playCopy(track, crossfade, startPositionMs)
                return
            }
            SmoothOrigin.LIGHT_STREAM -> {
                playLight(track, crossfade, startPositionMs)
                return
            }
            SmoothOrigin.CHOSEN_STREAM -> Unit
        }
        playChosenStream(track, crossfade, startPositionMs)
    }

    /** A file of the user's own music: straight from the device, no network, no cache. */
    private fun playLocalFile(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
        val uri = track.localUri
        if (uri == null) {
            _isStarting.value = false
            skipToNextLocal(track)
            return
        }
        _streamQuality.value = localMusic.qualityOf(track.idString)
        streamingTrackId = null
        playingCopyId = null
        playerRepository.playTrack(track.idString, uri, track.title, track.artists.firstOrNull()?.name, coverUrlFor(track), crossfade, startPositionMs)
    }

    /**
     * A file of the user's music would not open: the flash drive was taken out, the file deleted. The next one
     * that still opens plays; with none left the music stops and says why.
     */
    private fun skipToNextLocal(from: TrackDetailsDto) {
        val queue = _currentPlaylist.value
        val start = queue.indexOfFirst { it.idString == from.idString }
        val request = ++startRequestId
        currentPlayJob?.cancel()
        currentPlayJob = scope.launch {
            val order = (queue.drop(start + 1) + queue.take(start.coerceAtLeast(0))).filter { it.idString != from.idString }
            val next = order.firstOrNull { candidate ->
                val uri = candidate.localUri
                // A track of the service plays from the network or the device as always
                if (!candidate.isLocal) true else uri != null && localMusic.isReachable(uri)
            }
            if (request != startRequestId) return@launch
            if (next != null) {
                playTrack(next.idString, queue, chosen = false)
            } else {
                _isStarting.value = false
                playerRepository.stop()
                restoredPositionMs.value = 0L
                showMessage(context.getString(R.string.msg_local_unavailable))
            }
        }
    }

    /** The uneven link: a light stream, or the chosen one when no light one is offered. */
    private fun playLight(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
        val trackId = track.idString
        currentPlayJob = scope.launch {
            val light = lightStream(trackId)
            if (_currentTrack.value?.idString != trackId) return@launch
            if (light == null) {
                playChosenStream(track, crossfade, startPositionMs)
                return@launch
            }
            _streamQuality.value = context.getString(R.string.quality_light, light.quality.codec.uppercase(), light.quality.bitrateKbps)
            streamingTrackId = trackId
            playingCopyId = null
            journal.record(PlaybackJournal.Kind.LIGHT, track.title)
            playerRepository.playTrack(trackId, light.url, track.title, track.artists.firstOrNull()?.name, coverUrlFor(track), crossfade, startPositionMs)
        }
    }

    /** The stream of the chosen quality, as always. */
    private fun playChosenStream(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
        val trackId = track.idString
        // Instant start when the URL is already resolved
        val fastUrl = preloadManager.getResolvedUrl(trackId)
        if (fastUrl != null) {
            playStreamOrEqualCopy(track, fastUrl, crossfade, startPositionMs)
            return
        }

        currentPlayJob = scope.launch {
            val url = preloadManager.getOrFetchUrl(trackId)
                ?: run {
                    delay(URL_RETRY_DELAY_MS)
                    preloadManager.getOrFetchUrl(trackId)
                }
            if (url != null) {
                updateLink(LinkReason.SERVER_ANSWERED) { onServerReachable() }
                playStreamOrEqualCopy(track, url, crossfade, startPositionMs)
            } else {
                Log.e(TAG, "Failed to resolve mp3 URL for track $trackId")
                reportTrouble(LinkReason.REQUEST_FAILED)
                if (_currentTrack.value?.idString != trackId) return@launch
                onPlaybackFailure(PlaybackFailure(trackId, startPositionMs, "URL_RESOLVE"))
            }
        }
    }

    /**
     * The sound mode changed. Links and the warmed-up next track came from the old mode. A FLAC track
     * playing in silence is the reason for the switch, so it goes on from the same second at once;
     * anything else plays on, and the next tracks follow the new mode.
     */
    fun onSoundModeChanged(compatible: Boolean) {
        val track = _currentTrack.value?.takeIf { !it.isRadio }
        val id = track?.idString
        val playingFlac = id != null && playingCopyId != id && if (streamingTrackId == id) {
            preloadManager.qualityOf(id)?.isLossless == true
        } else {
            downloadManager.downloadedQuality(id)?.isLossless == true
        }
        preloadManager.clearCache()
        playerRepository.dropStandby()
        if (!compatible || track == null || !playingFlac) return
        if (isPlaying.value) {
            resolveAndPlay(track, crossfade = false, startPositionMs = progress.value)
        } else {
            // On pause: Play starts it again from here
            restoredPositionMs.value = progress.value
            playerRepository.stop()
        }
    }

    /** The stream, unless the reserve holds as good a copy: then it starts at once and costs no traffic. */
    private fun playStreamOrEqualCopy(track: TrackDetailsDto, url: String, crossfade: Boolean, startPositionMs: Long) {
        val trackId = track.idString
        val copy = reserve.qualityOf(trackId)
        if (copy != null && RoadPlan.copyIsEnough(preloadManager.qualityOf(trackId), copy)) {
            playCopy(track, crossfade, startPositionMs)
            return
        }
        publishQuality(trackId)
        streamingTrackId = trackId
        playingCopyId = null
        playerRepository.playTrack(trackId, url, track.title, track.artists.firstOrNull()?.name, coverUrlFor(track), crossfade, startPositionMs)
    }

    private fun playCopy(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
        val trackId = track.idString
        val file = reserve.fileOf(trackId)
        if (file == null) {
            scope.launch { reserve.remove(trackId) }
            _isStarting.value = false
            skipToNextOnDevice(track)
            return
        }
        currentPlayJob?.cancel()
        _isStarting.value = true
        _streamQuality.value = reserve.qualityOf(trackId)?.let { context.getString(R.string.quality_from_cache, it.codec.uppercase(), it.bitrateKbps) }
        streamingTrackId = null
        playingCopyId = trackId
        playerRepository.playTrack(trackId, file.toUri().toString(), track.title, track.artists.firstOrNull()?.name, coverUrlFor(track), crossfade, startPositionMs)
    }

    /** The music has stood still long enough to matter: go on from the copy if there is one, and find out why. */
    private fun onStreamStalled(track: TrackDetailsDto) {
        // In its own job: the music coming back ends the watchdog, and the music must not wait for the check
        scope.launch { reportTrouble(LinkReason.STREAM_STALLED) }
        val trackId = track.idString
        if (!reserve.has(trackId)) return
        val position = if (playerRepository.currentTrackId.value == trackId) progress.value else restoredPositionMs.value
        journal.record(PlaybackJournal.Kind.COPY, track.title, position)
        playCopy(track, crossfade = false, startPositionMs = position)
    }

    /**
     * Stops in the middle of a track tell an uneven link: one longer than 1.5 s, or two within two minutes.
     * Then the playing track goes on from its copy or a light stream, and the next ones follow; after five
     * steady minutes the chosen quality comes back with the next track.
     */
    private fun watchTheSteadiness() {
        playerRepository.isBuffering.onEach { buffering ->
            stallLastedJob?.cancel()
            val trackId = playerRepository.currentTrackId.value
            val track = _currentTrack.value
            if (buffering) {
                // A slow flash drive is not a bad link
                val midTrack = trackId != null && trackId == soundedTrackId && track?.idString == trackId && track.isStreamed
                if (!midTrack) return@onEach
                if (steadiness.onStall()) onLinkUneven()
                stallLastedJob = scope.launch {
                    delay(LinkSteadiness.LONG_STALL_MS)
                    if (playerRepository.isBuffering.value && steadiness.onStallLasted()) onLinkUneven()
                }
            } else {
                steadiness.onStallOver()?.let { journal.record(PlaybackJournal.Kind.STALL, track?.title, it) }
            }
        }.launchIn(scope)
        scope.launch {
            while (true) {
                delay(STEADY_CHECK_MS)
                if (steadiness.checkSteady()) {
                    lightStreams.clear()
                    journal.record(PlaybackJournal.Kind.STEADY)
                }
            }
        }
    }

    /** The link has just become uneven. */
    private fun onLinkUneven() {
        journal.record(PlaybackJournal.Kind.UNEVEN)
        if (!smoothOn.value) return
        val track = _currentTrack.value?.takeIf { !it.isRadio && !it.isLocal } ?: return
        val trackId = track.idString
        val fromStream = streamingTrackId == trackId && playerRepository.currentTrackId.value == trackId
        val switching = fromStream && switchToSmoothSource(track)
        // The next track was prepared from the chosen stream: it follows the uneven link too
        if (!switching) {
            playerRepository.dropStandby()
            prepareNextTrackInQueue()
        }
    }

    /** The playing track goes on from its copy, or a light stream when it is heavier. @return true if it does. */
    private fun switchToSmoothSource(track: TrackDetailsDto): Boolean {
        val trackId = track.idString
        val artist = track.artists.firstOrNull()?.name
        val copy = copyUri(trackId)
        if (copy != null) {
            _streamQuality.value = reserve.qualityOf(trackId)?.let { context.getString(R.string.quality_from_cache, it.codec.uppercase(), it.bitrateKbps) }
            streamingTrackId = null
            playingCopyId = trackId
            journal.record(PlaybackJournal.Kind.COPY, track.title, progress.value)
            playerRepository.switchSource(trackId, copy, track.title, artist, coverUrlFor(track))
            return true
        }
        val playing = preloadManager.qualityOf(trackId)
        if (playing != null && !playing.isLossless && playing.bitrateKbps <= SmoothPlan.LIGHT_KBPS) return false
        scope.launch {
            val light = lightStream(trackId) ?: return@launch
            if (_currentTrack.value?.idString != trackId || !steadiness.uneven) return@launch
            _streamQuality.value = context.getString(R.string.quality_light, light.quality.codec.uppercase(), light.quality.bitrateKbps)
            journal.record(PlaybackJournal.Kind.LIGHT, track.title)
            playerRepository.switchSource(trackId, light.url, track.title, artist, coverUrlFor(track))
        }
        return true
    }

    /** A light ordinary stream, asked for once per track while the link is uneven. */
    private suspend fun lightStream(trackId: String): TrackPreloadManager.ResolvedStream? {
        lightStreams[trackId]?.takeIf { SystemClock.elapsedRealtime() - it.second < LIGHT_URL_TTL_MS }?.let { return it.first }
        val light = preloadManager.resolveLightStream(trackId, SmoothPlan.LIGHT_KBPS) ?: return null
        lightStreams[trackId] = light to SystemClock.elapsedRealtime()
        return light
    }

    /** Where a track comes from while the link is uneven; the chosen stream otherwise. */
    private fun smoothOrigin(trackId: String): SmoothOrigin = SmoothPlan.originFor(steadiness.uneven, smoothOn.value, reserve.has(trackId))

    /** Is the server there at all? A short check, with no data to speak of: it tells a weak link from no link. */
    private suspend fun reportTrouble(why: LinkReason) {
        // A second try: on a very slow, busy link one connection can fail with the server still there
        val reachable = networkMonitor.isCurrentlyConnected() &&
            (networkMonitor.isYandexReachable(SERVER_CHECK_TIMEOUT_MS) || networkMonitor.isYandexReachable(SERVER_CHECK_TIMEOUT_MS))
        updateLink(if (reachable) why else LinkReason.SERVER_SILENT) { onTrouble(reachable) }
    }

    /** Changes the link's state; a change is written to the journal with [why], so a report can be read later. */
    private fun updateLink(why: LinkReason? = null, change: LinkTracker.() -> Unit) {
        val before = link.state
        link.change()
        val after = link.state
        _linkState.value = after
        if (before != after && why != null) {
            val kind = when (after) {
                LinkState.WEAK -> PlaybackJournal.Kind.LINK_WEAK
                LinkState.NONE -> PlaybackJournal.Kind.LINK_NONE
                LinkState.GOOD -> PlaybackJournal.Kind.LINK_BACK
            }
            journal.record(kind, valueMs = why.ordinal.toLong())
        }
    }

    /** The system's word on the network, the check of the server while it is out of reach, and coming back. */
    private fun watchTheLink() {
        networkMonitor.isConnectedFlow.onEach { present ->
            updateLink(if (present) LinkReason.NETWORK_BACK else LinkReason.NETWORK_GONE) { onNetwork(present) }
        }.launchIn(scope)
        scope.launch {
            _linkState.collectLatest { state ->
                if (state == LinkState.GOOD && waitingForConnection) {
                    waitingForConnection = false
                    play()
                }
                // Soon at first (a short drop), then every half-minute
                var pause = SERVER_CHECK_FIRST_MS
                while (state == LinkState.NONE) {
                    delay(pause)
                    pause = SERVER_CHECK_INTERVAL_MS
                    if (networkMonitor.isCurrentlyConnected() && networkMonitor.isYandexReachable(SERVER_CHECK_TIMEOUT_MS)) {
                        updateLink(LinkReason.SERVER_ANSWERED) { onServerReachable() }
                    }
                }
            }
        }
    }

    /** The connection is good again: the music left for a mix of what is on the device goes back. */
    private fun returnFromOfflineMix(): Boolean {
        val point = returnPoint ?: return false
        if (currentLink() != LinkState.GOOD) return false
        returnPoint = null
        if (point.source == PlaylistSource.VIBE) {
            playMyVibe()
            return true
        }
        val index = point.queue.indexOfFirst { it.idString == point.leftTrackId }
        val next = point.queue.getOrNull(index + 1) ?: point.queue.firstOrNull() ?: return false
        playTrack(next.idString, point.queue, point.source, chosen = false)
        return true
    }

    private fun upcomingTracks(): List<TrackDetailsDto> {
        val queue = _currentPlaylist.value
        val currentId = _currentTrack.value?.idString
        return queue.drop(queue.indexOfFirst { it.idString == currentId } + 1).filterNot { it.isLocal }
    }

    /**
     * First failure of a track: retry it once with a fresh URL from the same position (expired links
     * after a long pause). Repeated failure: skip. Several broken tracks in a row: stop and tell the user.
     */
    private fun onPlaybackFailure(failure: PlaybackFailure) {
        val current = _currentTrack.value ?: return
        if (failure.trackId != current.idString) return // stale event from a track we already left
        if (current.isLocal) {
            onLocalFailure(current, failure)
            return
        }

        preloadManager.evictUrl(failure.trackId)
        // A copy that does not play is no use to anyone
        val copyFailed = playingCopyId == failure.trackId
        if (copyFailed) {
            playingCopyId = null
            scope.launch { reserve.remove(failure.trackId) }
        }
        val action = PlaybackQueue.recoveryFor(
            offline = isOffline(),
            isRadio = current.isRadio,
            isDownloaded = preloadManager.hasPlayableCopy(current.idString),
            alreadyRetried = retriedTrackId == failure.trackId,
            failedTracksInARow = consecutiveFailedTracks + 1,
            hasCopy = !copyFailed && reserve.has(failure.trackId),
            weakLink = _linkState.value == LinkState.WEAK
        )
        when (action) {
            Recovery.RESUME_FROM_COPY -> {
                retriedTrackId = null
                playCopy(current, crossfade = false, startPositionMs = failure.positionMs)
            }
            Recovery.CONTINUE_OFFLINE -> {
                // The link dropped mid-track: not a broken track, continue with what is on the device
                retriedTrackId = null
                skipToNextOnDevice(current)
            }
            Recovery.RETRY -> {
                retriedTrackId = failure.trackId
                Log.w(TAG, "Retrying ${failure.trackId} from ${failure.positionMs} ms after ${failure.reason}")
                resolveAndPlay(current, crossfade = false, startPositionMs = failure.positionMs)
            }
            Recovery.STOP -> {
                consecutiveFailedTracks = 0
                retriedTrackId = null
                _isStarting.value = false
                playerRepository.stop()
                // A station that went silent with the connection comes back with it
                waitingForConnection = isOffline()
                // Keep the track on screen so Play retries it
                restoredPositionMs.value = failure.positionMs
                showMessage(context.getString(if (current.isRadio) R.string.msg_station_unavailable else R.string.msg_playback_failed))
            }
            Recovery.SKIP -> {
                consecutiveFailedTracks++
                retriedTrackId = null
                playNext()
            }
        }
    }

    /**
     * A file of the user's music failed. Taken out with the drive, it is skipped at once; a file that is there
     * but does not play (a format the device does not know) is skipped too, and several in a row stop the music.
     */
    private fun onLocalFailure(current: TrackDetailsDto, failure: PlaybackFailure) {
        Log.w(TAG, "Local file ${failure.trackId} failed: ${failure.reason}")
        retriedTrackId = null
        consecutiveFailedTracks++
        if (consecutiveFailedTracks >= PlaybackQueue.MAX_FAILED_TRACKS) {
            consecutiveFailedTracks = 0
            _isStarting.value = false
            playerRepository.stop()
            restoredPositionMs.value = failure.positionMs
            showMessage(context.getString(R.string.msg_local_unavailable))
            return
        }
        skipToNextLocal(current)
    }

    /** What plays now and what the server offered, for the player's quality details. */
    data class QualityDetails(
        val playing: String?,
        val offered: String?,
        val hasPlus: Boolean? = null
    )

    fun qualityDetails(): QualityDetails? {
        val track = _currentTrack.value?.takeIf { !it.isRadio } ?: return null
        val id = track.idString
        if (track.isLocal) return QualityDetails(_streamQuality.value, offered = null)
        val hasPlus = authRepository.getActiveAccount()?.hasPlus
        return QualityDetails(
            _streamQuality.value,
            preloadManager.offeredOf(id),
            hasPlus
        )
    }

    private fun publishQuality(trackId: String) {
        _streamQuality.value = if (preloadManager.hasPlayableCopy(trackId)) {
            val q = downloadManager.downloadedQuality(trackId)
            if (q != null) {
                if (q.codec.startsWith("flac", ignoreCase = true)) "FLAC · ОФЛАЙН"
                else context.getString(R.string.quality_offline_known, q.codec.uppercase(), q.bitrateKbps)
            } else {
                context.getString(R.string.quality_offline)
            }
        } else {
            preloadManager.qualityOf(trackId)?.let {
                if (it.isLossless) "FLAC · LOSSLESS"
                else context.getString(R.string.quality_stream, it.codec.uppercase(), it.bitrateKbps)
            }
        }
    }

    private fun isOffline(): Boolean = !networkMonitor.isCurrentlyConnected() || link.state == LinkState.NONE

    private fun currentLink(): LinkState = if (isOffline()) LinkState.NONE else link.state

    private fun isOnDevice(track: TrackDetailsDto): Boolean = track.isLocal || preloadManager.hasPlayableCopy(track.idString) || reserve.has(track.idString)

    /**
     * No connection and the queue has nothing more on the device: the rest of the reserve (what has not
     * been heard first), then the downloads, shuffled. With nothing at all the music waits for the connection.
     */
    private fun playOfflineMix(leaving: ReturnPoint) {
        val reserved = ReservePolicy.playOrder(reserve.entries.value.values) { it in recentPlayedTrackIds }
            .map { it.track }
            .filterNot { preloadManager.hasPlayableCopy(it.idString) }
        val downloaded = libraryTracks.filter { preloadManager.hasPlayableCopy(it.idString) }.shuffled()
        // Nothing of the service on the device: the user's own music plays rather than silence
        val own = if (reserved.isEmpty() && downloaded.isEmpty()) localMusic.toDtos(localMusic.library.value.tracks).shuffled() else emptyList()
        val mix = (reserved + downloaded + own).distinctBy { it.idString }
        if (mix.isEmpty()) {
            _isStarting.value = false
            waitingForConnection = true
            playerRepository.pause()
            showMessage(context.getString(R.string.msg_offline_no_downloads))
            return
        }
        // Coming from a mix already, the place to go back to stays what it was
        val point = returnPoint ?: leaving
        playTrack(mix.first().idString, mix, PlaylistSource.LIBRARY, chosen = false)
        returnPoint = point
    }

    /** The next track after [from] that is on the device, otherwise the mix of what is. */
    private fun skipToNextOnDevice(from: TrackDetailsDto) {
        if (from.isRadio) {
            playerRepository.stop()
            waitingForConnection = true
            showMessage(context.getString(R.string.msg_station_unavailable))
            return
        }
        val queue = _currentPlaylist.value
        val start = queue.indexOfFirst { it.idString == from.idString }
        val next = PlaybackQueue.nextMatching(queue, start) { isOnDevice(it) }
        if (next != null) playTrack(queue[next].idString, queue, chosen = false) else playOfflineMix(ReturnPoint(_currentSource.value, queue, from.idString))
    }

    private fun prepareNextTrackInQueue() {
        val current = _currentTrack.value ?: return
        if (current.isRadio) return
        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == current.idString }

        // Proactively refill the wave while 10 tracks are still ahead: the "next up" shelf never runs dry
        if (_currentSource.value == PlaylistSource.VIBE && playlist.size - currentIndex <= 10) {
            wave.fetchMore()
        }

        val nextTrack = when (val action = PlaybackQueue.nextAction(playlist, currentIndex, _currentSource.value, ::skippedOnTheWay)) {
            is NextAction.PlayIndex -> playlist[action.index]
            else -> null
        }
        nextTrack?.let { prepareStandby(it) }
    }

    private var standbyJob: Job? = null

    /**
     * The next track is readied once the current one has [RESERVE_HEADROOM_MS] in hand and no track change runs:
     * both loading at the start of a track split the line exactly when the new one needs all of it.
     */
    private fun prepareStandbyWhenSettled(track: TrackDetailsDto) {
        standbyJob?.cancel()
        val current = _currentTrack.value?.idString
        standbyJob = scope.launch {
            while (playerRepository.inTransition || playerRepository.bufferedAheadMs < RESERVE_HEADROOM_MS || playerRepository.currentTrackId.value != current) {
                if (_currentTrack.value?.idString != current) return@launch
                delay(STANDBY_POLL_MS)
            }
            prepareStandby(track)
        }
    }

    private fun prepareStandby(track: TrackDetailsDto) {
        if (track.isRadio) return
        scope.launch {
            val nextId = track.idString
            val url = when {
                track.isLocal -> track.localUri ?: return@launch
                preloadManager.hasPlayableCopy(nextId) -> ""
                RoadPlan.originFor(currentLink(), link.mayTryStream(), reserve.has(nextId)) == TrackOrigin.COPY -> copyUri(nextId) ?: return@launch
                currentLink() == LinkState.NONE -> return@launch
                smoothOrigin(nextId) == SmoothOrigin.COPY -> copyUri(nextId) ?: return@launch
                smoothOrigin(nextId) == SmoothOrigin.LIGHT_STREAM -> lightStream(nextId)?.url ?: return@launch
                else -> preloadManager.getOrFetchUrl(nextId)?.let { stream ->
                    val copy = reserve.qualityOf(nextId)
                    if (copy != null && RoadPlan.copyIsEnough(preloadManager.qualityOf(nextId), copy)) copyUri(nextId) ?: stream else stream
                } ?: return@launch
            }
            playerRepository.prepareNextTrack(nextId, url, track.title, track.artists.firstOrNull()?.name, coverUrlFor(track))
        }
    }

    /**
     * The wave page's "next up" shelf must never stay empty. Whenever it is short (first launch, a restored
     * paused wave with nothing after the current track, a failed request) more of the wave is fetched, again
     * after a growing pause if a request fails, and at once when the network comes back. Waits for the saved
     * session, so the first request does not race the restore.
     */
    private fun keepWaveShelfFilled() {
        scope.launch {
            sessionRestored.await()
            combine(
                _currentSource,
                _currentTrack,
                _currentPlaylist,
                wave.tracks,
                // Online and signed in: signed out there is nothing to fetch, and signing in checks again
                networkMonitor.isConnectedFlow.combine(authRepository.isAuthorized()) { online, signedIn -> online && signedIn }
            ) { source, track, queue, wave, canFetch ->
                canFetch && PlaybackQueue.waveShelfIsShort(
                    waveActive = source == PlaylistSource.VIBE && track != null,
                    currentId = track?.idString,
                    queue = queue.map { it.idString },
                    wave = wave.map { it.idString }
                )
            }.collectLatest { short ->
                if (!short) return@collectLatest
                var pause = WAVE_RETRY_START_MS
                // A successful request changes the lists, which re-checks the shelf from the top
                while (!wave.fetchMoreAwait()) {
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(WAVE_RETRY_MAX_MS)
                }
            }
        }
    }

    private fun copyUri(trackId: String): String? = reserve.fileOf(trackId)?.toUri()?.toString()

    private fun recordTrackPlayed(trackId: String) {
        val snapshot = recentPlayedTrackIds.record(trackId) ?: return
        val folder = activeUserFolder
        scope.launch { cacheManager.saveRecentTrackIds(folder, snapshot) }
    }

    private fun sendRotorFeedback(type: String, trackId: String, playedSeconds: Double? = null) {
        val station = wave.station
        scope.launch(Dispatchers.IO) {
            try {
                api.sendStationFeedback(station, type, trackId, from = WaveQueue.feedbackFrom(station), totalPlayedSeconds = playedSeconds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error sending rotor feedback $type", e)
            }
        }
    }

    private fun stepRadioStation(current: TrackDetailsDto, step: Int) {
        val stations = radioRepository.getBuiltinStations()
        RadioRules.step(stations, favouriteStations.value, current.idString.removePrefix("radio_"), step)?.let(::playRadioStation)
    }

    private fun stationFor(trackId: String): RadioStation? =
        radioRepository.getBuiltinStations().firstOrNull { "radio_${it.id}" == trackId }

    private fun radioTrackFor(station: RadioStation) = TrackDetailsDto(
        id = JsonPrimitive("radio_${station.id}"),
        title = station.name,
        artists = listOf(ArtistDto(id = JsonPrimitive("0"), name = context.getString(R.string.radio_live_frequency, station.frequency))),
        coverUri = station.iconRes?.let { "android.resource://${BuildConfig.APPLICATION_ID}/$it" } ?: station.logoUrl.ifEmpty { null },
        durationMs = 0,
        isRadio = true
    )

    private suspend fun persistSession() {
        val current = _currentTrack.value ?: return
        val queue = _currentPlaylist.value
        val index = queue.indexOfFirst { it.idString == current.idString }
        stateStore.save(
            PlaybackSnapshot(
                userFolder = activeUserFolder,
                source = _currentSource.value.name,
                queue = PlaybackQueue.snapshotWindow(queue, index).ifEmpty { listOf(current) },
                currentTrackId = current.idString,
                positionMs = progress.value,
                waveSeed = _waveSeed.value.takeIf { _currentSource.value == PlaylistSource.VIBE }
            )
        )
    }

    private suspend fun loadUserState(userFolder: String, restoreSession: Boolean) {
        val savedRecent = cacheManager.loadRecentTrackIds(userFolder)
        val cachedWave = cacheManager.loadWaveQueue(userFolder)
        if (activeUserFolder != userFolder) return // account switched while loading

        recentPlayedTrackIds.restore(savedRecent)
        // Without what was just heard, so the wave starts with something fresh
        wave.restore(cachedWave)

        if (!restoreSession || _currentTrack.value != null || playerRepository.currentTrackId.value != null) return
        val snapshot = stateStore.load() ?: return
        if (snapshot.userFolder != userFolder || _currentTrack.value != null) return

        val source = runCatching { PlaylistSource.valueOf(snapshot.source) }.getOrDefault(PlaylistSource.NONE)
        // isRadio is @Transient, so it is rebuilt from the id prefix.
        val queue = snapshot.queue.map { if (it.idString.startsWith("radio_")) it.copy(isRadio = true) else it }
        val track = queue.firstOrNull { it.idString == snapshot.currentTrackId } ?: return
        // A wave by a track goes on from where it was, with its own upcoming tracks
        val seed = snapshot.waveSeed?.takeIf { source == PlaylistSource.VIBE }
        if (seed != null) {
            wave.switchTo(WaveQueue.trackStation(seed.idString), queue.dropWhile { it.idString != track.idString }.drop(1))
            _waveSeed.value = seed
            loadWaveTuning()
        }
        // Shown paused, like Apple Music: Play continues from the saved position.
        _currentSource.value = source
        _currentPlaylist.value = if (source == PlaylistSource.VIBE && seed == null && wave.tracks.value.isNotEmpty()) {
            (queue + wave.tracks.value).distinctBy { it.idString }
        } else {
            queue
        }
        _currentTrack.value = track
        restoredPositionMs.value = if (track.isRadio) 0L else snapshot.positionMs
    }

    private fun coverUrlFor(track: TrackDetailsDto): String? = track.coverUri?.let { uri ->
        if (uri.startsWith("android.resource")) uri else uri.toCoverUrl(CoverSize.Medium)
    }

    /** User-facing playback errors; the visible UI shows them as a Snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private fun showMessage(text: String) {
        // No screen is collecting (app in background): fall back to a system toast
        if (_messages.subscriptionCount.value == 0) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        } else {
            _messages.tryEmit(text)
        }
    }

    /** Comes over the network: not a station (live, no copy) and not the user's own file. */
    private val TrackDetailsDto.isStreamed: Boolean get() = !isRadio && !isLocal

    companion object {
        const val WAVE_RETRY_START_MS = 2_000L
        const val WAVE_RETRY_MAX_MS = 60_000L
        private const val TAG = "PlaybackController"
        private const val RECENT_LIMIT = 100
        private const val URL_RETRY_DELAY_MS = 700L
        private const val SNAPSHOT_INTERVAL_MS = 15_000L
        private const val RESUMPTION_START_TIMEOUT_MS = 4_000L
        /** Longest a stream may keep buffering before we treat the stall as a failure and recover. */
        private const val BUFFERING_TIMEOUT_MS = 15_000L
        /** The player's buffer is empty and the music stands: the moment to look at the link. */
        private const val LINK_STALL_MS = 6_000L
        /** The cache fills only while the playing track has this much in hand. */
        const val RESERVE_HEADROOM_MS = 20_000L
        /** How many tracks ahead the AI marks are asked for. */
        private const val AI_LOOK_AHEAD = 5
        /** A mark that comes later than this into a track no longer takes it away: it has been heard. */
        private const val AI_LATE_LEAVE_MS = 20_000L
        private const val STEADY_CHECK_MS = 30_000L
        private const val LIGHT_URL_TTL_MS = 30 * 60_000L
        private const val SERVER_CHECK_INTERVAL_MS = 30_000L
        private const val SERVER_CHECK_FIRST_MS = 5_000L
        /** The first half-minute of a track: its own, not the cache's. */
        private const val TRACK_SETTLE_MS = 30_000L
        private const val STANDBY_POLL_MS = 500L
        /** Long enough for a slow link to answer: a weak link must not read as no link. */
        private const val SERVER_CHECK_TIMEOUT_MS = 4_000
    }
}

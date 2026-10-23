package com.korenskoy.yandexmusic.data.player

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
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
    private val settingsRepository: com.korenskoy.yandexmusic.domain.repository.SettingsRepository,
    private val networkMonitor: com.korenskoy.yandexmusic.data.network.NetworkMonitor
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Unhandled playback coroutine error", e)
        }
    )

    private val _currentSource = MutableStateFlow(PlaylistSource.NONE)
    val currentSource: StateFlow<PlaylistSource> = _currentSource.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = _currentPlaylist.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackDetailsDto?>(null)
    val currentTrack: StateFlow<TrackDetailsDto?> = _currentTrack.asStateFlow()

    private val _isFetchingMoreQueue = MutableStateFlow(false)
    val isFetchingMoreQueue: StateFlow<Boolean> = _isFetchingMoreQueue.asStateFlow()

    /** Playback was requested but the stream URL is still being resolved. */
    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

    /** Real stream quality of the current track ("MP3 · 320 кбит/с"), null for radio or while unknown. */
    private val _streamQuality = MutableStateFlow<String?>(null)
    val streamQuality: StateFlow<String?> = _streamQuality.asStateFlow()

    /** Position of a restored (not yet loaded) session, shown until the player takes over. */
    private val restoredPositionMs = MutableStateFlow(0L)

    /** Play/Pause icon state: true while playing, buffering, or resolving the next stream. */
    val isPlaying: StateFlow<Boolean> = combine(playerRepository.isPlaying, _isStarting) { playing, starting ->
        playing || starting
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isBuffering: StateFlow<Boolean> = combine(playerRepository.isBuffering, _isStarting) { buffering, starting ->
        buffering || starting
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val progress: StateFlow<Long> = combine(
        playerRepository.progress,
        playerRepository.currentTrackId,
        restoredPositionMs
    ) { position, loadedId, restored ->
        if (loadedId == null) restored else position
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    private val wavePlaylist = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    private var libraryTracks: List<TrackDetailsDto> = emptyList()
    private val recentPlayedTrackIds = ArrayDeque<String>(RECENT_LIMIT)

    private var currentPlayJob: Job? = null
    /** Incremented on every explicit start/stop so stale async callbacks can tell they were superseded. */
    private var startRequestId = 0L
    private var retriedTrackId: String? = null
    private var consecutiveFailedTracks = 0
    private val pendingFetchCallbacks = mutableListOf<(Boolean) -> Unit>()
    /** Completes once the saved session (if any) has been loaded from disk. */
    private val sessionRestored = kotlinx.coroutines.CompletableDeferred<Unit>()

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
        combine(playerRepository.isPlaying, playerRepository.isBuffering) { playing, buffering -> playing && !buffering }
            .onEach { audible ->
                if (audible) {
                    _isStarting.value = false
                    consecutiveFailedTracks = 0
                    retriedTrackId = null
                }
            }
            .launchIn(scope)

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
        startPositionMs: Long = 0L
    ) {
        val track = playlist.find { it.idString == trackId } ?: return
        startRequestId++
        source?.let { _currentSource.value = it }
        _currentPlaylist.value = playlist
        _currentTrack.value = track
        restoredPositionMs.value = startPositionMs

        recordTrackPlayed(trackId)

        val currentIndex = playlist.indexOfFirst { it.idString == trackId }
        val upcoming = playlist.drop(currentIndex + 1).take(3).map { it.idString }
        if (upcoming.isNotEmpty()) preloadManager.preloadTracks(upcoming)
        playlist.getOrNull(currentIndex + 1)?.let { prepareStandby(it) }

        // Continuous queue auto-refill: fewer than 20 tracks left in My Wave
        if (_currentSource.value == PlaylistSource.VIBE && playlist.size - currentIndex <= 20) {
            fetchMoreVibeTracks()
        }
        if (_currentSource.value == PlaylistSource.VIBE && !track.isRadio) {
            sendRotorFeedback("trackStarted", trackId)
        }

        resolveAndPlay(track, crossfade, startPositionMs)
        scope.launch { persistSession() }
    }

    fun playMyVibe() {
        if (isOffline()) {
            playOfflineMix()
            return
        }
        _currentSource.value = PlaylistSource.VIBE
        val waveList = wavePlaylist.value
        val unplayed = waveList.firstOrNull { it.idString !in recentPlayedTrackIds } ?: waveList.firstOrNull()
        if (unplayed != null) {
            playTrack(unplayed.idString, waveList, PlaylistSource.VIBE)
            if (waveList.size < 30) fetchMoreVibeTracks()
            return
        }

        // Nothing cached yet (first launch): show the loading state and start once tracks arrive.
        val request = ++startRequestId
        _isStarting.value = true
        fetchMoreVibeTracks { _ ->
            if (request != startRequestId) return@fetchMoreVibeTracks
            val list = wavePlaylist.value
            if (list.isNotEmpty()) {
                playTrack(list.first().idString, list, PlaylistSource.VIBE)
            } else {
                _isStarting.value = false
                showMessage(context.getString(R.string.msg_wave_load_failed))
            }
        }
    }

    fun playAllLikedTracks(shuffle: Boolean = false) {
        val playlist = if (shuffle) libraryTracks.shuffled() else libraryTracks
        if (playlist.isNotEmpty()) playTrack(playlist.first().idString, playlist, PlaylistSource.LIBRARY)
    }

    fun playHits(hits: List<TrackDetailsDto>, shuffle: Boolean = true) {
        val hitsList = if (shuffle) hits.shuffled() else hits
        if (hitsList.isEmpty()) return
        val trackToPlay = hitsList.firstOrNull { it.idString !in recentPlayedTrackIds } ?: hitsList.first()
        playTrack(trackToPlay.idString, hitsList, PlaylistSource.TRENDS)
    }

    fun playRadioStation(station: RadioStation) {
        val radioTrack = radioTrackFor(station)
        playTrack(radioTrack.idString, listOf(radioTrack), PlaylistSource.RADIO)
    }

    fun togglePlayPause() {
        if (isPlaying.value) pause() else play()
    }

    fun play() {
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

    fun playNext(isAuto: Boolean = false) {
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

        val currentId = current.idString
        if (_currentSource.value == PlaylistSource.VIBE) {
            val playedSeconds = progress.value / 1000.0
            sendRotorFeedback(if (isAuto) "trackFinished" else "skip", currentId, playedSeconds)
        }

        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == currentId }
        when (val action = PlaybackQueue.nextAction(playlist.size, currentIndex, _currentSource.value)) {
            is NextAction.PlayIndex -> playTrack(playlist[action.index].idString, playlist)
            NextAction.FetchMoreWave -> {
                // End of the wave playlist: fetch more instead of stopping.
                val request = ++startRequestId
                fetchMoreVibeTracks { _ ->
                    if (request != startRequestId) return@fetchMoreVibeTracks
                    val updated = _currentPlaylist.value
                    val newIndex = updated.indexOfFirst { it.idString == currentId }
                    when {
                        newIndex != -1 && newIndex + 1 < updated.size -> playTrack(updated[newIndex + 1].idString, updated)
                        updated.isNotEmpty() -> playTrack(updated.first().idString, updated)
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
        if (_currentSource.value == PlaylistSource.VIBE) fetchMoreVibeTracks()
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
        _currentPlaylist.value = _currentPlaylist.value.filterNot { it.idString == trackId }
    }

    /** Dislike behaves like Yandex Music: the current track is skipped right away. */
    fun onTrackDisliked(trackId: String) {
        wavePlaylist.value = wavePlaylist.value.filterNot { it.idString == trackId }
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
        _currentSource.value = PlaylistSource.NONE
        restoredPositionMs.value = 0L
        scope.launch { stateStore.clear() }
    }

    /** Account switch/logout: drop the session and load the other user's wave and history. */
    fun resetForAccount(userFolder: String?) {
        stopAndClear()
        recentPlayedTrackIds.clear()
        wavePlaylist.value = emptyList()
        libraryTracks = emptyList()
        if (userFolder != null) scope.launch { loadUserState(userFolder, restoreSession = false) }
    }

    fun clearWaveAndHistory() {
        wavePlaylist.value = emptyList()
        recentPlayedTrackIds.clear()
    }

    /** Silent My Wave refresh on launch: append fresh unplayed tracks and warm their URLs. */
    fun primeWave() {
        fetchMoreVibeTracks()
    }

    /** Pause playback after [minutes] with a gentle fade, like the sleep timer in Apple Music. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val durationMs = minutes * 60_000L
        _sleepTimer.value = SleepTimerState(android.os.SystemClock.elapsedRealtime() + durationMs, atEndOfTrack = false)
        sleepJob = scope.launch {
            delay((durationMs - SLEEP_FADE_MS).coerceAtLeast(0L))
            playerRepository.fadeOutAndPause(SLEEP_FADE_MS)
            delay(SLEEP_FADE_MS)
            _sleepTimer.value = null
            persistSession()
        }
    }

    /** Pause when the current track finishes instead of moving on. */
    fun setSleepAtEndOfTrack() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState(endsAtElapsedMs = null, atEndOfTrack = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = null
    }

    private fun onTrackEnded() {
        if (_sleepTimer.value?.atEndOfTrack == true) {
            _sleepTimer.value = null
            // Fires a few seconds before the end when crossfade is on: fade out over that tail
            playerRepository.fadeOutAndPause(
                kotlin.math.min(com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl.CROSSFADE_DURATION_MS, SLEEP_FADE_MS)
            )
            scope.launch { persistSession() }
            return
        }
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
        val track = _currentTrack.value ?: return null
        if (startPlayback) {
            play()
            withTimeoutOrNull(RESUMPTION_START_TIMEOUT_MS) { playerRepository.isPlaying.first { it } }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists.firstOrNull()?.name)
            .setArtworkUri(coverUrlFor(track)?.toUri())
            .build()
        val item = MediaItem.Builder()
            .setMediaId(track.idString)
            .setMediaMetadata(metadata)
            .build()
        return item to progress.value
    }

    // endregion

    private fun resolveAndPlay(track: TrackDetailsDto, crossfade: Boolean, startPositionMs: Long) {
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

        // Offline: streams cannot load, so move on to music that is on the device instead of failing
        if (isOffline() && !downloadManager.isKnownDownloaded(trackId)) {
            _isStarting.value = false
            skipToNextDownloaded(track)
            return
        }

        // Instant start when the file is on disk or the URL is already resolved
        val fastUrl = if (downloadManager.isKnownDownloaded(trackId)) "" else preloadManager.getResolvedUrl(trackId)
        if (fastUrl != null) {
            publishQuality(trackId)
            playerRepository.playTrack(trackId, fastUrl, title, artist, coverUrl, crossfade, startPositionMs)
            return
        }

        currentPlayJob = scope.launch {
            val url = preloadManager.getOrFetchUrl(trackId)
                ?: run {
                    delay(URL_RETRY_DELAY_MS)
                    preloadManager.getOrFetchUrl(trackId)
                }
            if (url != null) {
                publishQuality(trackId)
                playerRepository.playTrack(trackId, url, title, artist, coverUrl, crossfade, startPositionMs)
            } else {
                Log.e(TAG, "Failed to resolve mp3 URL for track $trackId")
                onPlaybackFailure(PlaybackFailure(trackId, startPositionMs, "URL_RESOLVE"))
            }
        }
    }

    /**
     * First failure of a track: retry it once with a fresh URL from the same position (expired links
     * after a long pause). Repeated failure: skip. Several broken tracks in a row: stop and tell the user.
     */
    private fun onPlaybackFailure(failure: PlaybackFailure) {
        val current = _currentTrack.value ?: return
        if (failure.trackId != current.idString) return // stale event from a track we already left

        preloadManager.evictUrl(failure.trackId)
        if (isOffline() && !current.isRadio && !downloadManager.isKnownDownloaded(current.idString)) {
            // The network dropped mid-track: not a broken track, continue with downloaded music
            retriedTrackId = null
            skipToNextDownloaded(current)
            return
        }
        if (retriedTrackId != failure.trackId) {
            retriedTrackId = failure.trackId
            Log.w(TAG, "Retrying ${failure.trackId} from ${failure.positionMs} ms after ${failure.reason}")
            resolveAndPlay(current, crossfade = false, startPositionMs = failure.positionMs)
            return
        }

        consecutiveFailedTracks++
        if (current.isRadio || consecutiveFailedTracks >= MAX_FAILED_TRACKS) {
            consecutiveFailedTracks = 0
            retriedTrackId = null
            _isStarting.value = false
            playerRepository.stop()
            // Keep the track on screen so Play retries it.
            restoredPositionMs.value = failure.positionMs
            showMessage(
                context.getString(if (current.isRadio) R.string.msg_station_unavailable else R.string.msg_playback_failed)
            )
            return
        }
        retriedTrackId = null
        playNext()
    }

    /** What plays now and what the server offered, for the player's quality details. */
    data class QualityDetails(
        val playing: String?,
        val offered: String?,
        val economy: Boolean,
        val hasPlus: Boolean? = null
    )

    fun qualityDetails(): QualityDetails? {
        val track = _currentTrack.value?.takeIf { !it.isRadio } ?: return null
        val id = track.idString
        val hasPlus = authRepository.getActiveAccount()?.hasPlus
        return QualityDetails(
            _streamQuality.value,
            preloadManager.offeredOf(id),
            !downloadManager.isKnownDownloaded(id) && preloadManager.isCapped(id),
            hasPlus
        )
    }

    private fun publishQuality(trackId: String) {
        _streamQuality.value = if (downloadManager.isKnownDownloaded(trackId)) {
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

    private fun isOffline(): Boolean = !networkMonitor.isCurrentlyConnected()

    private fun downloadedLibrary(): List<TrackDetailsDto> =
        libraryTracks.filter { downloadManager.isKnownDownloaded(it.idString) }

    /** No network: play the downloaded liked tracks, shuffled, like Apple Music's offline library. */
    private fun playOfflineMix() {
        val offline = downloadedLibrary().shuffled()
        if (offline.isEmpty()) {
            _isStarting.value = false
            showMessage(context.getString(R.string.msg_offline_no_downloads))
            return
        }
        showMessage(context.getString(R.string.msg_offline_playing_downloaded))
        playTrack(offline.first().idString, offline, PlaylistSource.LIBRARY)
    }

    /** Next downloaded track after [from] in the current queue, otherwise the offline mix. */
    private fun skipToNextDownloaded(from: TrackDetailsDto) {
        if (from.isRadio) {
            playerRepository.stop()
            showMessage(context.getString(R.string.msg_station_unavailable))
            return
        }
        val queue = _currentPlaylist.value
        val start = queue.indexOfFirst { it.idString == from.idString }
        val next = PlaybackQueue.nextMatching(queue, start) { downloadManager.isKnownDownloaded(it.idString) }
        if (next != null) playTrack(queue[next].idString, queue) else playOfflineMix()
    }

    private fun prepareNextTrackInQueue() {
        val current = _currentTrack.value ?: return
        if (current.isRadio) return
        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == current.idString }

        // Proactively refill Vibe queue if remaining tracks <= 5
        if (_currentSource.value == PlaylistSource.VIBE && playlist.size - currentIndex <= 5) {
            fetchMoreVibeTracks()
        }

        val nextTrack = when (val action = PlaybackQueue.nextAction(playlist.size, currentIndex, _currentSource.value)) {
            is NextAction.PlayIndex -> playlist[action.index]
            else -> null
        }
        nextTrack?.let { prepareStandby(it) }
    }

    private fun prepareStandby(track: TrackDetailsDto) {
        if (track.isRadio) return
        scope.launch {
            val nextId = track.idString
            val downloaded = downloadManager.isKnownDownloaded(nextId)
            val url = if (downloaded) "" else preloadManager.getOrFetchUrl(nextId) ?: return@launch
            playerRepository.prepareNextTrack(nextId, url, track.title, track.artists.firstOrNull()?.name, coverUrlFor(track))
        }
    }

    private fun fetchMoreVibeTracks(onFetched: ((Boolean) -> Unit)? = null) {
        if (isOffline()) {
            onFetched?.invoke(false)
            return
        }
        onFetched?.let { pendingFetchCallbacks += it }
        if (_isFetchingMoreQueue.value) return // the running fetch will invoke the callback
        _isFetchingMoreQueue.value = true
        scope.launch {
            var hasNew = false
            try {
                val waveTracks = withContext(Dispatchers.IO) { fetchWaveBatch() }
                if (waveTracks.isNotEmpty()) {
                    val existingIds = wavePlaylist.value.map { it.idString }.toSet()
                    // 1. Prefer tracks that are neither queued nor recently played
                    val fresh = waveTracks.filter { it.idString !in existingIds && it.idString !in recentPlayedTrackIds }
                        // 2. Adaptive fallback: still append anything not already queued
                        .ifEmpty { waveTracks.filter { it.idString !in existingIds } }
                    if (fresh.isNotEmpty()) {
                        wavePlaylist.value = wavePlaylist.value + fresh
                        if (_currentSource.value == PlaylistSource.VIBE) _currentPlaylist.value = wavePlaylist.value
                        hasNew = true
                        cacheManager.saveWaveQueue(activeUserFolder, wavePlaylist.value)
                        preloadManager.preloadTracks(fresh.take(5).map { it.idString })
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching more vibe tracks", e)
            } finally {
                _isFetchingMoreQueue.value = false
                val callbacks = pendingFetchCallbacks.toList()
                pendingFetchCallbacks.clear()
                callbacks.forEach { it(hasNew) }
            }
        }
    }

    private suspend fun fetchWaveBatch(): List<TrackDetailsDto> = supervisorScope {
        // One request continues from the last queued track, the other adds variety.
        val lastQueued = wavePlaylist.value.lastOrNull()?.idString
        val continued = async { api.getMyWaveTracks(queue = lastQueued).result?.sequence?.mapNotNull { it.track } }
        val fresh = async { api.getMyWaveTracks().result?.sequence?.mapNotNull { it.track } }
        val a = try { continued.await() ?: emptyList() } catch (e: Exception) { emptyList() }
        val b = try { fresh.await() ?: emptyList() } catch (e: Exception) { emptyList() }
        (a + b).distinctBy { it.idString }
    }

    private fun recordTrackPlayed(trackId: String) {
        if (trackId.isEmpty() || trackId.startsWith("radio_") || trackId in recentPlayedTrackIds) return
        recentPlayedTrackIds.addLast(trackId)
        if (recentPlayedTrackIds.size > RECENT_LIMIT) recentPlayedTrackIds.removeFirst()
        val folder = activeUserFolder
        val snapshot = recentPlayedTrackIds.toList()
        scope.launch { cacheManager.saveRecentTrackIds(folder, snapshot) }
    }

    private fun sendRotorFeedback(type: String, trackId: String, playedSeconds: Double? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                api.sendRotorFeedback(type, trackId, totalPlayedSeconds = playedSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending rotor feedback $type", e)
            }
        }
    }

    private fun stepRadioStation(current: TrackDetailsDto, step: Int) {
        val stations = radioRepository.getBuiltinStations()
        if (stations.isEmpty()) return
        val index = stations.indexOfFirst { "radio_${it.id}" == current.idString }
        if (index == -1) return
        playRadioStation(stations[(index + step + stations.size) % stations.size])
    }

    private fun stationFor(trackId: String): RadioStation? =
        radioRepository.getBuiltinStations().firstOrNull { "radio_${it.id}" == trackId }

    private fun radioTrackFor(station: RadioStation) = TrackDetailsDto(
        id = kotlinx.serialization.json.JsonPrimitive("radio_${station.id}"),
        title = station.name,
        artists = listOf(ArtistDto(id = kotlinx.serialization.json.JsonPrimitive("0"), name = "Прямой эфир • ${station.frequency}")),
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
                positionMs = progress.value
            )
        )
    }

    private suspend fun loadUserState(userFolder: String, restoreSession: Boolean) {
        val savedRecent = cacheManager.loadRecentTrackIds(userFolder)
        val cachedWave = cacheManager.loadWaveQueue(userFolder)
        if (activeUserFolder != userFolder) return // account switched while loading

        recentPlayedTrackIds.clear()
        recentPlayedTrackIds.addAll(savedRecent.takeLast(RECENT_LIMIT))
        if (cachedWave.isNotEmpty() && wavePlaylist.value.isEmpty()) {
            // Filter out already played tracks to guarantee a fresh first track on launch
            wavePlaylist.value = cachedWave.filter { it.idString !in recentPlayedTrackIds }.ifEmpty { cachedWave }
        }

        if (!restoreSession || _currentTrack.value != null || playerRepository.currentTrackId.value != null) return
        val snapshot = stateStore.load() ?: return
        if (snapshot.userFolder != userFolder || _currentTrack.value != null) return

        val source = runCatching { PlaylistSource.valueOf(snapshot.source) }.getOrDefault(PlaylistSource.NONE)
        // isRadio is @Transient, so it is rebuilt from the id prefix.
        val queue = snapshot.queue.map { if (it.idString.startsWith("radio_")) it.copy(isRadio = true) else it }
        val track = queue.firstOrNull { it.idString == snapshot.currentTrackId } ?: return
        // Shown paused, like Apple Music: Play continues from the saved position.
        _currentSource.value = source
        _currentPlaylist.value = if (source == PlaylistSource.VIBE && wavePlaylist.value.isNotEmpty()) {
            (queue + wavePlaylist.value).distinctBy { it.idString }
        } else {
            queue
        }
        _currentTrack.value = track
        restoredPositionMs.value = if (track.isRadio) 0L else snapshot.positionMs
    }

    private fun coverUrlFor(track: TrackDetailsDto): String? = track.coverUri?.let { uri ->
        if (uri.startsWith("http") || uri.startsWith("android.resource")) uri else "https://${uri.replace("%%", "1000x1000")}"
    }

    /** User-facing playback errors; the visible UI shows them as a Snackbar. */
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    private fun showMessage(text: String) {
        // No screen is collecting (app in background): fall back to a system toast
        if (_messages.subscriptionCount.value == 0) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        } else {
            _messages.tryEmit(text)
        }
    }

    companion object {
        private const val TAG = "PlaybackController"
        private const val RECENT_LIMIT = 100
        private const val MAX_FAILED_TRACKS = 3
        private const val URL_RETRY_DELAY_MS = 700L
        private const val SNAPSHOT_INTERVAL_MS = 15_000L
        private const val RESUMPTION_START_TIMEOUT_MS = 4_000L
        private const val SLEEP_FADE_MS = 8_000L
    }
}

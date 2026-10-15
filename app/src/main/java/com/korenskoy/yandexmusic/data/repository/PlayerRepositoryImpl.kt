package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.player.MusicService
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PlayerRepositoryImpl @Inject constructor(
    private val context: Context,
    private val playerA: ExoPlayer,
    private val playerB: ExoPlayer,
    private val downloadManager: TrackDownloadManager,
    private val preloadManager: TrackPreloadManager,
    private val wakeLockHelper: com.korenskoy.yandexmusic.data.player.PlaybackWakeLockHelper
) : PlayerRepository {

    private val _currentTrackId = MutableStateFlow<String?>(null)
    override val currentTrackId: StateFlow<String?> = _currentTrackId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    override val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _trackEndedEvent = MutableSharedFlow<Unit>()
    override val trackEndedEvent: Flow<Unit> = _trackEndedEvent.asSharedFlow()

    private val _prepareNextTrackEvent = MutableSharedFlow<Unit>()
    override val prepareNextTrackEvent: Flow<Unit> = _prepareNextTrackEvent.asSharedFlow()

    private val _skipNextEvent = MutableSharedFlow<Unit>()
    override val skipNextEvent: Flow<Unit> = _skipNextEvent.asSharedFlow()

    private val _skipPrevEvent = MutableSharedFlow<Unit>()
    override val skipPrevEvent: Flow<Unit> = _skipPrevEvent.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var crossfadeJob: Job? = null

    // Dual-Player ping-pong: 0 -> playerA, 1 -> playerB
    @Volatile
    private var activePlayerIndex = 0

    private val activePlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerA else playerB

    private val standbyPlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerB else playerA

    @Volatile
    private var lastSeekTimestamp = 0L
    @Volatile
    private var pendingSeekPosition = -1L

    @Volatile
    private var isTransitioning = false
    @Volatile
    private var isCrossfading = false
    @Volatile
    private var hasEmittedPrepareForCurrent = false
    @Volatile
    private var hasEmittedTrackEndedForCurrent = false

    private val delegatingPlayer = object : ForwardingPlayer(playerA) {
        override fun getPlayWhenReady(): Boolean = activePlayer.playWhenReady
        override fun getPlaybackState(): Int = activePlayer.playbackState
        override fun isPlaying(): Boolean = playerA.isPlaying || playerB.isPlaying
        override fun getCurrentPosition(): Long = activePlayer.currentPosition
        override fun getDuration(): Long = activePlayer.duration
        override fun getCurrentMediaItem(): MediaItem? = activePlayer.currentMediaItem
        override fun getMediaMetadata(): MediaMetadata = activePlayer.mediaMetadata
        override fun getCurrentTimeline(): androidx.media3.common.Timeline = activePlayer.currentTimeline
        override fun getCurrentTracks(): androidx.media3.common.Tracks = activePlayer.currentTracks
        override fun play() { this@PlayerRepositoryImpl.resume() }
        override fun pause() { this@PlayerRepositoryImpl.pause() }
        override fun stop() { this@PlayerRepositoryImpl.stop() }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            this@PlayerRepositoryImpl.seekTo(positionMs)
        }
    }

    init {
        setupPlayerListener(playerA, 0)
        setupPlayerListener(playerB, 1)

        scope.launch {
            while (isActive) {
                val currentP = activePlayer
                if (currentP.isPlaying && !isTransitioning) {
                    val now = System.currentTimeMillis()
                    val timeSinceSeek = now - lastSeekTimestamp
                    if (timeSinceSeek < 600L && pendingSeekPosition >= 0L) {
                        if (abs(currentP.currentPosition - pendingSeekPosition) < 400L) {
                            pendingSeekPosition = -1L
                            _progress.value = currentP.currentPosition
                        }
                    } else {
                        pendingSeekPosition = -1L
                        _progress.value = currentP.currentPosition
                    }

                    val duration = currentP.duration
                    val currentPos = currentP.currentPosition
                    if (duration > 8_000L && currentPos > 0L) {
                        val remaining = duration - currentPos

                        // 1. Warm standby preparation at 15 seconds remaining:
                        if (remaining <= PREPARE_STANDBY_THRESHOLD_MS && !hasEmittedPrepareForCurrent && !isCrossfading) {
                            hasEmittedPrepareForCurrent = true
                            scope.launch {
                                _prepareNextTrackEvent.emit(Unit)
                            }
                        }

                        // 2. 3-second early trigger for seamless overlapping crossfade:
                        if (remaining in 1L..CROSSFADE_DURATION_MS && !hasEmittedTrackEndedForCurrent && !isCrossfading) {
                            hasEmittedTrackEndedForCurrent = true
                            wakeLockHelper.acquireTransitionLock(30_000L)
                            scope.launch {
                                _trackEndedEvent.emit(Unit)
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun setupPlayerListener(player: ExoPlayer, playerIdx: Int) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = playerA.isPlaying || playerB.isPlaying
                if (isPlaying && activePlayerIndex == playerIdx) {
                    isTransitioning = false
                    wakeLockHelper.releaseTransitionLock()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    android.util.Log.d("PlayerRepositoryImpl", "Player[$playerIdx] reached STATE_ENDED, active=$activePlayerIndex, isCrossfading=$isCrossfading")
                    if (activePlayerIndex == playerIdx || (!playerA.isPlaying && !playerB.isPlaying)) {
                        wakeLockHelper.releaseTransitionLock()
                        isCrossfading = false
                        isTransitioning = false
                        hasEmittedTrackEndedForCurrent = false
                        hasEmittedPrepareForCurrent = false
                        triggerSkipNext()
                    }
                }
                if (playbackState == Player.STATE_READY && activePlayerIndex == playerIdx) {
                    isTransitioning = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlayerRepositoryImpl", "ExoPlayer error on player[$playerIdx]: ${error.errorCodeName}", error)
                if (activePlayerIndex == playerIdx) {
                    wakeLockHelper.releaseTransitionLock()
                    isTransitioning = false
                    isCrossfading = false
                    triggerSkipNext()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && activePlayerIndex == playerIdx) {
                    pendingSeekPosition = -1L
                    _progress.value = newPosition.positionMs
                }
            }
        })
    }

    override fun getPlayer(): Player {
        return delegatingPlayer
    }

    @Volatile
    private var preloadedTrackId: String? = null

    override fun prepareNextTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?) {
        if (preloadedTrackId == trackId || isCrossfading) return
        scope.launch {
            val resolvedUrl = preloadManager.getResolvedUrl(trackId) ?: url
            val localFile = downloadManager.getLocalTrackFile(trackId)
            val playUri = if (localFile != null) {
                android.net.Uri.fromFile(localFile).toString()
            } else {
                resolvedUrl
            }
            if (playUri.isBlank()) return@launch

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(title ?: "Unknown Track")
                .setDisplayTitle(title ?: "Unknown Track")
                .setArtist(artist ?: "Unknown Artist")
                .setArtworkUri(coverUrl?.let { android.net.Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(playUri)
                .setMediaMetadata(mediaMetadata)
                .build()

            val targetStandby = standbyPlayer
            targetStandby.volume = 0f
            targetStandby.setMediaItem(mediaItem, true)
            targetStandby.prepare()
            targetStandby.playWhenReady = false
            preloadedTrackId = trackId
            android.util.Log.d("PlayerRepositoryImpl", "Preloaded warm standby player ready for crossfade with trackId = $trackId")
        }
    }

    override fun playTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?) {
        scope.launch {
            val resolvedUrl = preloadManager.getResolvedUrl(trackId) ?: url
            val localFile = downloadManager.getLocalTrackFile(trackId)
            val playUri = if (localFile != null) {
                android.net.Uri.fromFile(localFile).toString()
            } else {
                resolvedUrl
            }

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(title ?: "Unknown Track")
                .setDisplayTitle(title ?: "Unknown Track")
                .setArtist(artist ?: "Unknown Artist")
                .setArtworkUri(coverUrl?.let { android.net.Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(playUri)
                .setMediaMetadata(mediaMetadata)
                .build()

            crossfadeJob?.cancel()
            hasEmittedPrepareForCurrent = false
            hasEmittedTrackEndedForCurrent = false
            _currentTrackId.value = trackId
            _progress.value = 0L
            pendingSeekPosition = -1L
            lastSeekTimestamp = 0L

            val currentActive = activePlayer
            val incomingStandby = standbyPlayer
            val wasPlaying = currentActive.isPlaying
            val isAlreadyPreloaded = (preloadedTrackId == trackId)
            preloadedTrackId = null

            if (wasPlaying) {
                // TRUE 5-SECOND OVERLAPPING CROSSFADE
                isCrossfading = true
                isTransitioning = true
                wakeLockHelper.acquireTransitionLock(30_000L)

                if (!isAlreadyPreloaded) {
                    incomingStandby.volume = 0f
                    incomingStandby.setMediaItem(mediaItem, true)
                    incomingStandby.prepare()
                }

                incomingStandby.volume = 0f
                incomingStandby.play()

                // Swap active index immediately so progress and UI lock onto incoming track
                activePlayerIndex = 1 - activePlayerIndex

                crossfadeJob = scope.launch {
                    // Guard: wait until incomingStandby is in STATE_READY and rendering audio
                    // If preloaded at 15s remaining, this check succeeds in 0-10ms.
                    // If buffering, currentActive continues playing at full volume without silent pause!
                    val waitStart = System.currentTimeMillis()
                    while (isActive && incomingStandby.playbackState != Player.STATE_READY && (System.currentTimeMillis() - waitStart < 3000L)) {
                        delay(20)
                    }

                    if (incomingStandby.playbackState != Player.STATE_READY) {
                        android.util.Log.w("PlayerRepositoryImpl", "Incoming standby player not ready within 3s; aborting crossfade safely to let current active player finish")
                        activePlayerIndex = 1 - activePlayerIndex
                        currentActive.volume = 1f
                        incomingStandby.volume = 0f
                        incomingStandby.stop()
                        isCrossfading = false
                        isTransitioning = false
                        wakeLockHelper.releaseTransitionLock()
                        return@launch
                    }

                    val steps = CROSSFADE_STEPS
                    val stepDelay = CROSSFADE_DURATION_MS / steps // 50ms per step

                    for (step in 1..steps) {
                        if (!isActive) break
                        val fraction = step.toDouble() / steps
                        val angle = fraction * (PI / 2.0)
                        
                        // Equal-Power Crossfade curve (cos^2 + sin^2 = 1.0)
                        val outVol = cos(angle).toFloat().coerceIn(0f, 1f)
                        val inVol = sin(angle).toFloat().coerceIn(0f, 1f)

                        currentActive.volume = outVol
                        incomingStandby.volume = inVol
                        delay(stepDelay)
                    }

                    // Finish crossfade: clean soft pause on previous player without harsh stop
                    currentActive.pause()
                    currentActive.clearMediaItems()
                    currentActive.volume = 1f
                    incomingStandby.volume = 1f
                    isCrossfading = false
                    isTransitioning = false
                    wakeLockHelper.releaseTransitionLock()
                }
            } else {
                // Immediate clean start when previous track wasn't playing
                isTransitioning = true
                currentActive.stop()
                currentActive.volume = 1f
                currentActive.setMediaItem(mediaItem, true)
                currentActive.prepare()
                currentActive.play()
                wakeLockHelper.releaseTransitionLock()
            }

            try {
                val intent = Intent(context, MusicService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun pause() {
        crossfadeJob?.cancel()
        isCrossfading = false
        playerA.pause()
        playerB.pause()
        _isPlaying.value = false
    }

    override fun resume() {
        activePlayer.play()
        _isPlaying.value = true
    }

    override fun stop() {
        crossfadeJob?.cancel()
        isCrossfading = false
        playerA.stop()
        playerB.stop()
        playerA.clearMediaItems()
        playerB.clearMediaItems()
        playerA.volume = 1f
        playerB.volume = 1f
        _isPlaying.value = false
        _currentTrackId.value = null
        _progress.value = 0L
    }

    override fun seekTo(position: Long) {
        crossfadeJob?.cancel()
        isCrossfading = false
        val p = activePlayer
        p.volume = 1f
        lastSeekTimestamp = System.currentTimeMillis()
        pendingSeekPosition = position
        _progress.value = position
        p.seekTo(position)
    }

    override fun triggerSkipNext() {
        scope.launch {
            _skipNextEvent.emit(Unit)
        }
    }

    override fun triggerSkipPrev() {
        scope.launch {
            _skipPrevEvent.emit(Unit)
        }
    }

    companion object {
        const val CROSSFADE_DURATION_MS = 3000L
        const val CROSSFADE_STEPS = 60
        const val PREPARE_STANDBY_THRESHOLD_MS = 15_000L
    }
}

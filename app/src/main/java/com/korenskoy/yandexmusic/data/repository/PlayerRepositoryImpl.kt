package com.korenskoy.yandexmusic.data.repository

import androidx.core.net.toUri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.domain.repository.PlaybackFailure
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dual-ExoPlayer engine. One player is active (audible), the other is a standby used for
 * preloading the next track and as the incoming side of a crossfade.
 *
 * All player access happens on the main thread.
 */
class PlayerRepositoryImpl(
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

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    override val progress: StateFlow<Long> = _progress.asStateFlow()

    // Buffered so that events are never dropped while the collector is busy.
    private val _trackEndedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = EVENT_BUFFER)
    override val trackEndedEvent: Flow<Unit> = _trackEndedEvent.asSharedFlow()

    private val _prepareNextTrackEvent = MutableSharedFlow<Unit>(extraBufferCapacity = EVENT_BUFFER)
    override val prepareNextTrackEvent: Flow<Unit> = _prepareNextTrackEvent.asSharedFlow()

    private val _skipNextEvent = MutableSharedFlow<Unit>(extraBufferCapacity = EVENT_BUFFER)
    override val skipNextEvent: Flow<Unit> = _skipNextEvent.asSharedFlow()

    private val _skipPrevEvent = MutableSharedFlow<Unit>(extraBufferCapacity = EVENT_BUFFER)
    override val skipPrevEvent: Flow<Unit> = _skipPrevEvent.asSharedFlow()

    private val _playbackErrorEvent = MutableSharedFlow<PlaybackFailure>(extraBufferCapacity = EVENT_BUFFER)
    override val playbackErrorEvent: Flow<PlaybackFailure> = _playbackErrorEvent.asSharedFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Unhandled player coroutine error", e)
        }
    )

    // Dual-Player ping-pong: 0 -> playerA, 1 -> playerB
    private var activePlayerIndex = 0

    private val activePlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerA else playerB

    private val standbyPlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerB else playerA

    private val _activePlayerState = MutableStateFlow<ExoPlayer>(playerA)
    override val activePlayerState: StateFlow<ExoPlayer> = _activePlayerState.asStateFlow()

    /** Player fading out during a crossfade; null when no crossfade is running. */
    private var outgoingPlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null
    private var playJob: Job? = null
    private var preloadedTrackId: String? = null

    // Volume requested by the crossfade, before ducking is applied.
    private val logicalVolume = floatArrayOf(1f, 1f)
    private var duckFactor = 1f
    private var crossfadeDurationMs = CROSSFADE_DURATION_MS

    private var lastSeekTimestamp = 0L
    private var pendingSeekPosition = -1L
    private var hasEmittedPrepareForCurrent = false
    private var hasEmittedTrackEndedForCurrent = false

    init {
        setupPlayerListener(playerA, 0)
        setupPlayerListener(playerB, 1)

        scope.launch {
            while (isActive) {
                val currentP = activePlayer
                if (currentP.isPlaying) {
                    updateProgress(currentP)
                    checkTransitionTriggers(currentP)
                    delay(PROGRESS_INTERVAL_MS)
                } else {
                    delay(IDLE_INTERVAL_MS)
                }
            }
        }
    }

    private fun updateProgress(player: ExoPlayer) {
        val position = player.currentPosition
        if (SystemClock.elapsedRealtime() - lastSeekTimestamp < 600L && pendingSeekPosition >= 0L) {
            // Ignore stale positions reported right after a seek until the player catches up.
            if (abs(position - pendingSeekPosition) < 400L) {
                pendingSeekPosition = -1L
                _progress.value = position
            }
        } else {
            pendingSeekPosition = -1L
            _progress.value = position
        }
    }

    private fun checkTransitionTriggers(player: ExoPlayer) {
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 8_000L || position <= 0L || outgoingPlayer != null) return
        val remaining = duration - position

        // 1. Warm standby preparation at 15 seconds remaining
        if (remaining <= PREPARE_STANDBY_THRESHOLD_MS && !hasEmittedPrepareForCurrent) {
            hasEmittedPrepareForCurrent = true
            _prepareNextTrackEvent.tryEmit(Unit)
        }

        // 2. Early trigger so the crossfade overlaps the last seconds of the track
        if (crossfadeDurationMs > 0L && remaining in 1L..crossfadeDurationMs && !hasEmittedTrackEndedForCurrent) {
            hasEmittedTrackEndedForCurrent = true
            wakeLockHelper.acquireTransitionLock(30_000L)
            _trackEndedEvent.tryEmit(Unit)
        }
    }

    private fun setupPlayerListener(player: ExoPlayer, playerIdx: Int) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshPlaybackState()
                if (isPlaying && playerIdx == activePlayerIndex && outgoingPlayer == null) {
                    wakeLockHelper.releaseTransitionLock()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                refreshPlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshPlaybackState()
                if (playbackState == Player.STATE_ENDED && playerIdx == activePlayerIndex && outgoingPlayer == null) {
                    Log.d(TAG, "Player[$playerIdx] reached STATE_ENDED, crossfadeRequested=$hasEmittedTrackEndedForCurrent")
                    // When the early crossfade trigger already fired, the controller is advancing — don't skip twice.
                    if (!hasEmittedTrackEndedForCurrent) {
                        hasEmittedTrackEndedForCurrent = true
                        wakeLockHelper.acquireTransitionLock(30_000L)
                        _trackEndedEvent.tryEmit(Unit)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "ExoPlayer error on player[$playerIdx]: ${error.errorCodeName}", error)
                player.currentMediaItem?.mediaId?.let { preloadManager.evictUrl(it) }
                when {
                    player === outgoingPlayer -> {
                        // The fading-out track broke; just drop it.
                        player.stop()
                        player.clearMediaItems()
                        outgoingPlayer = null
                        setVolume(activePlayer, 1f)
                    }
                    playerIdx == activePlayerIndex -> {
                        val failedId = _currentTrackId.value ?: player.currentMediaItem?.mediaId ?: return
                        wakeLockHelper.releaseTransitionLock()
                        _playbackErrorEvent.tryEmit(
                            PlaybackFailure(failedId, player.currentPosition.coerceAtLeast(0L), error.errorCodeName)
                        )
                    }
                    else -> {
                        // A preloaded standby went stale — never hand it to a transition.
                        if (preloadedTrackId == player.currentMediaItem?.mediaId) preloadedTrackId = null
                        player.stop()
                        player.clearMediaItems()
                    }
                }
                refreshPlaybackState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && playerIdx == activePlayerIndex) {
                    pendingSeekPosition = -1L
                    _progress.value = newPosition.positionMs
                }
            }
        })
    }

    private fun refreshPlaybackState() {
        val p = activePlayer
        val wantsToPlay = p.playWhenReady &&
            p.playerError == null &&
            p.mediaItemCount > 0 &&
            p.playbackState != Player.STATE_ENDED
        _isPlaying.value = wantsToPlay
        _isBuffering.value = wantsToPlay && !p.isPlaying
    }

    override fun getPlayer(): Player = activePlayer

    private suspend fun resolvePlayUri(trackId: String, url: String): String? {
        val localFile = withContext(Dispatchers.IO) { downloadManager.getLocalTrackFile(trackId) }
        if (localFile != null) return android.net.Uri.fromFile(localFile).toString()
        return (preloadManager.getResolvedUrl(trackId) ?: url).takeIf { it.isNotBlank() }
    }

    private fun buildMediaItem(trackId: String, uri: String, title: String?, artist: String?, coverUrl: String?): MediaItem {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title ?: "Unknown Track")
            .setDisplayTitle(title ?: "Unknown Track")
            .setArtist(artist ?: "Unknown Artist")
            .setArtworkUri(coverUrl?.toUri())
            .build()
        return MediaItem.Builder()
            .setMediaId(trackId)
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    override fun prepareNextTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?) {
        if (preloadedTrackId == trackId || outgoingPlayer != null) return
        scope.launch {
            val playUri = resolvePlayUri(trackId, url) ?: return@launch
            // State may have changed while the URL was resolving.
            if (outgoingPlayer != null || _currentTrackId.value == trackId) return@launch

            val standby = standbyPlayer
            standby.stop()
            setVolume(standby, 0f)
            standby.playWhenReady = false
            standby.setMediaItem(buildMediaItem(trackId, playUri, title, artist, coverUrl), 0L)
            standby.prepare()
            preloadedTrackId = trackId
            Log.d(TAG, "Preloaded warm standby player with trackId = $trackId")
        }
    }

    override fun playTrack(
        trackId: String,
        url: String,
        title: String?,
        artist: String?,
        coverUrl: String?,
        crossfade: Boolean,
        startPositionMs: Long
    ) {
        playJob?.cancel()
        playJob = scope.launch {
            val playUri = resolvePlayUri(trackId, url)
            if (playUri == null) {
                Log.w(TAG, "playUri is blank for track $trackId")
                _playbackErrorEvent.tryEmit(PlaybackFailure(trackId, startPositionMs, "EMPTY_URL"))
                return@launch
            }
            startTransition(buildMediaItem(trackId, playUri, title, artist, coverUrl), crossfade, startPositionMs)
        }
    }

    /** Switches the audible track on the main thread. */
    private suspend fun startTransition(item: MediaItem, crossfade: Boolean, startPositionMs: Long) {
        val trackId = item.mediaId
        // A crossfade that is still running (rapid skipping): fade its outgoing side out quickly instead of
        // cutting it, and keep the current track's volume to fade from.
        fadeOutRunningCrossfade()

        hasEmittedPrepareForCurrent = false
        hasEmittedTrackEndedForCurrent = false
        pendingSeekPosition = -1L
        lastSeekTimestamp = 0L
        _currentTrackId.value = trackId
        _progress.value = startPositionMs

        val current = activePlayer
        val incoming = standbyPlayer
        val reusePreloaded = preloadedTrackId == trackId &&
            incoming.currentMediaItem?.mediaId == trackId &&
            incoming.playerError == null &&
            incoming.playbackState != Player.STATE_IDLE
        preloadedTrackId = null

        if (reusePreloaded) {
            if (startPositionMs > 0L) incoming.seekTo(startPositionMs)
        } else {
            incoming.stop()
            incoming.setMediaItem(item, startPositionMs)
            incoming.prepare()
        }

        val isCurrentAudible = current.isPlaying && current.playerError == null
        activePlayerIndex = 1 - activePlayerIndex
        _activePlayerState.value = incoming

        if (crossfade && isCurrentAudible && crossfadeDurationMs > 0L) {
            wakeLockHelper.acquireTransitionLock(30_000L)
            outgoingPlayer = current
            setVolume(incoming, 0f)
            incoming.playWhenReady = true
            crossfadeJob = scope.launch { runCrossfade(current, incoming) }
        } else {
            current.stop()
            current.clearMediaItems()
            setVolume(current, 1f)
            setVolume(incoming, 1f)
            incoming.playWhenReady = true
        }
        refreshPlaybackState()
    }

    /**
     * Keeps [outgoing] audible until [incoming] actually renders audio, then blends them with an
     * equal-power curve (cos² + sin² = 1) so perceived loudness stays constant.
     */
    private suspend fun runCrossfade(outgoing: ExoPlayer, incoming: ExoPlayer) {
        val waitStart = SystemClock.elapsedRealtime()
        while (!incoming.isPlaying) {
            if (incoming.playerError != null) return // reported by the listener
            if (SystemClock.elapsedRealtime() - waitStart > INCOMING_READY_TIMEOUT_MS) {
                val id = incoming.currentMediaItem?.mediaId ?: return
                Log.w(TAG, "Incoming track $id did not start in time")
                _playbackErrorEvent.tryEmit(PlaybackFailure(id, 0L, "READY_TIMEOUT"))
                return
            }
            delay(20)
        }

        val outStartVolume = logicalVolume[indexOf(outgoing)]
        // ~50 ms per volume step whatever the duration (60 steps for the default 3 s)
        val steps = (crossfadeDurationMs / (CROSSFADE_DURATION_MS / CROSSFADE_STEPS)).toInt().coerceAtLeast(1)
        val stepDelay = crossfadeDurationMs / steps
        for (step in 1..steps) {
            val angle = step.toDouble() / steps * (PI / 2.0)
            setVolume(outgoing, (outStartVolume * cos(angle)).toFloat().coerceIn(0f, 1f))
            setVolume(incoming, sin(angle).toFloat().coerceIn(0f, 1f))
            delay(stepDelay)
        }

        outgoingPlayer = null
        outgoing.stop()
        outgoing.clearMediaItems()
        setVolume(outgoing, 1f)
        setVolume(incoming, 1f)
        wakeLockHelper.releaseTransitionLock()
    }

    /**
     * The outgoing player occupies the standby slot the next track needs, so it must be released first.
     * A [QUICK_FADE_MS] fade avoids an audible click; it is stopped even if this request is superseded.
     */
    private suspend fun fadeOutRunningCrossfade() {
        val out = outgoingPlayer ?: return
        crossfadeJob?.cancel()
        crossfadeJob = null
        try {
            val startVolume = logicalVolume[indexOf(out)]
            for (step in 1..QUICK_FADE_STEPS) {
                setVolume(out, startVolume * (1f - step.toFloat() / QUICK_FADE_STEPS))
                delay(QUICK_FADE_MS / QUICK_FADE_STEPS)
            }
        } finally {
            if (outgoingPlayer === out) {
                out.stop()
                out.clearMediaItems()
                setVolume(out, 1f)
                outgoingPlayer = null
            }
        }
    }

    /** Ends a running crossfade immediately: the outgoing track is silenced, at most one track stays audible. */
    private fun finishCrossfadeNow(restoreActiveVolume: Boolean) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        outgoingPlayer?.let { out ->
            out.stop()
            out.clearMediaItems()
            setVolume(out, 1f)
        }
        outgoingPlayer = null
        if (restoreActiveVolume) setVolume(activePlayer, 1f)
    }

    override fun pause() {
        fadeJob?.cancel()
        finishCrossfadeNow(restoreActiveVolume = true)
        activePlayer.pause()
        refreshPlaybackState()
    }

    private var fadeJob: Job? = null

    override fun fadeOutAndPause(durationMs: Long) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            finishCrossfadeNow(restoreActiveVolume = false)
            val player = activePlayer
            val start = logicalVolume[indexOf(player)]
            val steps = (durationMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                setVolume(player, start * (1f - step.toFloat() / steps))
                delay(FADE_STEP_MS)
            }
            player.pause()
            setVolume(player, 1f)
            refreshPlaybackState()
        }
    }

    override fun resume(): Boolean {
        val player = activePlayer
        if (player.mediaItemCount == 0) return false
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            // The stream is dead (e.g. the signed URL expired during a long pause): let the controller re-resolve it.
            val trackId = _currentTrackId.value ?: return false
            _playbackErrorEvent.tryEmit(PlaybackFailure(trackId, player.currentPosition.coerceAtLeast(0L), "RESUME_IDLE"))
            return true
        }
        if (player.playbackState == Player.STATE_ENDED) {
            triggerSkipNext()
            return true
        }
        setVolume(player, 1f)
        player.play()
        refreshPlaybackState()
        return true
    }

    override fun stop() {
        playJob?.cancel()
        finishCrossfadeNow(restoreActiveVolume = true)
        for (p in listOf(playerA, playerB)) {
            p.stop()
            p.clearMediaItems()
            setVolume(p, 1f)
        }
        preloadedTrackId = null
        hasEmittedPrepareForCurrent = false
        hasEmittedTrackEndedForCurrent = false
        wakeLockHelper.releaseTransitionLock()
        _currentTrackId.value = null
        _progress.value = 0L
        refreshPlaybackState()
    }

    override fun release() {
        scope.cancel()
        crossfadeJob?.cancel()
        playJob?.cancel()
        wakeLockHelper.releaseTransitionLock()
        playerA.release()
        playerB.release()
    }

    override fun seekTo(position: Long) {
        finishCrossfadeNow(restoreActiveVolume = true)
        val p = activePlayer
        lastSeekTimestamp = SystemClock.elapsedRealtime()
        pendingSeekPosition = position
        _progress.value = position
        p.seekTo(position)
        // Seeking back out of the crossfade window must allow the triggers to fire again.
        val duration = p.duration
        if (duration > 0L && duration - position > crossfadeDurationMs) hasEmittedTrackEndedForCurrent = false
        if (duration > 0L && duration - position > PREPARE_STANDBY_THRESHOLD_MS) hasEmittedPrepareForCurrent = false
    }

    override fun setDucked(ducked: Boolean) {
        duckFactor = if (ducked) DUCK_VOLUME else 1f
        playerA.volume = logicalVolume[0] * duckFactor
        playerB.volume = logicalVolume[1] * duckFactor
    }

    override fun setCrossfadeDuration(durationMs: Long) {
        crossfadeDurationMs = durationMs.coerceAtLeast(0L)
    }

    private fun indexOf(player: ExoPlayer) = if (player === playerA) 0 else 1

    private fun setVolume(player: ExoPlayer, volume: Float) {
        val idx = indexOf(player)
        logicalVolume[idx] = volume
        player.volume = volume * duckFactor
    }

    override fun triggerSkipNext() {
        _skipNextEvent.tryEmit(Unit)
    }

    override fun triggerSkipPrev() {
        _skipPrevEvent.tryEmit(Unit)
    }

    companion object {
        private const val TAG = "PlayerRepositoryImpl"
        const val CROSSFADE_DURATION_MS = 3000L
        const val CROSSFADE_STEPS = 60
        const val PREPARE_STANDBY_THRESHOLD_MS = 15_000L
        const val INCOMING_READY_TIMEOUT_MS = 8_000L
        const val DUCK_VOLUME = 0.2f
        const val QUICK_FADE_MS = 150L
        private const val FADE_STEP_MS = 50L
        private const val QUICK_FADE_STEPS = 6
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val IDLE_INTERVAL_MS = 500L
        private const val EVENT_BUFFER = 16
    }
}

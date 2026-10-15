package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow

class PlayerRepositoryImpl @Inject constructor(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
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

    private val _skipNextEvent = MutableSharedFlow<Unit>()
    override val skipNextEvent: Flow<Unit> = _skipNextEvent.asSharedFlow()

    private val _skipPrevEvent = MutableSharedFlow<Unit>()
    override val skipPrevEvent: Flow<Unit> = _skipPrevEvent.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var fadeJob: Job? = null

    @Volatile
    private var lastSeekTimestamp = 0L
    @Volatile
    private var pendingSeekPosition = -1L

    @Volatile
    private var isTransitioning = false
    @Volatile
    private var isFadingIn = false

    init {
        setupPlayerListener()

        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying && !isTransitioning) {
                    val now = System.currentTimeMillis()
                    val timeSinceSeek = now - lastSeekTimestamp
                    if (timeSinceSeek < 600L && pendingSeekPosition >= 0L) {
                        // Check if exoPlayer has caught up to the seek target
                        if (abs(exoPlayer.currentPosition - pendingSeekPosition) < 400L) {
                            pendingSeekPosition = -1L
                            _progress.value = exoPlayer.currentPosition
                        }
                        // Otherwise, hold optimistic _progress.value and ignore stale ticks
                    } else {
                        pendingSeekPosition = -1L
                        _progress.value = exoPlayer.currentPosition
                    }
                }
                delay(100)
            }
        }
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    isTransitioning = false
                    wakeLockHelper.releaseTransitionLock()
                }
                // Start Fade-In strictly when audio actually starts playing through decoder
                if (isPlaying && isFadingIn) {
                    startFadeIn()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !isTransitioning) {
                    wakeLockHelper.acquireTransitionLock(30_000L)
                    scope.launch {
                        _trackEndedEvent.emit(Unit)
                    }
                }
                if (playbackState == Player.STATE_READY) {
                    isTransitioning = false
                    if (exoPlayer.playWhenReady && isFadingIn) {
                        startFadeIn()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlayerRepositoryImpl", "ExoPlayer playback error: ${error.errorCodeName}", error)
                wakeLockHelper.releaseTransitionLock()
                isFadingIn = false
                isTransitioning = false
                triggerSkipNext()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    pendingSeekPosition = -1L
                    _progress.value = newPosition.positionMs
                }
            }
        })
    }

    private fun startFadeIn() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val fadeInSteps = 12
            for (i in 1..fadeInSteps) {
                if (!isActive) break
                // Logarithmic / exponential power curve for smooth audio perception
                val fraction = i.toFloat() / fadeInSteps
                val volumeCurve = fraction.toDouble().pow(1.5).toFloat().coerceIn(0f, 1f)
                exoPlayer.volume = volumeCurve
                delay(18) // ~216ms total fade-in
            }
            exoPlayer.volume = 1f
            isFadingIn = false
        }
    }

    private suspend fun performFadeOut(steps: Int = 5, stepDelay: Long = 12) {
        val currentVol = exoPlayer.volume
        if (currentVol <= 0f) return
        for (i in steps downTo 1) {
            val fraction = i.toFloat() / steps
            exoPlayer.volume = (currentVol * (fraction * fraction)).coerceIn(0f, 1f)
            delay(stepDelay)
        }
        exoPlayer.volume = 0f
    }

    override fun getPlayer(): Player {
        return exoPlayer
    }

    override fun playTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?) {
        scope.launch {
            // Check if we can use the preloaded URL
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

            fadeJob?.cancel()
            isTransitioning = true
            _currentTrackId.value = trackId
            _progress.value = 0L
            pendingSeekPosition = -1L
            lastSeekTimestamp = 0L

            val wasPlaying = exoPlayer.isPlaying
            if (wasPlaying) {
                // Quick ultra-smooth 40ms fade out of previous track/radio before starting next
                performFadeOut(steps = 4, stepDelay = 10)
            }

            wakeLockHelper.acquireTransitionLock(30_000L)
            isFadingIn = true
            exoPlayer.volume = 0f
            
            // Set media item with resetPosition=true (avoids full pipeline tear down/stop)
            exoPlayer.setMediaItem(mediaItem, true)
            exoPlayer.prepare()
            exoPlayer.play()

            // If already playing immediately (cached/local source)
            if (exoPlayer.isPlaying) {
                wakeLockHelper.releaseTransitionLock()
                startFadeIn()
            }

            try {
                val intent = Intent(context, MusicService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Ignore background start restrictions if service is already running
            }
        }
    }

    override fun pause() {
        wakeLockHelper.releaseTransitionLock()
        fadeJob?.cancel()
        isFadingIn = false
        fadeJob = scope.launch {
            performFadeOut(steps = 5, stepDelay = 12)
            exoPlayer.pause()
            exoPlayer.volume = 1f
        }
    }

    override fun resume() {
        wakeLockHelper.acquireTransitionLock(30_000L)
        fadeJob?.cancel()
        if (exoPlayer.playbackState == Player.STATE_ENDED || exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.seekTo(0)
            exoPlayer.prepare()
        }
        isFadingIn = true
        exoPlayer.volume = 0f
        exoPlayer.play()
        if (exoPlayer.isPlaying) {
            wakeLockHelper.releaseTransitionLock()
            startFadeIn()
        }
    }

    override fun seekTo(position: Long) {
        pendingSeekPosition = position
        lastSeekTimestamp = System.currentTimeMillis()
        _progress.value = position
        exoPlayer.seekTo(position)
    }

    override fun triggerSkipNext() {
        scope.launch { _skipNextEvent.emit(Unit) }
    }

    override fun triggerSkipPrev() {
        scope.launch { _skipPrevEvent.emit(Unit) }
    }

    override fun stop() {
        wakeLockHelper.releaseTransitionLock()
        fadeJob?.cancel()
        isFadingIn = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentTrackId.value = null
        _isPlaying.value = false
        _progress.value = 0L
        pendingSeekPosition = -1L
        lastSeekTimestamp = 0L
    }
}

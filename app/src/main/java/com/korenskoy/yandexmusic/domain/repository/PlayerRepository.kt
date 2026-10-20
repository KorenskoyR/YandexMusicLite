package com.korenskoy.yandexmusic.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Playback failure of the active track; [positionMs] lets the controller retry from the same place. */
data class PlaybackFailure(val trackId: String, val positionMs: Long, val reason: String)

interface PlayerRepository {
    val currentTrackId: StateFlow<String?>
    /** True while the user intends playback (playWhenReady), including buffering — drives the Play/Pause icon. */
    val isPlaying: StateFlow<Boolean>
    /** True while playback is requested but audio is not yet rendering. */
    val isBuffering: StateFlow<Boolean>
    val progress: StateFlow<Long>
    val trackEndedEvent: Flow<Unit>
    val prepareNextTrackEvent: Flow<Unit>
    val skipNextEvent: Flow<Unit>
    val skipPrevEvent: Flow<Unit>
    val playbackErrorEvent: Flow<PlaybackFailure>
    val activePlayerState: StateFlow<androidx.media3.exoplayer.ExoPlayer>

    /**
     * Starts [trackId]. With [crossfade] the currently audible track keeps playing until the new one
     * is ready, then both are blended over [com.korenskoy.yandexmusic.data.repository.PlayerRepositoryImpl.CROSSFADE_DURATION_MS].
     */
    fun playTrack(
        trackId: String,
        url: String,
        title: String?,
        artist: String?,
        coverUrl: String?,
        crossfade: Boolean = true,
        startPositionMs: Long = 0L
    )
    fun prepareNextTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?)
    fun pause()
    /** Resumes the loaded item. Returns false when nothing is loaded and the caller must start the track itself. */
    fun resume(): Boolean
    fun seekTo(position: Long)
    fun setDucked(ducked: Boolean)
    fun triggerSkipNext()
    fun triggerSkipPrev()
    fun stop()
    fun release()
    fun getPlayer(): androidx.media3.common.Player
}

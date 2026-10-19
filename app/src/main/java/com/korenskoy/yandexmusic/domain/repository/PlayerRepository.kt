package com.korenskoy.yandexmusic.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val currentTrackId: StateFlow<String?>
    val isPlaying: StateFlow<Boolean>
    val progress: StateFlow<Long>
    val trackEndedEvent: Flow<Unit>
    val prepareNextTrackEvent: Flow<Unit>
    val skipNextEvent: Flow<Unit>
    val skipPrevEvent: Flow<Unit>
    val activePlayerState: StateFlow<androidx.media3.exoplayer.ExoPlayer>

    fun playTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?, isAutoCrossfade: Boolean = false)
    fun prepareNextTrack(trackId: String, url: String, title: String?, artist: String?, coverUrl: String?)
    fun pause()
    fun resume()
    fun seekTo(position: Long)
    fun triggerSkipNext()
    fun triggerSkipPrev()
    fun stop()
    fun release()
    fun getPlayer(): androidx.media3.common.Player
}

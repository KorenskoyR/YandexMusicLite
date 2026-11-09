package com.korenskoy.yandexmusic.data.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who started the music. The user tapping a track, a playlist or an album may open the full player;
 * the app moving on by itself, a start on launch or the car never do.
 */
@Singleton
class PlayRequests @Inject constructor() {
    private val _userStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val userStarted: SharedFlow<Unit> = _userStarted.asSharedFlow()

    fun userStarted() {
        _userStarted.tryEmit(Unit)
    }

    private val startHandled = AtomicBoolean(false)

    /** True only the first time in this process: the music on launch plays once, not on every return to the app. */
    fun claimLaunchStart(): Boolean = startHandled.compareAndSet(false, true)
}

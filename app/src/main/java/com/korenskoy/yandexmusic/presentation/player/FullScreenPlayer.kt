package com.korenskoy.yandexmusic.presentation.player

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.SleepTimerState
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLiveLine
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.rememberPredictiveBack
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.toCoverUrl
import kotlinx.coroutines.launch

/** The queue around the current track: what plays now and what follows it. */
data class QueueSplit(val current: TrackDetailsDto?, val next: List<TrackDetailsDto>)

/** Pure; unit-tested. A current track missing from the queue leaves the whole queue as "next". */
fun queueSplitOf(queue: List<TrackDetailsDto>, currentId: String): QueueSplit {
    val i = queue.indexOfFirst { it.idString == currentId }
    return if (i == -1) QueueSplit(null, queue) else QueueSplit(queue[i], queue.drop(i + 1))
}

/** The source name under "СЕЙЧАС ИГРАЕТ"; null where it would be vague (search, catalog pages). */
fun playerSourceLabelRes(source: PlaylistSource, isRadio: Boolean): Int? = when {
    isRadio || source == PlaylistSource.RADIO -> R.string.car_source_radio
    source == PlaylistSource.VIBE -> R.string.car_source_wave
    source == PlaylistSource.TRENDS -> R.string.car_source_hits
    source == PlaylistSource.LIBRARY -> R.string.library_title
    else -> null
}

/** When a pull-down closes the player. Pure; unit-tested. */
object PlayerGesture {
    /** Share of the player's height it must be pulled down by. */
    const val CLOSE_FRACTION = 0.22f
    /** A downward fling this fast (px/s) closes it from anywhere; an upward one keeps it open. */
    const val CLOSE_VELOCITY = 1800f

    fun shouldClose(offsetPx: Float, heightPx: Float, velocity: Float): Boolean =
        velocity > CLOSE_VELOCITY || (heightPx > 0f && offsetPx / heightPx > CLOSE_FRACTION && velocity > -CLOSE_VELOCITY)

    /** +1 when the new track is further on in the queue (or its place is unknown), −1 when it is earlier. */
    fun direction(previousIndex: Int, newIndex: Int): Int = if (previousIndex >= 0 && newIndex in 0 until previousIndex) -1 else 1
}

/**
 * The full-screen player in the Hi-Fi language: glass header, a large cover with a gloss, the shared
 * timeline with elapsed / remaining time, glass like and dislike around three metal knobs, and a glass
 * "Далее" plate that opens the queue.
 *
 * It follows the finger: pulled down, it slides away with the screen behind showing and closes past a
 * fifth of its height or on a fling; the predictive Back gesture shrinks it towards where Back leads.
 * A new track slides in from the side it came from. [progress] is read only where it is drawn.
 */
// coverModifier carries the shared cover transition; it belongs to the cover, not to the whole screen
@Suppress("ModifierParameter")
@Composable
fun FullScreenPlayer(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    isLiked: Boolean,
    progress: () -> Long,
    queue: List<TrackDetailsDto>,
    onPlayPauseClick: () -> Unit,
    onToggleLike: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onCollapse: () -> Unit,
    onCloseAndClear: () -> Unit,
    onDislikeClick: () -> Unit,
    onMoveInQueue: (trackId: String, targetId: String) -> Unit = { _, _ -> },
    onLoadMoreQueue: () -> Unit = {},
    isFetchingMoreQueue: Boolean = false,
    sourceLabel: String = "",
    sleepTimer: SleepTimerState? = null,
    onSleepTimer: (Int) -> Unit = {},
    onSleepAtEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onTrackWave: (() -> Unit)? = null,
    onQualityClick: () -> Unit = {},
    coverModifier: Modifier = Modifier,
    /** Opens with the queue sheet up (screenshots). */
    initialQueueOpen: Boolean = false
) {
    var showQueue by remember { mutableStateOf(initialQueueOpen) }
    val back = rememberPredictiveBack(enabled = !showQueue, onBack = onCollapse)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val split = remember(queue, track.idString) { queueSplitOf(queue, track.idString) }
    val nextTrack = if (track.isRadio) null else split.next.firstOrNull()
    val durationMs = (track.durationMs ?: 0L).coerceAtLeast(0L)
    val direction = rememberTrackDirection(track.idString, queue.indexOfFirst { it.idString == track.idString })

    // Pull-down to close, following the finger; the offset is read only in the layer
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(0f) }
    val cornerPx = with(LocalDensity.current) { 28.dp.toPx() }

    val header = @Composable {
        PlayerHeader(
            sourceLabel = sourceLabel,
            isRadio = track.isRadio,
            sleepTimer = sleepTimer,
            onCollapse = onCollapse,
            onQueue = { showQueue = true },
            onSleepTimer = onSleepTimer,
            onSleepAtEndOfTrack = onSleepAtEndOfTrack,
            onCancelSleepTimer = onCancelSleepTimer
        )
        SleepTimerCaption(state = sleepTimer)
    }
    val cover = @Composable { modifier: Modifier ->
        PlayerCover(track, direction, isPlaying, coverModifier, onPrevClick, onNextClick, modifier)
    }
    val details = @Composable { compact: Boolean ->
        TitleRow(track, direction, compact, onAddToPlaylist, onTrackWave)
        Spacer(Modifier.height(10.dp))
        QualityChip(track.isRadio, onQualityClick)
        Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
        if (track.isRadio) {
            HiFiLiveLine(height = 44f)
            Spacer(Modifier.height(12.dp))
        } else {
            PlayerTimeline(progress, durationMs, isPlaying, onSeekTo)
        }
        Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
        PlayerControls(
            isPlaying = isPlaying,
            isLiked = isLiked,
            isRadio = track.isRadio,
            compact = compact,
            onDislike = onDislikeClick,
            onPrev = onPrevClick,
            onPlayPause = onPlayPauseClick,
            onNext = onNextClick,
            onLike = onToggleLike
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .graphicsLayer {
                val pulled = pull.value
                val p = back.value
                translationY = pulled
                val s = 1f - 0.08f * p - 0.05f * (pulled / size.height.coerceAtLeast(1f))
                scaleX = s
                scaleY = s
                if (pulled > 0f || p > 0f) {
                    shape = RoundedCornerShape(cornerPx)
                    clip = true
                }
            }
            .background(HiFi.Ground)
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !showQueue,
                state = rememberDraggableState { delta ->
                    scope.launch { pull.snapTo((pull.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (PlayerGesture.shouldClose(pull.value, heightPx, velocity)) onCollapse()
                    else pull.animateTo(0f, HiFiMotion.spring())
                }
            )
    ) {
        // Living background: the app tint and (Android 12+) the cover itself, blurred; then a vignette
        // around the cover and a fade to the ground under the controls
        HiFiBackdrop(coverUrl = if (track.isRadio) null else track.coverUri.toCoverUrl(CoverSize.Small), bottomFade = false)
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val vignette = Brush.radialGradient(
                        0.3f to Color.Transparent,
                        1f to HiFi.Ground.copy(alpha = 0.80f),
                        center = Offset(size.width / 2f, size.height * 0.3f),
                        radius = maxOf(size.width, size.height) * 0.62f
                    )
                    val fade = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to HiFi.Ground,
                        startY = size.height * 0.45f,
                        endY = size.height * 0.84f
                    )
                    onDrawBehind {
                        drawRect(vignette)
                        drawRect(fade)
                    }
                }
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                cover(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                Column(Modifier.weight(1f).fillMaxHeight().widthIn(max = 560.dp)) {
                    header()
                    Spacer(Modifier.weight(1f))
                    details(true)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
            ) {
                header()
                cover(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp, start = 22.dp, end = 22.dp))
                Spacer(Modifier.height(22.dp))
                details(false)
                if (!track.isRadio) {
                    Spacer(Modifier.height(20.dp))
                    // The plate's place is kept while the queue loads, so the controls do not jump
                    if (nextTrack != null) NextPlate(nextTrack, onClick = { showQueue = true })
                    else Spacer(Modifier.height(64.dp))
                }
            }
        }

        QueueSheet(
            visible = showQueue,
            split = split,
            isPlaying = isPlaying,
            sourceLabel = sourceLabel,
            isFetchingMore = isFetchingMoreQueue,
            fullHeight = isLandscape,
            onDismiss = { showQueue = false },
            onTrackSelect = onTrackSelect,
            onRemove = onRemoveFromQueue,
            onMove = onMoveInQueue,
            onLoadMore = onLoadMoreQueue
        )
    }
}

/** Which way the track changed (see [PlayerGesture.direction]); remembered between compositions, not state. */
@Composable
private fun rememberTrackDirection(trackId: String, queueIndex: Int): Int {
    val last = remember { IntArray(1) { queueIndex } }
    return remember(trackId) {
        val d = PlayerGesture.direction(last[0], queueIndex)
        last[0] = queueIndex
        d
    }
}

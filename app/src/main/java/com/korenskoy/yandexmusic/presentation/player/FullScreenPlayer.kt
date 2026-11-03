package com.korenskoy.yandexmusic.presentation.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.SleepTimerState
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLiveLine
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTimeline
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph
import com.korenskoy.yandexmusic.presentation.hifi.TimelineMath
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.presentation.player.components.AudioVisualizer
import com.korenskoy.yandexmusic.presentation.player.components.RadioCoverCard
import com.korenskoy.yandexmusic.utils.LocalStreamQuality
import com.korenskoy.yandexmusic.utils.toCoverUrl
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

private val CoverShape = RoundedCornerShape(24.dp)
private val PlateFill = Color(0x6B080A14)
private val SheetFill = Color(0xFF10121A)
private val KnobGlyph = Color(0xFFF4F6FA)

/**
 * The full-screen player in the Hi-Fi language: glass header, a large cover with a gloss, the shared
 * timeline with elapsed / remaining time, glass like and dislike around three metal knobs, and a glass
 * "Далее" plate that opens the queue. [progress] is read only where it is drawn (the timeline) or once a
 * second (the time labels), so the screen does not recompose with every progress tick.
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
    onLoadMoreQueue: () -> Unit = {},
    isFetchingMoreQueue: Boolean = false,
    sourceLabel: String = "",
    sleepTimer: SleepTimerState? = null,
    onSleepTimer: (Int) -> Unit = {},
    onSleepAtEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    coverModifier: Modifier = Modifier,
    /** Opens with the queue sheet up (screenshots). */
    initialQueueOpen: Boolean = false
) {
    var showQueue by remember { mutableStateOf(initialQueueOpen) }
    BackHandler { if (showQueue) showQueue = false else onCollapse() }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val split = remember(queue, track.idString) { queueSplitOf(queue, track.idString) }
    val nextTrack = if (track.isRadio) null else split.next.firstOrNull()
    val durationMs = (track.durationMs ?: 0L).coerceAtLeast(0L)

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
        PlayerCover(track, isPlaying, coverModifier, onPrevClick, onNextClick, modifier)
    }
    val details = @Composable { compact: Boolean ->
        TitleRow(track, compact, onAddToPlaylist)
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
            .background(HiFi.Ground)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 40f && !showQueue) onCollapse()
                }
            )
    ) {
        // Living background: the app tint and (Android 12+) the cover itself, blurred; then a vignette
        // around the cover and a fade to the ground under the controls
        HiFiBackdrop(coverUrl = if (track.isRadio) null else track.coverUri.toCoverUrl("400x400"), bottomFade = false)
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
            onLoadMore = onLoadMoreQueue
        )
    }
}

// --- Header -----------------------------------------------------------------------------------------

@Composable
private fun PlayerHeader(
    sourceLabel: String,
    isRadio: Boolean,
    sleepTimer: SleepTimerState?,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onSleepAtEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassCircleButton(onClick = onCollapse, contentDescription = stringResource(R.string.cd_svernut_pleer), size = 44.dp) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        // Balances the two buttons on the right, so the caption stays in the middle
        Spacer(Modifier.width(44.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.full_player_seychas_igraet),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.14.em,
                maxLines = 1
            )
            if (sourceLabel.isNotEmpty()) {
                Text(
                    text = sourceLabel,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SleepTimerButton(sleepTimer, onSleepTimer, onSleepAtEndOfTrack, onCancelSleepTimer)
        if (isRadio) {
            Spacer(Modifier.width(44.dp))
        } else {
            GlassCircleButton(onClick = onQueue, contentDescription = stringResource(R.string.cd_ochered), size = 44.dp) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
    }
}

/** Moon button with the sleep timer choices (15/30/45/60 min, end of track, off); anodized while set. */
@Composable
private fun SleepTimerButton(
    state: SleepTimerState?,
    onMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .then(if (state != null) Modifier.hifiAnodized(CircleShape) else Modifier.hifiGlass(CircleShape))
                .clickable(role = Role.Button) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = stringResource(R.string.cd_sleep_timer),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = HiFiDialogDefaults.Container,
            border = androidx.compose.foundation.BorderStroke(1.dp, HiFiDialogDefaults.Rim)
        ) {
            val colors = MenuDefaults.itemColors(textColor = Color.White)
            for (minutes in listOf(15, 30, 45, 60)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                    onClick = { expanded = false; onMinutes(minutes) },
                    colors = colors
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_end_of_track)) },
                onClick = { expanded = false; onEndOfTrack() },
                colors = colors
            )
            if (state != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_off), color = HiFi.Danger) },
                    onClick = { expanded = false; onCancel() },
                    colors = colors
                )
            }
        }
    }
}

/** "Сон через 12 мин" / "Сон в конце трека"; refreshes every 15 s while a timer runs. */
@Composable
private fun SleepTimerCaption(state: SleepTimerState?) {
    if (state == null) return
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(15_000L)
        }
    }
    val text = if (state.atEndOfTrack || state.endsAtElapsedMs == null) {
        stringResource(R.string.sleep_caption_end_of_track)
    } else {
        val minutes = ((state.endsAtElapsedMs - now + 59_999L) / 60_000L).coerceAtLeast(1L).toInt()
        stringResource(R.string.sleep_caption_minutes, minutes)
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.80f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}

// --- Cover ------------------------------------------------------------------------------------------

/** The cover, as large as fits; swipe it left / right for the next / previous track. */
@Suppress("ModifierParameter")
@Composable
private fun PlayerCover(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    coverModifier: Modifier,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lite = LocalHiFiLite.current
    // Subtle "breathing" while music plays; read in the layer, never recomposes
    val breath: State<Float>? = if (isPlaying && !lite && !track.isRadio) {
        rememberInfiniteTransition(label = "cover_breath").animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
            label = "cover_scale"
        )
    } else null
    var swipe by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val shownSwipe by animateFloatAsState(swipe, spring(stiffness = Spring.StiffnessMedium), label = "swipe_offset")
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(min(maxWidth, maxHeight), 440.dp)
        Box(
            Modifier
                .size(side)
                .graphicsLayer { translationX = shownSwipe }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipe > 150f) prev() else if (swipe < -150f) next()
                            swipe = 0f
                        },
                        onDragCancel = { swipe = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        swipe = (swipe + dragAmount).coerceIn(-400f, 400f)
                    }
                }
        ) {
            if (track.isRadio) {
                RadioCoverCard(
                    coverUrl = track.coverUri.toCoverUrl("1000x1000") ?: "",
                    stationName = track.title.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(coverModifier)
                        .hifiSoftShadow(CoverShape, 30.dp, Color.Black.copy(alpha = 0.55f))
                        .clip(CoverShape)
                        .background(Color(0xFF1A1C22))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(track.coverUri.toCoverUrl("1000x1000")).crossfade(true).build(),
                        contentDescription = stringResource(R.string.cd_oblozhka),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = breath?.value ?: 1f
                                scaleX = s
                                scaleY = s
                            }
                    )
                    // Gloss from the top-left and a hairline rim
                    Spacer(
                        Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val gloss = Brush.linearGradient(
                                    0f to Color.White.copy(alpha = 0.20f),
                                    0.4f to Color.Transparent,
                                    start = Offset(size.width * 0.33f, 0f),
                                    end = Offset(size.width * 0.67f, size.height)
                                )
                                val r = CornerRadius(24.dp.toPx())
                                onDrawBehind {
                                    drawRect(gloss)
                                    drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r, style = Stroke(1.dp.toPx()))
                                }
                            }
                    )
                }
            }
        }
    }
}

// --- Title, quality, timeline ------------------------------------------------------------------------

@Composable
private fun TitleRow(track: TrackDetailsDto, compact: Boolean, onAddToPlaylist: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.bez_nazvaniya),
                color = Color.White,
                fontSize = if (compact) 22.sp else 26.sp,
                lineHeight = if (compact) 28.sp else 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artists.joinToString(", ") { it.name ?: "" }.ifEmpty { stringResource(if (track.isRadio) R.string.car_source_radio else R.string.player_unknown_artist) },
                color = Color.White.copy(alpha = 0.70f),
                fontSize = if (compact) 15.sp else 17.sp,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!track.isRadio) {
            GlassCircleButton(
                onClick = onAddToPlaylist,
                contentDescription = stringResource(R.string.track_context_menu_dobavit_v_pleylist),
                size = 44.dp
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** The real stream quality in a recessed chip; a tap explains what plays and what the server offered. */
@Composable
private fun QualityChip(isRadio: Boolean, onClick: () -> Unit) {
    val text = if (isRadio) stringResource(R.string.full_player_fm_stereo_128_kbps) else LocalStreamQuality.current
    // The chip's height is kept while the quality is not known yet
    if (text == null) {
        Spacer(Modifier.height(28.dp))
        return
    }
    Box(
        modifier = Modifier
            .height(28.dp)
            .hifiRecessed(14.dp)
            .clip(CircleShape)
            .then(if (isRadio) Modifier else Modifier.clickable(onClickLabel = stringResource(R.string.quality_details_title), onClick = onClick))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.12.em,
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerTimeline(progress: () -> Long, durationMs: Long, isPlaying: Boolean, onSeek: (Long) -> Unit) {
    var scrub by remember(durationMs) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth()) {
        HiFiTimeline(progress, durationMs, isPlaying, onSeek, height = 44f, onScrub = { scrub = it })
        TimeReadout(progress, durationMs, scrub)
    }
}

/** "1:22 … −3:24": recomposes once a second (or with the finger), not with every progress tick. */
@Composable
private fun TimeReadout(progress: () -> Long, durationMs: Long, scrub: Float?) {
    val currentProgress by rememberUpdatedState(progress)
    val second by remember(durationMs, scrub) {
        derivedStateOf { (scrub?.let { TimelineMath.seekPosition(it, durationMs) } ?: currentProgress()) / 1000L }
    }
    val (elapsed, remaining) = TimelineMath.readout(second * 1000L, durationMs)
    val color by animateColorAsState(
        if (scrub != null) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.62f),
        HiFiMotion.quick(),
        label = "time_color"
    )
    Row(Modifier.fillMaxWidth().offset(y = (-4).dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(elapsed, color = color, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        Text(remaining, color = color, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}

// --- Controls ---------------------------------------------------------------------------------------

/** Glass dislike · metal previous · large metal play · metal next · glass like. Shrinks to fit narrow phones. */
@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isLiked: Boolean,
    isRadio: Boolean,
    compact: Boolean,
    onDislike: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit
) {
    // Wide rows (landscape, tablets) keep the cluster together instead of spreading it to the edges
    BoxWithConstraints(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 420.dp)) {
        // 52 + 68 + 96 + 68 + 52 = 336 dp of buttons; narrower rows scale them down
        val fit = (maxWidth / 344.dp).coerceIn(0.7f, 1f)
        val k = if (compact) fit * 0.8f else fit
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DislikeButton(onDislikeClick = onDislike, size = 52.dp * k)
            HiFiKnob(
                onClick = onPrev,
                contentDescription = stringResource(if (isRadio) R.string.cd_prev_station else R.string.cd_predyduschiy_trek),
                size = 68.dp * k
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = KnobGlyph, modifier = Modifier.size(30.dp * k))
            }
            HiFiKnob(onClick = onPlayPause, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = 96.dp * k) {
                PlayPauseGlyph(isPlaying = isPlaying, size = 40.dp * k)
            }
            HiFiKnob(
                onClick = onNext,
                contentDescription = stringResource(if (isRadio) R.string.cd_next_station else R.string.cd_sleduyuschiy_trek),
                size = 68.dp * k
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = KnobGlyph, modifier = Modifier.size(30.dp * k))
            }
            LikeHeartButton(isLiked = isLiked, onToggleLike = onLike, size = 52.dp * k)
        }
    }
}

/** A round glass button that sinks a little under the finger (no glow, no shadow band). */
@Composable
private fun GlassPressButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, HiFiMotion.spring(), label = "glass_press")
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun LikeHeartButton(isLiked: Boolean, onToggleLike: () -> Unit, size: Dp = 52.dp) {
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    GlassPressButton(
        onClick = {
            if (!isLiked) scope.launch {
                pop.snapTo(1.3f)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            onToggleLike()
        },
        contentDescription = stringResource(R.string.cd_nravitsya),
        size = size
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.86f),
            modifier = Modifier
                .size(size * 0.43f)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        )
    }
}

@Composable
fun DislikeButton(onDislikeClick: () -> Unit, size: Dp = 52.dp) {
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    GlassPressButton(
        onClick = {
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(450))
            }
            onDislikeClick()
        },
        contentDescription = stringResource(R.string.cd_ne_nravitsya),
        size = size
    ) {
        val base = Color.White.copy(alpha = 0.86f)
        Icon(
            imageVector = Icons.Outlined.HeartBroken,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.lerp(base, HiFi.Danger, flash.value),
            modifier = Modifier
                .size(size * 0.43f)
                .graphicsLayer {
                    val s = 1f + 0.25f * flash.value
                    scaleX = s
                    scaleY = s
                }
        )
    }
}

// --- "Далее" plate -----------------------------------------------------------------------------------

/** What plays next, on a glass plate; information only — a tap opens the queue. */
@Composable
private fun NextPlate(next: TrackDetailsDto, onClick: () -> Unit) {
    val context = LocalContext.current
    val title = next.title ?: stringResource(R.string.bez_nazvaniya)
    val artist = next.artists.joinToString(", ") { it.name ?: "" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PlateFill)
            .drawWithCache {
                val r = CornerRadius(20.dp.toPx())
                val highlight = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.08f to Color.White.copy(alpha = 0.12f), 1f to Color.White.copy(alpha = 0.12f))
                onDrawBehind { drawRoundRect(highlight, cornerRadius = r, style = Stroke(1.dp.toPx())) }
            }
            .clickable(onClickLabel = stringResource(R.string.cd_ochered), role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(next.coverUri.toCoverUrl("100x100")).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.full_player_dalee),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.18.em
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(title) }
                    if (artist.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.70f))) { append(" · $artist") }
                    }
                },
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- Queue sheet ------------------------------------------------------------------------------------

/** The queue as a dark sheet from the bottom: "Сейчас играет" on anodized metal, then what follows. */
@Composable
private fun QueueSheet(
    visible: Boolean,
    split: QueueSplit,
    isPlaying: Boolean,
    sourceLabel: String,
    isFetchingMore: Boolean,
    fullHeight: Boolean,
    onDismiss: () -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(HiFiMotion.standard()), exit = fadeOut(HiFiMotion.quick())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(HiFiMotion.large()) { it },
        exit = slideOutVertically(HiFiMotion.standard()) { it },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            QueueSheetContent(
                split = split,
                isPlaying = isPlaying,
                sourceLabel = sourceLabel,
                isFetchingMore = isFetchingMore,
                onDismiss = onDismiss,
                onTrackSelect = onTrackSelect,
                onRemove = onRemove,
                onLoadMore = onLoadMore,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (fullHeight) 0.94f else 0.78f)
            )
        }
    }
}

@Composable
private fun QueueSheetContent(
    split: QueueSplit,
    isPlaying: Boolean,
    sourceLabel: String,
    isFetchingMore: Boolean,
    onDismiss: () -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val fetching by rememberUpdatedState(isFetchingMore)
    val loadMore by rememberUpdatedState(onLoadMore)
    // Prefetch as soon as the list is within 12 rows of its end
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 12 && !fetching) loadMore()
        }
    }

    // Pull the sheet down by its header to close it
    val density = LocalDensity.current
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closeAt = with(density) { 120.dp.toPx() }
    val dismiss by rememberUpdatedState(onDismiss)
    val total = split.next.size + if (split.current != null) 1 else 0
    val subtitle = listOfNotNull(
        sourceLabel.takeIf { it.isNotEmpty() },
        pluralStringResource(R.plurals.player_queue_tracks, total, total)
    ).joinToString(" · ")

    Column(
        modifier
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(SheetFill)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.14f), Offset(28.dp.toPx(), 0.5f), Offset(size.width - 28.dp.toPx(), 0.5f), 1f) }
            // Swallow taps so they do not reach the scrim behind the sheet
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                onDragStopped = {
                    if (drag.value > closeAt) dismiss()
                    else drag.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                }
            )
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.player_queue_title),
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em
                    )
                    Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircleButton(onClick = onDismiss, contentDescription = stringResource(R.string.cd_zakryt), size = 44.dp) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            split.current?.let { now ->
                item(key = "now_caption") { QueueCaption(stringResource(R.string.full_player_seychas_igraet), top = 12.dp) }
                item(key = "now_row") { NowPlayingRow(now, isPlaying) }
            }
            if (split.next.isNotEmpty()) {
                item(key = "next_caption") { QueueCaption(stringResource(R.string.full_player_dalee), top = 18.dp) }
            }
            items(split.next, key = { it.idString }) { item ->
                QueueRow(
                    item = item,
                    onClick = {
                        onTrackSelect(item.idString)
                        onDismiss()
                    },
                    onRemove = { onRemove(item.idString) }
                )
            }
            if (isFetchingMore) {
                items(2, key = { "skeleton_$it" }) { QueueSkeletonRow() }
            }
        }
    }
}

@Composable
private fun QueueCaption(text: String, top: Dp) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.10.em,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = top, bottom = 8.dp)
    )
}

@Composable
private fun QueueCover(track: TrackDetailsDto) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(track.coverUri.toCoverUrl("100x100")).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
    )
}

@Composable
private fun QueueTexts(track: TrackDetailsDto, bold: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(
            track.title ?: stringResource(R.string.bez_nazvaniya),
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            track.artists.joinToString(", ") { it.name ?: "" },
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NowPlayingRow(track: TrackDetailsDto, isPlaying: Boolean) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .hifiAnodized(RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QueueCover(track)
        QueueTexts(track, bold = true, modifier = Modifier.weight(1f))
        AudioVisualizer(isPlaying = isPlaying, isCompact = true, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun QueueRow(item: TrackDetailsDto, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .padding(start = 12.dp, end = 4.dp)
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QueueCover(item)
        QueueTexts(item, bold = false, modifier = Modifier.weight(1f))
        val removeDescription = stringResource(R.string.cd_udalit_iz_ocheredi)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeDescription },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(30.dp).hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** A row-shaped placeholder while more of the queue loads; the shimmer is read in the draw phase. */
@Composable
private fun QueueSkeletonRow() {
    val lite = LocalHiFiLite.current
    val pulse: State<Float>? = if (!lite) {
        rememberInfiniteTransition(label = "queue_skeleton").animateFloat(
            initialValue = 0.06f,
            targetValue = 0.14f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "queue_skeleton_alpha"
        )
    } else null
    val loading = stringResource(R.string.player_queue_loading)
    Row(
        Modifier
            .padding(start = 22.dp, end = 16.dp)
            .fillMaxWidth()
            .height(62.dp)
            .semantics { contentDescription = loading },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val block = Modifier.drawBehind {
            drawRoundRect(Color.White.copy(alpha = pulse?.value ?: 0.08f), cornerRadius = CornerRadius(size.height / 2f))
        }
        Box(
            Modifier.size(46.dp).drawBehind {
                drawRoundRect(Color.White.copy(alpha = pulse?.value ?: 0.08f), cornerRadius = CornerRadius(10.dp.toPx()))
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 150.dp, height = 12.dp).then(block))
            Box(Modifier.size(width = 90.dp, height = 10.dp).then(block))
        }
    }
}

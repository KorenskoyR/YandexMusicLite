package com.korenskoy.yandexmusic.presentation.player

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Bedtime
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import com.korenskoy.yandexmusic.utils.browserUserAgent
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.player.components.AudioVisualizer
import com.korenskoy.yandexmusic.presentation.player.components.FluidBackground
import com.korenskoy.yandexmusic.presentation.player.components.RadioCoverCard
import com.korenskoy.yandexmusic.utils.toCoverUrl
import java.util.Locale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    isLiked: Boolean,
    progress: Long,
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
    sleepTimer: com.korenskoy.yandexmusic.data.player.SleepTimerState? = null,
    onSleepTimer: (Int) -> Unit = {},
    onSleepAtEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onQualityClick: () -> Unit = {}
) {
    androidx.activity.compose.BackHandler(onBack = onCollapse)

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val coverUrl = track.coverUri.toCoverUrl("1000x1000") ?: ""

    // Subtle scale animation for the cover (Live Canvas breathing effect)
    val infiniteTransition = rememberInfiniteTransition(label = "cover_breath")
    val coverScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cover_scale"
    )

    var showQueue by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val durationMs = (track.durationMs ?: 1L).coerceAtLeast(1L)
    val rawFraction = if (progress <= 0L || durationMs <= 0L) 0f else (progress.toFloat() / durationMs).coerceIn(0f, 1f)
    val targetFraction = if (isDraggingSlider) sliderPosition else rawFraction

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (isDraggingSlider) {
            androidx.compose.animation.core.snap()
        } else if (rawFraction == 0f) {
            androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 150, easing = androidx.compose.animation.core.LinearEasing)
        },
        label = "fullscreen_slider_progress"
    )

    val activeFraction = if (isDraggingSlider) sliderPosition else animatedFraction

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 40f && !showQueue) {
                        onCollapse()
                    }
                }
            )
    ) {
        // Fluid Gradient Background
        FluidBackground(coverUrl = coverUrl)

        // Dark overlay with subtle vertical gradient for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
                .systemBarsPadding()
                .padding(
                    top = if (isLandscape) 8.dp else 36.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = if (isLandscape) 8.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar with Frosted Glass Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x352A2A38), Color(0x201E1E28))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                                    )
                                ),
                                CircleShape
                            )
                            .clickable { onCollapse() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_svernut_pleer),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // Keeps the title centred against the timer and queue buttons on the right
                    if (!track.isRadio) Spacer(modifier = Modifier.width(48.dp))
                }
                Text(
                    text = stringResource(R.string.full_player_seychas_igraet),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White.copy(alpha = 0.6f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SleepTimerButton(
                        state = sleepTimer,
                        onMinutes = onSleepTimer,
                        onEndOfTrack = onSleepAtEndOfTrack,
                        onCancel = onCancelSleepTimer
                    )
                    if (!track.isRadio) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x352A2A38), Color(0x201E1E28))
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                                        )
                                    ),
                                    CircleShape
                                )
                                .clickable { showQueue = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.cd_ochered),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            SleepTimerCaption(state = sleepTimer)

            Spacer(modifier = Modifier.height(16.dp))

            var swipeOffset by remember { mutableFloatStateOf(0f) }
            val animatedSwipeOffset by animateFloatAsState(
                targetValue = swipeOffset,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "swipe_offset"
            )

            // Album Art (Adaptive 1:1 square centered in available space)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = if (isLandscape) 40.dp else 24.dp)
                    .graphicsLayer {
                        translationX = animatedSwipeOffset
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset > 150f) {
                                    onPrevClick()
                                } else if (swipeOffset < -150f) {
                                    onNextClick()
                                }
                                swipeOffset = 0f
                            },
                            onDragCancel = {
                                swipeOffset = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-400f, 400f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (track.isRadio) {
                    RadioCoverCard(
                        coverUrl = coverUrl,
                        stationName = track.title.orEmpty(),
                        scale = coverScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .aspectRatio(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = Color.Black.copy(alpha = 0.6f),
                                ambientColor = Color.Black.copy(alpha = 0.4f)
                            )
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_oblozhka),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = coverScale
                                    scaleY = coverScale
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track Info, with "add to playlist" on the right (balanced on the left to keep the title centred)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!track.isRadio) Spacer(modifier = Modifier.width(44.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title ?: "Без названия",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artists.joinToString(", ") { it.name ?: "" }.ifEmpty { "Неизвестный исполнитель" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!track.isRadio) {
                    IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.track_context_menu_dobavit_v_pleylist),
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visualizer
            AudioVisualizer(isPlaying = isPlaying)
            Spacer(modifier = Modifier.height(8.dp))

            // Metadata & Up Next Space
            val currentIndex = queue.indexOfFirst { it.idString == track.idString }
            val nextTrack = if (currentIndex != -1 && currentIndex + 1 < queue.size) queue[currentIndex + 1] else null
            
            PlayerMetadataAndUpNext(track = track, nextTrack = nextTrack, onQualityClick = onQualityClick)

            Spacer(modifier = Modifier.weight(0.2f))

            if (track.isRadio) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Premium Slim Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    val interactionSource = remember { MutableInteractionSource() }
    
                    Slider(
                        value = activeFraction,
                        onValueChange = { fraction ->
                            isDraggingSlider = true
                            sliderPosition = fraction
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            onSeekTo((sliderPosition * durationMs).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(if (isDraggingSlider) 16.dp else 12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .shadow(4.dp, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                drawStopIndicator = null
                            )
                        },
                        interactionSource = interactionSource
                    )
    
                    // Time indicators (Current / Remaining)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMs = (activeFraction * durationMs).toLong()
                        Text(
                            text = formatTime(currentMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "-" + formatTime((durationMs - currentMs).coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

            // Controls Row (Liquid Glass aesthetic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dislike Button
                DislikeButton(
                    onDislikeClick = onDislikeClick
                )

                // Previous Track Button
                BouncyIconButton(
                    onClick = onPrevClick,
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(10.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x40323242), Color(0x2520202C))
                            )
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.verticalGradient(
                                    listOf(Color(0x55FFFFFF), Color(0x18FFFFFF))
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_predyduschiy_trek),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Play / Pause Main Button (Liquid Glass Glowing Sphere)
                BouncyIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(if (isLandscape) 64.dp else 76.dp)
                        .shadow(
                            elevation = if (isPlaying) 26.dp else 14.dp,
                            shape = CircleShape,
                            spotColor = Color(0xFFFF3366).copy(alpha = if (isPlaying) 0.85f else 0.4f),
                            ambientColor = Color(0xFFFF8833).copy(alpha = 0.5f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF3366),
                                    Color(0xFFFF7722),
                                    Color(0xFFFF9900)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                2.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.25f))
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza),
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next Track Button
                BouncyIconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(10.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x40323242), Color(0x2520202C))
                            )
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.verticalGradient(
                                    listOf(Color(0x55FFFFFF), Color(0x18FFFFFF))
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.cd_sleduyuschiy_trek),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Like Heart Button with Pop Animation
                LikeHeartButton(
                    isLiked = isLiked,
                    onToggleLike = onToggleLike
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Queue Overlay (Centered Floating Modal Dialog)
        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showQueue = false },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = showQueue,
                    enter = scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = scaleOut(targetScale = 0.92f) + fadeOut(animationSpec = tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 480.dp)
                            .fillMaxHeight(0.75f)
                            .heightIn(max = 580.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Prevent click through to backdrop */ }
                    ) {
                        QueueDialogCard(
                            queue = queue,
                            currentTrackId = track.idString,
                            isPlaying = isPlaying,
                            onDismiss = { showQueue = false },
                            onTrackSelect = onTrackSelect,
                            onRemoveFromQueue = onRemoveFromQueue,
                            onLoadMoreQueue = onLoadMoreQueue,
                            isFetchingMore = isFetchingMoreQueue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueDialogCard(
    queue: List<TrackDetailsDto>,
    currentTrackId: String,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onLoadMoreQueue: () -> Unit,
    isFetchingMore: Boolean = false
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Memoize upcoming tracks to avoid recalculation on every recomposition
    val upcoming by remember(queue, currentTrackId) {
        mutableStateOf(
            queue.indexOfFirst { it.idString == currentTrackId }.let { currentIndex ->
                if (currentIndex != -1) queue.drop(currentIndex) else queue
            }
        )
    }

    // Fast responsive prefetch: trigger as soon as user is within 12 items of the end
    LaunchedEffect(listState, isFetchingMore) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            Pair(lastVisible, total)
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 12 && !isFetchingMore) {
                onLoadMoreQueue()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .shadow(32.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.8f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF22222A),
                        Color(0xFF14141A)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.full_player_ochered_vosproizvedeniya),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.player_queue_count, upcoming.size),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_zakryt),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(upcoming, key = { _, item -> item.idString }) { index, item ->
                val isCurrentlyPlaying = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCurrentlyPlaying) Color.White.copy(alpha = 0.06f)
                            else Color.Transparent
                        )
                        .clickable {
                            onTrackSelect(item.idString)
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val itemCoverUrl = item.coverUri.toCoverUrl("100x100") ?: ""

                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(itemCoverUrl)
                            .crossfade(true)
                            .apply {
                                if (item.isRadio) {
                                    browserUserAgent()
                                }
                            }
                            .build(),
                        contentDescription = stringResource(R.string.cd_oblozhka),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artists.joinToString { it.name ?: "" },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCurrentlyPlaying) {
                        AudioVisualizer(isPlaying = isPlaying, isCompact = true)
                    } else {
                        IconButton(
                            onClick = { onRemoveFromQueue(item.idString) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_udalit_iz_ocheredi),
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (isFetchingMore) {
                item(key = "queue_loading_spinner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun LikeHeartButton(
    isLiked: Boolean,
    onToggleLike: () -> Unit
) {
    var isPulsing by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPulsing) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { isPulsing = false },
        label = "heart_pop"
    )

    BouncyIconButton(
        onClick = {
            if (!isLiked) isPulsing = true
            onToggleLike()
        },
        modifier = Modifier
            .size(48.dp)
            .shadow(
                elevation = if (isLiked) 14.dp else 8.dp,
                shape = CircleShape,
                spotColor = if (isLiked) Color(0xFFFF3366).copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.4f)
            )
            .clip(CircleShape)
            .background(
                if (isLiked) {
                    Brush.verticalGradient(
                        listOf(Color(0x44FF3366), Color(0x22FF1144))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0x352A2A38), Color(0x201E1E28))
                    )
                }
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    if (isLiked) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF3366), Color(0x66FF3366))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                        )
                    }
                ),
                CircleShape
            )
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.cd_nravitsya),
            tint = if (isLiked) Color(0xFFFF3366) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun PlayerMetadataAndUpNext(track: TrackDetailsDto, nextTrack: TrackDetailsDto?, onQualityClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (track.isRadio) {
            // Radio Stream Quality Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.full_player_fm_stereo_128_kbps),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9900),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            // Real stream quality (codec and bitrate the player actually uses)
            val quality = com.korenskoy.yandexmusic.utils.LocalStreamQuality.current
            if (quality != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Tap: what plays and every variant the server offered
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClickLabel = stringResource(R.string.quality_details_title), onClick = onQualityClick)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Up Next Preview Card (Glassmorphism)
        if (!track.isRadio && nextTrack != null) {
            val context = LocalContext.current
            val nextCoverUrl = nextTrack.coverUri.toCoverUrl("100x100") ?: ""
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fallbackBadgeNext = @Composable {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF33333C), Color(0xFF1E1E24))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nextTrack.title?.take(1)?.uppercase() ?: "R",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(nextCoverUrl)
                        .crossfade(true)
                        .apply {
                            if (nextTrack.isRadio) {
                                browserUserAgent()
                            }
                        }
                        .build(),
                    contentDescription = stringResource(R.string.cd_oblozhka_sleduyuschego_treka),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    loading = { if (nextTrack.isRadio) fallbackBadgeNext() },
                    error = { if (nextTrack.isRadio) fallbackBadgeNext() }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.full_player_dalee),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = nextTrack.title ?: "Без названия",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_sleduyuschiy_trek),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun DislikeButton(
    onDislikeClick: () -> Unit
) {
    var isDisliked by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isDisliked) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { isDisliked = false },
        label = "dislike_pop"
    )

    BouncyIconButton(
        onClick = {
            isDisliked = true
            onDislikeClick()
        },
        modifier = Modifier
            .size(48.dp)
            .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x352A2A38), Color(0x201E1E28))
                )
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                    )
                ),
                CircleShape
            )
    ) {
        Icon(
            imageVector = Icons.Outlined.HeartBroken,
            contentDescription = stringResource(R.string.cd_ne_nravitsya),
            tint = if (isDisliked) Color(0xFFFF3B6A) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

/** Moon button with the sleep timer choices (15/30/45/60 min, end of track, off). */
@Composable
private fun SleepTimerButton(
    state: com.korenskoy.yandexmusic.data.player.SleepTimerState?,
    onMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (state != null) Color(0x55FF3366) else Color(0x352A2A38))
                .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = stringResource(R.string.cd_sleep_timer),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (minutes in listOf(15, 30, 45, 60)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                    onClick = { expanded = false; onMinutes(minutes) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_end_of_track)) },
                onClick = { expanded = false; onEndOfTrack() }
            )
            if (state != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_off)) },
                    onClick = { expanded = false; onCancel() }
                )
            }
        }
    }
}

/** "Сон через 12 мин" / "Сон в конце трека"; refreshes every 15 s while a timer runs. */
@Composable
private fun SleepTimerCaption(state: com.korenskoy.yandexmusic.data.player.SleepTimerState?) {
    if (state == null) return
    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
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
        color = Color(0xFFFF7799),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

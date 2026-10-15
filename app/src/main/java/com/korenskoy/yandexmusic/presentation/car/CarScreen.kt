package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.player.components.RadioCoverCard
import kotlinx.coroutines.delay
import com.korenskoy.yandexmusic.utils.toCoverUrl
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun CarScreen(
    currentTrack: TrackDetailsDto?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onDislikeClick: (TrackDetailsDto) -> Unit,
    onPlayMyVibe: () -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayHits: () -> Unit,
    onPlayDownloaded: () -> Unit,
    onPlayRadio: () -> Unit,
    onExit: () -> Unit,
    isLandscape: Boolean = false,
    progress: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {}
) {
    val isLiked = currentTrack?.let { likedTrackIds.contains(it.idString) } == true

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(Unit) {
        if (!isPlaying && currentTrack == null) {
            onPlayMyVibe()
        }
    }

    // Soft ambient glow behind everything
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13)) // Deep Obsidian Background
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) onPrevClick()
                    else if (dragAmount < -50) onNextClick()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { 
                    if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() 
                })
            }
    ) {
        // Soft ambient glow behind everything
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33FF3366), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        if (isLandscape) {
            CarScreenLandscape(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                isLiked = isLiked,
                onPlayPauseClick = { if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() },
                onNextClick = onNextClick,
                onPrevClick = onPrevClick,
                onToggleLike = onToggleLike,
                onDislikeClick = onDislikeClick,
                onPlayMyVibe = onPlayMyVibe,
                onPlayFavorites = onPlayFavorites,
                onPlayHits = onPlayHits,
                onPlayDownloaded = onPlayDownloaded,
                onPlayRadio = onPlayRadio,
                onExit = onExit,
                progress = progress,
                durationMs = durationMs,
                onSeek = onSeek
            )
        } else {
            CarScreenPortrait(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                isLiked = isLiked,
                onPlayPauseClick = { if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() },
                onNextClick = onNextClick,
                onPrevClick = onPrevClick,
                onToggleLike = onToggleLike,
                onDislikeClick = onDislikeClick,
                onPlayMyVibe = onPlayMyVibe,
                onPlayFavorites = onPlayFavorites,
                onPlayHits = onPlayHits,
                onPlayDownloaded = onPlayDownloaded,
                onPlayRadio = onPlayRadio,
                onExit = onExit,
                progress = progress,
                durationMs = durationMs,
                onSeek = onSeek
            )
        }
    }
}

@Composable
fun CarScreenPortrait(
    currentTrack: TrackDetailsDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onDislikeClick: (TrackDetailsDto) -> Unit,
    onPlayMyVibe: () -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayHits: () -> Unit,
    onPlayDownloaded: () -> Unit,
    onPlayRadio: () -> Unit,
    onExit: () -> Unit,
    progress: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Bar / Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "В АВТОМОБИЛЕ",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            CarTimeWidget()
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Album Art
        val coverUrl = currentTrack?.coverUri?.toCoverUrl("1000x1000") ?: "https://via.placeholder.com/600"
        val context = LocalContext.current

        if (currentTrack?.isRadio == true) {
            RadioCoverCard(
                coverUrl = coverUrl,
                showLiveBadge = true,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .widthIn(max = 160.dp)
            )
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .sizeIn(maxHeight = 160.dp, maxWidth = 160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFFFF3366).copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title and Artist
        Text(
            text = currentTrack?.title ?: "Неизвестный трек",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = currentTrack?.artists?.joinToString(", ") { it.name ?: "" } ?: "-",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(0.3f))

        // Liquid Glass Controls
        CarControls(
            isPlaying = isPlaying,
            isLiked = isLiked,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = onNextClick,
            onPrevClick = onPrevClick,
            onToggleLike = { currentTrack?.let { onToggleLike(it) } },
            onDislikeClick = { currentTrack?.let { onDislikeClick(it) } }
        )

        Spacer(modifier = Modifier.weight(0.3f))

        // Waveform & Timeline Visualizer
        CarWaveformVisualizerAndTimeline(
            progress = progress,
            durationMs = if (durationMs > 0) durationMs else (currentTrack?.durationMs ?: 0L),
            isPlaying = isPlaying,
            isRadio = currentTrack?.isRadio == true,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(0.4f))

        // Presets Grid
        Row(modifier = Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarActionTile(Modifier.weight(1f), "Моя Волна", Color(0xFF8A2387), Icons.Default.Waves) { onPlayMyVibe() }
            CarActionTile(Modifier.weight(1f), "Любимое", Color(0xFFC70039), Icons.Default.Favorite) { onPlayFavorites() }
            CarActionTile(Modifier.weight(1f), "Радио", Color(0xFFFF9900), Icons.Default.Radio) { onPlayRadio() }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarActionTile(Modifier.weight(1f), "Хиты", Color(0xFF00B4DB), Icons.AutoMirrored.Filled.TrendingUp) { onPlayHits() }
            CarActionTile(Modifier.weight(1f), "Скачанные", Color(0xFF4CAF50), Icons.Default.DownloadDone) { onPlayDownloaded() }
        }
    }
}

@Composable
fun CarScreenLandscape(
    currentTrack: TrackDetailsDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onDislikeClick: (TrackDetailsDto) -> Unit,
    onPlayMyVibe: () -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayHits: () -> Unit,
    onPlayDownloaded: () -> Unit,
    onPlayRadio: () -> Unit,
    onExit: () -> Unit,
    progress: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Full-width Top Header across the entire dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "В АВТОМОБИЛЕ",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            CarTimeWidget()
        }

        // Two-column dashboard content below top bar
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Art + Track Info (cleanly centered without clock clutter)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val coverUrl = currentTrack?.coverUri?.toCoverUrl("1000x1000") ?: "https://via.placeholder.com/600"
                val context = LocalContext.current
                if (currentTrack?.isRadio == true) {
                    RadioCoverCard(
                        coverUrl = coverUrl,
                        showLiveBadge = true,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f)
                            .widthIn(max = 280.dp)
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f)
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .shadow(32.dp, RoundedCornerShape(20.dp), spotColor = Color(0x66FF3366))
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = currentTrack?.title ?: "Неизвестный трек",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artists?.joinToString(", ") { it.name ?: "" } ?: "-",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Column: Controls + Dynamic Waveform Timeline + Presets
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .padding(start = 24.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(0.1f))

                // 1. Redesigned Liquid Glass Controls
                CarControls(
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                    onPrevClick = onPrevClick,
                    onToggleLike = { currentTrack?.let { onToggleLike(it) } },
                    onDislikeClick = { currentTrack?.let { onDislikeClick(it) } },
                    isLandscape = true
                )

                Spacer(modifier = Modifier.weight(0.2f))

                // 2. Interactive Waveform Visualizer & Timeline Widget (Fills the previous empty space!)
                CarWaveformVisualizerAndTimeline(
                    progress = progress,
                    durationMs = if (durationMs > 0) durationMs else (currentTrack?.durationMs ?: 0L),
                    isPlaying = isPlaying,
                    isRadio = currentTrack?.isRadio == true,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.weight(0.2f))

                // 3. Presets Block (Grouped together at bottom like in portrait mode)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CarActionTile(Modifier.weight(1f), "Моя Волна", Color(0xFFFF3366), Icons.Default.Waves) { onPlayMyVibe() }
                        CarActionTile(Modifier.weight(1f), "Любимое", Color(0xFFFF5252), Icons.Default.Favorite) { onPlayFavorites() }
                        CarActionTile(Modifier.weight(1f), "Радио", Color(0xFFFF9800), Icons.Default.Radio) { onPlayRadio() }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CarActionTile(Modifier.weight(1f), "Хиты", Color(0xFF00B4DB), Icons.AutoMirrored.Filled.TrendingUp) { onPlayHits() }
                        CarActionTile(Modifier.weight(1f), "Скачанные", Color(0xFF4CAF50), Icons.Default.DownloadDone) { onPlayDownloaded() }
                    }
                }
            }
        }
    }
}

@Composable
fun CarControls(
    isPlaying: Boolean,
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onToggleLike: () -> Unit,
    onDislikeClick: () -> Unit,
    isLandscape: Boolean = false
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaleFactor = (minOf(configuration.screenWidthDp, configuration.screenHeightDp) / 400f).coerceIn(0.85f, 1.25f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = if (isPlaying) 0.85f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_anim"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Dislike: Frosted Glass Capsule
        Box(
            modifier = Modifier
                .size((if (isLandscape) 52.dp else 46.dp) * scaleFactor)
                .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
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
                .clickable { onDislikeClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HeartBroken,
                contentDescription = "Дизлайк",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp * scaleFactor)
            )
        }

        // Previous: Frosted Glass Pill
        Box(
            modifier = Modifier
                .size((if (isLandscape) 64.dp else 56.dp) * scaleFactor)
                .shadow(12.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
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
                .clickable { onPrevClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Предыдущий",
                tint = Color.White,
                modifier = Modifier.size(32.dp * scaleFactor)
            )
        }

        // Play / Pause: Giant Glowing Liquid Glass Sphere
        Box(
            modifier = Modifier
                .size((if (isLandscape) 82.dp else 72.dp) * scaleFactor)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .shadow(
                    elevation = if (isPlaying) 28.dp else 16.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFFFF3366).copy(alpha = glowAlpha),
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
                .clickable { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Воспроизведение/Пауза",
                tint = Color.White,
                modifier = Modifier.size(42.dp * scaleFactor)
            )
        }

        // Next: Frosted Glass Pill
        Box(
            modifier = Modifier
                .size((if (isLandscape) 64.dp else 56.dp) * scaleFactor)
                .shadow(12.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
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
                .clickable { onNextClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Следующий",
                tint = Color.White,
                modifier = Modifier.size(32.dp * scaleFactor)
            )
        }

        // Like: Glowing Neon Heart
        Box(
            modifier = Modifier
                .size((if (isLandscape) 52.dp else 46.dp) * scaleFactor)
                .shadow(
                    elevation = if (isLiked) 16.dp else 8.dp,
                    shape = CircleShape,
                    spotColor = if (isLiked) Color(0xFFFF3366).copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f)
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
                .clickable { onToggleLike() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Нравится",
                tint = if (isLiked) Color(0xFFFF3366) else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp * scaleFactor)
            )
        }
    }
}

@Composable
fun CarWaveformVisualizerAndTimeline(
    progress: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isRadio: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaleFactor = (minOf(configuration.screenWidthDp, configuration.screenHeightDp) / 400f).coerceIn(0.85f, 1.25f)

    // Animated Waveform Heights using Transition
    val infiniteTransition = rememberInfiniteTransition(label = "car_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val rawProgressFraction = if (durationMs > 0 && progress > 0L) (progress.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val targetFraction = if (isDraggingSlider) dragFraction else rawProgressFraction

    val animatedProgressFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (isDraggingSlider) {
            androidx.compose.animation.core.snap()
        } else if (rawProgressFraction == 0f) {
            androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 150, easing = androidx.compose.animation.core.LinearEasing)
        },
        label = "car_slider_progress"
    )

    val displayFraction = if (isDraggingSlider) dragFraction else animatedProgressFraction
    val currentPositionMs = (displayFraction * durationMs).toLong()

    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x35282836),
                        Color(0x1F1A1A24),
                        Color(0x2E181820)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color(0x44FFFFFF), Color(0x10FFFFFF), Color(0x28FFFFFF))
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Quality Badge + Mode Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quality / Stream Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) Color(0xFF00E676) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRadio) "FM СТЕРЕО" else "HQ 320 KBPS",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = (10.5f * scaleFactor).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Mode Badge
                Text(
                    text = if (isRadio) "ПРЯМОЙ ЭФИР" else "МОЯ ВОЛНА",
                    color = Color(0xFFFF9900),
                    fontSize = (11f * scaleFactor).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Waveform Audio Visualizer (26 bars with harmonic synthesis & GPU acceleration)
            val playActivity by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0f,
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                label = "play_activity"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp * scaleFactor),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val barCount = 26
                for (i in 0 until barCount) {
                    // Integer harmonics: 1x, 2x, 3x, 4x guaranteed to be 100% periodic at 2*PI
                    val h1 = sin(phase * 1f + (i * 0.28f)) * 0.32f
                    val h2 = sin(phase * 2f + (i * 0.56f)) * 0.28f
                    val h3 = cos(phase * 3f - (i * 0.42f)) * 0.22f
                    val h4 = sin(phase * 4f + (i * 0.84f)) * 0.12f

                    val combined = h1 + h2 + h3 + h4
                    val normI = i.toFloat() / (barCount - 1)
                    val eqEnvelope = 0.65f + 0.35f * sin((normI * Math.PI).toFloat())
                    val rawHeight = (0.22f + (combined + 0.94f) * 0.42f * eqEnvelope).coerceIn(0.14f, 1.0f)
                    val barScale = 0.12f + (rawHeight - 0.12f) * playActivity

                    val isBarPassed = if (!isRadio && durationMs > 0) (i.toFloat() / barCount) <= displayFraction else isPlaying
                    val barColor = if (isBarPassed) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF3366), Color(0xFFFF9900))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0x55FFFFFF), Color(0x22FFFFFF))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.2.dp)
                            .fillMaxHeight()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1.0f) // Scale upwards from bottom baseline
                                scaleY = barScale
                            }
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Progress Timeline (hidden or simplified for radio)
            if (!isRadio && durationMs > 0) {
                // Interactive Scrub Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeek((fraction * durationMs).toLong())
                            }
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDraggingSlider = true
                                    dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    isDraggingSlider = false
                                    onSeek((dragFraction * durationMs).toLong())
                                },
                                onDragCancel = {
                                    isDraggingSlider = false
                                }
                            ) { change, _ ->
                                dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Background Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    // Active Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayFraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF3366), Color(0xFFFF9900))
                                )
                            )
                    )
                }

                // Time indicators Row (01:24 / 03:45)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentSec = if (isDraggingSlider) (dragFraction * durationMs / 1000).toLong() else (currentPositionMs / 1000)
                    val totalSec = durationMs / 1000
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", currentSec / 60, currentSec % 60),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = (11.5f * scaleFactor).sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = (11.5f * scaleFactor).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Radio Live Broadcast Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color(0xFFFF9900),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Бесконечный поток радиовещания",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = (12 * scaleFactor).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CarActionTile(modifier: Modifier = Modifier, title: String, accentColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = accentColor.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF22222C),
                        Color(0xFF16161C)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.4f),
                            Color(0x10FFFFFF)
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CarTimeWidget() {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMM, E", Locale("ru"))
        while (true) {
            val calendar = Calendar.getInstance()
            currentTime = timeFormat.format(calendar.time)
            currentDate = dateFormat.format(calendar.time)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0x352A2A38),
                        Color(0x201E1E28),
                        Color(0x352A2A38)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0x40FFFFFF),
                            Color(0x10FFFFFF),
                            Color(0x25FFFFFF)
                        )
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentDate,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

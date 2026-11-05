package com.korenskoy.yandexmusic.presentation.car

import com.korenskoy.yandexmusic.presentation.hifi.HiFiLinkBadge
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph
import com.korenskoy.yandexmusic.presentation.hifi.RingMode
import com.korenskoy.yandexmusic.presentation.hifi.SpectrumRing
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiGrain
import com.korenskoy.yandexmusic.utils.browserUserAgent
import com.korenskoy.yandexmusic.utils.toCoverUrl
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Time source of the car clock; screenshot tests pin it. */
val LocalCarClock = androidx.compose.runtime.staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** The source tiles of the dock, in order. */
enum class CarSource { WAVE, LIKED, RADIO, HITS, DOWNLOADED }

/** Which dock tile is lit for the playing [source]. Pure; unit-tested. */
fun carSourceOf(source: PlaylistSource, isRadio: Boolean): CarSource? = when {
    isRadio || source == PlaylistSource.RADIO -> CarSource.RADIO
    source == PlaylistSource.VIBE -> CarSource.WAVE
    source == PlaylistSource.LIBRARY -> CarSource.LIKED
    source == PlaylistSource.TRENDS -> CarSource.HITS
    else -> null
}

/**
 * "В авто" — the screen people spend the most time on, built like the digital instrument cluster of a
 * premium car: the driver's side holds a gauge (a large metal knob in a spectrum ring that is also the
 * track progress), knobs for previous/next and a dock of sources; the far side holds the cover with a
 * glass "next" plate. Everything is laid out for 1280×720 and scaled to the head unit, mirrored for
 * right-hand drive.
 *
 * [progress] is read where it is drawn (ring) or once a second (readout), so the screen is not
 * recomposed on every progress tick.
 */
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
    steeringWheelOnLeft: Boolean = true,
    progress: () -> Long = { 0L },
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    nextTrack: TrackDetailsDto? = null,
    source: PlaylistSource = PlaylistSource.NONE,
    isLiked: Boolean = currentTrack?.let { likedTrackIds.contains(it.idString) } == true,
    isBuffering: Boolean = false
) {
    androidx.activity.compose.BackHandler(onBack = onExit)
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        if (!isPlaying && currentTrack == null) onPlayMyVibe()
    }

    val playPause = { if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() }
    val isRadio = currentTrack?.isRadio == true
    val active = carSourceOf(source, isRadio)
    val sourceLabel = when (active) {
        CarSource.WAVE -> stringResource(R.string.car_source_wave)
        CarSource.LIKED -> stringResource(R.string.library_liked)
        CarSource.RADIO -> stringResource(R.string.car_source_radio)
        CarSource.HITS -> stringResource(R.string.car_source_hits)
        else -> ""
    }
    val coverUrl = currentTrack?.coverUri.toCoverUrl(CoverSize.Medium)
    val tint = LocalArtworkTint.current
    val ringMode = when {
        isBuffering -> RingMode.Loading
        isPlaying -> RingMode.Playing
        else -> RingMode.Paused
    }
    val fraction = { if (durationMs > 0 && !isRadio) progress().toFloat() / durationMs else 0f }
    val sources = listOf(
        DockItem(CarSource.WAVE, Icons.Rounded.Waves, stringResource(R.string.car_source_wave), onPlayMyVibe),
        DockItem(CarSource.LIKED, Icons.Rounded.Favorite, stringResource(R.string.car_source_liked), onPlayFavorites),
        DockItem(CarSource.RADIO, Icons.Rounded.Radio, stringResource(R.string.car_source_radio), onPlayRadio),
        DockItem(CarSource.HITS, Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.car_source_hits), onPlayHits),
        DockItem(CarSource.DOWNLOADED, Icons.Rounded.DownloadDone, stringResource(R.string.car_source_downloaded), onPlayDownloaded)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HiFi.Ground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) onPrevClick() else if (dragAmount < -50) onNextClick()
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { playPause() }) }
    ) {
        // Scale the 1280×720 (landscape) or 600×1060 (portrait) layout to this head unit
        val s = if (isLandscape) minOf(maxWidth / 1280.dp, maxHeight / 720.dp) else minOf(maxWidth / 600.dp, maxHeight / 1060.dp)
        val u = Scale(s.coerceIn(0.55f, 2f))
        val farFocus = if (isLandscape) Offset(if (steeringWheelOnLeft) 0.78f else 0.22f, 0.45f) else Offset(0.5f, 0.25f)
        HiFiBackdrop(tint = tint, coverUrl = coverUrl, glowCenter = farFocus, bottomFade = !isLandscape)
        if (isLandscape) {
            // Darken the driver's side so text reads at a glance
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val shade = Brush.horizontalGradient(
                            0f to HiFi.Ground.copy(alpha = 0.92f), 0.42f to HiFi.Ground.copy(alpha = 0.72f), 0.78f to Color.Transparent,
                            startX = if (steeringWheelOnLeft) 0f else size.width,
                            endX = if (steeringWheelOnLeft) size.width else 0f
                        )
                        onDrawBehind { drawRect(shade) }
                    }
            )
        }

        // The theme's fixed 24sp line height would not follow the head-unit scale and clip small text:
        // lines follow the (scaled) font instead
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified)
        ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            CarHeader(u, onExit)
            if (isLandscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = u(48f), end = u(40f), bottom = u(28f)),
                    horizontalArrangement = Arrangement.spacedBy(u(40f))
                ) {
                    val driver: @Composable () -> Unit = {
                        DriverPanel(
                            u = u, track = currentTrack, sourceLabel = sourceLabel, isPlaying = isPlaying, isLiked = isLiked,
                            ringMode = ringMode, fraction = fraction, progress = progress, durationMs = durationMs, isRadio = isRadio,
                            onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                            onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                            sources = sources, active = active, onSeek = onSeek,
                            modifier = Modifier.width(u(652f)).fillMaxHeight()
                        )
                    }
                    val far: @Composable () -> Unit = {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CoverWithNext(u, currentTrack, nextTrack, Modifier.size(u(500f)))
                        }
                    }
                    if (steeringWheelOnLeft) { driver(); far() } else { far(); driver() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = u(28f)).padding(bottom = u(24f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverWithNext(u, currentTrack, nextTrack, Modifier.size(u(440f)))
                    Spacer(Modifier.height(u(28f)))
                    DriverPanel(
                        u = u, track = currentTrack, sourceLabel = sourceLabel, isPlaying = isPlaying, isLiked = isLiked,
                        ringMode = ringMode, fraction = fraction, progress = progress, durationMs = durationMs, isRadio = isRadio,
                        onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                        onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                        sources = sources, active = active, onSeek = onSeek,
                        modifier = Modifier.width(u(560f)).weight(1f),
                        compactDock = true
                    )
                }
            }
        }
        }
    }
}

/** dp sizes of the 1280×720 design, scaled to the screen. */
internal class Scale(val factor: Float) {
    operator fun invoke(v: Float): Dp = (v * factor).dp
    fun sp(v: Float): TextUnit = (v * factor).sp
}

private data class DockItem(val source: CarSource, val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun CarHeader(u: Scale, onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = u(48f), end = u(40f), top = u(22f), bottom = u(10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(u(56f))
                .hifiGlass(CircleShape)
                .clickable(role = Role.Button, onClick = onExit),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.car_exit), tint = Color.White, modifier = Modifier.size(u(26f)))
        }
        Spacer(Modifier.width(u(16f)))
        Text(stringResource(R.string.car_label), color = Color.White.copy(alpha = 0.66f), fontSize = u.sp(14f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em)
        Spacer(Modifier.width(u(14f)))
        HiFiLinkBadge(height = u(32f), fontSize = u.sp(14f))
        Spacer(Modifier.weight(1f))
        CarClock(u)
    }
}

/** Large bold time over a small-caps date, right-aligned like a dashboard; refreshes once a minute. */
@Composable
private fun CarClock(u: Scale) {
    val clock = LocalCarClock.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(clock) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE · d MMMM", Locale.forLanguageTag("ru"))
        while (true) {
            val now = clock()
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            time = timeFormat.format(calendar.time)
            date = dateFormat.format(calendar.time).uppercase(Locale.forLanguageTag("ru"))
            delay((60_000L - now % 60_000L).coerceIn(1_000L, 60_000L))
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(time, color = Color.White, fontSize = u.sp(40f), lineHeight = u.sp(42f), fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, style = Tabular)
        Text(date, color = Color.White.copy(alpha = 0.60f), fontSize = u.sp(12f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em, maxLines = 1)
    }
}

/** Tabular digits: the clock and the readout don't jump as numbers change. */
private val Tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun DriverPanel(
    u: Scale,
    track: TrackDetailsDto?,
    sourceLabel: String,
    isPlaying: Boolean,
    isLiked: Boolean,
    ringMode: RingMode,
    fraction: () -> Float,
    progress: () -> Long,
    durationMs: Long,
    isRadio: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    sources: List<DockItem>,
    active: CarSource?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compactDock: Boolean = false
) {
    Column(modifier) {
        if (sourceLabel.isNotEmpty()) {
            Text(sourceLabel.uppercase(), color = Color.White.copy(alpha = 0.72f), fontSize = u.sp(14f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em)
        }
        AnimatedContent(
            targetState = track,
            contentKey = { it?.idString },
            transitionSpec = { (fadeIn(HiFiMotion.large()) + slideInVertically(HiFiMotion.large()) { it / 10 }) togetherWith fadeOut(HiFiMotion.quick()) },
            label = "car_title"
        ) { shown ->
            Column {
                Text(
                    shown?.title ?: stringResource(R.string.car_source_wave),
                    color = Color.White,
                    fontSize = u.sp(56f),
                    lineHeight = u.sp(62f),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.03).em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = u(6f))
                )
                Text(
                    if (shown?.isRadio == true) stringResource(R.string.mini_player_live) else shown?.artists?.joinToString { it.name ?: "" }.orEmpty(),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = u.sp(26f),
                    lineHeight = u.sp(32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // The gauge cluster; in portrait (560 wide) the side controls shrink so all five fit
        val sideSize = if (compactDock) 64f else 72f
        val skipSize = if (compactDock) 88f else 96f
        Row(
            Modifier.fillMaxWidth().height(u(240f)).padding(top = u(0f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassRound(u(sideSize), stringResource(R.string.cd_ne_nravitsya), onDislike) {
                Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(u(28f)))
            }
            HiFiKnob(onClick = onPrev, contentDescription = stringResource(R.string.cd_predyduschiy_trek), size = u(skipSize)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(u(38f)))
            }
            Box(Modifier.size(u(240f)), contentAlignment = Alignment.Center) {
                SpectrumRing(
                    mode = ringMode,
                    progress = fraction,
                    bars = if (LocalHiFiLite.current) 60 else 90,
                    innerRadius = u(98f),
                    maxLength = u(20f),
                    barWidth = u(3f),
                    modifier = Modifier.size(u(240f))
                )
                HiFiKnob(onClick = onPlayPause, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = u(176f)) {
                    PlayPauseGlyph(isPlaying = isPlaying, size = u(56f))
                }
            }
            HiFiKnob(onClick = onNext, contentDescription = stringResource(R.string.cd_sleduyuschiy_trek), size = u(skipSize)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(u(38f)))
            }
            GlassRound(u(sideSize), stringResource(R.string.cd_nravitsya), onLike) {
                Icon(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(u(28f))
                )
            }
        }
        // A timeline without digits under the gauge; radio is live and shows the on-air line
        if (isRadio) CarLiveLine(u, Modifier.padding(top = u(10f)))
        else CarTimeline(u, progress, durationMs, isPlaying, onSeek, Modifier.padding(top = u(10f)))
        Spacer(Modifier.weight(1f))
        Dock(u, sources, active, compact = compactDock)
    }
}

@Composable
private fun GlassRound(size: Dp, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Five big source tiles; the playing one is dark metal with a steel rim. */
@Composable
private fun Dock(u: Scale, items: List<DockItem>, active: CarSource?, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u(12f))) {
        items.forEach { item ->
            val selected = item.source == active
            val shape = RoundedCornerShape(u(22f))
            Column(
                Modifier
                    .weight(1f)
                    .height(u(if (compact) 88f else 96f))
                    .then(
                        if (selected) Modifier.hifiSoftShadow(shape, u(14f)).hifiAnodized(shape)
                        else Modifier.hifiGlass(shape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.10f))
                    )
                    .clickable(role = Role.Button, onClick = item.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.86f), modifier = Modifier.size(u(28f)))
                Spacer(Modifier.height(u(8f)))
                Text(
                    item.label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.86f),
                    fontSize = u.sp(16f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The cover with a gloss and a coloured shadow; the next track sits on a glass plate over its lower edge. */
@Composable
private fun CoverWithNext(u: Scale, track: TrackDetailsDto?, next: TrackDetailsDto?, modifier: Modifier) {
    val context = LocalContext.current
    val tint: ArtworkTint = LocalArtworkTint.current
    val shape = RoundedCornerShape(u(30f))
    Box(
        modifier
            .aspectRatio(1f)
            .hifiSoftShadow(shape, u(44f), tint.primary.copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.primary, tint.secondary)))
    ) {
        AnimatedContent(
            targetState = track?.coverUri,
            transitionSpec = { fadeIn(HiFiMotion.large()) togetherWith fadeOut(HiFiMotion.large()) },
            label = "car_cover"
        ) { uri ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .cover(uri, CoverSize.Large)
                    .crossfade(true)
                    .apply { if (track?.isRadio == true) browserUserAgent() }
                    .build(),
                contentDescription = track?.title,
                contentScale = if (track?.isRadio == true) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (track?.isRadio == true) Modifier.background(Color.White).padding(u(60f)) else Modifier)
            )
        }
        // Gloss
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gloss = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.55f, size.height * 0.55f))
                    val foot = Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))
                    onDrawBehind {
                        drawRect(gloss)
                        drawRect(foot)
                    }
                }
        )
        AnimatedVisibility(
            visible = next != null,
            enter = fadeIn(HiFiMotion.large()),
            exit = fadeOut(HiFiMotion.quick()),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (next != null) NextPlate(u, next)
        }
    }
}

@Composable
private fun NextPlate(u: Scale, next: TrackDetailsDto) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(u(22f))
    AnimatedContent(
        targetState = next,
        contentKey = { it.idString },
        transitionSpec = { fadeIn(HiFiMotion.large()) togetherWith fadeOut(HiFiMotion.quick()) },
        label = "car_next"
    ) { shown ->
        Row(
            Modifier
                .padding(u(14f))
                .fillMaxWidth()
                .height(u(84f))
                .hifiGlass(shape, fill = Color(0x8C080A14), border = Color.White.copy(alpha = 0.16f))
                .hifiGrain(enabled = !LocalHiFiLite.current, alpha = 0.25f)
                .padding(start = u(12f), end = u(20f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).cover(shown.coverUri, CoverSize.Small).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(u(60f))
                    .hifiSoftShadow(RoundedCornerShape(u(13f)), u(10f))
                    .clip(RoundedCornerShape(u(13f)))
                    .background(HiFi.Placeholder)
            )
            Spacer(Modifier.width(u(14f)))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.car_next), color = Color.White.copy(alpha = 0.72f), fontSize = u.sp(11f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.18.em)
                Text(shown.title ?: "", color = Color.White, fontSize = u.sp(21f), lineHeight = u.sp(26f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown.artists.joinToString { it.name ?: "" }, color = Color.White.copy(alpha = 0.72f), fontSize = u.sp(16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

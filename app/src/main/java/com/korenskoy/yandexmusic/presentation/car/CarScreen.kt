package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import com.korenskoy.yandexmusic.presentation.main.components.waveTuningSummary
import com.korenskoy.yandexmusic.presentation.main.components.WaveTuneChoices
import com.korenskoy.yandexmusic.presentation.main.components.WavePill
import com.korenskoy.yandexmusic.domain.model.WaveTuning
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
import androidx.compose.ui.graphics.graphicsLayer
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
    upcoming: List<TrackDetailsDto> = emptyList(),
    upcomingLoading: Boolean = false,
    onUpcomingClick: (TrackDetailsDto) -> Unit = {},
    source: PlaylistSource = PlaylistSource.NONE,
    isLiked: Boolean = currentTrack?.let { likedTrackIds.contains(it.idString) } == true,
    isBuffering: Boolean = false,
    showClock: Boolean = true,
    waveTuning: WaveTuning = WaveTuning(),
    trackWaveSeed: TrackDetailsDto? = null,
    onWaveTuningChange: (WaveTuning) -> Unit = {},
    onTrackWave: (TrackDetailsDto) -> Unit = {},
    onBackToMyWave: () -> Unit = {},
    initialTuneOpen: Boolean = false
) {
    var tuneOpen by rememberSaveable { mutableStateOf(initialTuneOpen) }
    androidx.activity.compose.BackHandler(enabled = tuneOpen) { tuneOpen = false }
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
    val nextCaption = when {
        source == PlaylistSource.VIBE && trackWaveSeed != null -> stringResource(R.string.car_next_track_wave)
        source == PlaylistSource.VIBE -> stringResource(R.string.car_next_wave)
        else -> stringResource(R.string.car_next)
    }
    val sourceLabel = when {
        trackWaveSeed != null && active == CarSource.WAVE -> stringResource(R.string.wave_by_track_car, trackWaveSeed.title.orEmpty())
        else -> null
    } ?: when (active) {
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
        // Scale the 1280×720 (landscape) or 600×1200 (portrait: cover, track, controls, the "next" row, sources)
        // layout to this head unit. Standing, the height is what runs out (the Geely's 960×1280): count only what
        // the system bars leave, or the source dock at the bottom gets squeezed
        val bars = WindowInsets.systemBars.asPaddingValues()
        val usableHeight = maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()
        val u = Scale(carScaleOf(maxWidth, maxHeight, usableHeight, isLandscape))
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
        // "Настроить волну": big enough to hit without aiming. Lying down it opens over the cover column,
        // standing it takes the cover's place — the controls and the sources are never covered
        val tunePanel: @Composable (Modifier, Boolean) -> Unit = { panelModifier, segments ->
            Column(
                panelModifier
                    .clip(RoundedCornerShape(u(30f)))
                    .background(HiFi.SheetFill.copy(alpha = 0.97f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    // Never cut: on a cramped screen the panel scrolls inside its place
                    .verticalScroll(rememberScrollState())
                    .padding(start = u(24f), end = u(18f), top = u(16f), bottom = u(22f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wave_tune_title), color = Color.White, fontSize = u.sp(28f), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(u(56f))
                            .hifiGlass(CircleShape)
                            .clickable(role = Role.Button) { tuneOpen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_zakryt), tint = Color.White, modifier = Modifier.size(u(24f)))
                    }
                }
                WaveTuneChoices(waveTuning, onWaveTuningChange, segments = segments, optionHeight = u(56f), fontSize = u.sp(15f), captionSize = u.sp(14f), gap = u(12f))
            }
        }
        // The wave's buttons: in the header on a wide screen, in a row under it on a tall one
        val onWave = active == CarSource.WAVE
        val wavePills: @Composable () -> Unit = {
            val pillHeight = u(56f)
            val pillFont = u.sp(18f)
            if (trackWaveSeed != null && onWave) {
                WavePill(Icons.Rounded.Waves, stringResource(R.string.wave_back_to_mine), active = false, height = pillHeight, fontSize = pillFont, onClick = onBackToMyWave)
                WavePill(Icons.Rounded.Tune, stringResource(R.string.wave_tune_short), active = !waveTuning.isDefault, height = pillHeight, fontSize = pillFont,
                    onClick = { tuneOpen = true }, description = stringResource(R.string.wave_tune_title))
            } else {
                currentTrack?.takeIf { !it.isRadio }?.let { track ->
                    WavePill(Icons.Rounded.Waves, stringResource(R.string.wave_by_track), active = false, height = pillHeight, fontSize = pillFont, onClick = { onTrackWave(track) })
                }
                if (onWave) {
                    val summary = waveTuningSummary(waveTuning)
                    WavePill(Icons.Rounded.Tune, summary ?: stringResource(R.string.wave_tune_short), active = summary != null, height = pillHeight, fontSize = pillFont,
                        onClick = { tuneOpen = true }, description = summary?.let { stringResource(R.string.wave_tune_cd, it) } ?: stringResource(R.string.wave_tune_title))
                }
            }
        }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            CarHeader(u, onExit, showClock, pills = if (isLandscape) wavePills else null)
            if (!isLandscape) {
                Row(
                    Modifier.fillMaxWidth().padding(start = u(28f), end = u(28f), bottom = u(14f)),
                    horizontalArrangement = Arrangement.spacedBy(u(12f))
                ) { wavePills() }
            }
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
                    // The cover alone, and under it the covers of what comes next: nothing lies on the cover
                    val far: @Composable () -> Unit = {
                        // "Настроить волну" opens over this column: it fades away, so nothing peeks from under the panel
                        val farAlpha by androidx.compose.animation.core.animateFloatAsState(if (tuneOpen) 0f else 1f, HiFiMotion.standard(), label = "car_far")
                        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = farAlpha }, contentAlignment = Alignment.Center) {
                            if (isRadio) {
                                CarCover(u, currentTrack, Modifier.size(u(500f)))
                            } else {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(u(22f), Alignment.CenterVertically)
                                ) {
                                    // Gets what the row leaves, never more than 300: the row is measured first
                                    CarCover(
                                        u, currentTrack,
                                        Modifier
                                            .weight(1f, fill = false)
                                            .sizeIn(maxWidth = u(300f), maxHeight = u(300f))
                                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    )
                                    NextRow(u, nextCaption, upcoming, upcomingLoading, maxCard = u(152f), gap = u(22f), onClick = onUpcomingClick)
                                }
                            }
                        }
                    }
                    if (steeringWheelOnLeft) { driver(); far() } else { far(); driver() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = u(28f)).padding(bottom = u(24f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // As on the phone's wave page: cover, track, controls, then "next" right above the sources.
                    // "Настроить волну" takes the place of the cover and the "next" row (more room than it needs),
                    // so the controls and the sources stay reachable
                    androidx.compose.animation.Crossfade(
                        targetState = tuneOpen,
                        modifier = Modifier.widthIn(max = u(640f)).fillMaxWidth(),
                        label = "car_tune"
                    ) { open ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            if (open) tunePanel(Modifier.fillMaxWidth(), true)
                            else CarCover(u, currentTrack, Modifier.size(u(if (isRadio) 400f else 220f)))
                        }
                    }
                    Spacer(Modifier.height(u(24f)))
                    DriverPanel(
                        u = u, track = currentTrack, sourceLabel = sourceLabel, isPlaying = isPlaying, isLiked = isLiked,
                        ringMode = ringMode, fraction = fraction, progress = progress, durationMs = durationMs, isRadio = isRadio,
                        onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                        onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                        sources = sources, active = active, onSeek = onSeek,
                        // At most 560 wide and centred: on a wide standing screen (the Geely's 960) the controls
                        // stay together instead of spreading to the edges
                        modifier = Modifier.widthIn(max = u(560f)).fillMaxWidth().weight(1f),
                        compactDock = true,
                        beforeDock = if (isRadio || tuneOpen) null else {
                            { NextRow(u, nextCaption, upcoming, upcomingLoading, maxCard = u(168f), gap = u(20f), onClick = onUpcomingClick) }
                        }
                    )
                }
            }
        }
        // "Настроить волну" lying down: over the cover column, so the controls and the sources stay free
        if (tuneOpen && isLandscape) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tuneOpen = false }
            )
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // Over the cover column only: never wider than what the driver's panel leaves (it is u(652) wide
            // with u(48) + u(40) + u(40) around it), so the controls and the sources stay uncovered
            val panelWidth = androidx.compose.ui.unit.min(u(500f), maxWidth - u(780f)).coerceAtLeast(u(400f))
            tunePanel(
                Modifier
                    .align(if (steeringWheelOnLeft) Alignment.TopEnd else Alignment.TopStart)
                    .padding(top = u(96f), start = u(48f), end = u(40f), bottom = u(24f))
                    .width(panelWidth),
                // One-line strips need the full width; a narrower panel wraps the choices as chips
                panelWidth >= u(460f)
            )
            }
        }
        }
    }
}

/** The car layout's scale for a screen: the 1280×720 design lying, 600×1200 standing (by the height the bars leave). */
internal fun carScaleOf(maxWidth: Dp, maxHeight: Dp, usableHeight: Dp, isLandscape: Boolean): Float {
    val s = if (isLandscape) minOf(maxWidth / 1280.dp, maxHeight / 720.dp) else minOf(maxWidth / 600.dp, usableHeight / 1200.dp)
    return s.coerceIn(0.55f, 2f)
}

/**
 * How high the source dock reaches from the bottom of the car screen above the navigation bar, plus a gap:
 * a message placed over this never covers the sources. Lying: 28 + 96 + 12; standing: 24 + 88 + 12.
 */
fun carDockClearance(maxWidth: Dp, maxHeight: Dp, usableHeight: Dp, isLandscape: Boolean): Dp {
    val s = carScaleOf(maxWidth, maxHeight, usableHeight, isLandscape)
    return ((if (isLandscape) 136f else 124f) * s).dp
}

/** dp sizes of the 1280×720 design, scaled to the screen. */
internal class Scale(val factor: Float) {
    operator fun invoke(v: Float): Dp = (v * factor).dp
    fun sp(v: Float): TextUnit = (v * factor).sp
}

private data class DockItem(val source: CarSource, val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun CarHeader(u: Scale, onExit: () -> Unit, showClock: Boolean, pills: (@Composable () -> Unit)? = null) {
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
        if (pills != null) {
            Spacer(Modifier.width(u(24f)))
            // Never over the clock: the buttons get what is left and their names shorten first
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(u(12f)), verticalAlignment = Alignment.CenterVertically) { pills() }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showClock) {
            Spacer(Modifier.width(u(16f)))
            CarClock(u)
        }
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
    compactDock: Boolean = false,
    beforeDock: (@Composable () -> Unit)? = null
) {
    Column(modifier) {
        if (sourceLabel.isNotEmpty()) {
            // One line always: a long track name in "Волна по треку «…»" must not push the title down
            Text(sourceLabel.uppercase(), color = Color.White.copy(alpha = 0.72f), fontSize = u.sp(14f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    fontSize = u.sp(if (compactDock) 44f else 56f),
                    lineHeight = u.sp(if (compactDock) 50f else 62f),
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

        // The gauge cluster. Standing (544 wide) all of it is smaller, so the five never touch:
        // 60 + 80 + 196 + 80 + 60 = 476, about 17 between neighbours; lying (652 wide) 19 between them
        val sideSize = if (compactDock) 60f else 72f
        val skipSize = if (compactDock) 80f else 96f
        val ringSize = if (compactDock) 196f else 240f
        val knobSize = if (compactDock) 144f else 176f
        val sideIcon = if (compactDock) 24f else 28f
        val skipIcon = if (compactDock) 32f else 38f
        Row(
            Modifier.fillMaxWidth().height(u(ringSize + 4f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassRound(u(sideSize), stringResource(R.string.cd_ne_nravitsya), onDislike) {
                Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(u(sideIcon)))
            }
            HiFiKnob(onClick = onPrev, contentDescription = stringResource(R.string.cd_predyduschiy_trek), size = u(skipSize)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(u(skipIcon)))
            }
            // The ring's bars end inside its own square: inner radius + the longest bar < half of it
            Box(Modifier.size(u(ringSize)), contentAlignment = Alignment.Center) {
                SpectrumRing(
                    mode = ringMode,
                    progress = fraction,
                    bars = if (LocalHiFiLite.current) 60 else 90,
                    innerRadius = u(if (compactDock) 80f else 98f),
                    maxLength = u(if (compactDock) 16f else 20f),
                    barWidth = u(3f),
                    modifier = Modifier.size(u(ringSize))
                )
                HiFiKnob(onClick = onPlayPause, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = u(knobSize)) {
                    PlayPauseGlyph(isPlaying = isPlaying, size = u(if (compactDock) 46f else 56f))
                }
            }
            HiFiKnob(onClick = onNext, contentDescription = stringResource(R.string.cd_sleduyuschiy_trek), size = u(skipSize)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(u(skipIcon)))
            }
            GlassRound(u(sideSize), stringResource(R.string.cd_nravitsya), onLike) {
                Icon(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(u(sideIcon))
                )
            }
        }
        // A timeline without digits under the gauge; radio is live and shows the on-air line
        if (isRadio) CarLiveLine(u, Modifier.padding(top = u(10f)))
        else CarTimeline(u, progress, durationMs, isPlaying, onSeek, Modifier.padding(top = u(10f)))
        Spacer(Modifier.weight(1f))
        if (beforeDock != null) {
            beforeDock()
            Spacer(Modifier.height(u(18f)))
        }
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

/** The cover with a gloss and a coloured shadow. Nothing lies on it: what comes next has its own row. */
@Composable
private fun CarCover(u: Scale, track: TrackDetailsDto?, modifier: Modifier) {
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
    }
}

/**
 * What comes next: up to three covers with title and artist, like the wave page on a phone. Fixed, not a
 * carousel: a swipe on the car screen switches tracks, and nothing slides under the driver's finger. While
 * the wave is still fetching, placeholders of the same size keep the place.
 */
@Composable
private fun NextRow(
    u: Scale,
    caption: String,
    tracks: List<TrackDetailsDto>,
    loading: Boolean,
    maxCard: Dp,
    gap: Dp,
    onClick: (TrackDetailsDto) -> Unit
) {
    if (tracks.isEmpty() && !loading) return
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val card = ((maxWidth - gap * 2) / 3).coerceAtMost(maxCard)
        Column(Modifier.width(card * 3 + gap * 2)) {
            Text(
                caption.uppercase(),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = u.sp(13f),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.16.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(u(12f)))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { slot ->
                    AnimatedContent(
                        targetState = tracks.getOrNull(slot),
                        contentKey = { it?.idString },
                        transitionSpec = { fadeIn(HiFiMotion.large()) togetherWith fadeOut(HiFiMotion.quick()) },
                        label = "car_next_$slot"
                    ) { track ->
                        when {
                            track != null -> NextCard(u, track, card) { onClick(track) }
                            loading -> NextCardPlaceholder(u, card)
                            else -> Spacer(Modifier.width(card))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(u: Scale, track: TrackDetailsDto, size: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(u(18f))
    val title = track.title ?: stringResource(R.string.bez_nazvaniya)
    val description = stringResource(R.string.car_next_play_cd, title)
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(u(18f)))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Medium).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .hifiSoftShadow(shape, u(16f))
                .clip(shape)
                .background(HiFi.Placeholder)
        )
        Spacer(Modifier.height(u(10f)))
        Text(title, color = Color.White, fontSize = u.sp(17f), lineHeight = u.sp(22f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            track.artists.joinToString { it.name ?: "" },
            color = Color.White.copy(alpha = 0.66f),
            fontSize = u.sp(15f),
            lineHeight = u.sp(20f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Exactly a card's height (cover, 10, two lines), so the row does not jump when the tracks arrive. */
@Composable
private fun NextCardPlaceholder(u: Scale, size: Dp) {
    val fill = Color.White.copy(alpha = 0.06f)
    Column(Modifier.width(size)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(u(18f))).background(fill))
        Spacer(Modifier.height(u(15f)))
        Box(Modifier.width(size * 0.72f).height(u(12f)).clip(RoundedCornerShape(u(6f))).background(fill))
        Spacer(Modifier.height(u(10f)))
        Box(Modifier.width(size * 0.48f).height(u(10f)).clip(RoundedCornerShape(u(5f))).background(fill))
        Spacer(Modifier.height(u(5f)))
    }
}

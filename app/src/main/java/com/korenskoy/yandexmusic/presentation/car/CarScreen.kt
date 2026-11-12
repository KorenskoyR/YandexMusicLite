package com.korenskoy.yandexmusic.presentation.car

import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import com.korenskoy.yandexmusic.presentation.hifi.LyricsPanel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.korenskoy.yandexmusic.presentation.hifi.lyricsClose
import androidx.compose.runtime.SideEffect
import com.korenskoy.yandexmusic.domain.model.CarLayout
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Lyrics
import com.korenskoy.yandexmusic.presentation.hifi.rememberLyrics
import com.korenskoy.yandexmusic.presentation.hifi.rememberAiMark
import com.korenskoy.yandexmusic.presentation.hifi.AiChip
import androidx.compose.material.icons.rounded.Mic
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.korenskoy.yandexmusic.presentation.hifi.HiFiWellButton
import androidx.compose.material.icons.rounded.Settings
import com.korenskoy.yandexmusic.utils.russianQuantityString
import com.korenskoy.yandexmusic.domain.model.CarUnderCover
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    source == PlaylistSource.DEVICE -> CarSource.DOWNLOADED
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
    underCover: CarUnderCover = CarUnderCover.QUEUE,
    /** Lying on a wide head unit: three columns, two, or by the room ([CarWide]). */
    layout: CarLayout = CarLayout.AUTO,
    waveTuning: WaveTuning = WaveTuning(),
    trackWaveSeed: TrackDetailsDto? = null,
    onWaveTuningChange: (WaveTuning) -> Unit = {},
    onTrackWave: (TrackDetailsDto) -> Unit = {},
    onBackToMyWave: () -> Unit = {},
    initialTuneOpen: Boolean = false,
    initialQueueOpen: Boolean = false,
    /** Opens the same settings as on "Моя музыка"; null — no button. */
    onOpenSettings: (() -> Unit)? = null,
    /** The microphone: what is said plays at once; null — the device has no voice input. */
    onVoiceSearch: (() -> Unit)? = null,
    /** The user's own music: with some, "Скачанные" opens a choice; null — it plays the downloads at once. */
    deviceMusic: CarDeviceMusic? = null,
    /** Plays the music on the device, or one of its folders by key. */
    onPlayDevice: (folderKey: String?) -> Unit = {},
    initialDownloadsOpen: Boolean = false,
    initialLyricsOpen: Boolean = false,
    /** "Любимое" plays shuffled; [onToggleShuffle] turns it on and off. */
    shuffled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    /** The stations, the favourites, and what a tap on them does; null — no «Станции». */
    radio: CarRadio? = null,
    initialStationsOpen: Boolean = false,
    /** The user's playlists for «В плейлист»; null — no such button (not signed in, a preview). */
    playlists: CarPlaylists? = null,
    initialPlaylistsOpen: Boolean = false
) {
    var tuneOpen by rememberSaveable { mutableStateOf(initialTuneOpen) }
    var queueOpen by rememberSaveable { mutableStateOf(initialQueueOpen) }
    var downloadsOpen by rememberSaveable { mutableStateOf(initialDownloadsOpen && deviceMusic?.opensPanel == true) }
    BackHandler(enabled = tuneOpen) { tuneOpen = false }
    BackHandler(enabled = queueOpen) { queueOpen = false }
    BackHandler(enabled = downloadsOpen) { downloadsOpen = false }
    // The words of the track, over the cover column; a new track closes them
    val lyrics = rememberLyrics(currentTrack)
    // Open words stay open on the next track and show its words; a track without any closes them
    var lyricsOpen by rememberSaveable { mutableStateOf(initialLyricsOpen) }
    LaunchedEffect(lyrics == null, lyrics?.answer) { if (lyricsClose(lyrics)) lyricsOpen = false }
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    var stationsOpen by rememberSaveable { mutableStateOf(initialStationsOpen) }
    BackHandler(enabled = stationsOpen) { stationsOpen = false }
    val canAdd = playlists != null && canAddToPlaylist(currentTrack)
    var playlistsOpen by rememberSaveable { mutableStateOf(initialPlaylistsOpen && canAdd) }
    BackHandler(enabled = playlistsOpen) { playlistsOpen = false }
    // A station or a file of the user's own has no playlists: the panel goes with such a track
    LaunchedEffect(canAdd) { if (!canAdd) playlistsOpen = false }
    // Any other panel takes the same place
    LaunchedEffect(tuneOpen, queueOpen, downloadsOpen, lyricsOpen, stationsOpen) {
        if (listOf(tuneOpen, queueOpen, downloadsOpen, lyricsOpen, stationsOpen).any { it }) playlistsOpen = false
    }
    val togglePlaylists = {
        if (!playlistsOpen) { tuneOpen = false; queueOpen = false; downloadsOpen = false; lyricsOpen = false; stationsOpen = false }
        playlistsOpen = !playlistsOpen
    }
    // On a flat head unit the panels lying down are as wide as the cover column, never over the next tracks
    var wideCover by remember { mutableStateOf<Dp?>(null) }
    // A panel over the cover column: the column fades away under it
    val panelOpen = tuneOpen || queueOpen || downloadsOpen || stationsOpen || playlistsOpen
    // The music left the device (the drive was taken out): the choice goes with it
    LaunchedEffect(deviceMusic?.opensPanel) { if (deviceMusic?.opensPanel != true) downloadsOpen = false }
    BackHandler(onBack = onExit)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        if (!isPlaying && currentTrack == null) onPlayMyVibe()
    }

    val playPause = { if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() }
    val isRadio = currentTrack?.isRadio == true
    val withQueue = underCover == CarUnderCover.QUEUE
    val wideShown = remember { WideShown() }
    // A station has no queue: the panel never stays open over one
    LaunchedEffect(isRadio) { if (isRadio) queueOpen = false }
    // A station leaves «Станции» open only while the radio plays
    LaunchedEffect(isRadio) { if (!isRadio) stationsOpen = false }
    val openStations = { tuneOpen = false; queueOpen = false; downloadsOpen = false; lyricsOpen = false; stationsOpen = true }
    val openQueue = { tuneOpen = false; downloadsOpen = false; lyricsOpen = false; queueOpen = true }
    val openTune = { queueOpen = false; downloadsOpen = false; lyricsOpen = false; tuneOpen = true }
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
        CarSource.LIKED -> if (shuffled) stringResource(R.string.car_liked_shuffled) else stringResource(R.string.library_liked)
        CarSource.RADIO -> if (radio?.onAir != null && radio.onAir in radio.favourites) stringResource(R.string.car_source_radio_favourites)
        else stringResource(R.string.car_source_radio)
        CarSource.HITS -> stringResource(R.string.car_source_hits)
        CarSource.DOWNLOADED -> stringResource(R.string.local_title)
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
        DockItem(CarSource.DOWNLOADED, Icons.Rounded.DownloadDone, stringResource(R.string.car_source_downloaded)) {
            if (deviceMusic?.opensPanel == true) {
                tuneOpen = false
                queueOpen = false
                lyricsOpen = false
                downloadsOpen = true
            } else {
                onPlayDownloaded()
            }
        }
    )

    val latestPrev by androidx.compose.runtime.rememberUpdatedState(onPrevClick)
    val latestNext by androidx.compose.runtime.rememberUpdatedState(onNextClick)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HiFi.Ground)
            .pointerInput(Unit) {
                // One track per swipe: the distance adds up over the whole gesture and counts once the finger
                // lifts (a quick swipe arrives as several moves, each of which used to switch a track)
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        when (carSwipeStep(travelled, CarSwipeThreshold.toPx())) {
                            1 -> latestPrev()
                            -1 -> latestNext()
                        }
                        travelled = 0f
                    },
                    onDragCancel = { travelled = 0f }
                ) { _, dragAmount -> travelled += dragAmount }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { playPause() }) }
    ) {
        // Scale the 1280×720 (landscape) or 600×1200 (portrait: cover, track, controls, the "next" row, sources)
        // layout to this head unit. Standing, the height is what runs out (the Geely's 960×1280): count only what
        // the system bars leave, or the source dock at the bottom gets squeezed
        val bars = WindowInsets.systemBars.asPaddingValues()
        val usableHeight = maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()
        val u = Scale(carScaleOf(maxWidth, maxHeight, usableHeight, isLandscape))
        // A panel lying down covers the cover column only: never wider than the driver's panel leaves (u(652)
        // with u(48) + u(40) + u(40) around it), and on a flat head unit as wide as the cover column itself
        val lyingPanelWidth: (Dp) -> Dp = { width ->
            (wideCover ?: androidx.compose.ui.unit.min(u(500f), width - u(780f))).coerceAtLeast(u(400f))
        }
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
        // The car's type is already sized for a glance from the driver's seat and the whole layout is fitted to
        // the screen: Android's "font size" does not grow it, or labels get cut and the dock leaves the screen
        val systemDensity = LocalDensity.current
        val carDensity = remember(systemDensity) {
            Density(systemDensity.density, systemDensity.fontScale.coerceAtMost(CAR_MAX_FONT_SCALE))
        }
        CompositionLocalProvider(
            LocalDensity provides carDensity,
            LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified),
            // The words, as in the player: on the cover itself, wherever the cover is (lying, flat, standing)
            LocalCoverWords provides CoverWords(
                lyrics = lyrics?.takeIf { lyricsOpen },
                progress = progress,
                onSeek = onSeek,
                onClose = { lyricsOpen = false }
            )
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
        // "Очередь": what plays now and what follows, in rows big enough to hit while parked; a tap plays it
        val queueCaption = listOf(sourceLabel, stringResource(R.string.car_queue_more, russianQuantityString(upcoming.size, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many)))
            .filter { it.isNotBlank() }.joinToString(" · ")
        val queuePanel: @Composable (Modifier) -> Unit = { panelModifier ->
            CarQueuePanel(
                u = u, modifier = panelModifier, caption = queueCaption, current = currentTrack, upcoming = upcoming, loading = upcomingLoading,
                onClick = { queueOpen = false; onUpcomingClick(it) }, onClose = { queueOpen = false }
            )
        }
        // "Скачанные" with the user's own music: in the queue's place
        val downloadsPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            deviceMusic?.let { music ->
                CarDownloadsPanel(
                    u = u, modifier = panelModifier, music = music,
                    onPlayDownloaded = { downloadsOpen = false; onPlayDownloaded() },
                    onPlayDevice = { key -> downloadsOpen = false; onPlayDevice(key) },
                    onClose = { downloadsOpen = false }
                )
            }
        }
        val queueCount = russianQuantityString(upcoming.size, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many)
        // The wave's buttons: in the header on a wide screen, in a row under it on a tall one
        val onWave = active == CarSource.WAVE
        val wavePills: @Composable (Modifier) -> Unit = { pill ->
            val pillHeight = u(56f)
            val pillFont = u.sp(18f)
            // Standing on a phone three pills and more go without icons: each says its whole name
            val pills = (if (trackWaveSeed != null && onWave) 2 else listOf(currentTrack?.let { !it.isRadio && !it.isLocal } == true, onWave).count { it }) +
                listOf(active == CarSource.LIKED, lyrics?.offered == true).count { it }
            val icons = isLandscape || pills < 3 || maxWidth.value / u.factor >= WIDE_STANDING_ROW
            if (trackWaveSeed != null && onWave) {
                WavePill(Icons.Rounded.Waves, stringResource(R.string.wave_back_to_mine), modifier = pill, showIcon = icons, active = false, height = pillHeight, fontSize = pillFont, onClick = onBackToMyWave)
                WavePill(Icons.Rounded.Tune, stringResource(R.string.wave_tune_short), modifier = pill, showIcon = icons, active = !waveTuning.isDefault, height = pillHeight, fontSize = pillFont,
                    onClick = openTune, description = stringResource(R.string.wave_tune_title))
            } else {
                currentTrack?.takeIf { !it.isRadio && !it.isLocal }?.let { track ->
                    WavePill(Icons.Rounded.Waves, stringResource(R.string.wave_by_track), modifier = pill, showIcon = icons, active = false, height = pillHeight, fontSize = pillFont, onClick = { onTrackWave(track) })
                }
                if (onWave) {
                    val summary = waveTuningSummary(waveTuning)
                    WavePill(Icons.Rounded.Tune, summary ?: stringResource(R.string.wave_tune_short), modifier = pill, showIcon = icons, active = summary != null, height = pillHeight, fontSize = pillFont,
                        onClick = openTune, description = summary?.let { stringResource(R.string.wave_tune_cd, it) } ?: stringResource(R.string.wave_tune_title))
                }
            }
            // "Любимое": its own order or shuffled; the current track plays on either way
            if (active == CarSource.LIKED) {
                WavePill(
                    Icons.Rounded.Shuffle, stringResource(if (shuffled) R.string.car_shuffled else R.string.car_shuffle), modifier = pill, showIcon = icons, active = shuffled,
                    height = pillHeight, fontSize = pillFont, onClick = onToggleShuffle
                )
            }
            if (lyrics?.offered == true) {
                WavePill(
                    Icons.Rounded.Lyrics, stringResource(R.string.lyrics_short), modifier = pill, showIcon = icons, active = lyricsOpen, height = pillHeight, fontSize = pillFont,
                    description = stringResource(if (lyricsOpen) R.string.lyrics_hide else R.string.lyrics_show),
                    onClick = {
                        tuneOpen = false; queueOpen = false; downloadsOpen = false
                        lyricsOpen = !lyricsOpen
                    }
                )
            }
        }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Standing there is no room for the caption beside both buttons, the badge and the clock: it goes
            CarHeader(
                u, onExit, showClock, pills = if (isLandscape) ({ wavePills(Modifier) }) else null,
                onOpenSettings = onOpenSettings, onVoiceSearch = onVoiceSearch,
                showCaption = isLandscape || (onOpenSettings == null && onVoiceSearch == null)
            )
            if (!isLandscape) {
                Row(
                    Modifier.fillMaxWidth().padding(start = u(28f), end = u(28f), bottom = u(14f)),
                    horizontalArrangement = Arrangement.spacedBy(u(12f))
                ) { wavePills(Modifier.weight(1f, fill = false)) }
            }
            if (isLandscape) BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(start = u(48f), end = u(40f), bottom = u(28f))
            ) {
                val wide = CarWide.layout(maxWidth.value / u.factor, maxHeight.value / u.factor, layout, wideShown.wide)
                SideEffect { wideShown.wide = wide != null }
                val coverColumn = wide?.let { u(it.cover) }
                SideEffect { wideCover = coverColumn }
                if (wide != null) {
                    // A flat head unit: the player, the next tracks with the sources, the cover as tall as the row
                    val big = Scale(u.factor * wide.driverScale)
                    val driver: @Composable () -> Unit = {
                        DriverPanel(
                            u = big, track = currentTrack, sourceLabel = sourceLabel, isPlaying = isPlaying, isLiked = isLiked,
                            ringMode = ringMode, fraction = fraction, progress = progress, durationMs = durationMs, isRadio = isRadio,
                            onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                            onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                            sources = sources, active = active, onSeek = onSeek,
                            modifier = Modifier.width(big(CarWide.DRIVER_WIDTH)).fillMaxHeight(),
                            showDock = false,
                            onAddToPlaylist = togglePlaylists.takeIf { canAdd }, playlistsOpen = playlistsOpen
                        )
                    }
                    val middle: @Composable () -> Unit = {
                        Column(Modifier.width(u(wide.middle)).fillMaxHeight()) {
                            if (isRadio && radio != null) {
                                CarStationsList(u, radio, Modifier.weight(1f).fillMaxWidth())
                            } else if (isRadio) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                CarNextList(
                                    u = u, modifier = Modifier.weight(1f).fillMaxWidth(), caption = nextCaption, count = queueCount,
                                    current = currentTrack, upcoming = upcoming, loading = upcomingLoading, onClick = onUpcomingClick
                                )
                            }
                            Spacer(Modifier.height(u(14f)))
                            Dock(u, sources, active, compact = false, narrow = true)
                        }
                    }
                    val cover: @Composable () -> Unit = {
                        val farAlpha by androidx.compose.animation.core.animateFloatAsState(if (panelOpen && !queueOpen) 0f else 1f, HiFiMotion.standard(), label = "car_far_wide")
                        // Faded under a panel: out of reach for touch and for the screen reader too
                        CarCover(u, currentTrack, Modifier.size(u(wide.cover)).graphicsLayer { alpha = farAlpha }.hiddenUnder(panelOpen && !queueOpen))
                    }
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(u(CarWide.GAP))) {
                        if (steeringWheelOnLeft) { driver(); middle(); cover() } else { cover(); middle(); driver() }
                    }
                } else Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(u(40f))
                ) {
                    val driver: @Composable () -> Unit = {
                        DriverPanel(
                            u = u, track = currentTrack, sourceLabel = sourceLabel, isPlaying = isPlaying, isLiked = isLiked,
                            ringMode = ringMode, fraction = fraction, progress = progress, durationMs = durationMs, isRadio = isRadio,
                            onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                            onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                            sources = sources, active = active, onSeek = onSeek,
                            modifier = Modifier.width(u(652f)).fillMaxHeight(),
                            onAddToPlaylist = togglePlaylists.takeIf { canAdd }, playlistsOpen = playlistsOpen
                        )
                    }
                    // The cover alone, and under it the covers of what comes next: nothing lies on the cover
                    val far: @Composable () -> Unit = {
                        // "Настроить волну" opens over this column: it fades away, so nothing peeks from under the panel
                        val farAlpha by androidx.compose.animation.core.animateFloatAsState(if (panelOpen) 0f else 1f, HiFiMotion.standard(), label = "car_far")
                        // Faded under a panel: its «Очередь» or «Станции» out of reach for touch and the screen reader
                        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = farAlpha }.hiddenUnder(panelOpen), contentAlignment = Alignment.Center) {
                            if (isRadio && radio != null) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(u(18f), Alignment.CenterVertically)
                                ) {
                                    CarCover(
                                        u, currentTrack,
                                        Modifier
                                            .weight(1f, fill = false)
                                            .sizeIn(maxWidth = u(500f), maxHeight = u(500f))
                                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    )
                                    StationsPill(u, radio.favourites.size, onClick = openStations)
                                }
                            } else if (isRadio) {
                                CarCover(u, currentTrack, Modifier.size(u(500f)))
                            } else if (withQueue) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(u(18f), Alignment.CenterVertically)
                                ) {
                                    // As big as the column allows (500), less only when the button would not fit
                                    CarCover(
                                        u, currentTrack,
                                        Modifier
                                            .weight(1f, fill = false)
                                            .sizeIn(maxWidth = u(500f), maxHeight = u(500f))
                                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    )
                                    QueuePill(u, queueCount, onClick = openQueue)
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(u(18f), Alignment.CenterVertically)
                                ) {
                                    // Gets what the row leaves, never more than 400: the row is measured first
                                    CarCover(
                                        u, currentTrack,
                                        Modifier
                                            .weight(1f, fill = false)
                                            .sizeIn(maxWidth = u(400f), maxHeight = u(400f))
                                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    )
                                    // Four small covers exactly as wide as the cover at its full size
                                    NextRow(u, nextCaption, upcoming, upcomingLoading, maxCard = u(88f), gap = u(16f), maxWidth = u(400f), onClick = onUpcomingClick, onCaptionClick = openQueue)
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
                    Crossfade(
                        targetState = when {
                            tuneOpen -> 1
                            queueOpen -> 2
                            downloadsOpen -> 3
                            stationsOpen -> 5
                            playlistsOpen -> 6
                            else -> 0
                        },
                        modifier = Modifier.widthIn(max = u(640f)).fillMaxWidth(),
                        label = "car_tune"
                    ) { open ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            when (open) {
                                1 -> tunePanel(Modifier.fillMaxWidth(), true)
                                2 -> queuePanel(Modifier.fillMaxWidth().height(u(400f)))
                                // Taller than the queue: two big rows and the folders under them
                                3 -> downloadsPanel(Modifier.fillMaxWidth().height(u(470f)))
                                5 -> radio?.let { CarStationsPanel(u, it, Modifier.fillMaxWidth().height(u(470f)), onClose = { stationsOpen = false }) }
                                6 -> if (playlists != null && currentTrack != null) {
                                    CarPlaylistsPanel(u, playlists, currentTrack, Modifier.fillMaxWidth().height(u(470f)), onClose = { playlistsOpen = false })
                                }
                                else -> CarCover(u, currentTrack, Modifier.size(u(if (isRadio || withQueue) 400f else 310f)))
                            }
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
                        onAddToPlaylist = togglePlaylists.takeIf { canAdd }, playlistsOpen = playlistsOpen,
                        beforeDock = when {
                            isRadio && radio != null && !stationsOpen -> { { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StationsPill(u, radio.favourites.size, onClick = openStations) } } }
                            isRadio || tuneOpen || downloadsOpen || playlistsOpen -> null
                            withQueue -> { { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QueuePill(u, queueCount, onClick = openQueue) } } }
                            else -> { { NextRow(u, nextCaption, upcoming, upcomingLoading, maxCard = u(124f), gap = u(16f), onClick = onUpcomingClick, onCaptionClick = openQueue) } }
                        }
                    )
                }
            }
        }
        // "Очередь" lying down: over the cover column too, the whole height of it
        if (queueOpen && isLandscape) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val panelWidth = lyingPanelWidth(maxWidth)
                queuePanel(
                    Modifier
                        .align(if (steeringWheelOnLeft) Alignment.TopEnd else Alignment.TopStart)
                        .padding(top = u(96f), start = u(48f), end = u(40f), bottom = u(28f))
                        .width(panelWidth)
                        .fillMaxHeight()
                )
            }
        }
        // "Скачанные" lying down: over the cover column too, the whole height of it
        if (downloadsOpen && isLandscape) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val panelWidth = lyingPanelWidth(maxWidth)
                downloadsPanel(
                    Modifier
                        .align(if (steeringWheelOnLeft) Alignment.TopEnd else Alignment.TopStart)
                        .padding(top = u(96f), start = u(48f), end = u(40f), bottom = u(28f))
                        .width(panelWidth)
                        .fillMaxHeight()
                )
            }
        }
        // «В плейлист» lying down: over the cover column, the whole height of it
        val lyingPlaylists = playlists?.takeIf { playlistsOpen && isLandscape }
        if (lyingPlaylists != null && currentTrack != null) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val panelWidth = lyingPanelWidth(maxWidth)
                CarPlaylistsPanel(
                    u, lyingPlaylists, currentTrack,
                    Modifier
                        .align(if (steeringWheelOnLeft) Alignment.TopEnd else Alignment.TopStart)
                        .padding(top = u(96f), start = u(48f), end = u(40f), bottom = u(28f))
                        .width(panelWidth)
                        .fillMaxHeight(),
                    onClose = { playlistsOpen = false }
                )
            }
        }
        // «Станции» lying down: over the cover column, the whole height of it
        if (stationsOpen && isLandscape && radio != null) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val panelWidth = lyingPanelWidth(maxWidth)
                CarStationsPanel(
                    u, radio,
                    Modifier
                        .align(if (steeringWheelOnLeft) Alignment.TopEnd else Alignment.TopStart)
                        .padding(top = u(96f), start = u(48f), end = u(40f), bottom = u(28f))
                        .width(panelWidth)
                        .fillMaxHeight(),
                    onClose = { stationsOpen = false }
                )
            }
        }
        // "Настроить волну" lying down: over the cover column, so the controls and the sources stay free
        if (tuneOpen && isLandscape) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // Over the cover column only: never wider than what the driver's panel leaves (it is u(652) wide
            // with u(48) + u(40) + u(40) around it), so the controls and the sources stay uncovered
            val panelWidth = lyingPanelWidth(maxWidth)
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
/**
 * Whether «В авто» can have three columns on a screen of [width] × [height] dp lying (the settings offer the
 * choice only then): the header and the margins taken off as on the car screen.
 */
fun carWidePossible(width: Dp, height: Dp): Boolean {
    val s = carScaleOf(width, height, height, isLandscape = true)
    return CarWide.possible(width.value / s - CAR_SIDE_MARGINS, height.value / s - CAR_HEADER_AND_BOTTOM)
}

/** Lying: 48 + 40 at the sides; the header (22 + 58 + 10) and 28 at the bottom. */
private const val CAR_SIDE_MARGINS = 88f
private const val CAR_HEADER_AND_BOTTOM = 118f

/** The cover column under an open panel: no semantics, no touches (the panel lies over it, faded out). */
private fun Modifier.hiddenUnder(panel: Boolean): Modifier =
    if (panel) clearAndSetSemantics { }.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }
    else this

/** What the screen showed last, wide or not: «Авто» holds it near the edge ([CarWide.HOLD]). */
private class WideShown(var wide: Boolean? = null)

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
private fun CarHeader(
    u: Scale,
    onExit: () -> Unit,
    showClock: Boolean,
    pills: (@Composable () -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onVoiceSearch: (() -> Unit)? = null,
    showCaption: Boolean = true
) {
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
        // The same settings button as on "Моя музыка", as big as "back" so it is hit without aiming
        if (onOpenSettings != null) {
            Spacer(Modifier.width(u(16f)))
            HiFiWellButton(
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.library_settings),
                size = u(56f)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(u(26f)))
            }
        }
        // The microphone beside it, the same well: one tap, then say what to play
        if (onVoiceSearch != null) {
            Spacer(Modifier.width(u(16f)))
            HiFiWellButton(
                onClick = onVoiceSearch,
                contentDescription = stringResource(R.string.voice_search),
                size = u(56f)
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(u(26f)))
            }
        }
        Spacer(Modifier.width(u(16f)))
        if (showCaption) {
            Text(stringResource(R.string.car_label), color = Color.White.copy(alpha = 0.66f), fontSize = u.sp(14f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em)
            Spacer(Modifier.width(u(14f)))
        }
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
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

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
    beforeDock: (@Composable () -> Unit)? = null,
    /** False on a flat head unit: the sources live beside, and the gauge takes the height they leave. */
    showDock: Boolean = true,
    /** «В плейлист» beside the title; null — not for this track. */
    onAddToPlaylist: (() -> Unit)? = null,
    playlistsOpen: Boolean = false
) {
    Column(modifier) {
        if (sourceLabel.isNotEmpty()) {
            // One line always: a long track name in "Волна по треку «…»" must not push the title down
            Text(sourceLabel.uppercase(), color = Color.White.copy(alpha = 0.72f), fontSize = u.sp(14f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = track,
            contentKey = { it?.idString },
            transitionSpec = { (fadeIn(HiFiMotion.large()) + slideInVertically(HiFiMotion.large()) { it / 10 }) togetherWith fadeOut(HiFiMotion.quick()) },
            label = "car_title",
            modifier = Modifier.weight(1f)
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
                val aiMark = rememberAiMark(shown)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (shown?.isRadio == true) stringResource(R.string.mini_player_live) else shown?.artists?.joinToString { it.name ?: "" }.orEmpty(),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = u.sp(26f),
                        lineHeight = u.sp(32f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (aiMark != null) AiChip(Modifier.padding(start = u(12f)), fontSize = u.sp(15f), height = u(28f), horizontal = u(10f))
                }
            }
        }
        if (onAddToPlaylist != null) {
            Spacer(Modifier.width(u(16f)))
            AddToPlaylistButton(u(if (compactDock) 64f else 72f), open = playlistsOpen, onClick = onAddToPlaylist)
        }
        }

        if (!showDock) Spacer(Modifier.weight(1f))
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
            // The user's own file: nothing to like or dislike, the knobs keep their places
            val canReact = track?.isLocal != true
            if (canReact) {
                GlassRound(u(sideSize), stringResource(R.string.cd_ne_nravitsya), onDislike) {
                    Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(u(sideIcon)))
                }
            } else {
                Spacer(Modifier.size(u(sideSize)))
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
            if (canReact) {
                GlassRound(u(sideSize), stringResource(R.string.cd_nravitsya), onLike) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(u(sideIcon))
                    )
                }
            } else {
                Spacer(Modifier.size(u(sideSize)))
            }
        }
        // A timeline without digits under the gauge; radio is live and shows the on-air line
        if (!showDock) Spacer(Modifier.weight(1f))
        if (isRadio) CarLiveLine(u, Modifier.padding(top = u(10f)))
        else CarTimeline(u, progress, durationMs, isPlaying, onSeek, Modifier.padding(top = u(10f)))
        if (!showDock) {
            // Level with the middle of the sources beside
            Spacer(Modifier.height(u(30f)))
            return@Column
        }
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
private fun Dock(u: Scale, items: List<DockItem>, active: CarSource?, compact: Boolean, narrow: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u(if (narrow) 8f else 12f))) {
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
                    fontSize = u.sp(if (narrow) 14f else 16f),
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
        // The words over the darkened cover, as the player shows them; a tap on them closes them
        val words = LocalCoverWords.current
        AnimatedVisibility(visible = words.lyrics != null, enter = fadeIn(HiFiMotion.quick()), exit = fadeOut(HiFiMotion.quick())) {
            // Kept while it fades out, after the words were closed (a plain holder: no state written while composing)
            val kept = remember { arrayOfNulls<TrackLyrics>(1) }
            words.lyrics?.let { kept[0] = it }
            kept[0]?.let {
                LyricsPanel(
                    lyrics = it,
                    onClose = words.onClose,
                    progress = words.progress,
                    onSeek = words.onSeek,
                    fontSize = u.sp(26f),
                    lineHeight = u.sp(38f),
                    padding = u(28f),
                    veil = Color.Black.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/** The words a car cover shows over itself, and what closes or seeks them; none — the cover alone. */
internal class CoverWords(
    val lyrics: TrackLyrics?,
    val progress: () -> Long = { 0L },
    val onSeek: (Long) -> Unit = {},
    val onClose: () -> Unit
)

internal val LocalCoverWords = compositionLocalOf { CoverWords(null) {} }

/** Standing, a screen at least this wide (in the car's design units) keeps the icons on a row of three pills and more. */
private const val WIDE_STANDING_ROW = 820f

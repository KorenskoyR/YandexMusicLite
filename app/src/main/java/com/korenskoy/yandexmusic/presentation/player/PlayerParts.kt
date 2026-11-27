package com.korenskoy.yandexmusic.presentation.player

import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import com.korenskoy.yandexmusic.domain.model.RepeatMode
import com.korenskoy.yandexmusic.presentation.hifi.showsExplicit
import com.korenskoy.yandexmusic.presentation.hifi.TrackHeadline
import com.korenskoy.yandexmusic.presentation.hifi.toArtist
import com.korenskoy.yandexmusic.presentation.hifi.toAlbum
import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.ui.unit.TextUnit
import com.korenskoy.yandexmusic.presentation.hifi.rememberAiMark
import com.korenskoy.yandexmusic.presentation.hifi.AiChip
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.korenskoy.yandexmusic.presentation.hifi.AppScale
import androidx.compose.material.icons.rounded.Waves
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLinkBadge
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.utils.toCoverUrl
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.SleepTimerState
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTimeline
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.TimelineMath
import com.korenskoy.yandexmusic.presentation.player.components.RadioCoverCard
import com.korenskoy.yandexmusic.utils.LocalStreamQuality

internal val CoverShape = RoundedCornerShape(24.dp)
private val PlateFill = Color(0x6B080A14)

/** A new track slides a quarter of the way in from the side it came from and fades in; instant in lite mode. */
internal fun trackChange(direction: Int, lite: Boolean): ContentTransform =
    if (lite) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (slideInHorizontally(HiFiMotion.large()) { it * direction / 4 } + fadeIn(HiFiMotion.standard())) togetherWith
            (slideOutHorizontally(HiFiMotion.standard()) { -it * direction / 4 } + fadeOut(HiFiMotion.quick()))
    }

// --- Header -----------------------------------------------------------------------------------------

@Composable
internal fun PlayerHeader(
    sourceLabel: String,
    isRadio: Boolean,
    sleepTimer: SleepTimerState?,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onSleepAtEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    val palette = HiFi.palette
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // On a narrow phone (360 dp) the balancing space would leave the caption too little room for both words
    val balanced = maxWidth >= 350.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassCircleButton(onClick = onCollapse, contentDescription = stringResource(R.string.cd_svernut_pleer), size = 44.dp) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(28.dp))
        }
        // Balances the two buttons on the right, so the caption stays in the middle
        if (balanced) Spacer(Modifier.width(44.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            // A narrow window (a split screen, a player lying in 480 dp): the caption shrinks rather than gets cut
            androidx.compose.foundation.text.BasicText(
                text = stringResource(R.string.full_player_seychas_igraet),
                style = androidx.compose.ui.text.TextStyle(
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.14.em
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp)
            )
            if (sourceLabel.isNotEmpty()) {
                Text(
                    text = sourceLabel,
                    color = palette.textPrimary.copy(alpha = 0.88f),
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
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(21.dp))
            }
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
    val palette = HiFi.palette
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
                tint = palette.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        val appDensity = LocalDensity.current
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = HiFiDialogDefaults.Container,
            border = BorderStroke(1.dp, HiFiDialogDefaults.Rim)
        ) {
        AppScale(appDensity) {
            val colors = MenuDefaults.itemColors(textColor = palette.textPrimary)
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
                    text = { Text(stringResource(R.string.sleep_off), color = palette.danger) },
                    onClick = { expanded = false; onCancel() },
                    colors = colors
                )
            }
        }
}
    }
}

/** "Сон через 12 мин" / "Сон в конце трека"; refreshes every 15 s while a timer runs. */
@Composable
internal fun SleepTimerCaption(state: SleepTimerState?) {
    if (state == null) return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
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
        color = HiFi.palette.textPrimary.copy(alpha = 0.80f),
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
internal fun PlayerCover(
    track: TrackDetailsDto,
    direction: Int,
    isPlaying: Boolean,
    coverModifier: Modifier,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
    /** Drawn over the cover inside its frame: the words of the track. */
    overlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val lite = LocalHiFiLite.current
    // Subtle "breathing" while music plays; read in the layer, never recomposes
    val breath: State<Float>? = if (isPlaying && !lite && !track.isRadio) {
        rememberInfiniteTransition(label = "cover_breath").animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), AnimRepeatMode.Reverse),
            label = "cover_scale"
        )
    } else null
    var swipe by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val shownSwipe by animateFloatAsState(swipe, spring(stiffness = Spring.StiffnessMedium), label = "swipe_offset")
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // As big as its place: the standing column and the lying share already size it to the screen (a phone's
        // 440 was a ceiling on a tablet too)
        val side = min(min(maxWidth, maxHeight), 720.dp)
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
                    coverUrl = track.coverUri.toCoverUrl(CoverSize.Large) ?: "",
                    stationName = track.title.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(coverModifier)
                        .hifiSoftShadow(CoverShape, 30.dp, if (HiFi.palette.isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF11141C).copy(alpha = 0.22f))
                        .clip(CoverShape)
                        .background(HiFi.palette.placeholder)
                ) {
                    // A new track's cover slides in from the side it came from, inside the same frame
                    AnimatedContent(
                        targetState = track,
                        contentKey = { it.idString },
                        transitionSpec = { trackChange(direction, lite) },
                        label = "cover_change"
                    ) { shown ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).cover(shown.coverUri, CoverSize.Large).crossfade(true).build(),
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
                    }
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
                    overlay()
                }
            }
            // A station's cover has "В эфире" in this corner
            HiFiLinkBadge(Modifier.align(if (track.isRadio) Alignment.TopEnd else Alignment.TopStart).padding(14.dp), height = 30.dp)
        }
    }
}

// --- Title, quality, timeline ------------------------------------------------------------------------

@Composable
internal fun TitleRow(
    track: TrackDetailsDto,
    direction: Int,
    compact: Boolean,
    onAddToPlaylist: () -> Unit,
    onTrackWave: (() -> Unit)? = null,
    /** The track on the device; null: no download button (a station). */
    offline: TrackOfflineState? = null,
    onDownloadClick: () -> Unit = {},
    /** The words of the track: the "Текст" button shows when Yandex has them. */
    lyrics: TrackLyrics? = null,
    lyricsOpen: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onCycleRepeat: () -> Unit = {},
    shuffled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    showRepeatAndShuffle: Boolean = false,
    strip: Boolean = false,
    onLyrics: () -> Unit = {}
) {
    val palette = HiFi.palette
    val lite = LocalHiFiLite.current
    val showOnlineActions = !track.isRadio && !track.isLocal
    val showLyrics = showOnlineActions && lyrics?.offered == true
    val showDownload = showOnlineActions && offline != null
    val showTrackWave = showOnlineActions && onTrackWave != null
    val actionCount = (if (showLyrics) 1 else 0) +
        (if (showDownload) 1 else 0) +
        (if (showOnlineActions) 1 else 0) +
        (if (showTrackWave) 1 else 0) +
        (if (showRepeatAndShuffle) 2 else 0)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < NARROW_TITLE_ROW_DP.dp
        val button = when {
            strip -> 34.dp
            narrow -> 40.dp
            else -> 44.dp
        }
        val gap = if (strip) 6.dp else if (narrow) 8.dp else 10.dp
        val stackActionsBelow = !strip && actionCount > 3 && maxWidth < 520.dp

        val titleColumn: @Composable (Modifier) -> Unit = { mod ->
            AnimatedContent(
                targetState = track,
                contentKey = { it.idString },
                transitionSpec = { trackChange(direction, lite) },
                label = "title_change",
                modifier = mod
            ) { t ->
                Column(Modifier.fillMaxWidth()) {
                    TrackHeadline(
                        title = t.title ?: stringResource(R.string.bez_nazvaniya),
                        fontSize = if (compact) 22.sp else 26.sp,
                        explicit = t.showsExplicit,
                        color = palette.textPrimary,
                        modifier = Modifier.toAlbum(t)
                    )
                    val aiMark = rememberAiMark(t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t.artists.joinToString(", ") { it.name ?: "" }.ifEmpty { stringResource(if (t.isRadio) R.string.car_source_radio else R.string.player_unknown_artist) },
                            color = palette.textSecondary,
                            fontSize = if (compact) 15.sp else 17.sp,
                            lineHeight = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).toArtist(t)
                        )
                        if (aiMark != null) AiChip(Modifier.padding(start = 8.dp), fontSize = 11.sp, height = 20.dp, horizontal = 7.dp)
                    }
                }
            }
        }

        val actionButtons: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                if (showLyrics) LyricsButton(open = lyricsOpen, size = button, onClick = onLyrics)
                offline?.takeIf { showOnlineActions }?.let { TrackDownloadButton(it, onDownloadClick, size = button) }
                if (showOnlineActions) {
                    GlassCircleButton(
                        onClick = onAddToPlaylist,
                        contentDescription = stringResource(R.string.track_context_menu_dobavit_v_pleylist),
                        size = button
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                    }
                    onTrackWave?.let { waveClick ->
                        GlassCircleButton(onClick = waveClick, contentDescription = stringResource(R.string.wave_by_track_cd), size = button) {
                            Icon(Icons.Rounded.Waves, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                if (showRepeatAndShuffle) {
                    RepeatActionButton(repeatMode = repeatMode, onClick = onCycleRepeat, size = button)
                    ShuffleActionButton(shuffled = shuffled, onClick = onToggleShuffle, size = button)
                }
            }
        }

        if (stackActionsBelow && actionCount > 0) {
            Column(Modifier.fillMaxWidth()) {
                titleColumn(Modifier.fillMaxWidth())
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                actionButtons()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 12.dp)) {
                titleColumn(Modifier.weight(1f))
                if (actionCount > 0) actionButtons()
            }
        }
    }
}

@Composable
private fun RepeatActionButton(repeatMode: RepeatMode, onClick: () -> Unit, size: Dp = 44.dp) {
    val palette = HiFi.palette
    val active = repeatMode != RepeatMode.OFF
    GlassCircleButton(
        onClick = onClick,
        contentDescription = stringResource(R.string.cd_repeat),
        size = size,
        fill = if (active) palette.textPrimary.copy(alpha = 0.92f) else palette.glassFill,
        border = if (active) palette.textPrimary.copy(alpha = 0.92f) else palette.glassBorder
    ) {
        Icon(
            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = null,
            tint = if (active) palette.ground else palette.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ShuffleActionButton(shuffled: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    val palette = HiFi.palette
    GlassCircleButton(
        onClick = onClick,
        contentDescription = stringResource(R.string.cd_shuffle),
        size = size,
        fill = if (shuffled) palette.textPrimary.copy(alpha = 0.92f) else palette.glassFill,
        border = if (shuffled) palette.textPrimary.copy(alpha = 0.92f) else palette.glassBorder
    ) {
        Icon(
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = null,
            tint = if (shuffled) palette.ground else palette.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** "Текст": glass, lit while the words are shown over the cover. */
@Composable
internal fun LyricsButton(open: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    val palette = HiFi.palette
    GlassCircleButton(
        onClick = onClick,
        contentDescription = stringResource(if (open) R.string.lyrics_hide else R.string.lyrics_show),
        size = size,
        fill = if (open) palette.textPrimary.copy(alpha = 0.92f) else palette.glassFill,
        border = if (open) palette.textPrimary.copy(alpha = 0.92f) else palette.glassBorder
    ) {
        Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = if (open) palette.ground else palette.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Narrower than this, the buttons beside the player's title are smaller. */
private const val NARROW_TITLE_ROW_DP = 400

/**
 * "Скачать" for the playing track: an arrow, then a ring that turns while it downloads (a tap stops it), then
 * the mark every downloaded track has. Glass like its neighbours; the ring stands still in the light mode.
 */
@Composable
internal fun TrackDownloadButton(state: TrackOfflineState, onClick: () -> Unit, size: Dp = 44.dp) {
    val palette = HiFi.palette
    val description = stringResource(
        when (state) {
            TrackOfflineState.NONE -> R.string.track_download
            TrackOfflineState.DOWNLOADING -> R.string.track_download_stop
            TrackOfflineState.DONE -> R.string.track_on_device
        }
    )
    GlassCircleButton(onClick = onClick, contentDescription = description, size = size) {
        Crossfade(targetState = state, animationSpec = HiFiMotion.quick(), label = "download_state") { shown ->
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                when (shown) {
                    TrackOfflineState.NONE -> Icon(Icons.Rounded.Download, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                    TrackOfflineState.DONE -> Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                    TrackOfflineState.DOWNLOADING -> {
                        val lite = LocalHiFiLite.current
                        val turn = if (lite) null else rememberInfiniteTransition(label = "download_ring")
                            .animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "download_turn")
                        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                            val stroke = 2.5.dp.toPx()
                            drawArc(palette.textPrimary.copy(alpha = 0.16f), 0f, 360f, false, style = Stroke(stroke))
                            drawArc(palette.textPrimary, (turn?.value ?: 0f) - 90f, 110f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.5.dp)).background(palette.textPrimary))
                    }
                }
            }
        }
    }
}

/** The real stream quality in a recessed chip; a tap explains what plays and what the server offered. */
@Composable
internal fun QualityChip(isRadio: Boolean, onClick: () -> Unit) {
    val palette = HiFi.palette
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
            color = palette.textPrimary.copy(alpha = 0.80f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.12.em,
            maxLines = 1
        )
    }
}

@Composable
internal fun PlayerTimeline(progress: () -> Long, durationMs: Long, isPlaying: Boolean, onSeek: (Long) -> Unit, height: Float = 44f) {
    var scrub by remember(durationMs) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth()) {
        HiFiTimeline(progress, durationMs, isPlaying, onSeek, height = height, onScrub = { scrub = it })
        TimeReadout(progress, durationMs, scrub)
    }
}

/** "1:22 … −3:24": recomposes once a second (or with the finger), not with every progress tick. */
@Composable
internal fun TimeReadout(
    progress: () -> Long,
    durationMs: Long,
    scrub: Float?,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 16.sp,
    lift: Dp = 4.dp
) {
    val palette = HiFi.palette
    val currentProgress by rememberUpdatedState(progress)
    val second by remember(durationMs, scrub) {
        derivedStateOf { (scrub?.let { TimelineMath.seekPosition(it, durationMs) } ?: currentProgress()) / 1000L }
    }
    val (elapsed, remaining) = TimelineMath.readout(second * 1000L, durationMs)
    val color by animateColorAsState(
        if (scrub != null) palette.textPrimary.copy(alpha = 0.95f) else palette.textSecondary,
        HiFiMotion.quick(),
        label = "time_color"
    )
    Row(Modifier.fillMaxWidth().offset(y = -lift), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(elapsed, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
        Text(remaining, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

// --- "Далее" plate -----------------------------------------------------------------------------------

/** What plays next, on a glass plate; information only — a tap opens the queue. */
@Composable
internal fun NextPlate(next: TrackDetailsDto, onClick: () -> Unit) {
    val context = LocalContext.current
    val palette = HiFi.palette
    val plateFill = if (palette.isDark) PlateFill else palette.cardFill
    val highlightColors = if (palette.isDark) {
        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))
    } else {
        listOf(palette.glassBorder, palette.glassBorder, palette.glassBorder)
    }
    val title = next.title ?: stringResource(R.string.bez_nazvaniya)
    val artist = next.artists.joinToString(", ") { it.name ?: "" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(plateFill)
            .drawWithCache {
                val r = CornerRadius(20.dp.toPx())
                val highlight = Brush.verticalGradient(0f to highlightColors[0], 0.08f to highlightColors[1], 1f to highlightColors[2])
                onDrawBehind { drawRoundRect(highlight, cornerRadius = r, style = Stroke(1.dp.toPx())) }
            }
            .clickable(onClickLabel = stringResource(R.string.cd_ochered), role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).cover(next.coverUri, CoverSize.Small).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.placeholder)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.full_player_dalee),
                color = palette.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.18.em
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.textPrimary)) { append(title) }
                    if (artist.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = palette.textSecondary)) { append(" · $artist") }
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
            tint = palette.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

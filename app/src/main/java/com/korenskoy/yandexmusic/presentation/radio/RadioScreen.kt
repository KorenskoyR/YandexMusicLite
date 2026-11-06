package com.korenskoy.yandexmusic.presentation.radio

import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import com.korenskoy.yandexmusic.presentation.hifi.rememberHeaderCollapse
import com.korenskoy.yandexmusic.presentation.hifi.fadeUnderHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiCollapsingTitle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiFilterPill
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLargeTitle
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.PlayPauseGlyph
import com.korenskoy.yandexmusic.presentation.hifi.hifiGrain
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.utils.browserUserAgent
import com.korenskoy.yandexmusic.utils.russianQuantityString
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The FM scale of the dial; pure so the station picking is unit-tested. */
object RadioDial {
    const val MIN_MHZ = 88f
    const val MAX_MHZ = 108f

    /** "106.2 FM", "106,2" → 106.2; null for online-only stations. */
    fun parseMhz(frequency: String): Float? =
        Regex("""(\d{2,3})[.,](\d)""").find(frequency)?.let { "${it.groupValues[1]}.${it.groupValues[2]}".toFloatOrNull() }
            ?.takeIf { it in MIN_MHZ..MAX_MHZ }

    fun fraction(mhz: Float): Float = ((mhz - MIN_MHZ) / (MAX_MHZ - MIN_MHZ)).coerceIn(0f, 1f)

    /** The tuned station: the one closest to [fraction] on the scale. */
    fun nearest(stations: List<RadioStation>, fraction: Float): RadioStation? =
        stations.mapNotNull { s -> parseMhz(s.frequency)?.let { s to fraction(it) } }
            .minByOrNull { (_, f) -> abs(f - fraction) }?.first
}

private val RadioFadeStart = 72.dp
private val RadioFadeLength = 24.dp
/** With the title small the list comes up this much closer. */
private val RadioFadeRise = 10.dp
private val RadioPinnedGenresTop = 62.dp
private val RadioGenresPadding = 18.dp

/**
 * FM radio as a real receiver: the station on air on a card in its brand colors with a metal knob and
 * a tuning window (88–108 MHz) — swipe the window to tune to a neighbouring station, each station
 * ticks under the finger. Genres, favourite tiles and a compact list of all stations below.
 */
@Composable
fun RadioScreen(
    stations: List<RadioStation>,
    allStations: List<RadioStation>,
    selectedCategory: RadioCategory,
    onCategorySelect: (RadioCategory) -> Unit,
    currentStationId: String?,
    isPlaying: Boolean,
    favoriteStationIds: Set<String>,
    onStationClick: (RadioStation) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    hasActiveTrack: Boolean = false
) {
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val contentStart = 16.dp
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = if (isLandscape) DockClearance + navigationBar else (if (hasActiveTrack) 168.dp else 100.dp)
    val current = allStations.firstOrNull { it.id == currentStationId }
    val hero = current ?: allStations.firstOrNull { it.id in favoriteStationIds } ?: allStations.firstOrNull()
    val favorites = remember(allStations, favoriteStationIds) { allStations.filter { it.id in favoriteStationIds } }

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val list = rememberLazyListState()
    val collapseState = rememberHeaderCollapse(list)
    val pinTopPx = with(density) { (statusBar + RadioPinnedGenresTop).toPx() }
    val genresTopInsidePx = with(density) { RadioGenresPadding.toPx() }
    // The genres stop under the header once the list has carried them up to it
    val genresPinned by remember(list, pinTopPx, genresTopInsidePx) {
        derivedStateOf {
            val info = list.layoutInfo
            val genres = info.visibleItemsInfo.firstOrNull { it.key == "genres" }
            if (genres != null) genres.offset - info.viewportStartOffset + genresTopInsidePx <= pinTopPx
            else list.firstVisibleItemIndex >= 1
        }
    }
    val fadeStartPx = {
        with(density) {
            val start = if (genresPinned) RadioPinnedGenresTop + 36.dp + 2.dp else RadioFadeStart - RadioFadeRise * collapseState.value
            (statusBar + start).toPx()
        }
    }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().fadeUnderHeader(fadeStartPx, with(density) { RadioFadeLength.toPx() }),
        contentPadding = PaddingValues(top = statusBar + RadioFadeStart + RadioFadeLength + 2.dp, bottom = contentBottom)
    ) {
        if (hero != null) {
            item(key = "hero") {
                OnAirCard(
                    station = hero,
                    allStations = allStations,
                    live = current != null && isPlaying,
                    isFavorite = hero.id in favoriteStationIds,
                    onKnob = { if (current?.id == hero.id) onPlayPause() else onStationClick(hero) },
                    onToggleFavorite = { onToggleFavorite(hero.id) },
                    onTune = onStationClick,
                    modifier = Modifier
                        .padding(start = contentStart, end = 16.dp)
                        .widthIn(max = 720.dp)
                )
            }
        }
        item(key = "genres") {
            GenreRow(selectedCategory, onCategorySelect, contentStart, Modifier.padding(top = RadioGenresPadding))
        }
        if (favorites.isNotEmpty() && selectedCategory == RadioCategory.ALL) {
            item(key = "fav_title") {
                HiFiSectionHeader(stringResource(R.string.radio_favorites), Modifier.padding(start = contentStart + 4.dp, end = 20.dp, top = 22.dp, bottom = 12.dp))
            }
            item(key = "favorites") {
                LazyRow(contentPadding = PaddingValues(start = contentStart, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(favorites, key = { "fav_" + it.id }) { station ->
                        Column(
                            Modifier.width(80.dp).hifiPress({ onStationClick(station) }),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StationLogo(station, 76.dp, 20.dp, Modifier.hifiSoftShadow(RoundedCornerShape(20.dp), 16.dp))
                            Spacer(Modifier.height(7.dp))
                            Text(station.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item(key = "all_title") {
            HiFiSectionHeader(stringResource(R.string.radio_all_stations), Modifier.padding(start = contentStart + 4.dp, end = 20.dp, top = 22.dp, bottom = 4.dp))
        }
        items(stations, key = { it.id }, contentType = { "station" }) { station ->
            StationRow(
                station = station,
                isCurrent = station.id == currentStationId,
                isPlaying = isPlaying && station.id == currentStationId,
                isFavorite = station.id in favoriteStationIds,
                onClick = { onStationClick(station) },
                onToggleFavorite = { onToggleFavorite(station.id) },
                modifier = Modifier.padding(start = contentStart + 4.dp, end = 12.dp)
            )
        }
    }

    HiFiCollapsingTitle(
        stringResource(R.string.radio_title),
        collapse = { collapseState.value },
        modifier = Modifier
            .padding(start = contentStart + 4.dp, end = 76.dp, top = statusBar + 14.dp)
            .graphicsLayer { translationY = -collapseState.value * 6.dp.toPx() }
    )
    Text(
        russianQuantityString(allStations.size, R.string.stations_count_one, R.string.stations_count_few, R.string.stations_count_many),
        style = HiFiType.Caption.copy(fontSize = 15.sp),
        modifier = Modifier
            .padding(start = contentStart + 4.dp, top = statusBar + 50.dp)
            .graphicsLayer {
                alpha = if (current != null) (1f - collapseState.value * 1.8f).coerceAtLeast(0f) else 1f
                translationY = -collapseState.value * 12.dp.toPx()
            }
    )
    // Down in the list the station on air stays in reach: its name and a small knob in the header
    if (current != null) {
        Row(
            Modifier
                .padding(start = contentStart + 4.dp, end = 76.dp, top = statusBar + 50.dp)
                .height(20.dp)
                .graphicsLayer {
                    alpha = (collapseState.value * 1.8f - 0.8f).coerceIn(0f, 1f)
                    translationY = -collapseState.value * 12.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlaying) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(HiFi.Live))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                stringResource(R.string.radio_on_air_station, current.name),
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val knobShown by remember(collapseState) { derivedStateOf { collapseState.value > 0.45f } }
        AnimatedVisibility(
            visible = knobShown,
            enter = fadeIn(HiFiMotion.standard()),
            exit = fadeOut(HiFiMotion.quick()),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = statusBar + 10.dp)
        ) {
            HiFiKnob(onClick = onPlayPause, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = 44.dp) {
                PlayPauseGlyph(isPlaying = isPlaying, size = 22.dp)
            }
        }
    }
    if (genresPinned) {
        GenreRow(selectedCategory, onCategorySelect, contentStart, Modifier.padding(top = statusBar + RadioPinnedGenresTop))
    }
    }
}

@Composable
private fun GenreRow(selected: RadioCategory, onSelect: (RadioCategory) -> Unit, contentStart: Dp, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = contentStart, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(RadioCategory.entries, key = { it.name }) { category ->
            HiFiFilterPill(
                text = category.title,
                selected = category == selected,
                onClick = { onSelect(category) },
                icon = if (category == RadioCategory.FAVORITES) Icons.Rounded.Favorite else null
            )
        }
    }
}

@Composable
private fun OnAirCard(
    station: RadioStation,
    allStations: List<RadioStation>,
    live: Boolean,
    isFavorite: Boolean,
    onKnob: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTune: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val start by animateColorAsState(Color(station.brandColorStart), HiFiMotion.tint(), label = "radio_card_start")
    val end by animateColorAsState(Color(station.brandColorEnd), HiFiMotion.tint(), label = "radio_card_end")
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .hifiSoftShadow(shape, 30.dp, start.copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(start, end, HiFi.Ground)))
            .hifiGrain(enabled = !LocalHiFiLite.current, alpha = 0.5f)
            .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = 2.dp.toPx())) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Row(
                    Modifier
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(HiFi.Live))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.radio_on_air), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            GlassCircleButton(onClick = onToggleFavorite, contentDescription = stringResource(R.string.cd_v_izbrannoe)) {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null, tint = if (isFavorite) HiFi.Love else Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StationLogo(station, 76.dp, 18.dp, Modifier.hifiSoftShadow(RoundedCornerShape(18.dp), 16.dp), plainBackground = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stationSubtitle(station), color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            HiFiKnob(onClick = onKnob, contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza), size = 60.dp) {
                PlayPauseGlyph(isPlaying = live, size = 26.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        TuningWindow(selected = station, stations = allStations, onTune = onTune)
    }
}

private fun stationSubtitle(station: RadioStation): String =
    listOfNotNull(station.category.title, RadioDial.parseMhz(station.frequency)?.let { "$it FM" }).joinToString(" · ")

/**
 * The receiver window: the FM scale with the favourite-station marks and a lit needle. Dragging moves the
 * needle under the finger with a haptic tick on every station; letting go tunes to the nearest one.
 */
@Composable
private fun TuningWindow(selected: RadioStation, stations: List<RadioStation>, onTune: (RadioStation) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val marks = remember(stations) { stations.mapNotNull { RadioDial.parseMhz(it.frequency)?.let(RadioDial::fraction) } }
    val selectedFraction = RadioDial.parseMhz(selected.frequency)?.let(RadioDial::fraction) ?: 0.5f
    val needle = remember { Animatable(selectedFraction) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableStateOf<RadioStation?>(null) }
    LaunchedEffect(selectedFraction) { if (!dragging) needle.animateTo(selectedFraction, HiFiMotion.spring()) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .hifiRecessed(16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(stations) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val station = RadioDial.nearest(stations, needle.value)
                            if (station != null && station.id != selected.id) onTune(station)
                            else scope.launch { needle.animateTo(selectedFraction, HiFiMotion.spring()) }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { needle.animateTo(selectedFraction, HiFiMotion.spring()) }
                        }
                    ) { change, delta ->
                        change.consume()
                        val next = (needle.value + delta / widthPx).coerceIn(0f, 1f)
                        scope.launch { needle.snapTo(next) }
                        val near = RadioDial.nearest(stations, next)
                        val nearFraction = near?.let { RadioDial.parseMhz(it.frequency)?.let(RadioDial::fraction) }
                        if (near != null && near != lastTick && nearFraction != null && abs(nearFraction - next) < 0.012f) {
                            lastTick = near
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
                .drawBehind {
                    val scaleTop = 12.dp.toPx()
                    val minorStep = size.width / 40f
                    var i = 0
                    while (i <= 40) {
                        val x = i * minorStep
                        val major = i % 8 == 0
                        val h = if (major) 20.dp.toPx() else 12.dp.toPx()
                        drawLine(Color.White.copy(alpha = if (major) 0.75f else 0.32f), Offset(x, scaleTop + (20.dp.toPx() - h)), Offset(x, scaleTop + 20.dp.toPx()), if (major) 1.5.dp.toPx() else 1f)
                        i++
                    }
                    marks.forEach { f -> drawCircle(Color.White.copy(alpha = 0.55f), 2.dp.toPx(), Offset(f * size.width, 3.dp.toPx())) }
                    val nx = needle.value * size.width
                    drawLine(HiFi.Accent.copy(alpha = 0.35f), Offset(nx, 0f), Offset(nx, scaleTop + 22.dp.toPx()), 6.dp.toPx())
                    drawLine(HiFi.Accent, Offset(nx, 0f), Offset(nx, scaleTop + 22.dp.toPx()), 2.dp.toPx())
                }
        )
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset { IntOffset(0, 0) }, horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("88", "92", "96", "100", "104", "108").forEach {
                Text(it, color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(14.dp))
            .hifiPress(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(station, 48.dp, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.95f), fontSize = 16.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stationSubtitle(station), style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isPlaying) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = HiFi.Accent, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggleFavorite), contentAlignment = Alignment.Center) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_v_izbrannoe),
                tint = if (isFavorite) HiFi.Love else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Station logo on its brand colors (or plain white, as on the on-air card). */
@Composable
private fun StationLogo(station: RadioStation, size: Dp, corner: Dp, modifier: Modifier = Modifier, plainBackground: Boolean = false) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                if (plainBackground) Brush.linearGradient(listOf(Color.White, HiFi.Paper))
                else Brush.linearGradient(listOf(Color(station.brandColorStart), Color(station.brandColorEnd)))
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(station.iconRes ?: station.logoUrl)
                .crossfade(true)
                .browserUserAgent()
                .build(),
            contentDescription = station.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size * 0.14f)
        )
    }
}

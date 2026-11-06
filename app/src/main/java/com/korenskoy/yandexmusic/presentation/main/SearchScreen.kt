package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.presentation.main.components.landscapeColumns
import com.korenskoy.yandexmusic.presentation.main.components.DockClearance
import androidx.compose.foundation.layout.navigationBars
import kotlin.math.roundToInt
import com.korenskoy.yandexmusic.presentation.hifi.rememberHeaderCollapse
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.presentation.hifi.hifiMachined
import com.korenskoy.yandexmusic.presentation.hifi.fadeUnderHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiCollapsingTitle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateFloatAsState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.catalog.Cover
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLargeTitle
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTextAction
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed

internal enum class SearchFilter { ALL, TRACKS, ARTISTS, ALBUMS }

/**
 * Search: the field is a well cut into the page with the history button in its edge (recent queries pop
 * out of it on demand). The header stays in place, the title gives way to the field when the page
 * scrolls or the field is in use, and the list dissolves under it. Results get their filters in the header.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<TrackDetailsDto>,
    weeklyHits: List<TrackDetailsDto>,
    trendTracks: List<TrackDetailsDto>,
    isSearching: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    onTrackClick: (String, List<TrackDetailsDto>) -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onLoadMoreHits: () -> Unit = {},
    onLoadMoreTrends: () -> Unit = {},
    hasActiveTrack: Boolean = false,
    artists: List<ArtistDto> = emptyList(),
    albums: List<AlbumDto> = emptyList(),
    history: List<String> = emptyList(),
    onArtistClick: (ArtistDto) -> Unit = {},
    onAlbumClick: (AlbumDto) -> Unit = {},
    onRemoveFromHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onSubmit: () -> Unit = {},
    initiallyShowHistory: Boolean = false
) {
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val contentStart = 20.dp
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = if (isLandscape) DockClearance + navigationBar else (if (hasActiveTrack) 168.dp else 100.dp)
    val focusManager = LocalFocusManager.current
    val historyPanel = remember { MutableTransitionState(initiallyShowHistory) }
    val historyOpen = historyPanel.targetState
    BackHandler(enabled = historyOpen) { historyPanel.targetState = false }
    // The panel never outlives the history it shows
    LaunchedEffect(history.isEmpty(), query.isNotEmpty()) {
        if (history.isEmpty() || query.isNotEmpty()) historyPanel.targetState = false
    }

    var focused by remember { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(SearchFilter.ALL.name) }
    val filters = if (query.isEmpty() || isSearching) emptyList() else availableFilters(results, artists, albums)
    val discoverList = rememberLazyListState()
    val scrolled by rememberHeaderCollapse(discoverList)
    val inUse by animateFloatAsState(if (focused || query.isNotEmpty()) 1f else 0f, HiFiMotion.standard(), label = "search_in_use")
    val collapse = { if (isLandscape) 0f else maxOf(scrolled, inUse) }
    val fieldTopRest = if (isLandscape) SearchHeaderTop else SearchHeaderTop + TitleHeight + TitleToField
    val filterSpace = if (filters.isEmpty()) 0.dp else FieldToFilters + FilterHeight
    // Where the list starts to show: under the field and, with results, under the filters
    val fadeStartPx = {
        with(density) {
            (statusBar + fieldTopRest + SearchFieldHeight + filterSpace + FadeGap).toPx() - collapse() * CollapseShift.toPx()
        }
    }
    val fieldShiftPx = { with(density) { -(collapse() * if (isLandscape) 0f else CollapseShift.toPx()).roundToInt() } }
    val listTop = statusBar + fieldTopRest + SearchFieldHeight + filterSpace + FadeGap + FadeLength -
        if (query.isEmpty()) 0.dp else CollapseShift + ResultsLift

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().fadeUnderHeader(fadeStartPx, with(density) { FadeLength.toPx() })) {
            when {
                query.isEmpty() -> DiscoverList(
                    list = discoverList,
                    top = listTop,
                    weeklyHits = weeklyHits,
                    trendTracks = trendTracks,
                    contentStart = contentStart,
                    contentBottom = contentBottom,
                    columns = if (isLandscape) landscapeColumns(com.korenskoy.yandexmusic.utils.currentWindowSizeDp().widthDp) else 2,
                    onTrackClick = onTrackClick,
                    onLoadMoreHits = onLoadMoreHits,
                    onLoadMoreTrends = onLoadMoreTrends
                )
                isSearching -> HiFiSkeletonRows(Modifier.padding(top = listTop), count = 6)
                else -> ResultsList(
                    top = listTop,
                    filter = SearchFilter.valueOf(filterName),
                    results = results,
                    artists = artists,
                    albums = albums,
                    contentStart = contentStart,
                    contentBottom = contentBottom,
                    isLandscape = isLandscape,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    likedTrackIds = likedTrackIds,
                    onTrackClick = onTrackClick,
                    onToggleLike = onToggleLike,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick
                )
            }
        }

        if (!isLandscape) {
            HiFiCollapsingTitle(
                stringResource(R.string.search_title),
                collapse = collapse,
                modifier = Modifier
                    .padding(start = contentStart, end = 20.dp, top = statusBar + SearchHeaderTop)
                    .graphicsLayer {
                        alpha = (1f - collapse() * 1.8f).coerceAtLeast(0f)
                        translationY = -collapse() * 14.dp.toPx()
                    }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, fieldShiftPx()) }
                .padding(start = contentStart, end = 20.dp, top = statusBar + fieldTopRest),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLandscape) {
                HiFiLargeTitle(stringResource(R.string.search_title))
                Spacer(Modifier.width(20.dp))
            }
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = {
                    onSubmit()
                    focusManager.clearFocus()
                },
                focused = focused,
                onFocusChange = { focused = it },
                hasHistory = history.isNotEmpty(),
                historyOpen = historyOpen,
                onHistory = { historyPanel.targetState = !historyOpen },
                modifier = Modifier.weight(1f)
            )
            if (focused || query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                HiFiTextAction(stringResource(R.string.search_cancel), onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }, color = Color.White)
            }
        }
        if (filters.isNotEmpty()) {
            SearchFilterRow(
                filters = filters,
                selected = SearchFilter.valueOf(filterName),
                onSelect = { filterName = it.name },
                contentStart = contentStart,
                modifier = Modifier
                    .offset { IntOffset(0, fieldShiftPx()) }
                    .padding(top = statusBar + fieldTopRest + SearchFieldHeight + FieldToFilters)
            )
        }

        // Recent queries: a glass panel that grows out of the history button over a dimmed page
        AnimatedVisibility(
            visibleState = historyPanel,
            enter = fadeIn(HiFiMotion.standard()),
            exit = fadeOut(HiFiMotion.quick()),
            modifier = Modifier.liftAboveCards()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { historyPanel.targetState = false }
            )
        }
        AnimatedVisibility(
            visibleState = historyPanel,
            enter = scaleIn(HiFiMotion.standard(), initialScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)) + fadeIn(HiFiMotion.standard()),
            exit = scaleOut(HiFiMotion.quick(), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)) + fadeOut(HiFiMotion.quick()),
            modifier = Modifier
                .liftAboveCards()
                .offset { IntOffset(0, fieldShiftPx()) }
                .padding(start = contentStart, end = 20.dp, top = statusBar + fieldTopRest + SearchFieldHeight + 10.dp)
                .align(Alignment.TopEnd)
        ) {
            RecentPanel(
                history = history,
                onPick = {
                    historyPanel.targetState = false
                    onQueryChange(it)
                    onSubmit()
                },
                onRemove = onRemoveFromHistory,
                onClear = {
                    onClearHistory()
                    historyPanel.targetState = false
                },
                onClose = { historyPanel.targetState = false }
            )
        }
    }
}

private val SearchHeaderTop = 14.dp
private val TitleHeight = 36.dp
private val TitleToField = 16.dp
private val SearchFieldHeight = 54.dp
/** How far the field rises when the title gives way. */
private val CollapseShift = TitleHeight + TitleToField
private val FieldToFilters = 10.dp
private val FilterHeight = 36.dp
private val FadeGap = 2.dp
private val FadeLength = 26.dp
/** Results have small headers of their own: they start a little higher than the discover list. */
private val ResultsLift = 14.dp

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    hasHistory: Boolean,
    historyOpen: Boolean,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lit by animateFloatAsState(if (focused) 1f else 0f, HiFiMotion.standard(), label = "field_rim")
    Row(
        modifier = modifier
            .height(SearchFieldHeight)
            .hifiSoftShadow(RoundedCornerShape(27.dp), 26.dp)
            .hifiMachined(27.dp, lit = { lit })
            .padding(start = 16.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_treki_artisty_albomy), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(HiFi.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
            )
        }
        when {
            query.isNotEmpty() -> FieldIconButton(onClick = { onQueryChange("") }, description = stringResource(R.string.cd_ochistit)) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
            }
            hasHistory -> {
                Spacer(Modifier.size(width = 1.dp, height = 24.dp).background(Color.White.copy(alpha = 0.14f)))
                FieldIconButton(onClick = onHistory, description = stringResource(R.string.search_recent_button), selected = historyOpen) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = if (historyOpen) 1f else 0.78f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun FieldIconButton(onClick: () -> Unit, description: String, selected: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .then(if (selected) Modifier.hifiAnodized(CircleShape) else Modifier.clip(CircleShape))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Cover cards cast shadows, so their layers sit above the page on the Z axis and would show through an
 * overlay drawn at Z = 0. The overlay is lifted above them with an elevation that casts no shadow.
 */
internal fun Modifier.liftAboveCards(): Modifier = this.graphicsLayer {
    shadowElevation = 48.dp.toPx()
    ambientShadowColor = Color.Transparent
    spotShadowColor = Color.Transparent
}

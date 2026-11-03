package com.korenskoy.yandexmusic.presentation.main

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.catalog.Cover
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiFilterPill
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiLargeTitle
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTextAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem

private enum class SearchFilter { ALL, TRACKS, ARTISTS, ALBUMS }

/**
 * Search, in the same frame as "Моя музыка": a recessed field with a history button (recent queries
 * pop out of it on demand instead of taking room), weekly hits and trends as covers, results with
 * filters and a "best result".
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
    val contentStart = if (isLandscape) 140.dp else 20.dp
    val contentBottom = if (isLandscape) 90.dp else (if (hasActiveTrack) 168.dp else 100.dp)
    val focusManager = LocalFocusManager.current
    val historyPanel = remember { MutableTransitionState(initiallyShowHistory) }
    val historyOpen = historyPanel.targetState
    BackHandler(enabled = historyOpen) { historyPanel.targetState = false }
    // The panel never outlives the history it shows
    LaunchedEffect(history.isEmpty(), query.isNotEmpty()) {
        if (history.isEmpty() || query.isNotEmpty()) historyPanel.targetState = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HiFiLargeTitle(stringResource(R.string.search_title), modifier = Modifier.padding(start = contentStart, end = 20.dp, top = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = contentStart, end = 20.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = {
                        onSubmit()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    HiFiTextAction(stringResource(R.string.search_cancel), onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    }, color = Color.White)
                } else if (history.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    HistoryButton(open = historyOpen, onClick = { historyPanel.targetState = !historyOpen })
                }
            }

            when {
                query.isEmpty() -> DiscoverList(
                    weeklyHits = weeklyHits,
                    trendTracks = trendTracks,
                    contentStart = contentStart,
                    contentBottom = contentBottom,
                    columns = if (isLandscape) 4 else 2,
                    onTrackClick = onTrackClick,
                    onLoadMoreHits = onLoadMoreHits,
                    onLoadMoreTrends = onLoadMoreTrends
                )
                isSearching -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                else -> ResultsList(
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
                .statusBarsPadding()
                .padding(start = contentStart, end = 20.dp, top = 128.dp)
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

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .hifiRecessed(26.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_treki_artisty_albomy), color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(HiFi.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_ochistit), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HistoryButton(open: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.search_recent_button)
    if (open) {
        Box(
            Modifier
                .size(52.dp)
                .hifiAnodized(CircleShape)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.History, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    } else {
        GlassCircleButton(onClick = onClick, contentDescription = description, size = 52.dp) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Cover cards cast shadows, so their layers sit above the page on the Z axis and would show through an
 * overlay drawn at Z = 0. The overlay is lifted above them with an elevation that casts no shadow.
 */
private fun Modifier.liftAboveCards(): Modifier = this.graphicsLayer {
    shadowElevation = 48.dp.toPx()
    ambientShadowColor = Color.Transparent
    spotShadowColor = Color.Transparent
}

@Composable
private fun RecentPanel(history: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .shadow(30.dp, RoundedCornerShape(24.dp))
            .hifiGlass(RoundedCornerShape(24.dp), fill = Color(0xFF171922), border = Color.White.copy(alpha = 0.12f))
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        HiFiSectionHeader(stringResource(R.string.search_recent), Modifier.padding(start = 18.dp, end = 8.dp)) {
            HiFiTextAction(stringResource(R.string.search_clear_history), onClick = onClear)
        }
        history.take(8).forEach { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = Role.Button) { onPick(q) }
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(q, style = HiFiType.Body.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { onRemove(q) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove_from_history), tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoverList(
    weeklyHits: List<TrackDetailsDto>,
    trendTracks: List<TrackDetailsDto>,
    contentStart: Dp,
    contentBottom: Dp,
    columns: Int,
    onTrackClick: (String, List<TrackDetailsDto>) -> Unit,
    onLoadMoreHits: () -> Unit,
    onLoadMoreTrends: () -> Unit
) {
    val hits = weeklyHits.ifEmpty { trendTracks.take(20) }
    val trends = trendTracks.ifEmpty { weeklyHits }
    if (hits.isEmpty() && trends.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }
    val columnState = rememberLazyListState()
    val rowState = rememberLazyListState()
    LaunchedEffect(rowState) {
        snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to rowState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 3) onLoadMoreHits() }
    }
    LaunchedEffect(columnState) {
        snapshotFlow { columnState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to columnState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 2) onLoadMoreTrends() }
    }
    LazyColumn(state = columnState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentBottom)) {
        if (hits.isNotEmpty()) {
            item(key = "hits_title") {
                HiFiSectionHeader(stringResource(R.string.search_hity_nedeli), Modifier.padding(start = contentStart, end = 20.dp, top = 26.dp, bottom = 12.dp))
            }
            item(key = "hits") {
                LazyRow(state = rowState, contentPadding = PaddingValues(start = contentStart, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(hits, key = { it.idString }, contentType = { "hit" }) { track ->
                        CoverCard(track, Modifier.width(150.dp)) { onTrackClick(track.idString, hits) }
                    }
                }
            }
        }
        if (trends.isNotEmpty()) {
            item(key = "trends_title") {
                HiFiSectionHeader(stringResource(R.string.search_v_trende), Modifier.padding(start = contentStart, end = 20.dp, top = 24.dp, bottom = 12.dp))
            }
            items(trends.chunked(columns), key = { row -> "trend_" + row.joinToString("|") { it.idString } }, contentType = { "cover_row" }) { row ->
                Row(Modifier.fillMaxWidth().padding(start = contentStart, end = 20.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { track -> CoverCard(track, Modifier.weight(1f)) { onTrackClick(track.idString, trends) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: List<TrackDetailsDto>,
    artists: List<ArtistDto>,
    albums: List<AlbumDto>,
    contentStart: Dp,
    contentBottom: Dp,
    isLandscape: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    onTrackClick: (String, List<TrackDetailsDto>) -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onArtistClick: (ArtistDto) -> Unit,
    onAlbumClick: (AlbumDto) -> Unit
) {
    var filterName by rememberSaveable { mutableStateOf(SearchFilter.ALL.name) }
    val filter = SearchFilter.valueOf(filterName)
    if (results.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        Text(
            stringResource(R.string.search_nothing_found),
            style = HiFiType.Body.copy(color = HiFi.TextSecondary),
            modifier = Modifier.padding(start = contentStart, top = 40.dp)
        )
        return
    }
    val available = buildList {
        add(SearchFilter.ALL)
        if (results.isNotEmpty()) add(SearchFilter.TRACKS)
        if (artists.isNotEmpty()) add(SearchFilter.ARTISTS)
        if (albums.isNotEmpty()) add(SearchFilter.ALBUMS)
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentBottom)) {
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(start = contentStart, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(available, key = { it.name }) { f ->
                    HiFiFilterPill(
                        text = stringResource(
                            when (f) {
                                SearchFilter.ALL -> R.string.search_filter_all
                                SearchFilter.TRACKS -> R.string.search_filter_tracks
                                SearchFilter.ARTISTS -> R.string.search_filter_artists
                                SearchFilter.ALBUMS -> R.string.search_filter_albums
                            }
                        ),
                        selected = f == filter,
                        onClick = { filterName = f.name }
                    )
                }
            }
        }
        if (filter == SearchFilter.ALL) {
            item(key = "best_title") {
                HiFiSectionHeader(stringResource(R.string.search_best_result), Modifier.padding(start = contentStart, end = 20.dp, top = 22.dp, bottom = 12.dp))
            }
            item(key = "best") {
                Box(Modifier.padding(start = contentStart, end = 20.dp)) {
                    val artist = artists.firstOrNull()
                    val track = results.firstOrNull()
                    if (artist != null) {
                        BestResultCard(title = artist.name.orEmpty(), subtitle = stringResource(R.string.search_artist_single), coverUri = artist.coverUri, round = true, onClick = { onArtistClick(artist) }, onPlay = null)
                    } else if (track != null) {
                        BestResultCard(
                            title = track.title.orEmpty(),
                            subtitle = track.artists.joinToString { it.name ?: "" },
                            coverUri = track.coverUri,
                            round = false,
                            onClick = { onTrackClick(track.idString, results) },
                            onPlay = { onTrackClick(track.idString, results) }
                        )
                    }
                }
            }
        }
        if ((filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) && artists.isNotEmpty()) {
            item(key = "artists_title") {
                HiFiSectionHeader(stringResource(R.string.search_artists), Modifier.padding(start = contentStart, end = 20.dp, top = 24.dp, bottom = 12.dp))
            }
            item(key = "artists") {
                LazyRow(contentPadding = PaddingValues(start = contentStart, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists, key = { it.idString ?: it.name.orEmpty() }) { artist ->
                        Column(
                            Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onArtistClick(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.shadow(10.dp, CircleShape)) { Cover(artist.coverUri, Icons.Rounded.Person, CircleShape, 88.dp) }
                            Spacer(Modifier.height(8.dp))
                            Text(artist.name.orEmpty(), style = HiFiType.CardTitle.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if ((filter == SearchFilter.ALL || filter == SearchFilter.TRACKS) && results.isNotEmpty()) {
            item(key = "tracks_title") {
                HiFiSectionHeader(stringResource(R.string.search_tracks), Modifier.padding(start = contentStart, end = 20.dp, top = 24.dp, bottom = 6.dp))
            }
            val shown = if (filter == SearchFilter.ALL) results.take(6) else results
            if (isLandscape) {
                items(shown.chunked(2), key = { row -> "t_" + row.joinToString("|") { it.idString } }, contentType = { "track_row" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(start = contentStart - 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { track ->
                            Box(Modifier.weight(1f)) {
                                TrackItem(track = track, isPlaying = isPlaying && track.idString == currentTrackId, isLiked = likedTrackIds.contains(track.idString), onToggleLike = { onToggleLike(track) }, onClick = { onTrackClick(track.idString, results) })
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(shown, key = { "t_" + it.idString }, contentType = { "track" }) { track ->
                    Box(Modifier.padding(start = contentStart - 12.dp, end = 8.dp)) {
                        TrackItem(track = track, isPlaying = isPlaying && track.idString == currentTrackId, isLiked = likedTrackIds.contains(track.idString), onToggleLike = { onToggleLike(track) }, onClick = { onTrackClick(track.idString, results) })
                    }
                }
            }
        }
        if ((filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) && albums.isNotEmpty()) {
            item(key = "albums_title") {
                HiFiSectionHeader(stringResource(R.string.search_albums), Modifier.padding(start = contentStart, end = 20.dp, top = 24.dp, bottom = 12.dp))
            }
            item(key = "albums") {
                LazyRow(contentPadding = PaddingValues(start = contentStart, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(albums, key = { it.idString ?: it.title.orEmpty() }) { album ->
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp)).clickable(role = Role.Button) { onAlbumClick(album) }) {
                            Box(Modifier.shadow(14.dp, RoundedCornerShape(16.dp))) { Cover(album.coverUri, Icons.Rounded.Album, RoundedCornerShape(16.dp), 150.dp) }
                            Spacer(Modifier.height(9.dp))
                            Text(album.title.orEmpty(), style = HiFiType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(album.year?.toString(), album.artists.firstOrNull()?.name).joinToString(" · "), style = HiFiType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The top match on a card tinted with the app color; a knob plays a track result right away. */
@Composable
private fun BestResultCard(title: String, subtitle: String, coverUri: String?, round: Boolean, onClick: () -> Unit, onPlay: (() -> Unit)?) {
    val tint = LocalArtworkTint.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.primary.copy(alpha = 0.55f), tint.secondary.copy(alpha = 0.55f), Color(0xFF12141C))))
            .hifiGlass(shape, fill = Color.Black.copy(alpha = 0.18f), border = Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.shadow(10.dp, if (round) CircleShape else RoundedCornerShape(14.dp))) {
            Cover(coverUri, if (round) Icons.Rounded.Person else Icons.Rounded.Album, if (round) CircleShape else RoundedCornerShape(14.dp), 76.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.76f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onPlay != null) {
            HiFiKnob(onClick = onPlay, contentDescription = stringResource(R.string.cd_slushat), size = 54.dp) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFFF4F6FA), modifier = Modifier.size(26.dp))
            }
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        }
    }
}

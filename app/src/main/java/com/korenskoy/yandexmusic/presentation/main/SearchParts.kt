package com.korenskoy.yandexmusic.presentation.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.SearchOff
import com.korenskoy.yandexmusic.presentation.hifi.appearOnce
import com.korenskoy.yandexmusic.presentation.hifi.rememberAppearance
import com.korenskoy.yandexmusic.presentation.hifi.HiFiEmptyState
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.catalog.Cover
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiFilterPill
import com.korenskoy.yandexmusic.presentation.hifi.HiFiKnob
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import com.korenskoy.yandexmusic.presentation.hifi.HiFiTextAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem

// The parts of the search page: recent queries, the discover lists and the results.

@Composable
internal fun RecentPanel(history: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .hifiSoftShadow(RoundedCornerShape(24.dp), 30.dp)
            .hifiGlass(RoundedCornerShape(24.dp), fill = HiFi.SheetFill, border = Color.White.copy(alpha = 0.12f))
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
internal fun DiscoverList(
    list: LazyListState,
    top: Dp,
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
        HiFiSkeletonRows(Modifier.padding(top = top), count = 6)
        return
    }
    val columnState = list
    val rowState = rememberLazyListState()
    LaunchedEffect(rowState) {
        snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to rowState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 3) onLoadMoreHits() }
    }
    LaunchedEffect(columnState) {
        snapshotFlow { columnState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to columnState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 2) onLoadMoreTrends() }
    }
    LazyColumn(state = columnState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = contentBottom)) {
        if (hits.isNotEmpty()) {
            item(key = "hits_title") {
                HiFiSectionHeader(stringResource(R.string.search_hity_nedeli), Modifier.padding(start = contentStart, end = 20.dp, bottom = 12.dp))
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

/** The filters worth offering for these results; nothing found, nothing to filter. */
internal fun availableFilters(results: List<TrackDetailsDto>, artists: List<ArtistDto>, albums: List<AlbumDto>): List<SearchFilter> =
    if (results.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        emptyList()
    } else {
        buildList {
            add(SearchFilter.ALL)
            if (results.isNotEmpty()) add(SearchFilter.TRACKS)
            if (artists.isNotEmpty()) add(SearchFilter.ARTISTS)
            if (albums.isNotEmpty()) add(SearchFilter.ALBUMS)
        }
    }

@Composable
internal fun SearchFilterRow(
    filters: List<SearchFilter>,
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
    contentStart: Dp,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = contentStart, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters, key = { it.name }) { f ->
            HiFiFilterPill(
                text = stringResource(
                    when (f) {
                        SearchFilter.ALL -> R.string.search_filter_all
                        SearchFilter.TRACKS -> R.string.search_filter_tracks
                        SearchFilter.ARTISTS -> R.string.search_filter_artists
                        SearchFilter.ALBUMS -> R.string.search_filter_albums
                    }
                ),
                selected = f == selected,
                onClick = { onSelect(f) }
            )
        }
    }
}

@Composable
internal fun ResultsList(
    top: Dp,
    filter: SearchFilter,
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
    if (results.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        HiFiEmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.search_nothing_found),
            message = stringResource(R.string.search_nothing_found_hint),
            modifier = Modifier.padding(start = contentStart - 20.dp, top = top)
        )
        return
    }
    // A new set of results rises in row by row
    val appearance = rememberAppearance(results)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = contentBottom)) {
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
                            Modifier.width(96.dp).hifiPress({ onArtistClick(artist) }),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.hifiSoftShadow(CircleShape, 14.dp)) { Cover(artist.coverUri, Icons.Rounded.Person, CircleShape, 88.dp) }
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
                itemsIndexed(shown, key = { _, t -> "t_" + t.idString }, contentType = { _, _ -> "track" }) { index, track ->
                    Box(Modifier.padding(start = contentStart - 12.dp, end = 8.dp).appearOnce(appearance, track.idString, index)) {
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
                        Column(Modifier.width(150.dp).hifiPress({ onAlbumClick(album) })) {
                            Box(Modifier.hifiSoftShadow(RoundedCornerShape(16.dp), 18.dp)) { Cover(album.coverUri, Icons.Rounded.Album, RoundedCornerShape(16.dp), 150.dp) }
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
internal fun BestResultCard(title: String, subtitle: String, coverUri: String?, round: Boolean, onClick: () -> Unit, onPlay: (() -> Unit)?) {
    val tint = LocalArtworkTint.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .hifiSoftShadow(shape, 16.dp)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.primary.copy(alpha = 0.55f), tint.secondary.copy(alpha = 0.55f), HiFi.Ground)))
            .hifiGlass(shape, fill = Color.Black.copy(alpha = 0.18f), border = Color.White.copy(alpha = 0.12f))
            .hifiPress(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.hifiSoftShadow(if (round) CircleShape else RoundedCornerShape(14.dp), 14.dp)) {
            Cover(coverUri, if (round) Icons.Rounded.Person else Icons.Rounded.Album, if (round) CircleShape else RoundedCornerShape(14.dp), 76.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.76f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onPlay != null) {
            HiFiKnob(onClick = onPlay, contentDescription = stringResource(R.string.cd_slushat), size = 54.dp) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = HiFi.OnMetal, modifier = Modifier.size(26.dp))
            }
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        }
    }
}

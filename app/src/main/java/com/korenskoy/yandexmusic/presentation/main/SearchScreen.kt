package com.korenskoy.yandexmusic.presentation.main

import androidx.compose.material.icons.filled.History
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem

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
    artists: List<com.korenskoy.yandexmusic.data.dto.ArtistDto> = emptyList(),
    albums: List<com.korenskoy.yandexmusic.data.dto.AlbumDto> = emptyList(),
    history: List<String> = emptyList(),
    onArtistClick: (com.korenskoy.yandexmusic.data.dto.ArtistDto) -> Unit = {},
    onAlbumClick: (com.korenskoy.yandexmusic.data.dto.AlbumDto) -> Unit = {},
    onRemoveFromHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onSubmit: () -> Unit = {}
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val contentStart = if (isLandscape) 140.dp else 16.dp
    val contentBottom = if (isLandscape) 90.dp else (if (hasActiveTrack) 168.dp else 100.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141928),
                        Color(0xFF10131E),
                        Color(0xFF0F0F14)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 12.dp, start = contentStart, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_poisk),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = stringResource(R.string.cd_poisk),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = stringResource(R.string.search_katalog_yandex_music_lite),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscape) 0.6f else 1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp)),
                    placeholder = { Text(stringResource(R.string.search_treki_artisty_albomy), color = Color.White.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_poisk), tint = Color.White.copy(alpha = 0.5f)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_ochistit),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() })
                )
            }
        }
        
        if (query.isEmpty()) {
            val hits = weeklyHits.ifEmpty { trendTracks.take(20) }
            val trends = trendTracks.ifEmpty { weeklyHits }

            if (hits.isNotEmpty() || trends.isNotEmpty()) {
                val columnState = rememberLazyListState()
                val rowState = rememberLazyListState()
                
                LaunchedEffect(rowState) {
                    snapshotFlow { 
                        Pair(rowState.layoutInfo, rowState.isScrollInProgress)
                    }.collect { (layoutInfo, isScrolling) ->
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        if (isScrolling && totalItems > 0 && lastVisible >= totalItems - 3) {
                            onLoadMoreHits()
                        }
                    }
                }
                
                LaunchedEffect(columnState) {
                    snapshotFlow { 
                        Pair(columnState.layoutInfo, columnState.isScrollInProgress)
                    }.collect { (layoutInfo, isScrolling) ->
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        if (isScrolling && totalItems > 0 && lastVisible >= totalItems - 2) {
                            onLoadMoreTrends()
                        }
                    }
                }

                LazyColumn(
                    state = columnState,
                    contentPadding = PaddingValues(bottom = contentBottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    if (history.isNotEmpty()) {
                        item(key = "history") {
                            RecentSearches(
                                history = history,
                                onClick = onQueryChange,
                                onRemove = onRemoveFromHistory,
                                onClear = onClearHistory,
                                modifier = Modifier.padding(start = contentStart, end = 16.dp, bottom = 16.dp)
                            )
                        }
                    }
                    // Weekly Hits Section
                    if (hits.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = stringResource(R.string.search_hity_nedeli),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = contentStart, end = 16.dp, bottom = 12.dp)
                                )
                                
                                LazyRow(
                                    state = rowState,
                                    contentPadding = PaddingValues(start = contentStart, end = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(hits, key = { it.idString }) { track ->
                                        Box(
                                            modifier = Modifier
                                                .width(135.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1E1E22))
                                                .clickable { onTrackClick(track.idString, hits) }
                                                .padding(8.dp)
                                        ) {
                                            Column {
                                                val context = LocalContext.current
                                                val coverUrl = track.coverUri?.let { uri ->
                                                    if (uri.startsWith("http")) uri else "https://${uri.replace("%%", "200x200")}"
                                                } ?: ""
                                                
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(coverUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = track.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(119.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Text(
                                                    text = track.title ?: "Без названия",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                Text(
                                                    text = track.artists.joinToString { it.name ?: "" },
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                    
                    // Trending Tracks Section
                    if (trends.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.search_v_trende),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = contentStart, end = 16.dp, bottom = 8.dp, top = 12.dp)
                            )
                        }
                        if (isLandscape) {
                            items(trends.chunked(2), key = { row -> row.joinToString("|") { it.idString } }) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = contentStart, end = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowItems.forEach { track ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            TrackItem(
                                                track = track,
                                                isPlaying = isPlaying && track.idString == currentTrackId,
                                                isLiked = likedTrackIds.contains(track.idString),
                                                onToggleLike = { onToggleLike(track) },
                                                onClick = { onTrackClick(track.idString, trends) }
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(trends, key = { it.idString }) { track ->
                                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    TrackItem(
                                        track = track,
                                        isPlaying = isPlaying && track.idString == currentTrackId,
                                        isLiked = likedTrackIds.contains(track.idString),
                                        onToggleLike = { onToggleLike(track) },
                                        onClick = { onTrackClick(track.idString, trends) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            // Search Results Section
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = contentStart, end = 12.dp, top = 8.dp, bottom = contentBottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    if (artists.isNotEmpty()) {
                        item(key = "artists") {
                            Column(Modifier.padding(bottom = 12.dp)) {
                                SearchSectionTitle(stringResource(R.string.search_artists))
                                com.korenskoy.yandexmusic.presentation.catalog.ArtistsRow(artists, onArtistClick, PaddingValues(0.dp))
                            }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item(key = "albums") {
                            Column(Modifier.padding(bottom = 12.dp)) {
                                SearchSectionTitle(stringResource(R.string.search_albums))
                                com.korenskoy.yandexmusic.presentation.catalog.AlbumsRow(albums, onAlbumClick, PaddingValues(0.dp))
                            }
                        }
                    }
                    if (isLandscape) {
                        items(results.chunked(2), key = { row -> row.joinToString("|") { it.idString } }) { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                rowItems.forEach { track ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        TrackItem(
                                            track = track,
                                            isPlaying = isPlaying && track.idString == currentTrackId,
                                            isLiked = likedTrackIds.contains(track.idString),
                                            onToggleLike = { onToggleLike(track) },
                                            onClick = { onTrackClick(track.idString, results) }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(results, key = { it.idString }) { track ->
                            TrackItem(
                                track = track,
                                isPlaying = isPlaying && track.idString == currentTrackId,
                                isLiked = likedTrackIds.contains(track.idString),
                                onToggleLike = { onToggleLike(track) },
                                onClick = { onTrackClick(track.idString, results) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PopularTrackCard(track: TrackDetailsDto, isLandscape: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val coverUrl = track.coverUri?.let { uri ->
        if (uri.startsWith("http")) uri else "https://${uri.replace("%%", "600x600")}"
    } ?: ""

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    val cardWidth = if (isLandscape) 150.dp else 140.dp
    val cardHeight = if (isLandscape) 185.dp else 175.dp

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E24))
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
            }
    ) {
        // High quality Album Cover as background
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.cd_oblozhka),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Multi-layer Dark Gradient for seamless contrast & text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.92f)
                        ),
                        startY = 60f
                    )
                )
        )
        
        // Clean Title and Artist only
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = track.title ?: "Без названия",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 16.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = track.artists.joinToString { it.name ?: "" }.ifEmpty { "Неизвестный исполнитель" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** The last searches: tap to search again, ✕ to forget one, "Clear" to forget all. */
@Composable
private fun RecentSearches(
    history: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_recent),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.search_clear_history),
                color = Color(0xFFFF3366),
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        history.forEach { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClick(query) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(query, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(query) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove_from_history), tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

package com.korenskoy.yandexmusic.presentation.main

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
                isSearching -> HiFiSkeletonRows(Modifier.padding(top = 16.dp), count = 6)
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
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
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
internal fun HistoryButton(open: Boolean, onClick: () -> Unit) {
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
internal fun Modifier.liftAboveCards(): Modifier = this.graphicsLayer {
    shadowElevation = 48.dp.toPx()
    ambientShadowColor = Color.Transparent
    spotShadowColor = Color.Transparent
}

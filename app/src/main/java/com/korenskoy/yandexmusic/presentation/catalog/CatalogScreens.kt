package com.korenskoy.yandexmusic.presentation.catalog

import com.korenskoy.yandexmusic.presentation.hifi.HiFiWideHeader
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSectionHeader
import androidx.compose.ui.semantics.Role
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import androidx.compose.foundation.lazy.itemsIndexed
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFiErrorState
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSkeletonRows
import com.korenskoy.yandexmusic.presentation.hifi.appearOnce
import com.korenskoy.yandexmusic.presentation.hifi.rememberAppearance
import com.korenskoy.yandexmusic.presentation.hifi.predictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.rememberPredictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString

/** What every catalog page needs from the main screen. */
class CatalogActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenArtist: (id: String, name: String) -> Unit,
    val onOpenAlbum: (id: String, title: String) -> Unit,
    val onPlay: (tracks: List<TrackDetailsDto>, trackId: String?, shuffle: Boolean) -> Unit,
    val onToggleLike: (TrackDetailsDto) -> Unit
)

/** The page on top of the catalog stack: an artist or an album. Back returns to the previous page. */
@Composable
fun CatalogPageScreen(
    state: CatalogPageState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    contentBottom: Dp,
    actions: CatalogActions
) {
    val back = rememberPredictiveBack(enabled = true, onBack = actions.onBack)
    val isArtist = state.page is CatalogPage.Artist
    val tracks = state.artist?.popularTracks ?: state.album?.tracks.orEmpty()
    val appearance = rememberAppearance(state.page)
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentBottom),
        modifier = Modifier
            .fillMaxSize()
            .predictiveBack(back)
            .navigationBarsPadding()
    ) {
        item(key = "header") {
            if (isLandscape) {
                HiFiWideHeader(
                    title = state.artist?.artist?.name ?: state.album?.title ?: state.page.title,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    back = {
                        GlassCircleButton(onClick = actions.onBack, contentDescription = stringResource(R.string.cd_nazad)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    },
                    cover = { size ->
                        Cover(
                            uri = if (isArtist) state.artist?.artist?.coverUri else state.album?.coverUri,
                            placeholder = if (isArtist) Icons.Default.Person else Icons.Default.Album,
                            shape = if (isArtist) CircleShape else RoundedCornerShape(12.dp),
                            size = size
                        )
                    },
                    details = { state.album?.let { album -> AlbumSubtitle(album, actions) } },
                    actions = {
                        if (tracks.isNotEmpty()) {
                            PlayButton(Icons.Default.PlayArrow, stringResource(R.string.cd_slushat), primary = true, Modifier.weight(1f)) {
                                actions.onPlay(tracks, null, false)
                            }
                            PlayButton(Icons.Default.Shuffle, stringResource(R.string.cd_peremeshat), primary = false, Modifier.weight(1f)) {
                                actions.onPlay(tracks, null, true)
                            }
                        }
                    }
                )
            } else Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleButton(onClick = actions.onBack, contentDescription = stringResource(R.string.cd_nazad)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(
                        uri = if (isArtist) state.artist?.artist?.coverUri else state.album?.coverUri,
                        placeholder = if (isArtist) Icons.Default.Person else Icons.Default.Album,
                        shape = if (isArtist) CircleShape else RoundedCornerShape(12.dp),
                        size = 112.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = state.artist?.artist?.name ?: state.album?.title ?: state.page.title,
                            style = HiFiType.LargeTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.album?.let { album -> AlbumSubtitle(album, actions) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (tracks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayButton(Icons.Default.PlayArrow, stringResource(R.string.cd_slushat), primary = true, Modifier.weight(1f)) {
                            actions.onPlay(tracks, null, false)
                        }
                        PlayButton(Icons.Default.Shuffle, stringResource(R.string.cd_peremeshat), primary = false, Modifier.weight(1f)) {
                            actions.onPlay(tracks, null, true)
                        }
                    }
                }
            }
        }

        when {
            state.isLoading -> item(key = "loading") { HiFiSkeletonRows(Modifier.padding(top = 20.dp), count = 6) }
            state.failed -> item(key = "failed") {
                HiFiErrorState(onRetry = actions.onRetry, message = stringResource(R.string.catalog_load_failed))
            }
            else -> {
                if (isArtist && tracks.isNotEmpty()) {
                    item(key = "popular_title") { SectionTitle(stringResource(R.string.catalog_popular_tracks)) }
                }
                itemsIndexed(tracks, key = { _, t -> "t" + t.idString }, contentType = { _, _ -> "track" }) { index, track ->
                    Box(Modifier.padding(horizontal = 8.dp).appearOnce(appearance, track.idString, index)) {
                        TrackItem(
                            track = track,
                            isPlaying = isPlaying && track.idString == currentTrackId,
                            isLiked = track.idString in likedTrackIds,
                            onToggleLike = { actions.onToggleLike(track) },
                            onClick = { actions.onPlay(tracks, track.idString, false) }
                        )
                    }
                }
                val albums = state.artist?.albums.orEmpty().filter { it.idString != null }
                if (albums.isNotEmpty()) {
                    item(key = "albums") {
                        Column {
                            SectionTitle(stringResource(R.string.catalog_albums))
                            AlbumsRow(albums, onOpen = { album -> album.idString?.let { actions.onOpenAlbum(it, album.title.orEmpty()) } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSubtitle(album: AlbumDto, actions: CatalogActions) {
    val artist = album.artists.firstOrNull { it.idString != null }
    if (artist != null) {
        Text(
            text = album.artists.joinToString { it.name.orEmpty() },
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(role = Role.Button) { artist.idString?.let { actions.onOpenArtist(it, artist.name.orEmpty()) } }
        )
    }
    Text(
        text = listOfNotNull(
            album.year?.toString(),
            russianQuantityString(album.tracks.size.takeIf { it > 0 } ?: album.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many)
        ).joinToString(" · "),
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 13.sp
    )
}

/** A horizontal row of album covers, used on artist pages and in search results. */
@Composable
fun AlbumsRow(albums: List<AlbumDto>, onOpen: (AlbumDto) -> Unit, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.idString ?: it.title.orEmpty() }) { album ->
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .hifiPress({ onOpen(album) })
            ) {
                Cover(album.coverUri, Icons.Default.Album, RoundedCornerShape(12.dp), 120.dp)
                Spacer(Modifier.height(6.dp))
                Text(album.title.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = album.year?.toString() ?: album.artists.joinToString { it.name.orEmpty() },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    // The section caps of every other page
    HiFiSectionHeader(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
internal fun Cover(uri: String?, placeholder: ImageVector, shape: Shape, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))),
        contentAlignment = Alignment.Center
    ) {
        Icon(placeholder, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(size / 3))
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).cover(uri, CoverSize.Medium).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** "Слушать" is the Hi-Fi metal capsule, "Перемешать" glass — the same pair as on "Моя музыка". */
@Composable
private fun PlayButton(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (primary) MetalPillButton(label, onClick, modifier, icon = icon, height = 48.dp)
    else GlassPillButton(label, onClick, modifier, icon = icon, height = 48.dp)
}

package com.korenskoy.yandexmusic.presentation.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString
import com.korenskoy.yandexmusic.utils.toCoverUrl

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
    BackHandler(onBack = actions.onBack)
    val isArtist = state.page is CatalogPage.Artist
    val tracks = state.artist?.popularTracks ?: state.album?.tracks.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentBottom),
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2A), Color(0xFF131319), Color(0xFF111115))))
            .navigationBarsPadding()
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x352A2A38))
                        .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                        .clickable(onClick = actions.onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_nazad), tint = Color.White, modifier = Modifier.size(22.dp))
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
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
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
            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.failed -> item(key = "failed") {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.catalog_load_failed), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = actions.onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.playlist_retry), color = Color.White) }
                }
            }
            else -> {
                if (isArtist && tracks.isNotEmpty()) {
                    item(key = "popular_title") { SectionTitle(stringResource(R.string.catalog_popular_tracks)) }
                }
                items(tracks, key = { "t" + it.idString }) { track ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
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
                            AlbumsRow(albums, onOpen = { actions.onOpenAlbum(it.idString!!, it.title.orEmpty()) })
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
            color = Color(0xFFFF9900),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { actions.onOpenArtist(artist.idString!!, artist.name.orEmpty()) }
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
        items(albums, key = { it.idString!! }) { album ->
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(album) }
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

/** A horizontal row of artists (round photos), used in search results. */
@Composable
fun ArtistsRow(
    artists: List<com.korenskoy.yandexmusic.data.dto.ArtistDto>,
    onOpen: (com.korenskoy.yandexmusic.data.dto.ArtistDto) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(artists, key = { it.idString!! }) { artist ->
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(artist) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Cover(artist.coverUri, Icons.Default.Person, CircleShape, 88.dp)
                Spacer(Modifier.height(6.dp))
                Text(artist.name.orEmpty(), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Cover(uri: String?, placeholder: ImageVector, shape: Shape, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3A2A4A), Color(0xFF22222C)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(placeholder, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(size / 3))
        AsyncImage(
            model = uri.toCoverUrl("400x400"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PlayButton(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(
                if (primary) Brush.linearGradient(listOf(Color(0xFFFF3366), Color(0xFFFF7722), Color(0xFFFF9900)))
                else Brush.verticalGradient(listOf(Color(0x352A2A38), Color(0x201E1E28)))
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (primary) 0.6f else 0.25f)), CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

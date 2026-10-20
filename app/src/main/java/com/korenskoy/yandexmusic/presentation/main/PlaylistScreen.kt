package com.korenskoy.yandexmusic.presentation.main

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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem
import com.korenskoy.yandexmusic.utils.russianQuantityString
import com.korenskoy.yandexmusic.utils.toCoverUrl

/** Horizontal row of the user's playlists in the library header. */
@Composable
fun PlaylistsRow(
    playlists: List<UserPlaylistDto>,
    onOpen: (UserPlaylistDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_playlists),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists, key = { it.kindString }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(playlist) }
                ) {
                    PlaylistCover(playlist, Modifier.size(104.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playlist.title.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCover(playlist: UserPlaylistDto, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2A4A), Color(0xFF22222C)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(36.dp)
        )
        AsyncImage(
            model = playlist.coverUri.toCoverUrl("400x400"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Opened playlist: cover, play/shuffle and the track list. Back closes it. */
@Composable
fun PlaylistScreen(
    opened: OpenedPlaylist,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    contentBottom: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onPlay: (shuffle: Boolean) -> Unit,
    onTrackClick: (String) -> Unit,
    onToggleLike: (com.korenskoy.yandexmusic.data.dto.TrackDetailsDto) -> Unit,
    onRetry: () -> Unit
) {
    BackHandler(onBack = onBack)
    val playlist = opened.playlist
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1F1626), Color(0xFF151219), Color(0xFF111115))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x352A2A38))
                    .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_nazad),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(playlist, Modifier.size(112.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = playlist.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = russianQuantityString(
                        if (opened.isLoading) playlist.trackCount else opened.tracks.size,
                        R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val enabled = opened.tracks.isNotEmpty()
            PlaylistAction(Icons.Default.PlayArrow, stringResource(R.string.cd_slushat), primary = true, enabled = enabled,
                modifier = Modifier.weight(1f)) { onPlay(false) }
            PlaylistAction(Icons.Default.Shuffle, stringResource(R.string.cd_peremeshat), primary = false, enabled = enabled,
                modifier = Modifier.weight(1f)) { onPlay(true) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            opened.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            opened.failed || opened.tracks.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = contentBottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(if (opened.failed) R.string.playlist_load_failed else R.string.playlist_empty),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp
                )
                if (opened.failed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(stringResource(R.string.playlist_retry), color = Color.White) }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = contentBottom),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(8.dp)
            ) {
                items(opened.tracks, key = { it.idString }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = isPlaying && track.idString == currentTrackId,
                        isLiked = likedTrackIds.contains(track.idString),
                        isDownloaded = downloadedTrackIds.contains(track.idString),
                        onToggleLike = { onToggleLike(track) },
                        onClick = { onTrackClick(track.idString) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(
                if (primary) Brush.linearGradient(listOf(Color(0xFFFF3366), Color(0xFFFF7722), Color(0xFFFF9900)))
                else Brush.verticalGradient(listOf(Color(0x352A2A38), Color(0x201E1E28)))
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (primary) 0.6f else 0.25f)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

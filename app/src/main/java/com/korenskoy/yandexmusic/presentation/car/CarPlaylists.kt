package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.main.QuickAdd
import com.korenskoy.yandexmusic.presentation.main.QuickMark
import com.korenskoy.yandexmusic.presentation.main.components.PlaylistNameDialog
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.russianQuantityString
import kotlinx.coroutines.delay

/** The user's own playlists as «В плейлист» in the car shows them, and what a tap does. */
class CarPlaylists(
    val playlists: List<UserPlaylistDto>,
    val state: QuickAdd,
    /** The panel opened for this track: playlists that have it get their mark. */
    val onOpen: (TrackDetailsDto) -> Unit,
    val onAdd: (UserPlaylistDto, TrackDetailsDto) -> Unit,
    val onCreate: (String, TrackDetailsDto) -> Unit
) {
    fun markOf(playlist: UserPlaylistDto, track: TrackDetailsDto): QuickMark? =
        state.marks[playlist.kindString].takeIf { state.trackId == track.idString }
}

/** Only a track of the catalogue goes into a playlist: not a station, not a file of the user's own. */
internal fun canAddToPlaylist(track: TrackDetailsDto?): Boolean = track != null && !track.isRadio && !track.isLocal

/** The round «В плейлист» beside the track's title; metal while its panel is open. */
@Composable
internal fun AddToPlaylistButton(size: androidx.compose.ui.unit.Dp, open: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.car_playlist_add_cd)
    Box(
        Modifier
            .size(size)
            .then(
                if (open) Modifier.hifiAnodized(CircleShape)
                else Modifier.hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f))
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

/**
 * «В плейлист» over the cover's place: «Новый плейлист» first, then the user's playlists. A tap adds the track and
 * the row says so; the panel stays open, so the track can go to several playlists. A line at the bottom (never
 * over a row) says what happened last.
 */
@Composable
internal fun CarPlaylistsPanel(u: Scale, playlists: CarPlaylists, track: TrackDetailsDto, modifier: Modifier, onClose: () -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(track.idString) { playlists.onOpen(track) }
    Column(
        modifier
            .clip(RoundedCornerShape(u(30f)))
            .background(HiFi.SheetFill.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = u(12f), end = u(16f), top = u(18f), bottom = u(12f))
    ) {
        Row(Modifier.padding(start = u(12f), end = u(2f), bottom = u(8f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.car_playlists_title), color = Color.White, fontSize = u.sp(28f), lineHeight = u.sp(34f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    listOfNotNull(track.title, track.artists.joinToString { it.name.orEmpty() }.ifBlank { null }).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(15f), lineHeight = u.sp(20f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(u(12f)))
            Box(
                Modifier
                    .size(u(56f))
                    .hifiGlass(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_zakryt), tint = Color.White, modifier = Modifier.size(u(24f)))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(u(4f))) {
            item(key = "new") { NewPlaylistRow(u) { naming = true } }
            if (playlists.playlists.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.car_playlists_none),
                        color = Color.White.copy(alpha = 0.66f), fontSize = u.sp(18f), lineHeight = u.sp(26f), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = u(16f), vertical = u(20f))
                    )
                }
            }
            items(playlists.playlists, key = { "p:" + it.kindString }) { playlist ->
                PlaylistRow(u, playlist, playlists.markOf(playlist, track)) { playlists.onAdd(playlist, track) }
            }
        }
        NoteLine(u, playlists.state.note?.takeIf { playlists.state.trackId == track.idString })
    }
    if (naming) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { name -> naming = false; playlists.onCreate(name, track) },
            onDismiss = { naming = false }
        )
    }
}

@Composable
private fun NewPlaylistRow(u: Scale, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u(22f))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(80f))
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = u(12f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dash = Color.White.copy(alpha = 0.34f)
        Box(
            Modifier
                .size(u(56f))
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        dash, cornerRadius = CornerRadius(14.dp.toPx() * u.factor),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(26f)))
        }
        Spacer(Modifier.width(u(16f)))
        Text(stringResource(R.string.car_playlist_new), color = Color.White, fontSize = u.sp(22f), lineHeight = u.sp(28f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistRow(u: Scale, playlist: UserPlaylistDto, mark: QuickMark?, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(u(22f))
    val title = playlist.title.orEmpty()
    val addLabel = stringResource(R.string.car_playlist_add_to_cd, title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(80f))
            .clip(shape)
            // A row says what became of the track; only a row without a mark adds, so never twice
            .then(if (mark == null) Modifier.clickable(role = Role.Button, onClickLabel = addLabel, onClick = onAdd) else Modifier)
            .padding(start = u(12f), end = u(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCover(u, playlist)
        Spacer(Modifier.width(u(16f)))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = u.sp(22f), lineHeight = u.sp(28f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                russianQuantityString(playlist.trackCount, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(15f), lineHeight = u.sp(20f), maxLines = 1
            )
        }
        Spacer(Modifier.width(u(12f)))
        when (mark) {
            null -> Box(
                Modifier
                    .size(u(56f))
                    .hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f))
                    .semantics { contentDescription = addLabel },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(26f))) }
            QuickMark.ADDING -> Box(Modifier.size(u(56f)), contentAlignment = Alignment.Center) { HiFiActivityIndicator(size = u(24f)) }
            QuickMark.ADDED -> MarkPill(u, Icons.Rounded.Check, stringResource(R.string.car_playlist_added))
            QuickMark.ALREADY -> MarkPill(u, Icons.Rounded.Check, stringResource(R.string.car_playlist_already))
            QuickMark.WAITING -> MarkPill(u, Icons.Rounded.Schedule, stringResource(R.string.car_playlist_waiting))
        }
    }
}

@Composable
private fun MarkPill(u: Scale, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .heightIn(min = u(40f))
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = u(14f), vertical = u(6f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(20f)))
        Spacer(Modifier.width(u(6f)))
        Text(text, color = Color.White, fontSize = u.sp(15f), lineHeight = u.sp(20f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PlaylistCover(u: Scale, playlist: UserPlaylistDto) {
    val shape = RoundedCornerShape(u(14f))
    Box(
        Modifier
            .size(u(56f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2C303B), Color(0xFF14161C)))),
        contentAlignment = Alignment.Center
    ) {
        val uri = playlist.coverUri
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).cover(uri, CoverSize.Small).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(u(26f)))
        }
    }
}

/** What happened last, under the list (the list gives it room): it fades after a few seconds. */
@Composable
private fun NoteLine(u: Scale, note: com.korenskoy.yandexmusic.presentation.main.QuickNote?) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note?.id) {
        shown = note?.text ?: return@LaunchedEffect
        delay(NOTE_MS)
        shown = null
    }
    AnimatedVisibility(visible = shown != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = u(8f))
                .heightIn(min = u(52f))
                .clip(CircleShape)
                .background(HiFi.Paper)
                .padding(horizontal = u(20f), vertical = u(10f))
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center
        ) {
            Text(
                shown.orEmpty(), color = Color(0xFF0B0D12), fontSize = u.sp(17f), lineHeight = u.sp(22f), fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    }
}

private const val NOTE_MS = 3_000L

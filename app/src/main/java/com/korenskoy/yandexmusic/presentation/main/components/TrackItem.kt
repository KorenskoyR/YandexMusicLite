package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import kotlin.math.sin

/**
 * A track row of every list: cover, title and artist, the downloaded mark, a glass heart and "⋮".
 * The playing track is marked like in the player's queue — a white bold title and a small equalizer
 * over its cover — not with a colour. A long press opens the track menu.
 */
@Composable
fun TrackItem(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onToggleLike: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val menu = LocalTrackMenu.current
    val menuPlaylist = LocalTrackMenuPlaylist.current
    val openMenu: (() -> Unit)? = menu?.let { { it.open(track, menuPlaylist) } }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hifiPress(
                onClick = onClick,
                role = null,
                onLongClick = {
                    onLongClick()
                    openMenu?.invoke()
                }
            )
            // The menu button carries its own touch padding
            .padding(start = 16.dp, end = if (openMenu != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HiFi.Placeholder),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                // Asked for at the size it is shown: a list never decodes full-size covers
                model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Small).build(),
                contentDescription = stringResource(R.string.cd_oblozhka),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    PlayingBars(size = 18.dp)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.bez_nazvaniya),
                style = HiFiType.Body.copy(fontSize = HiFiType.RowTitle.fontSize, lineHeight = HiFiType.RowTitle.lineHeight),
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artists.joinToString { it.name ?: "" }.ifEmpty { stringResource(R.string.player_unknown_artist) },
                style = HiFiType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isDownloaded) {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                tint = Color.White.copy(alpha = 0.62f),
                contentDescription = stringResource(R.string.cd_skachan),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        // A 44 dp touch area around the 34 dp glass circle
        val likeDescription = stringResource(R.string.cd_nravitsya)
        Box(
            modifier = Modifier
                .size(44.dp)
                .hifiPress(onToggleLike, pressedScale = 0.9f)
                .semantics { contentDescription = likeDescription },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(34.dp).hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    tint = if (isLiked) HiFi.Love else Color.White.copy(alpha = 0.6f),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        if (openMenu != null) {
            val menuDescription = stringResource(R.string.cd_track_menu)
            Box(
                Modifier
                    .size(40.dp)
                    .hifiPress(openMenu, pressedScale = 0.9f)
                    .semantics { contentDescription = menuDescription },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Three white bars bouncing like a level meter — the mark of the playing track. Drawn and animated in the
 * draw phase (nothing recomposes per frame); still in lite mode.
 */
@Composable
fun PlayingBars(size: Dp, modifier: Modifier = Modifier, color: Color = Color.White) {
    val phase: State<Float>? = if (!LocalHiFiLite.current) {
        rememberInfiniteTransition(label = "bars").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "bars_phase"
        )
    } else null
    Spacer(
        modifier
            .size(size)
            .drawBehind {
                val bar = this.size.width / 5f
                val p = phase?.value ?: 0f
                for (i in 0 until 3) {
                    val level = if (phase == null) 0.5f + 0.2f * (i % 2) else 0.25f + 0.75f * ((sin(p * (1f + i * 0.35f) + i * 1.7f) + 1f) / 2f)
                    val h = this.size.height * level
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * bar * 2f, this.size.height - h),
                        size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2f)
                    )
                }
            }
    )
}

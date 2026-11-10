package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Close
import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass

/* What plays next in the car: the "Очередь" button and panel, and the "Далее" row. */

/** How far Android's "font size" may grow the car screen's own, already large, type: not at all. */
internal const val CAR_MAX_FONT_SCALE = 1f

/** How many tracks the "Далее" row shows. */
const val CAR_NEXT_COUNT = 4

/** How far ahead the car's queue looks: the row shows the first [CAR_NEXT_COUNT], the panel all of them. */
const val CAR_QUEUE_LIMIT = 50

/** How far a finger travels across the car screen to switch a track. */
internal val CarSwipeThreshold = 72.dp

/** A swipe's result: 1 — back a track (to the right), -1 — on to the next (to the left), 0 — too short. */
internal fun carSwipeStep(travelledPx: Float, thresholdPx: Float): Int = when {
    travelledPx >= thresholdPx -> 1
    travelledPx <= -thresholdPx -> -1
    else -> 0
}

/** "Очередь · 24 трека": the glass button under the biggest cover. */
@Composable
internal fun QueuePill(u: Scale, count: String, onClick: () -> Unit) {
    val description = stringResource(R.string.car_queue_cd, count)
    Row(
        Modifier
            .height(u(64f))
            .hifiGlass(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = u(28f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(24f)))
        Spacer(Modifier.width(u(12f)))
        Text(stringResource(R.string.car_queue), color = Color.White, fontSize = u.sp(20f), fontWeight = FontWeight.Bold, maxLines = 1)
        Text(" · $count", color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(20f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The queue over the cover's place: what plays now, then what follows; a tap plays that track. */
@Composable
internal fun CarQueuePanel(
    u: Scale,
    modifier: Modifier,
    caption: String,
    current: TrackDetailsDto?,
    upcoming: List<TrackDetailsDto>,
    loading: Boolean,
    onClick: (TrackDetailsDto) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(30f)))
            .background(HiFi.SheetFill.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = u(16f), end = u(16f), top = u(18f), bottom = u(8f))
    ) {
        Row(Modifier.padding(start = u(8f), end = u(2f), bottom = u(8f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.car_queue), color = Color.White, fontSize = u.sp(28f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(caption, color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(u(4f))
        ) {
            current?.let { track -> item(key = "now") { QueueRow(u, track, isCurrent = true, onClick = null) } }
            items(upcoming.size, key = { "q" + it + upcoming[it].idString }) { index ->
                val track = upcoming[index]
                QueueRow(u, track, isCurrent = false) { onClick(track) }
            }
            if (loading && upcoming.isEmpty()) items(3, key = { "wait$it" }) { QueueRowPlaceholder(u) }
            if (!loading && upcoming.isEmpty()) item(key = "empty") {
                Text(
                    stringResource(R.string.car_queue_empty),
                    color = Color.White.copy(alpha = 0.62f), fontSize = u.sp(17f),
                    modifier = Modifier.padding(horizontal = u(10f), vertical = u(16f))
                )
            }
        }
    }
}

@Composable
private fun QueueRow(u: Scale, track: TrackDetailsDto, isCurrent: Boolean, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val title = track.title ?: stringResource(R.string.bez_nazvaniya)
    val shape = RoundedCornerShape(u(18f))
    val description = if (onClick != null) stringResource(R.string.car_next_play_cd, title) else title
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = u(84f))
            .clip(shape)
            .then(if (isCurrent) Modifier.background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = u(10f), vertical = u(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(u(68f)).clip(RoundedCornerShape(u(14f))).background(HiFi.Placeholder), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Small).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Spacer(Modifier.width(u(16f)))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = u.sp(22f), lineHeight = u.sp(28f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Text(stringResource(R.string.car_queue_now) + " · ", color = Color.White.copy(alpha = 0.86f), fontSize = u.sp(13f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em, maxLines = 1)
                }
                Text(
                    track.artists.joinToString { it.name ?: "" },
                    color = Color.White.copy(alpha = 0.64f), fontSize = u.sp(17f), lineHeight = u.sp(22f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QueueRowPlaceholder(u: Scale) {
    val fill = Color.White.copy(alpha = 0.06f)
    Row(Modifier.fillMaxWidth().height(u(84f)).padding(horizontal = u(10f), vertical = u(8f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(u(68f)).clip(RoundedCornerShape(u(14f))).background(fill))
        Spacer(Modifier.width(u(16f)))
        Column {
            Box(Modifier.width(u(200f)).height(u(16f)).clip(RoundedCornerShape(u(6f))).background(fill))
            Spacer(Modifier.height(u(10f)))
            Box(Modifier.width(u(120f)).height(u(12f)).clip(RoundedCornerShape(u(5f))).background(fill))
        }
    }
}

/**
 * What comes next: up to four small covers with title and artist, so the current cover stays the big one.
 * Fixed, not a carousel: a swipe on the car screen switches tracks, and nothing slides under the driver's
 * finger. While the wave is still fetching, placeholders of the same size keep the place.
 */
@Composable
internal fun NextRow(
    u: Scale,
    caption: String,
    tracks: List<TrackDetailsDto>,
    loading: Boolean,
    maxCard: Dp,
    gap: Dp,
    maxWidth: Dp = Dp.Infinity,
    onClick: (TrackDetailsDto) -> Unit,
    onCaptionClick: (() -> Unit)? = null
) {
    if (tracks.isEmpty() && !loading) return
    val limit = maxWidth
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val n = CAR_NEXT_COUNT
        val card = ((androidx.compose.ui.unit.min(this.maxWidth, limit) - gap * (n - 1)) / n).coerceAtMost(maxCard)
        Column(Modifier.width(card * n + gap * (n - 1))) {
            Text(
                caption.uppercase() + if (onCaptionClick != null) " ›" else "",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = u.sp(13f),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.16.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onCaptionClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(u(8f)))
                        .clickable(role = Role.Button, onClick = onCaptionClick)
                        .padding(vertical = u(4f))
                } else Modifier
            )
            Spacer(Modifier.height(u(10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(n) { slot ->
                    AnimatedContent(
                        targetState = tracks.getOrNull(slot),
                        contentKey = { it?.idString },
                        transitionSpec = { fadeIn(HiFiMotion.large()) togetherWith fadeOut(HiFiMotion.quick()) },
                        label = "car_next_$slot"
                    ) { track ->
                        when {
                            track != null -> NextCard(u, track, card) { onClick(track) }
                            loading -> NextCardPlaceholder(u, card)
                            else -> Spacer(Modifier.width(card))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(u: Scale, track: TrackDetailsDto, size: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(u(14f))
    val title = track.title ?: stringResource(R.string.bez_nazvaniya)
    val description = stringResource(R.string.car_next_play_cd, title)
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(u(14f)))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Medium).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .hifiSoftShadow(shape, u(12f))
                .clip(shape)
                .background(HiFi.Placeholder)
        )
        Spacer(Modifier.height(u(8f)))
        Text(title, color = Color.White, fontSize = u.sp(15f), lineHeight = u.sp(19f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            track.artists.joinToString { it.name ?: "" },
            color = Color.White.copy(alpha = 0.66f),
            fontSize = u.sp(13f),
            lineHeight = u.sp(17f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Exactly a card's height (cover, 8, lines of 19 and 17), so the row does not jump when the tracks arrive. */
@Composable
private fun NextCardPlaceholder(u: Scale, size: Dp) {
    val fill = Color.White.copy(alpha = 0.06f)
    Column(Modifier.width(size)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(u(14f))).background(fill))
        Spacer(Modifier.height(u(13f)))
        Box(Modifier.width(size * 0.72f).height(u(11f)).clip(RoundedCornerShape(u(5f))).background(fill))
        Spacer(Modifier.height(u(8f)))
        Box(Modifier.width(size * 0.48f).height(u(9f)).clip(RoundedCornerShape(u(4f))).background(fill))
        Spacer(Modifier.height(u(3f)))
    }
}

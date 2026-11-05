package com.korenskoy.yandexmusic.presentation.player

import com.korenskoy.yandexmusic.utils.cover
import com.korenskoy.yandexmusic.utils.CoverSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import com.korenskoy.yandexmusic.presentation.hifi.hifiPress
import com.korenskoy.yandexmusic.presentation.hifi.reorderHandle
import com.korenskoy.yandexmusic.presentation.hifi.reorderItem
import com.korenskoy.yandexmusic.presentation.hifi.rememberReorderState
import com.korenskoy.yandexmusic.presentation.hifi.rememberPredictiveBack
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.player.components.AudioVisualizer

// --- Queue sheet ------------------------------------------------------------------------------------

/** The queue as a dark sheet from the bottom: "Сейчас играет" on anodized metal, then what follows. */
@Composable
internal fun QueueSheet(
    visible: Boolean,
    split: QueueSplit,
    isPlaying: Boolean,
    sourceLabel: String,
    isFetchingMore: Boolean,
    fullHeight: Boolean,
    onDismiss: () -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onLoadMore: () -> Unit
) {
    // Back closes the sheet first; the predictive gesture lowers it under the finger
    val back = rememberPredictiveBack(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(HiFiMotion.standard()), exit = fadeOut(HiFiMotion.quick())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(HiFiMotion.large()) { it },
        exit = slideOutVertically(HiFiMotion.standard()) { it },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            QueueSheetContent(
                split = split,
                isPlaying = isPlaying,
                sourceLabel = sourceLabel,
                isFetchingMore = isFetchingMore,
                onDismiss = onDismiss,
                onTrackSelect = onTrackSelect,
                onRemove = onRemove,
                onMove = onMove,
                onLoadMore = onLoadMore,
                modifier = Modifier
                    .graphicsLayer { translationY = back.value * 160.dp.toPx() }
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (fullHeight) 0.94f else 0.78f)
            )
        }
    }
}

@Composable
private fun QueueSheetContent(
    split: QueueSplit,
    isPlaying: Boolean,
    sourceLabel: String,
    isFetchingMore: Boolean,
    onDismiss: () -> Unit,
    onTrackSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    val nextIds = remember(split.next) { split.next.map { it.idString }.toHashSet() }
    val fetching by rememberUpdatedState(isFetchingMore)
    val loadMore by rememberUpdatedState(onLoadMore)
    // Prefetch as soon as the list is within 12 rows of its end
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 12 && !fetching) loadMore()
        }
    }

    // Pull the sheet down by its header to close it
    val density = LocalDensity.current
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closeAt = with(density) { 120.dp.toPx() }
    val dismiss by rememberUpdatedState(onDismiss)
    val total = split.next.size + if (split.current != null) 1 else 0
    val subtitle = listOfNotNull(
        sourceLabel.takeIf { it.isNotEmpty() },
        pluralStringResource(R.plurals.player_queue_tracks, total, total)
    ).joinToString(" · ")

    Column(
        modifier
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(HiFi.SheetFill)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.14f), Offset(28.dp.toPx(), 0.5f), Offset(size.width - 28.dp.toPx(), 0.5f), 1f) }
            // Swallow taps so they do not reach the scrim behind the sheet
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                onDragStopped = {
                    if (drag.value > closeAt) dismiss()
                    else drag.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                }
            )
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.player_queue_title),
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em
                    )
                    Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircleButton(onClick = onDismiss, contentDescription = stringResource(R.string.cd_zakryt), size = 44.dp) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            split.current?.let { now ->
                item(key = "now_caption") { QueueCaption(stringResource(R.string.full_player_seychas_igraet), top = 12.dp) }
                item(key = "now_row") { NowPlayingRow(now, isPlaying) }
            }
            if (split.next.isNotEmpty()) {
                item(key = "next_caption") { QueueCaption(stringResource(R.string.full_player_dalee), top = 18.dp) }
            }
            itemsIndexed(split.next, key = { _, t -> t.idString }) { index, item ->
                val id = item.idString
                val dragged = reorder.draggedKey == id
                QueueRow(
                    item = item,
                    modifier = Modifier
                        // Neighbours glide into their new places; the dragged row follows the finger instead
                        .animateItem(placementSpec = if (dragged) null else HiFiMotion.spring())
                        .reorderItem(reorder, id)
                        .reorderHandle(reorder, id, canMoveTo = { it in nextIds }, afterLongPress = true) { from, to ->
                            onMove(from as String, to as String)
                        },
                    moveUp = split.next.getOrNull(index - 1)?.let { prev -> { onMove(id, prev.idString) } },
                    moveDown = split.next.getOrNull(index + 1)?.let { nxt -> { onMove(id, nxt.idString) } },
                    onClick = {
                        onTrackSelect(id)
                        onDismiss()
                    },
                    onRemove = { onRemove(id) }
                )
            }
            if (isFetchingMore) {
                items(2, key = { "skeleton_$it" }) { QueueSkeletonRow() }
            }
        }
    }
}

@Composable
private fun QueueCaption(text: String, top: Dp) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.10.em,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = top, bottom = 8.dp)
    )
}

@Composable
private fun QueueCover(track: TrackDetailsDto) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).cover(track.coverUri, CoverSize.Small).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
    )
}

@Composable
private fun QueueTexts(track: TrackDetailsDto, bold: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(
            track.title ?: stringResource(R.string.bez_nazvaniya),
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            track.artists.joinToString(", ") { it.name ?: "" },
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NowPlayingRow(track: TrackDetailsDto, isPlaying: Boolean) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .hifiAnodized(RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QueueCover(track)
        QueueTexts(track, bold = true, modifier = Modifier.weight(1f))
        AudioVisualizer(isPlaying = isPlaying, isCompact = true, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun QueueRow(
    item: TrackDetailsDto,
    modifier: Modifier,
    moveUp: (() -> Unit)?,
    moveDown: (() -> Unit)?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    // Swipe left to remove: a red "Убрать" plate shows under the row
    val swipe = rememberSwipeToDismissBoxState()
    val remove by rememberUpdatedState(onRemove)
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) remove()
    }
    val upLabel = stringResource(R.string.action_move_up)
    val downLabel = stringResource(R.string.action_move_down)
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        modifier = modifier.padding(start = 12.dp, end = 4.dp),
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HiFi.Danger.copy(alpha = 0.22f))
                    .padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.queue_remove), color = HiFi.Danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HiFi.SheetFill)
            .hifiPress(onClick, role = null)
            .semantics {
                customActions = listOfNotNull(
                    moveUp?.let { CustomAccessibilityAction(upLabel) { it(); true } },
                    moveDown?.let { CustomAccessibilityAction(downLabel) { it(); true } }
                )
            }
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QueueCover(item)
        QueueTexts(item, bold = false, modifier = Modifier.weight(1f))
        val removeDescription = stringResource(R.string.cd_udalit_iz_ocheredi)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeDescription },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(30.dp).hifiGlass(CircleShape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
    }
}

/** A row-shaped placeholder while more of the queue loads; the shimmer is read in the draw phase. */
@Composable
private fun QueueSkeletonRow() {
    val lite = LocalHiFiLite.current
    val pulse: State<Float>? = if (!lite) {
        rememberInfiniteTransition(label = "queue_skeleton").animateFloat(
            initialValue = 0.06f,
            targetValue = 0.14f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "queue_skeleton_alpha"
        )
    } else null
    val loading = stringResource(R.string.player_queue_loading)
    Row(
        Modifier
            .padding(start = 22.dp, end = 16.dp)
            .fillMaxWidth()
            .height(62.dp)
            .semantics { contentDescription = loading },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val block = Modifier.drawBehind {
            drawRoundRect(Color.White.copy(alpha = pulse?.value ?: 0.08f), cornerRadius = CornerRadius(size.height / 2f))
        }
        Box(
            Modifier.size(46.dp).drawBehind {
                drawRoundRect(Color.White.copy(alpha = pulse?.value ?: 0.08f), cornerRadius = CornerRadius(10.dp.toPx()))
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 150.dp, height = 12.dp).then(block))
            Box(Modifier.size(width = 90.dp, height = 10.dp).then(block))
        }
    }
}

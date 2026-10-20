package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import com.korenskoy.yandexmusic.utils.browserUserAgent
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import com.korenskoy.yandexmusic.utils.toCoverUrl

@Composable
fun MiniPlayer(
    track: TrackDetailsDto,
    isPlaying: Boolean,
    isLiked: Boolean,
    progress: Long,
    onPlayPauseClick: () -> Unit,
    onToggleLike: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(track.idString) {
        offsetX.snapTo(0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { 
                alpha = 1f - (abs(offsetX.value) / (300f * density.density)).coerceIn(0f, 0.8f) 
            }
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x99000000),
                ambientColor = Color(0x55000000)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xE6242432),
                        Color(0xD9181824),
                        Color(0xE6242432)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x4DFFFFFF),
                            Color(0x15FFFFFF),
                            Color(0x33FFFFFF)
                        )
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(offsetX.value) > 100.dp.toPx()) {
                            scope.launch {
                                offsetX.animateTo(if (offsetX.value > 0) 1000f else -1000f)
                                onDismiss()
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val coverUrl = track.coverUri.toCoverUrl("100x100") ?: ""

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (track.isRadio) Color(0xFF282836) else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .apply {
                            if (track.isRadio) {
                                browserUserAgent()
                            }
                        }
                        .build(),
                    contentDescription = stringResource(R.string.cd_oblozhka),
                    contentScale = if (track.isRadio) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (track.isRadio) 3.dp else 0.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: "Без названия",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists.joinToString { it.name ?: "" }.ifEmpty { "Неизвестный исполнитель" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (isLiked) 8.dp else 4.dp,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        spotColor = if (isLiked) Color(0xFFFF3366).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f)
                    )
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isLiked) {
                            Brush.verticalGradient(listOf(Color(0x44FF3366), Color(0x22FF1144)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0x352A2A38), Color(0x201E1E28)))
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isLiked) {
                                Brush.verticalGradient(listOf(Color(0xFFFF3366), Color(0x66FF3366)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF)))
                            }
                        ),
                        androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onToggleLike() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_nravitsya),
                    tint = if (isLiked) Color(0xFFFF3366) else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (isPlaying) 12.dp else 4.dp,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        spotColor = Color(0xFFFF3366).copy(alpha = if (isPlaying) 0.7f else 0.3f)
                    )
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF3366), Color(0xFFFF7722), Color(0xFFFF9900))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.25f))
                            )
                        ),
                        androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onPlayPauseClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_vosproizvedenie_ili_pauza),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Progress bar at bottom
        val durationMs = track.durationMs ?: 1L
        val targetProgressFraction = if (durationMs > 0 && progress > 0L) {
            (progress.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        val progressFraction by androidx.compose.animation.core.animateFloatAsState(
            targetValue = targetProgressFraction,
            animationSpec = if (targetProgressFraction == 0f) {
                androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            } else {
                androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing)
            },
            label = stringResource(R.string.mini_player_mini_progress)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.BottomStart)
        )
    }
}

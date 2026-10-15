package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

import androidx.compose.material.icons.filled.CheckCircle

@OptIn(ExperimentalFoundationApi::class)
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
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { 
                    onClick() 
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val coverUrl = track.coverUri?.let { uri ->
            if (uri.startsWith("http")) uri else "https://${uri.replace("%%", "100x100")}"
        } ?: ""

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizer()
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title ?: "Без названия",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Lossless badge mockup
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HQ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artists?.joinToString { it.name ?: "" } ?: "Неизвестный исполнитель",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                tint = Color(0xFF4CAF50),
                contentDescription = "Downloaded",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .shadow(
                    elevation = if (isLiked) 6.dp else 2.dp,
                    shape = CircleShape,
                    spotColor = if (isLiked) Color(0xFFFF3366).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(
                    if (isLiked) {
                        Brush.verticalGradient(
                            listOf(Color(0x44FF3366), Color(0x22FF1144))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0x252A2A38), Color(0x151E1E28))
                        )
                    }
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isLiked) {
                            Brush.verticalGradient(
                                listOf(Color(0xFFFF3366), Color(0x66FF3366))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0x35FFFFFF), Color(0x10FFFFFF))
                            )
                        }
                    ),
                    CircleShape
                )
                .clickable { onToggleLike() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (isLiked) Color(0xFFFF3366) else Color.White.copy(alpha = 0.45f),
                contentDescription = "Like",
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
fun AnimatedEqualizer() {
    val transition = rememberInfiniteTransition()
    
    val bar1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier.size(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar1).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar2).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar3).background(MaterialTheme.colorScheme.primary))
    }
}

package com.korenskoy.yandexmusic.presentation.radio

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.crossfade
import com.korenskoy.yandexmusic.utils.browserUserAgent
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    stations: List<RadioStation>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: RadioCategory,
    onCategorySelect: (RadioCategory) -> Unit,
    currentStationId: String?,
    isPlaying: Boolean,
    favoriteStationIds: Set<String>,
    onStationClick: (RadioStation) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasActiveTrack: Boolean = false
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val contentStart = if (isLandscape) 140.dp else 16.dp
    val contentBottom = if (isLandscape) 90.dp else (if (hasActiveTrack) 168.dp else 100.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF261A10),
                        Color(0xFF1B1410),
                        Color(0xFF101014)
                    )
                )
            )
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Hero Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = contentStart, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF9900), Color(0xFFFF5500))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "FM Радио",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "FM Радио",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = "${stations.size} радиостанций",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск радиостанций или жанра", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1E24),
                unfocusedContainerColor = Color(0xFF1E1E24),
                disabledContainerColor = Color(0xFF1E1E24),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = contentStart, end = 16.dp, top = 8.dp, bottom = 8.dp)
        )

        // Categories
        LazyRow(
            contentPadding = PaddingValues(start = contentStart, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(RadioCategory.entries.toTypedArray()) { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = if (isSelected) 10.dp else 4.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = if (isSelected) Color(0xFFFF9900).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) {
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF9900), Color(0xFFFF7700), Color(0xFFFF5500))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0x352A2A38), Color(0x201E1E28))
                                )
                            }
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.2f)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF)))
                                }
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onCategorySelect(category) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (category == RadioCategory.FAVORITES) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFFFF3366),
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = category.title,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stations Grid
        if (stations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (selectedCategory == RadioCategory.FAVORITES) {
                    Text(
                        text = "В избранном пока нет станций.\nНажмите на сердечко на карточке станции, чтобы добавить её сюда.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp)
                    )
                } else {
                    Text(
                        text = "Станции не найдены.\nПопробуйте изменить запрос или категорию.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 145.dp),
                contentPadding = PaddingValues(start = contentStart, end = 16.dp, top = 8.dp, bottom = contentBottom),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                items(stations, key = { it.id }) { station ->
                    val isStationPlaying = isPlaying && currentStationId == station.id
                    val isFavorite = favoriteStationIds.contains(station.id)
                    RadioStationCard(
                        station = station,
                        isPlaying = isStationPlaying,
                        isFavorite = isFavorite,
                        onClick = { onStationClick(station) },
                        onToggleFavorite = { onToggleFavorite(station.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RadioStationCard(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(station.brandColorStart).copy(alpha = 0.5f),
            Color(station.brandColorEnd).copy(alpha = 0.25f),
            Color(0xFF0F0F14)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .shadow(
                elevation = if (isPlaying) 16.dp else 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isPlaying) Color(0xFFFF9900).copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBrush)
            .border(
                BorderStroke(
                    if (isPlaying) 1.5.dp else 1.dp,
                    if (isPlaying) Color(0xFFFF9900) else Color.White.copy(alpha = 0.08f)
                ),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
    ) {
        // Center: High-Res Original Station Logo with Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Brand Glow behind Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(station.brandColorStart).copy(alpha = 0.5f),
                                Color(station.brandColorEnd).copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(station.iconRes ?: station.logoUrl)
                    .crossfade(true)
                    .browserUserAgent()
                    .build(),
                contentDescription = station.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(90.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(16.dp)),
                alpha = 1.0f,
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = station.name.take(1).uppercase(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    }
                }
            )
        }

        // Top Right: Favorite Button (Frosted Glass Capsule)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(34.dp)
                .shadow(
                    elevation = if (isFavorite) 8.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = if (isFavorite) Color(0xFFFF3366).copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.4f)
                )
                .clip(CircleShape)
                .background(
                    if (isFavorite) {
                        Brush.verticalGradient(listOf(Color(0x55FF3366), Color(0x33FF1144)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0x501A1A24), Color(0x3012121A)))
                    }
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isFavorite) {
                            Brush.verticalGradient(listOf(Color(0xFFFF3366), Color(0x66FF3366)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x15FFFFFF)))
                        }
                    ),
                    CircleShape
                )
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF3366) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Bottom Bar: Title & Category Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = station.category.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isPlaying) {
                    Spacer(modifier = Modifier.width(6.dp))
                    NeonEqualizer()
                }
            }
        }
    }
}

@Composable
fun NeonEqualizer() {
    val infiniteTransition = rememberInfiniteTransition()
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(20.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(height1.dp).clip(CircleShape).background(Color(0xFFFF9900)))
        Box(modifier = Modifier.width(3.dp).height(height2.dp).clip(CircleShape).background(Color(0xFFFF9900)))
        Box(modifier = Modifier.width(3.dp).height(height3.dp).clip(CircleShape).background(Color(0xFFFF9900)))
    }
}

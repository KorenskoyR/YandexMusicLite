package com.korenskoy.yandexmusic.presentation.main

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.DownloadProgressInfo
import com.korenskoy.yandexmusic.presentation.main.components.CacheMenuBottomSheet
import com.korenskoy.yandexmusic.presentation.main.components.DownloadProgressModal
import com.korenskoy.yandexmusic.presentation.main.components.TrackItem

@Composable
fun LibraryScreen(
    tracks: List<TrackDetailsDto>,
    isLoading: Boolean,
    isDownloadingAll: Boolean,
    autoDownloadEnabled: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    cacheSizeBytes: Long,
    downloadProgress: DownloadProgressInfo?,
    onTrackClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadAllClick: () -> Unit,
    onEnableAutoDownload: (Boolean) -> Unit,
    crossfadeSeconds: Int = 3,
    onCrossfadeChange: (Int) -> Unit = {},
    onCancelDownload: () -> Unit,
    onClearCache: () -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onRefresh: () -> Unit = {},
    hasActiveTrack: Boolean = false,
    accountName: String = "",
    avatarUrl: String? = null,
    defaultAccountName: String? = null,
    updateStatus: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onAccountManagementClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var showCacheMenu by remember { mutableStateOf(false) }
    var showProgressModal by remember { mutableStateOf(false) }
    var showOnlyDownloaded by remember { mutableStateOf(false) }
    
    val filteredTracks = if (showOnlyDownloaded) tracks.filter { downloadedTrackIds.contains(it.idString) } else tracks
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val contentStart = if (isLandscape) 140.dp else 16.dp
    val contentBottom = if (isLandscape) 90.dp else (if (hasActiveTrack) 168.dp else 100.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF26141F),
                        Color(0xFF1B1218),
                        Color(0xFF111115)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Compact Premium Hero Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = contentStart, end = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFFF3366), Color(0xFFFF8833))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.cd_lyubimye_treki),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = stringResource(R.string.library_mne_nravitsya),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(
                                text = com.korenskoy.yandexmusic.utils.russianQuantityString(tracks.size, R.string.tracks_count_one, R.string.tracks_count_few, R.string.tracks_count_many),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    // Liquid Glass Header Toolbar Capsule (Refresh + Settings)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_obnovit),
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isDownloadingAll) {
                                    showProgressModal = true
                                } else {
                                    showCacheMenu = true
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            if (isDownloadingAll) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.cd_nastroyki_kesha),
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play All Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .shadow(12.dp, CircleShape, spotColor = Color(0xFFFF3366).copy(alpha = 0.5f))
                            .clip(CircleShape)
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
                                CircleShape
                            )
                            .clickable { onPlayAllClick() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_slushat), tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.cd_slushat), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    
                    // Shuffle Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x352A2A38), Color(0x201E1E28))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.2.dp,
                                    Brush.verticalGradient(
                                        listOf(Color(0x45FFFFFF), Color(0x15FFFFFF))
                                    )
                                ),
                                CircleShape
                            )
                            .clickable { onShuffleClick() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.cd_peremeshat), tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.cd_peremeshat), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Only Downloaded Toggle
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .shadow(
                            elevation = if (showOnlyDownloaded) 8.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = if (showOnlyDownloaded) Color(0xFF00E676).copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (showOnlyDownloaded) {
                                Brush.verticalGradient(listOf(Color(0x4400E676), Color(0x2200C853)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0x352A2A38), Color(0x201E1E28)))
                            }
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (showOnlyDownloaded) {
                                    Brush.verticalGradient(listOf(Color(0xFF00E676), Color(0x6600E676)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x10FFFFFF)))
                                }
                            ),
                            CircleShape
                        )
                        .clickable { showOnlyDownloaded = !showOnlyDownloaded }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = stringResource(R.string.cd_tolko_skachannye),
                        tint = if (showOnlyDownloaded) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.cd_tolko_skachannye),
                        color = if (showOnlyDownloaded) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (isLandscape) 104.dp else 16.dp, end = 16.dp, bottom = contentBottom),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (showOnlyDownloaded) "Нет скачанных треков" else "Медиатека пуста или загружается",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showOnlyDownloaded) "Скачайте треки или снимите фильтр" else "Нажмите кнопку, чтобы загрузить любимые треки с вашего аккаунта",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.library_obnovit_mediateku), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentBottom),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = if (isLandscape) 140.dp else 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0x1AFFFFFF)) // Glassmorphism container
                    .padding(8.dp)
            ) {
                if (isLandscape) {
                    items(filteredTracks.chunked(2), key = { row -> row.joinToString("|") { it.idString } }) { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowItems.forEach { track ->
                                Box(modifier = Modifier.weight(1f)) {
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
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredTracks, key = { it.idString }) { track ->
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

    if (showCacheMenu) {
        CacheMenuBottomSheet(
            onDismiss = { showCacheMenu = false },
            cacheSizeBytes = cacheSizeBytes,
            autoDownloadEnabled = autoDownloadEnabled,
            // The switch value is passed through, so auto-download can be turned off again
            onEnableAutoDownload = onEnableAutoDownload,
            crossfadeSeconds = crossfadeSeconds,
            onCrossfadeChange = onCrossfadeChange,
            onDownloadAllClick = {
                onDownloadAllClick()
                showProgressModal = true
            },
            onClearCache = onClearCache,
            accountName = accountName,
            avatarUrl = avatarUrl,
            defaultAccountName = defaultAccountName,
            updateStatus = updateStatus,
            onCheckForUpdates = onCheckForUpdates,
            onAccountManagementClick = onAccountManagementClick,
            onLogout = onLogout
        )
    }

    if (showProgressModal && downloadProgress != null) {
        DownloadProgressModal(
            progressInfo = downloadProgress,
            onDismiss = { showProgressModal = false },
            onCancel = {
                onCancelDownload()
                showProgressModal = false
            }
        )
    }
    }
}

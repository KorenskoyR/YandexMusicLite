package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun CacheMenuBottomSheet(
    onDismiss: () -> Unit,
    cacheSizeBytes: Long,
    autoDownloadEnabled: Boolean,
    onEnableAutoDownload: (Boolean) -> Unit,
    onDownloadAllClick: () -> Unit,
    onClearCache: () -> Unit,
    accountName: String = "",
    updateStatus: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onAccountManagementClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val cacheSizeMb = cacheSizeBytes / (1024.0 * 1024.0)
    val cacheSizeFormatted = String.format(Locale.US, "%.1f МБ", cacheSizeMb)
    val scrollState = rememberScrollState()

    // Fullscreen Glass Backdrop with Centered Floating Modal
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(220)),
            exit = scaleOut(targetScale = 0.92f) + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
                    .wrapContentHeight()
                    .heightIn(max = 640.dp)
                    .shadow(36.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.85f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF23232C),
                                Color(0xFF16161E)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Prevent click through to backdrop */ }
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Header Bar (Title + Close Button) - pinned outside verticalScroll to prevent any clipping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Настройки медиатеки",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Параметры кэша и синхронизации",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                    ) {
                        // Account Card (if accountName present)
                        if (accountName.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.07f),
                                                Color.White.copy(alpha = 0.03f)
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        onAccountManagementClick()
                                        onDismiss()
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFFF3366), Color(0xFFFF9900))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Аккаунт Яндекс",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = accountName,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Active Status Indicator Badge
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x2200E676))
                                    .border(BorderStroke(1.dp, Color(0x5500E676)), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "В сети",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Unified Storage & Auto-Download Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        // Storage Size Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x2000B4DB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = Color(0xFF00B4DB),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Размер кэша",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Локальные MP3 треки",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            // Formatted size pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = cacheSizeFormatted,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.06f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Auto-Download Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEnableAutoDownload(!autoDownloadEnabled) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (autoDownloadEnabled) Color(0x2200E676) else Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = if (autoDownloadEnabled) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Авто-скачивание",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Сохранять лайкнутые треки офлайн",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = autoDownloadEnabled,
                                onCheckedChange = { onEnableAutoDownload(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFFF3366),
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button: Download All Collection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x2800B4DB), Color(0x1400B4DB))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Color(0x6000B4DB), Color(0x2500B4DB))
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onDownloadAllClick()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00B4DB).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF00B4DB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Скачать всю коллекцию",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Загрузить все треки для прослушивания офлайн",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Button: Clear Cache
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x24FF9900), Color(0x10FF9900))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Color(0x55FF9900), Color(0x20FF9900))
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onClearCache()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9900).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = Color(0xFFFF9900),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Очистить кэш",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Освободить $cacheSizeFormatted локального хранилища",
                                color = Color(0xFFFFB74D).copy(alpha = 0.7f),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Button: Check for Updates & Version info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x1800E5FF), Color(0x0C00E5FF))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Color(0x4000E5FF), Color(0x1500E5FF))
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onCheckForUpdates() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Yandex Music Lite",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v${com.korenskoy.yandexmusic.BuildConfig.VERSION_NAME}",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = updateStatus ?: "Нажмите для проверки обновлений",
                                color = if (updateStatus != null) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Button: Logout / Switch Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x25FF3366), Color(0x10FF3366))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Color(0x65FF3366), Color(0x20FF3366))
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onAccountManagementClick()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3366).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Сменить аккаунт / Выйти",
                                color = Color(0xFFFF5252),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Переключение профилей и настройки входа",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
}


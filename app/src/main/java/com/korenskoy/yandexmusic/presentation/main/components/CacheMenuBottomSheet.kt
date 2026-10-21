package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.material.icons.filled.HighQuality
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
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
    avatarUrl: String? = null,
    defaultAccountName: String? = null,
    updateStatus: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onAccountManagementClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    crossfadeSeconds: Int = 3,
    onCrossfadeChange: (Int) -> Unit = {},
    wifiOnly: Boolean = true,
    onWifiOnlyChange: (Boolean) -> Unit = {},
    economyOnMobile: Boolean = false,
    onEconomyOnMobileChange: (Boolean) -> Unit = {},
    onUpgradeDownloads: () -> Unit = {},
    crashReports: Int = 0,
    onOpenCrashLog: () -> Unit = {}
) {
    val cacheSizeMb = cacheSizeBytes / (1024.0 * 1024.0)
    val cacheSizeFormatted = String.format(Locale.US, "%.1f МБ", cacheSizeMb)
    val scrollState = rememberScrollState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowSize = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val screenHeight = windowSize.heightDp.dp
    val maxModalHeight = if (isLandscape) {
        (screenHeight * 0.94f).coerceAtLeast(340.dp)
    } else {
        (screenHeight * 0.90f).coerceIn(600.dp, 800.dp)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Fullscreen Glass Backdrop with Centered Floating Modal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(
                    start = if (isLandscape) 96.dp else 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                ),
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
                        .fillMaxWidth(if (isLandscape) 0.85f else 0.94f)
                        .widthIn(max = 500.dp)
                        .wrapContentHeight()
                        .heightIn(max = maxModalHeight)
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
                                text = stringResource(R.string.cache_menu_nastroyki_mediateki),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.cache_menu_parametry_kesha_i_sinhronizatsii),
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
                                contentDescription = stringResource(R.string.cd_zakryt),
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFFF3366), Color(0xFFFF9900))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!avatarUrl.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = avatarUrl,
                                        contentDescription = stringResource(R.string.cd_avatar),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        error = {
                                            val initialLetter = (accountName.firstOrNull() ?: 'Я').uppercaseChar()
                                            Text(
                                                text = initialLetter.toString(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        }
                                    )
                                } else {
                                    val initialLetter = (accountName.firstOrNull() ?: 'Я').uppercaseChar()
                                    Text(
                                        text = initialLetter.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cache_menu_akkaunt_yandeks),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            // Quick Switch Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onAccountManagementClick()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cache_menu_smenit),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Default Profile Row
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAccountManagementClick() }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.cache_menu_profil_po_umolchaniyu),
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = defaultAccountName ?: "Спрашивать при старте",
                                color = if (defaultAccountName != null) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                                        text = stringResource(R.string.cache_menu_razmer_kesha),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.cache_menu_lokalnye_mp3_treki),
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
                                    text = stringResource(R.string.cache_menu_avto_skachivanie),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.cache_menu_sohranyat_layknutye_treki_oflayn),
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

                    SwitchSettingCard(
                        title = stringResource(R.string.settings_wifi_only_title),
                        subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                        enabled = wifiOnly,
                        onChange = onWifiOnlyChange
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SwitchSettingCard(
                        title = stringResource(R.string.settings_economy_mobile),
                        subtitle = stringResource(R.string.settings_economy_mobile_hint),
                        enabled = economyOnMobile,
                        onChange = onEconomyOnMobileChange
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ActionSettingCard(
                        title = stringResource(R.string.settings_upgrade_downloads),
                        subtitle = stringResource(R.string.settings_upgrade_downloads_hint),
                        onClick = onUpgradeDownloads
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ActionSettingCard(
                        title = stringResource(R.string.crash_log_title),
                        subtitle = if (crashReports == 0) stringResource(R.string.crash_log_none)
                        else stringResource(R.string.crash_log_count, crashReports),
                        onClick = onOpenCrashLog
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    CrossfadeSettingCard(seconds = crossfadeSeconds, onChange = onCrossfadeChange)

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
                                text = stringResource(R.string.cache_menu_skachat_vsyu_kollektsiyu),
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.cache_menu_zagruzit_vse_treki_dlya),
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
                                text = stringResource(R.string.cache_menu_ochistit_kesh),
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.cache_free_space, cacheSizeFormatted),
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
                                    text = stringResource(R.string.cache_menu_yandex_music_lite),
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
                                text = stringResource(R.string.cache_menu_smenit_akkaunt_vyyti),
                                color = Color(0xFFFF5252),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.account_management_pereklyuchenie_profiley_i_nastroyki),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}
}
}

/** Offline downloads only on Wi-Fi (the default), like "Download over cellular" off in Apple Music. */
@Composable
private fun SwitchSettingCard(title: String, subtitle: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp))
            .clickable { onChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.5.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
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

/** A one-off action in the settings (it runs in the background, progress is in the notification). */
@Composable
private fun ActionSettingCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp)
        }
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.HighQuality,
            contentDescription = null,
            tint = Color(0xFFFF3366)
        )
    }
}

/** Crossfade between tracks, 0–12 s (0 = off), like the setting in Apple Music. */
@Composable
private fun CrossfadeSettingCard(seconds: Int, onChange: (Int) -> Unit) {
    // Local state keeps the slider smooth; the value is saved when the finger is lifted
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cache_menu_krossfeyd),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.cache_menu_plavnyy_perehod_mezhdu_trekami),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.5.sp
                )
            }
            val shown = value.roundToInt()
            Text(
                text = if (shown == 0) "Выкл." else "$shown с",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 0f..12f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFFF3366),
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

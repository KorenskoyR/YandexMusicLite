package com.korenskoy.yandexmusic.presentation.update

import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.rounded.SkipNext
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.HiFiProgressBar
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.data.updater.UpdateCheckResult

import androidx.compose.material.icons.filled.Settings

@Composable
fun UpdateDialog(
    updateInfo: UpdateCheckResult.UpdateAvailable,
    downloadProgress: Float, // -1f = idle, 0f..1f = downloading, 2f = ready to install
    statusMessage: String? = null,
    hasInstallPermission: Boolean = true,
    isSignatureMismatch: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onShareOrSave: () -> Unit = {},
    onDismiss: () -> Unit,
    /** "Пропустить эту версию"; offered until a download starts. */
    onSkip: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(HiFiDialogDefaults.Container)
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_dostupno_obnovlenie),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.cache_menu_yandex_music_lite),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${updateInfo.versionName}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_zakryt),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Changelog Card
                Text(
                    text = stringResource(R.string.update_chto_novogo_v_etoy),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = updateInfo.changelog,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                if (updateInfo.apkSizeBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val sizeMb = String.format(java.util.Locale.US, "%.2f", updateInfo.apkSizeBytes / (1024f * 1024f))
                    Text(
                        text = stringResource(R.string.update_file_size, sizeMb.toString()),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }

                // Download Progress or Status Message
                if (downloadProgress in 0f..1f) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val percent = (downloadProgress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.update_zagruzka_paketa),
                            color = HiFi.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HiFiProgressBar(progress = { downloadProgress }, height = 8.dp)
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage,
                        color = HiFi.Danger,
                        fontSize = 12.sp
                    )
                }

                if (isSignatureMismatch) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HiFi.Warning.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, HiFi.Warning.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = HiFi.Warning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.update_perehod_na_ofitsialnyy_reliz),
                                    color = HiFi.Warning,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.update_etot_reliz_podpisan_ofitsialnym),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                if (!hasInstallPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HiFi.Warning.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, HiFi.Warning.copy(alpha = 0.35f)), RoundedCornerShape(14.dp))
                            .clickable { onRequestPermission() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = HiFi.Warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.update_trebuetsya_odin_raz_razreshit),
                                color = HiFi.Warning,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Later Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_pozzhe),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isSignatureMismatch) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(14.dp))
                                .clickable { onShareOrSave() }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_sohranit_apk),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Primary Action Button (Download / Install)
                    val isDownloading = downloadProgress in 0f..1f
                    val isReadyToInstall = downloadProgress >= 2f

                    Box(
                        modifier = Modifier
                            // The Hi-Fi metal capsule: steel rim, anodized face
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(HiFi.MetalEdge))
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(HiFi.KnobFace, center = androidx.compose.ui.geometry.Offset.Zero, radius = 900f))
                            .clickable(enabled = !isDownloading) {
                                if (isReadyToInstall) {
                                    if (!hasInstallPermission) {
                                        onRequestPermission()
                                    } else {
                                        onInstall()
                                    }
                                } else {
                                    onStartDownload()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDownloading) {
                                HiFiActivityIndicator(size = 16.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.update_zagruzka),
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isReadyToInstall && !hasInstallPermission) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.update_razreshit_v_nastroykah),
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isReadyToInstall) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(if (isSignatureMismatch) R.string.update_open_installer else R.string.update_install),
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.update_obnovit_seychas),
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Not now and not this version: quiet, below the main actions, until a download starts
                if (onSkip != null && downloadProgress < 0f) {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button, onClick = onSkip)
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_skip_version), color = Color.White.copy(alpha = 0.78f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        stringResource(R.string.update_skip_hint),
                        style = HiFiType.Caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

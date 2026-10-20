package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.data.network.DownloadProgressInfo

@Composable
fun DownloadProgressModal(
    progressInfo: DownloadProgressInfo,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressInfo.percent,
        animationSpec = tween(durationMillis = 300),
        label = "progressAnim"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xE61E1E22)) // Glassmorphic translucent background
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.download_progress_modal_zagruzka_kollektsii),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.download_progress_count, progressInfo.current, progressInfo.total),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current track info
                Text(
                    text = progressInfo.currentTrackTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progressInfo.currentArtistName,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text(stringResource(R.string.download_progress_modal_svernut), fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3366).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFF3366)
                        ),
                        elevation = null
                    ) {
                        Text(stringResource(R.string.download_progress_modal_otmenit), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

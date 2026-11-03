package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFiProgressBar
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
import com.korenskoy.yandexmusic.data.download.DownloadQueueState
import com.korenskoy.yandexmusic.data.network.DownloadProgressInfo

@Composable
fun DownloadProgressModal(
    state: DownloadQueueState,
    progressInfo: DownloadProgressInfo?,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    // Queued (waiting for Wi-Fi/storage) or no per-track progress yet: show an indeterminate "waiting" state
    // so the user still sees what is happening and can always cancel.
    val waiting = state == DownloadQueueState.WAITING || progressInfo == null
    val animatedProgress by animateFloatAsState(
        targetValue = progressInfo?.percent ?: 0f,
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
                    text = stringResource(
                        if (waiting) R.string.download_progress_waiting_title
                        else R.string.download_progress_modal_zagruzka_kollektsii
                    ),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (waiting) stringResource(R.string.download_progress_waiting_hint)
                    else stringResource(R.string.download_progress_count, progressInfo?.current ?: 0, progressInfo?.total ?: 0),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                HiFiProgressBar(progress = if (waiting) null else ({ animatedProgress }), height = 8.dp)

                if (progressInfo != null && progressInfo.currentTrackTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassPillButton(stringResource(R.string.download_progress_modal_svernut), onDismiss, Modifier.weight(1f), height = 48.dp)
                    // Cancelling throws the queue away: a red glass capsule, like every destructive action
                    Box(Modifier.weight(1f)) {
                        HiFiDialogAction(stringResource(R.string.download_progress_modal_otmenit), onCancel, HiFiDialogActionKind.Danger)
                    }
                }
            }
        }
    }
}

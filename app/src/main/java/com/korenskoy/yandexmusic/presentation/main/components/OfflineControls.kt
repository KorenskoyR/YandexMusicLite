package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.download.OfflineState
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion

/**
 * The round button next to "Слушать" and "Перемешать": download the playlist, the progress ring with a
 * stop while it downloads, and the "downloaded" mark the track rows use once it is on the device.
 */
@Composable
fun OfflineButton(
    state: OfflineState,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onAskRemove: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    when (state) {
        OfflineState.None -> GlassCircleButton(onClick = onDownload, contentDescription = stringResource(R.string.offline_download_cd), modifier = modifier, size = size) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        is OfflineState.Downloading -> {
            val fraction by animateFloatAsState(
                if (state.total > 0) state.done.toFloat() / state.total else 0f,
                HiFiMotion.standard(),
                label = "offline_ring"
            )
            GlassCircleButton(onClick = onStop, contentDescription = stringResource(R.string.offline_stop_cd, state.done, state.total), modifier = modifier, size = size) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val stroke = 3.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, style = Stroke(stroke))
                    drawArc(Color.White.copy(alpha = 0.92f), -90f, 360f * fraction.coerceAtLeast(0.02f), false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Box(Modifier.align(Alignment.Center).size(13.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
        }
        is OfflineState.Done -> GlassCircleButton(onClick = onAskRemove, contentDescription = stringResource(R.string.offline_done_cd), modifier = modifier, size = size) {
            Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** Under the playlist's title: "Скачиваем: 3 из 34" while it downloads, "Скачан · 412 МБ" once it is on the device. */
@Composable
fun OfflineCaption(state: OfflineState) {
    when (state) {
        OfflineState.None -> Unit
        is OfflineState.Downloading -> {
            Text(
                stringResource(R.string.offline_downloading, state.done, state.total),
                color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(stringResource(R.string.offline_downloading_hint), color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
        }
        is OfflineState.Done -> {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.complete) stringResource(R.string.offline_done, formatSize(state.bytes))
                    else stringResource(R.string.offline_partly, state.done, state.total, formatSize(state.bytes)),
                    color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Text(stringResource(R.string.offline_done_hint), color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
        }
    }
}

/** "Удалить «В дорогу» с устройства?" — it stays in the library and plays from the network. */
@Composable
fun RemoveOfflineDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.offline_remove_title, title),
        text = stringResource(R.string.offline_remove_text),
        confirm = stringResource(R.string.offline_remove),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

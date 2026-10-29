package com.korenskoy.yandexmusic.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.PlaybackController

/** What the player streams and every variant the server offered for the track. */
@Composable
fun QualityDetailsDialog(details: PlaybackController.QualityDetails, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quality_details_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                details.playing?.let {
                    Text(stringResource(R.string.quality_details_playing, it), color = Color.White, fontSize = 14.sp)
                }
                details.offered?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.quality_details_offered, it), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                details.hasPlus?.let { hasPlus ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(if (hasPlus) R.string.quality_details_plus_active else R.string.quality_details_plus_inactive),
                        color = if (hasPlus) Color(0xFF4CAF50) else Color(0xFFFF9900),
                        fontSize = 13.sp
                    )
                }
            }
        },
        containerColor = Color(0xFF1E1E26),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.quality_details_ok), color = Color(0xFFFF3366), fontWeight = FontWeight.Bold)
            }
        }
    )
}

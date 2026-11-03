package com.korenskoy.yandexmusic.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.PlaybackController

/** What the player streams and every variant the server offered for the track. */
@Composable
fun QualityDetailsDialog(details: PlaybackController.QualityDetails, onDismiss: () -> Unit) {
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.quality_details_title),
        content = {
            Column {
                details.playing?.let {
                    Text(stringResource(R.string.quality_details_playing, it), color = Color.White, fontSize = 15.sp)
                }
                details.offered?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.quality_details_offered, it), color = HiFi.TextSecondary, fontSize = 14.sp)
                }
                details.hasPlus?.let { hasPlus ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(if (hasPlus) R.string.quality_details_plus_active else R.string.quality_details_plus_inactive),
                        color = if (hasPlus) HiFi.Success else HiFi.Warning,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirm = { HiFiDialogAction(stringResource(R.string.quality_details_ok), onDismiss) }
    )
}

package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.LyricsPanel
import com.korenskoy.yandexmusic.presentation.hifi.TrackLyrics
import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass

/** "Текст" in the car: the words of the playing track, large, on the same sheet as the queue; a tap or the cross closes it. */
@Composable
internal fun CarLyricsPanel(u: Scale, track: TrackDetailsDto?, lyrics: TrackLyrics, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(30f)))
            .background(HiFi.SheetFill.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(u(30f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Row(Modifier.padding(start = u(26f), end = u(16f), top = u(16f), bottom = u(4f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lyrics_short), color = Color.White, fontSize = u.sp(28f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                val about = listOfNotNull(track?.title, track?.artists?.joinToString { it.name.orEmpty() }?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (about.isNotEmpty()) {
                    Text(about, color = Color.White.copy(alpha = 0.6f), fontSize = u.sp(15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .size(u(56f))
                    .hifiGlass(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_zakryt), tint = Color.White, modifier = Modifier.size(u(24f)))
            }
        }
        LyricsPanel(
            lyrics, onClose = onClose, modifier = Modifier.weight(1f),
            fontSize = u.sp(26f), lineHeight = u.sp(38f), padding = u(26f), veil = Color.Transparent
        )
    }
}

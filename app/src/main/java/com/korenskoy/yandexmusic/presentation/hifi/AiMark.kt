package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.ai.AiRules
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

/** The AI marks known so far, and how a row asks for its own. */
@Immutable
class AiMarksUi(
    /** Track id → Yandex's words, for tracks marked as made with AI. */
    val marks: Map<String, String> = emptyMap(),
    val want: (String) -> Unit = {},
    val unwant: (String) -> Unit = {}
)

val LocalAiMarks = compositionLocalOf { AiMarksUi() }

/**
 * The AI mark of [track] — Yandex's words, or null. Asked for while the track is on the screen; a row scrolled
 * away before the answer costs no request.
 */
@Composable
fun rememberAiMark(track: TrackDetailsDto?): String? {
    val ui = LocalAiMarks.current
    val id = track?.idString?.takeIf { !track.isLocal && !track.isRadio && AiRules.isAskable(it) } ?: return null
    DisposableEffect(id) {
        ui.want(id)
        onDispose { ui.unwant(id) }
    }
    return ui.marks[id]
}

/** "ИИ" after the artist: quiet glass, like the rest of the row's secondary marks. */
@Composable
fun AiChip(modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp, height: Dp = 17.dp, horizontal: Dp = 6.dp) {
    val shape = RoundedCornerShape(height / 2 + 1.dp)
    val description = stringResource(R.string.ai_mark_cd)
    Box(
        modifier
            .height(height)
            .background(Color.White.copy(alpha = 0.10f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape)
            .padding(horizontal = horizontal)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.ai_mark),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.em,
            maxLines = 1
        )
    }
}

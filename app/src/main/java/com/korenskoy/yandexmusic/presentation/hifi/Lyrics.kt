package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.lyrics.LyricsRules

/** Where the words of tracks come from; the default knows none (previews, screenshots). */
@Immutable
class LyricsUi(
    val known: (String) -> LyricsAnswer? = { null },
    val load: suspend (String) -> LyricsAnswer = { LyricsAnswer.None }
)

val LocalLyrics = compositionLocalOf { LyricsUi() }

/** The words of one track as the screen sees them. */
@Immutable
class TrackLyrics(
    /** The words, "none", "failed", or null while asked. */
    val answer: LyricsAnswer?,
    /** Whether the "Текст" button is shown: only once the words have come, so it never shows and then goes. */
    val offered: Boolean,
    val retry: () -> Unit
)

/**
 * The words of [track]: asked when it is shown, if Yandex did not say it has none. Null for a track that can
 * have no words (a station, the user's own file).
 */
@Composable
fun rememberLyrics(track: TrackDetailsDto?): TrackLyrics? {
    val ui = LocalLyrics.current
    val id = track?.takeIf { LyricsRules.canHave(it) }?.idString ?: return null
    var attempt by remember(id) { mutableIntStateOf(0) }
    // One state per track: a new track starts from what is known about it in the same frame, never from the last one's words
    val state = remember(id) { mutableStateOf(ui.known(id)) }
    LaunchedEffect(id, attempt) {
        if (state.value != null && state.value != LyricsAnswer.Failed) return@LaunchedEffect
        state.value = null
        // No connection for a moment: asked again a little later, a few times, while the track is shown
        for (n in 1..LOAD_TRIES) {
            val got = ui.load(id)
            state.value = got
            if (got != LyricsAnswer.Failed) break
            if (n < LOAD_TRIES) delay(RETRY_MS * n)
        }
    }
    val answer = state.value
    val offered = answer is LyricsAnswer.Text
    return TrackLyrics(answer, offered) { attempt++ }
}

/**
 * Whether open words close: the track cannot have any (a station, a file of the user's own) or Yandex has none for
 * it. While the next track's words are asked for, or the asking failed for a moment, they stay open.
 */
fun lyricsClose(lyrics: TrackLyrics?): Boolean = lyrics == null || lyrics.answer == LyricsAnswer.None

/** Asking for the words: this many times at most, the pauses growing from [RETRY_MS]. */
private const val LOAD_TRIES = 3
private const val RETRY_MS = 4_000L

/** The words get about this many letters' width per line at their largest; below [MIN_TYPE] sp they do not go. */
private const val FIT_LETTERS = 17f
private const val MIN_TYPE = 13f

/**
 * The words over a cover: the cover darkened under them, the text scrolls, its edges fade. A tap closes it
 * (or, after a failure, asks again). No line follows the music: the timed words need a signed method.
 */
@Composable
fun LyricsPanel(
    lyrics: TrackLyrics,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 19.sp,
    lineHeight: TextUnit = 28.sp,
    padding: Dp = 22.dp,
    veil: Color = Color.Black.copy(alpha = 0.74f)
) {
    val answer = lyrics.answer
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(veil)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(if (answer == LyricsAnswer.Failed) R.string.lyrics_failed else R.string.lyrics_hide)
            ) { if (answer == LyricsAnswer.Failed) lyrics.retry() else onClose() },
        contentAlignment = Alignment.Center
    ) {
        // About 17 letters' width per line at most: a small cover gets a smaller type, never below 13 sp
        val fit = with(LocalDensity.current) { (maxWidth / FIT_LETTERS).toSp() }
        val shrink = (fit.value / fontSize.value).coerceIn(MIN_TYPE / fontSize.value, 1f)
        val fontSize = fontSize * shrink
        val lineHeight = lineHeight * shrink
        when (answer) {
            null -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            is LyricsAnswer.Text -> {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Soft edges where the text runs on above or below
                            val edge = padding.toPx()
                            if (scroll.canScrollBackward) {
                                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = edge), blendMode = BlendMode.DstIn)
                            }
                            if (scroll.canScrollForward) {
                                drawRect(
                                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - edge, endY = size.height),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                        .verticalScroll(scroll)
                        .padding(padding)
                ) {
                    Text(answer.text, color = Color.White, fontSize = fontSize, lineHeight = lineHeight, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(padding))
                    Text(stringResource(R.string.lyrics_source), color = Color.White.copy(alpha = 0.55f), fontSize = fontSize * 0.62f, lineHeight = fontSize * 0.86f)
                }
            }
            else -> Text(
                stringResource(if (answer == LyricsAnswer.Failed) R.string.lyrics_failed else R.string.lyrics_none),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = fontSize * 0.84f,
                lineHeight = lineHeight * 0.84f,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(padding)
            )
        }
    }
}

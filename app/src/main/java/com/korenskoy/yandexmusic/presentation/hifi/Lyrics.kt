package com.korenskoy.yandexmusic.presentation.hifi

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.korenskoy.yandexmusic.data.lyrics.LyricLine
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.lyrics.LyricsRules
import kotlinx.coroutines.delay
import kotlin.math.abs

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
    val offered = answer is LyricsAnswer.Synced || answer is LyricsAnswer.Text
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

/** Lead the audio position slightly so the line lights up as singing begins. */
private const val LEAD_MS = 250L

/** Manual scrolling pauses automatic lyric following for this long. */
private const val USER_SCROLL_PAUSE_MS = 3_500L

/**
 * The words over a cover: darkened background, smooth scrolling, softly faded edges.
 * For synced lyrics, lines highlight dynamically in rhythm with [progress], and tapping a line calls [onSeek].
 */
@Composable
fun LyricsPanel(
    lyrics: TrackLyrics,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    progress: () -> Long = { 0L },
    onSeek: (Long) -> Unit = {},
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
        val fit = with(LocalDensity.current) { (maxWidth / FIT_LETTERS).toSp() }
        val shrink = (fit.value / fontSize.value).coerceIn(MIN_TYPE / fontSize.value, 1f)
        val scaledFontSize = fontSize * shrink
        val scaledLineHeight = lineHeight * shrink

        when (answer) {
            null -> CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            is LyricsAnswer.Synced -> SyncedLyricsView(
                lines = answer.lines,
                progress = progress,
                onSeek = onSeek,
                fontSize = scaledFontSize,
                lineHeight = scaledLineHeight,
                padding = padding,
                modifier = Modifier.fillMaxSize()
            )
            is LyricsAnswer.Text -> {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
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
                    Text(answer.text, color = Color.White, fontSize = scaledFontSize, lineHeight = scaledLineHeight, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(padding))
                    Text(stringResource(R.string.lyrics_source), color = Color.White.copy(alpha = 0.55f), fontSize = scaledFontSize * 0.62f, lineHeight = scaledFontSize * 0.86f)
                }
            }
            else -> Text(
                stringResource(if (answer == LyricsAnswer.Failed) R.string.lyrics_failed else R.string.lyrics_none),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = scaledFontSize * 0.84f,
                lineHeight = scaledLineHeight * 0.84f,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(padding)
            )
        }
    }
}

@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    progress: () -> Long,
    onSeek: (Long) -> Unit,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    padding: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val currentLineIndex by remember(lines) {
        derivedStateOf { LyricsRules.currentIndex(lines, progress() + LEAD_MS) }
    }
    var userScrolledUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolledUntilMs = SystemClock.elapsedRealtime() + USER_SCROLL_PAUSE_MS
        }
    }

    var lastScrolledIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(lines) {
        lastScrolledIndex = -1
        listState.scrollToItem(0)
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val targetOffsetPx = -(viewportHeightPx * 0.28f).toInt()

        LaunchedEffect(currentLineIndex, userScrolledUntilMs) {
            val now = SystemClock.elapsedRealtime()
            if (now < userScrolledUntilMs) return@LaunchedEffect
            if (currentLineIndex < 0) return@LaunchedEffect

            val target = currentLineIndex.coerceIn(0, lines.lastIndex)
            val diff = abs(target - lastScrolledIndex)
            val wasInitial = lastScrolledIndex == -1
            lastScrolledIndex = target

            if (diff > 3 || wasInitial) {
                listState.scrollToItem(target, targetOffsetPx)
            } else if (diff > 0) {
                listState.animateScrollToItem(target, targetOffsetPx)
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = true,
            contentPadding = PaddingValues(
                top = maxHeight * 0.28f,
                bottom = maxHeight * 0.55f,
                start = padding,
                end = padding
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val edge = padding.toPx()
                    if (listState.canScrollBackward) {
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = edge),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    if (listState.canScrollForward) {
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Transparent,
                                startY = size.height - edge,
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            itemsIndexed(
                items = lines,
                key = { i, line -> "${i}_${line.timeMs}" }
            ) { index, line ->
                val isCurrent = index == currentLineIndex
                val isPast = index < currentLineIndex
                val targetAlpha = when {
                    isCurrent -> 1f
                    isPast -> 0.35f
                    else -> 0.55f
                }
                val color by animateColorAsState(
                    targetValue = Color.White.copy(alpha = targetAlpha),
                    animationSpec = HiFiMotion.quick(),
                    label = "synced_line_color"
                )
                val targetScale = if (isCurrent) 1.03f else 1.0f
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = HiFiMotion.quick(),
                    label = "synced_line_scale"
                )

                Text(
                    text = line.text.ifBlank { "♪" },
                    color = color,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.lyrics_seek_line)
                        ) {
                            userScrolledUntilMs = 0L
                            onSeek(line.timeMs)
                        }
                        .padding(vertical = (lineHeight.value - fontSize.value).dp.coerceIn(3.dp, 8.dp))
                )
            }

            item("source") {
                Spacer(Modifier.height(padding))
                Text(
                    text = stringResource(R.string.lyrics_source),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = fontSize * 0.62f,
                    lineHeight = fontSize * 0.86f
                )
            }
        }
    }
}

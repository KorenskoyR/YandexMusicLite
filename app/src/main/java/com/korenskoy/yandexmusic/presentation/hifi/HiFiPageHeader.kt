package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.LinkState
import kotlin.math.min

// The page header of the Hi-Fi language: it stays in place, the title shrinks as the page scrolls and the
// list dissolves under it, so nothing is ever cut by a hard line.

/** The scroll over which the title shrinks. */
private val CollapseRange = 70.dp
/** The title at the end of it: 30 → 20. */
private const val CollapsedTitleScale = 0.66f

/** 0 with the list at the top, 1 once it has moved by [CollapseRange]. */
@Composable
fun rememberHeaderCollapse(list: LazyListState): State<Float> {
    val range = with(LocalDensity.current) { CollapseRange.toPx() }
    return remember(list, range) {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) 1f else min(list.firstVisibleItemScrollOffset / range, 1f)
        }
    }
}

/** What is drawn above [topPx] is gone, then it fades in over [fadePx]. Read in the draw phase, so scrolling costs no recomposition. */
fun Modifier.fadeUnderHeader(topPx: () -> Float, fadePx: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val start = (topPx() / height).coerceIn(0f, 1f)
        val end = ((topPx() + fadePx) / height).coerceIn(start, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                start to Color.Transparent,
                end to Color.Black,
                1f to Color.Black
            ),
            blendMode = BlendMode.DstIn
        )
    }

/** The page title; it shrinks towards its start as [collapse] goes from 0 to 1. */
@Composable
fun HiFiCollapsingTitle(text: String, collapse: () -> Float, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = HiFiType.LargeTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer {
            val scale = 1f - (1f - CollapsedTitleScale) * collapse()
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    )
}

/**
 * A well cut into the panel with a steel rim: the search field and the small buttons next to the title.
 * [lit] brightens the rim (a field in use); it is read in the draw phase.
 */
fun Modifier.hifiMachined(cornerRadius: Dp, lit: () -> Float = { 0f }): Modifier = this.drawWithCache {
    val rimWidth = 1.dp.toPx()
    val outer = CornerRadius(cornerRadius.toPx())
    val inner = CornerRadius((cornerRadius.toPx() - rimWidth).coerceAtLeast(0f))
    val rim = Brush.verticalGradient(
        0f to HiFi.SteelEdge.copy(alpha = 0.50f),
        0.45f to HiFi.MetalEdge[1].copy(alpha = 0.70f),
        1f to HiFi.MetalEdge[2].copy(alpha = 0.50f)
    )
    val litRim = Brush.sweepGradient(HiFi.MetalSweep, Offset(size.width / 2f, size.height / 2f))
    val well = Brush.verticalGradient(listOf(WellTop, WellBottom))
    val innerShadow = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.14f to Color.Transparent)
    val wellSize = Size(size.width - 2 * rimWidth, size.height - 2 * rimWidth)
    val wellAt = Offset(rimWidth, rimWidth)
    val lipAt = Offset(0f, size.height - rimWidth)
    onDrawBehind {
        drawRoundRect(rim, cornerRadius = outer, style = Stroke(rimWidth * 2))
        val glow = lit()
        if (glow > 0f) drawRoundRect(litRim, cornerRadius = outer, style = Stroke(rimWidth * 2), alpha = glow)
        drawRoundRect(well, wellAt, wellSize, inner)
        drawRoundRect(innerShadow, wellAt, wellSize, inner)
        drawRect(Color.White.copy(alpha = 0.04f), lipAt, Size(size.width, rimWidth))
    }
}

private val WellTop = Color(0xFF090A0F)
private val WellBottom = Color(0xFF15171F)

/** A round button in the same material as the search field, for the header of a page. */
@Composable
fun HiFiWellButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .hifiSoftShadow(CircleShape, 20.dp)
            .hifiMachined(22.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** The state of the link, provided by MainScreen for the screens that show it. */
val LocalLinkState = compositionLocalOf { LinkState.GOOD }

/** "Слабая связь" or "Нет связи": a quiet label with a signal mark; nothing while the link is good. */
@Composable
fun HiFiLinkBadge(modifier: Modifier = Modifier, height: Dp = 28.dp, fontSize: TextUnit = 12.sp) {
    val state = LocalLinkState.current
    // The text stays through the fade-out
    val lastBad = remember { arrayOf(LinkState.WEAK) }
    if (state != LinkState.GOOD) lastBad[0] = state
    AnimatedVisibility(
        visible = state != LinkState.GOOD,
        enter = fadeIn(HiFiMotion.standard()),
        exit = fadeOut(HiFiMotion.standard()),
        modifier = modifier
    ) {
        Row(
            Modifier
                .height(height)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), CircleShape)
                .padding(start = height * 0.34f, end = height * 0.42f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(height * 0.26f)
        ) {
            SignalMark(litBars = if (lastBad[0] == LinkState.WEAK) 1 else 0, size = height * 0.5f)
            Text(
                stringResource(if (lastBad[0] == LinkState.WEAK) R.string.link_weak else R.string.link_none),
                color = Color.White.copy(alpha = 0.90f),
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SignalMark(litBars: Int, size: Dp) {
    Canvas(Modifier.size(size)) {
        val bars = 4
        val width = this.size.width / (bars * 2 - 1)
        for (bar in 0 until bars) {
            val barHeight = this.size.height * (bar + 1) / bars
            drawRoundRect(
                color = if (bar < litBars) Color.White else Color.White.copy(alpha = 0.28f),
                topLeft = Offset(bar * width * 2, this.size.height - barHeight),
                size = Size(width, barHeight),
                cornerRadius = CornerRadius(width / 2.5f)
            )
        }
    }
}

/**
 * The head of an inner page on a side (head unit, phone on its side): back, cover, name and actions in
 * one row, because on a wide screen height is what is short.
 */
@Composable
fun HiFiWideHeader(
    title: String,
    back: @Composable () -> Unit,
    cover: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val window = com.korenskoy.yandexmusic.utils.currentWindowSizeDp()
    val short = window.heightDp < 480
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        back()
        Spacer(Modifier.width(if (short) 12.dp else 16.dp))
        cover(if (short) 52.dp else 88.dp)
        Spacer(Modifier.width(if (short) 12.dp else 16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = HiFiType.LargeTitle.copy(
                    fontSize = if (short) 20.sp else 26.sp,
                    lineHeight = if (short) 26.sp else 32.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            details()
        }
        Spacer(Modifier.width(12.dp))
        Row(
            // Room for "Перемешать" in full, and always some left for the name
            Modifier.width(minOf(if (short) 340f else 380f, window.widthDp * 0.46f).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
        trailing()
    }
}

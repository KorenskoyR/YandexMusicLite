package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

/**
 * The plate of a track with explicit words, as in the official app: a grey "E" whose letter follows the plate's
 * own height, so it reads the same in a list row, beside a headline and inside a shrinking title.
 */
@Composable
fun ExplicitMark(modifier: Modifier = Modifier) {
    val palette = HiFi.palette
    val label = stringResource(R.string.explicit_mark_cd)
    BoxWithConstraints(
        modifier
            .semantics { contentDescription = label }
            .background(palette.explicitPlate, RoundedCornerShape(percent = 18)),
        contentAlignment = Alignment.Center
    ) {
        val letter = with(LocalDensity.current) { (maxHeight * EXPLICIT_LETTER).toSp() }
        Text("E", color = palette.ground, fontSize = letter, lineHeight = letter, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** The plate beside text of [fontSize] in a row (lists, cards): as tall as the letters' caps. */
@Composable
fun ExplicitMark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val height = with(LocalDensity.current) { (fontSize * EXPLICIT_HEIGHT).toDp() }
    ExplicitMark(modifier.size(width = height * EXPLICIT_WIDTH, height = height))
}

/**
 * A track's title wherever it is the headline (the player, the wave, «В авто»): up to [maxLines] lines, the type
 * shrinking down to [minScale] of [fontSize] before an ellipsis ever shows; the "E" plate after the last word for an
 * explicit track. One rule for all of them (a long name was cut after a few letters in a large type, 1.14.12).
 */
@Composable
fun TrackHeadline(
    title: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    explicit: Boolean = false,
    color: Color = HiFi.palette.textPrimary,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    maxLines: Int = 2,
    minScale: Float = 0.7f,
    textAlign: TextAlign = TextAlign.Start
) {
    val text = buildAnnotatedString {
        append(title)
        if (explicit) {
            append(' ')
            appendInlineContent(EXPLICIT_ID, "E")
        }
    }
    val inline = if (!explicit) emptyMap() else mapOf(
        EXPLICIT_ID to InlineTextContent(Placeholder((EXPLICIT_HEIGHT * EXPLICIT_WIDTH).em, EXPLICIT_HEIGHT.em, PlaceholderVerticalAlign.TextCenter)) {
            ExplicitMark(Modifier.fillMaxSize())
        }
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = 1.18.em,
            fontWeight = fontWeight,
            letterSpacing = (-0.02).em,
            textAlign = textAlign
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inline,
        autoSize = TextAutoSize.StepBased(minFontSize = fontSize * minScale, maxFontSize = fontSize, stepSize = 0.5.sp)
    )
}

private const val EXPLICIT_ID = "explicit"

/** The plate's height to the text's size, its width to its height, the letter to the plate. */
private const val EXPLICIT_HEIGHT = 0.82f
private const val EXPLICIT_WIDTH = 1.18f
private const val EXPLICIT_LETTER = 0.7f

/** A track of the service marked explicit by Yandex (not a station, not the user's own file). */
val TrackDetailsDto.showsExplicit: Boolean get() = isExplicit && !isRadio && !isLocal

/**
 * A track's title on one line in a list, a card, a menu or the mini player: the "E" plate right after it for an
 * explicit track, never pushed out by a long name (the name gives way, the plate stays).
 */
@Composable
fun TrackTitleLine(
    title: String,
    explicit: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = HiFi.palette.textPrimary,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = style,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (explicit) ExplicitMark(fontSize, Modifier.padding(start = (fontSize.value * 0.35f).dp))
    }
}

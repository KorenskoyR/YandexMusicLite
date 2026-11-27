package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Text styles of the Hi-Fi language. */
object HiFiType {
    val LargeTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = HiFi.palette.textPrimary)
    val SectionCaps: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em, color = HiFi.palette.textSecondary)
    val RowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = HiFi.palette.textPrimary)
    val CardTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = HiFi.palette.textPrimary)
    val Body: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = HiFi.palette.textPrimary)
    val Caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = HiFi.palette.textSecondary)
}

/** Frosted glass surface (fill + hairline border). */
@Composable
fun Modifier.hifiGlass(shape: Shape, fill: Color = HiFi.palette.glassFill, border: Color = HiFi.palette.glassBorder): Modifier =
    this.clip(shape).background(fill).border(1.dp, border, shape)

/**
 * A soft drop shadow drawn only OUTSIDE [shape]. A platform elevation shadow under a translucent glass
 * surface shows through it as a dark band along the bottom edge; this one never does. The blur is
 * computed once per size, and the trailing layer keeps redraws of the content (a progress line, a
 * sliding indicator) from re-recording the shadow. Skipped in lite mode and where the hardware
 * renderer cannot blur (Android 8.1 and older).
 */
fun Modifier.hifiSoftShadow(
    shape: Shape,
    elevation: Dp,
    color: Color = Color.Black.copy(alpha = 0.55f),
    enabled: Boolean = true
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val body = Path().apply { addOutline(outline) }
            val shadow = Path().apply { addPath(body, Offset(0f, elevation.toPx() / 3f)) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                maskFilter = BlurMaskFilter(elevation.toPx().coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            onDrawBehind {
                clipPath(body, ClipOp.Difference) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(shadow.asAndroidPath(), paint) }
                }
            }
        }
        .graphicsLayer()
}

/** A "recessed" window: dark in night mode, softly inset in daylight mode. */
@Composable
fun Modifier.hifiRecessed(cornerRadius: Dp): Modifier {
    val isDark = HiFi.palette.isDark
    return this.drawWithCache {
        val r = CornerRadius(cornerRadius.toPx())
        val fill = if (isDark) {
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.Black.copy(alpha = 0.36f)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF11141C).copy(alpha = 0.10f), Color(0xFF11141C).copy(alpha = 0.05f)))
        }
        val innerShadow = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (isDark) 0.55f else 0.09f),
            0.18f to Color.Transparent
        )
        val lip = 1.dp.toPx()
        onDrawBehind {
            drawRoundRect(fill, cornerRadius = r)
            drawRoundRect(innerShadow, cornerRadius = r)
            drawRoundRect(
                color = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFF11141C).copy(alpha = 0.12f),
                cornerRadius = r,
                style = Stroke(1f)
            )
            // The light lip follows the rounded bottom edge (a straight line used to poke out past the corners)
            clipRect(top = size.height - r.y) {
                drawRoundRect(
                    color = Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                    cornerRadius = r,
                    style = Stroke(lip)
                )
            }
        }
    }
}

/** Anodized metal with a thin steel edge — the "selected" material (dark metal in night, satin silver in day). */
@Composable
fun Modifier.hifiAnodized(shape: Shape): Modifier {
    val p = HiFi.palette
    return this
        .clip(shape)
        .background(Brush.radialGradient(listOf(p.anodizedLight, p.anodizedDark), radius = 600f, center = Offset(0f, 0f)))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.isDark) 0.06f else 0.45f), Color.Transparent)))
        .border(BorderStroke(1.dp, Brush.verticalGradient(p.metalEdge.map { it.copy(alpha = if (p.isDark) 0.65f else 0.85f) })), shape)
}

/** Page title, 30/700. */
@Composable
fun HiFiLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HiFiType.LargeTitle.copy(color = HiFi.palette.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Section header: small caps with letter-spacing, optional trailing action. */
@Composable
fun HiFiSectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text.uppercase(), style = HiFiType.SectionCaps.copy(color = HiFi.palette.textSecondary), modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A quiet text action (≥44dp touch height). */
@Composable
fun HiFiTextAction(text: String, onClick: () -> Unit, color: Color = HiFi.palette.textPrimary.copy(alpha = 0.82f)) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Round glass button (back, close, settings, history). */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fill: Color = HiFi.palette.glassFill,
    border: Color = HiFi.palette.glassBorder,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .hifiGlass(CircleShape, fill = fill, border = border)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Primary action: an anodized metal capsule with a steel rim. */
@Composable
fun MetalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val p = HiFi.palette
    val shadowCol = if (p.isDark) Color.Black else Color(0xFF11141C).copy(alpha = 0.25f)
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(if (p.isDark) 14.dp else 8.dp, CircleShape, ambientColor = shadowCol, spotColor = shadowCol)
            .clip(CircleShape)
            .background(Brush.verticalGradient(p.metalEdge))
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(p.knobFace, center = Offset(0f, 0f), radius = 900f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.isDark) 0.10f else 0.35f), Color.Transparent)))
            .clickable(enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        PillLabel(text, icon)
    }
}

/** Secondary action: a frosted glass capsule. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .hifiGlass(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PillLabel(text, icon)
    }
}

/**
 * The capsule's icon and word. Where three buttons share a row (a playlist with its download button, a
 * small phone) the word shrinks a little instead of being cut, and a very narrow capsule drops the icon.
 */
@Composable
private fun PillLabel(text: String, icon: ImageVector?) {
    val contentCol = HiFi.palette.onMetal
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val boxWidth = maxWidth
        val sidePad = if (boxWidth < 130.dp) 10.dp else 14.dp
        Row(Modifier.padding(horizontal = sidePad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null && boxWidth >= 148.dp) {
                Icon(icon, contentDescription = null, tint = contentCol, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            BasicText(
                text,
                style = TextStyle(color = contentCol, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp)
            )
        }
    }
}

/** Filter/genre pill: anodized when selected, glass otherwise. */
@Composable
fun HiFiFilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val p = HiFi.palette
    val shape = RoundedCornerShape(18.dp)
    val txtCol = if (selected) p.textPrimary else p.textPrimary.copy(alpha = 0.86f)
    Row(
        modifier = modifier
            .height(36.dp)
            .then(
                if (selected) Modifier.hifiAnodized(shape)
                else Modifier.hifiGlass(shape, fill = p.pillFill, border = p.glassBorder)
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = txtCol, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = txtCol,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** A small metal plaque behind a row's icon. */
@Composable
fun IconPlaque(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp, tint: Color = HiFi.palette.textPrimary, danger: Boolean = false) {
    val p = HiFi.palette
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.verticalGradient(
                    when {
                        danger -> listOf(p.danger.copy(alpha = 0.18f), p.danger.copy(alpha = 0.06f))
                        p.isDark -> listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f))
                        else -> listOf(Color(0xFF11141C).copy(alpha = 0.09f), Color(0xFF11141C).copy(alpha = 0.04f))
                    }
                )
            )
            .drawBehind {
                drawLine(
                    if (p.isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.70f),
                    Offset(size.toPx() * 0.15f, 0.5f),
                    Offset(this.size.width - size.toPx() * 0.15f, 0.5f),
                    1f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) p.danger else tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Segmented control: a recessed track with a metal piece sliding under the selected option. */
@Composable
fun HiFiSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    val p = HiFi.palette
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .hifiRecessed(22.dp)
    ) {
        val inset = 3.dp
        val segment = (maxWidth - inset * 2) / options.size
        val offset by animateDpAsState(segment * selectedIndex, HiFiMotion.spring(), label = "segment")
        Box(
            modifier = Modifier
                .padding(inset)
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .hifiAnodized(RoundedCornerShape(19.dp))
        )
        // Each option is touched over the whole height of the track, not only over the piece inside it
        Row(Modifier.fillMaxSize().padding(horizontal = inset)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // A long word on a narrow screen shrinks a little rather than being cut
                    BasicText(
                        label,
                        style = TextStyle(
                            color = if (selected) p.textPrimary else p.textSecondary,
                            fontSize = fontSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = fontSize * 0.75f, maxFontSize = fontSize, stepSize = 0.5.sp)
                    )
                }
            }
        }
    }
}

/** A hardware-like toggle: satin silver/graphite track when on, a bright metal thumb. */
@Composable
fun HiFiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = HiFi.palette
    val offTrack = if (p.isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFF11141C).copy(alpha = 0.16f)
    val onTrack = if (p.isDark) Color(0xFFDCE1EB) else Color(0xFF1B1E28)
    val track by animateColorAsState(if (checked) onTrack else offTrack, HiFiMotion.quick(), label = "switch_track")
    val thumbX by animateDpAsState(if (checked) 24.dp else 2.dp, HiFiMotion.spring(), label = "switch_thumb")
    val thumbBorder = if (p.isDark) Color(0xFF141720).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.35f)
    val view = LocalView.current
    // Touched over 48 dp of height; the track itself stays 54 × 32
    Box(
        modifier = modifier
            .size(width = 54.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCheckedChange(it)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 32.dp)
                .clip(CircleShape)
                .background(track)
                .border(1.dp, if (checked) p.glassBorder.copy(alpha = 0.35f) else p.glassBorder, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 2.dp.roundToPx()) }
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(p.brightMetalSweep))
                    .border(0.75.dp, thumbBorder, CircleShape)
            )
        }
    }
}

/** Slider with a recessed track, white/dark filled part and a small metal knob. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiFiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val p = HiFi.palette
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .shadow(5.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(p.metalSweep))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(p.knobFace))
            )
        },
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .hifiRecessed(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (p.isDark) Color.White.copy(alpha = 0.9f) else p.textPrimary.copy(alpha = 0.85f))
                )
            }
        },
        colors = SliderDefaults.colors(thumbColor = p.onMetal, activeTrackColor = p.textPrimary)
    )
}

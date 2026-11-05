package com.korenskoy.yandexmusic.presentation.hifi

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
    val LargeTitle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = Color.White)
    val SectionCaps = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em, color = HiFi.TextSecondary)
    val RowTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val CardTitle = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = Color.White)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = HiFi.TextSecondary)
}

/** Frosted glass surface (fill + hairline border). */
fun Modifier.hifiGlass(shape: Shape, fill: Color = HiFi.GlassFill, border: Color = HiFi.GlassBorder): Modifier =
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
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return this
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val body = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
            val shadow = androidx.compose.ui.graphics.Path().apply { addPath(body, Offset(0f, elevation.toPx() / 3f)) }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(elevation.toPx().coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            onDrawBehind {
                clipPath(body, androidx.compose.ui.graphics.ClipOp.Difference) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(shadow.asAndroidPath(), paint) }
                }
            }
        }
        .graphicsLayer()
}

/** A "recessed" window: dark, with an inner shadow at the top and a light lip at the bottom. */
fun Modifier.hifiRecessed(cornerRadius: Dp): Modifier = this.drawWithCache {
    val r = CornerRadius(cornerRadius.toPx())
    val fill = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.Black.copy(alpha = 0.36f)))
    val innerShadow = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.18f to Color.Transparent)
    val lip = 1.dp.toPx()
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = r)
        drawRoundRect(innerShadow, cornerRadius = r)
        drawRoundRect(color = Color.White.copy(alpha = 0.06f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        // The light lip follows the rounded bottom edge (a straight line used to poke out past the corners)
        clipRect(top = size.height - r.y) {
            drawRoundRect(color = Color.White.copy(alpha = 0.10f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(lip))
        }
    }
}

/** Dark anodized metal with a thin steel edge — the "selected" material (no glowing dots). */
fun Modifier.hifiAnodized(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.radialGradient(listOf(HiFi.AnodizedLight, HiFi.AnodizedDark), radius = 600f, center = Offset(0f, 0f)))
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)))
    .border(BorderStroke(1.dp, Brush.verticalGradient(HiFi.MetalEdge.map { it.copy(alpha = 0.65f) })), shape)

/** Page title, 30/700. */
@Composable
fun HiFiLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HiFiType.LargeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Section header: small caps with letter-spacing, optional trailing action. */
@Composable
fun HiFiSectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text.uppercase(), style = HiFiType.SectionCaps, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A quiet text action (≥44dp touch height). */
@Composable
fun HiFiTextAction(text: String, onClick: () -> Unit, color: Color = Color.White.copy(alpha = 0.82f)) {
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
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .hifiGlass(CircleShape)
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
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Brush.verticalGradient(HiFi.MetalEdge))
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(HiFi.KnobFace, center = Offset(0f, 0f), radius = 900f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
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

@Composable
private fun PillLabel(text: String, icon: ImageVector?) {
    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Filter/genre pill: anodized when selected, glass otherwise. */
@Composable
fun HiFiFilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .then(if (selected) Modifier.hifiAnodized(shape) else Modifier.hifiGlass(shape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f)))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.86f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** A small metal plaque behind a row's icon. */
@Composable
fun IconPlaque(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp, tint: Color = Color.White, danger: Boolean = false) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.verticalGradient(
                    if (danger) listOf(HiFi.Danger.copy(alpha = 0.18f), HiFi.Danger.copy(alpha = 0.06f))
                    else listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f))
                )
            )
            .drawBehind {
                drawLine(Color.White.copy(alpha = 0.18f), Offset(size.toPx() * 0.15f, 0.5f), Offset(this.size.width - size.toPx() * 0.15f, 0.5f), 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) HiFi.Danger else tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Segmented control: a recessed track with a metal piece sliding under the selected option. */
@Composable
fun HiFiSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .hifiRecessed(22.dp)
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, HiFiMotion.spring(), label = "segment")
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .hifiAnodized(RoundedCornerShape(19.dp))
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.70f),
                        fontSize = fontSize,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A hardware-like toggle: blue track when on, a bright metal thumb. */
@Composable
fun HiFiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) HiFi.Accent else Color.Black.copy(alpha = 0.5f), HiFiMotion.quick(), label = "switch_track")
    val thumbX by animateDpAsState(if (checked) 24.dp else 2.dp, HiFiMotion.spring(), label = "switch_thumb")
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, Color.White.copy(alpha = if (checked) 0.0f else 0.10f), CircleShape)
            .toggleable(value = checked, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCheckedChange(it)
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbX.roundToPx(), 2.dp.roundToPx()) }
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.sweepGradient(HiFi.BrightMetalSweep))
        )
    }
}

/** Slider with a recessed track, white filled part and a small metal knob. */
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
                    .background(Brush.sweepGradient(HiFi.MetalSweep))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(HiFi.KnobFace))
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
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        },
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
    )
}

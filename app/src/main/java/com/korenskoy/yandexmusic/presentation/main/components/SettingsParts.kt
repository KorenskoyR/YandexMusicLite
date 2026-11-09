package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.material.icons.rounded.GraphicEq
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSlider
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.utils.DisplayScale
import java.util.Locale
import kotlin.math.roundToInt

// The rows, cards and controls of the settings sheet.

internal fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.forLanguageTag("ru"), "%.1f ГБ", mb / 1024) else String.format(Locale.forLanguageTag("ru"), "%.0f МБ", mb)
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = { Text(text, color = HiFi.TextSecondary, fontSize = 15.sp) },
        confirm = { HiFiDialogAction(confirm, onConfirm, HiFiDialogActionKind.Danger) },
        dismiss = { HiFiDialogAction(stringResource(R.string.settings_cancel), onDismiss, HiFiDialogActionKind.Quiet) }
    )
}

@Composable
internal fun ColumnScope.SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            title.uppercase(),
            style = HiFiType.SectionCaps.copy(fontSize = 12.sp),
            modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp)
        )
    } else {
        Spacer(Modifier.height(18.dp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.03f), Color.Transparent))),
        content = content
    )
}

@Composable
internal fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = if (trailing != null) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(icon, size = 32.dp, danger = danger)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) HiFi.Danger else Color.White, fontSize = 16.sp, fontWeight = if (danger) FontWeight.SemiBold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = HiFi.TextTertiary, fontSize = 15.sp, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun AccountCard(name: String, avatarUrl: String?, hasPlus: Boolean, onSwitch: (() -> Unit)?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(HiFi.MetalEdge))
                .padding(2.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(HiFi.SteelLight, HiFi.SteelDark))),
            contentAlignment = Alignment.Center
        ) {
            Text((name.firstOrNull() ?: 'Я').uppercaseChar().toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifEmpty { "—" }, color = Color.White, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (hasPlus) R.string.settings_account_plus else R.string.settings_account_no_plus),
                color = HiFi.TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onSwitch != null) {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(role = Role.Button, onClick = onSwitch)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_switch_account), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun CrossfadeRow(seconds: Int, onChange: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlaque(Icons.Rounded.SwapHoriz, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_crossfade), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            val shown = value.roundToInt()
            Text(
                if (shown == 0) stringResource(R.string.settings_crossfade_off) else stringResource(R.string.settings_crossfade_seconds, shown),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        HiFiSlider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 0f..12f,
            steps = 11
        )
    }
}

/** For a device that plays FLAC in silence: the app cannot hear that by itself, so the user chooses. */
@Composable
internal fun SoundQualityRow(compatible: Boolean, onChange: (Boolean) -> Unit) {
    SegmentedRow(Icons.Rounded.GraphicEq, stringResource(R.string.settings_sound_quality)) {
        HiFiSegmented(
            options = listOf(stringResource(R.string.settings_sound_max), stringResource(R.string.settings_sound_compatible)),
            selectedIndex = if (compatible) 1 else 0,
            onSelect = { onChange(it == 1) },
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (compatible) R.string.settings_sound_compatible_note else R.string.settings_sound_max_note),
            color = HiFi.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
internal fun SegmentedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlaque(icon, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        control()
    }
}

/** The road reserve: its size, what that means in tracks and hours, how full it is. */
@Composable
internal fun RoadReserveRows(sizeMb: Int, onSizeChange: (Int) -> Unit, usedBytes: Long, onClear: () -> Unit) {
    val sizes = SettingsRepository.ROAD_RESERVE_SIZES_MB
    val enabled = sizeMb > 0
    val labels = sizes.map { mb ->
        when {
            mb == 0 -> stringResource(R.string.settings_reserve_off)
            mb >= 1024 -> stringResource(R.string.settings_reserve_gb, mb / 1024)
            else -> stringResource(R.string.settings_reserve_mb, mb)
        }
    }
    SegmentedRow(Icons.Rounded.Route, stringResource(R.string.settings_reserve_size)) {
        Text(stringResource(R.string.settings_reserve_hint), color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        HiFiSegmented(
            options = labels,
            selectedIndex = sizes.indexOf(sizeMb).coerceAtLeast(0),
            onSelect = { onSizeChange(sizes[it]) },
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                when (sizeMb) {
                    0 -> R.string.settings_reserve_estimate_off
                    100 -> R.string.settings_reserve_estimate_100
                    300 -> R.string.settings_reserve_estimate_300
                    500 -> R.string.settings_reserve_estimate_500
                    else -> R.string.settings_reserve_estimate_1024
                }
            ),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp
        )
        if (enabled) {
            val limitBytes = sizeMb * 1024L * 1024L
            val fraction = (usedBytes.toFloat() / limitBytes).coerceIn(0f, 1f)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.45f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(HiFi.Paper, HiFi.SteelEdge))))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_reserve_used, formatSize(usedBytes), labels[sizes.indexOf(sizeMb).coerceAtLeast(0)]), color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
    if (enabled) {
        SettingsRow(
            icon = Icons.Rounded.DeleteOutline,
            title = stringResource(R.string.settings_reserve_clear_title),
            chevron = false,
            onClick = null,
            trailing = {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.settings_clear), color = HiFi.Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    Text(
        stringResource(R.string.settings_reserve_note),
        color = HiFi.TextTertiary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp)
    )
}

/** UI scale for screens whose reported density is wrong (dense car head units); applied in MainActivity. */
@Composable
internal fun UiScaleRow(auto: Boolean, onAutoChange: (Boolean) -> Unit, manualScale: Float, onManualChange: (Float) -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val info = remember(resources) { DisplayScale.analyze(resources.displayMetrics) }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlaque(Icons.Rounded.Fullscreen, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_ui_scale_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            val percent = ((if (auto) info.autoScale else manualScale) * 100).roundToInt()
            Text("$percent%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        HiFiSegmented(
            options = listOf(stringResource(R.string.settings_ui_scale_auto), stringResource(R.string.settings_ui_scale_manual)),
            selectedIndex = if (auto) 0 else 1,
            onSelect = { onAutoChange(it == 0) }
        )
        Spacer(Modifier.height(10.dp))
        if (auto) {
            val physical = if (info.physicalDpi > 0) info.physicalDpi.toString() else "—"
            Text(
                stringResource(R.string.settings_ui_scale_auto_info, info.widthPx, info.heightPx, info.reportedDpi, physical, (info.autoScale * 100).roundToInt()),
                color = HiFi.TextTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        } else {
            var value by remember(manualScale) { mutableFloatStateOf(manualScale) }
            Text(
                stringResource(R.string.settings_ui_scale_manual_value, (value * 100).roundToInt(), (info.reportedDpi * value).roundToInt()),
                color = HiFi.TextTertiary,
                fontSize = 13.sp
            )
            HiFiSlider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onManualChange(value) }, valueRange = 0.5f..2.0f, steps = 29)
        }
    }
}

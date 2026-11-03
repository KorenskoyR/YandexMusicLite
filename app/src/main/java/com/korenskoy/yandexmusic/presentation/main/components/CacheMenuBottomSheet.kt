package com.korenskoy.yandexmusic.presentation.main.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSlider
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSwitch
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.hifiGrain
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.utils.DisplayScale
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings — the only place for the account. A glass sheet slides up (350 ms) over the dimmed page;
 * drag the header down or tap outside to close. Groups like system settings; on wide landscape
 * screens (car head units) the groups go into two columns.
 */
@Composable
fun CacheMenuBottomSheet(
    onDismiss: () -> Unit,
    cacheSizeBytes: Long,
    autoDownloadEnabled: Boolean,
    onEnableAutoDownload: (Boolean) -> Unit,
    onDownloadAllClick: () -> Unit,
    onClearCache: () -> Unit,
    accountName: String = "",
    avatarUrl: String? = null,
    hasPlus: Boolean = false,
    defaultAccountName: String? = null,
    updateStatus: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onAccountManagementClick: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onLogout: () -> Unit = {},
    crossfadeSeconds: Int = 3,
    onCrossfadeChange: (Int) -> Unit = {},
    steeringWheelOnLeft: Boolean = true,
    onSteeringWheelChange: (Boolean) -> Unit = {},
    uiScaleAuto: Boolean = true,
    onUiScaleAutoChange: (Boolean) -> Unit = {},
    uiScaleManual: Float = 1f,
    onUiScaleManualChange: (Float) -> Unit = {},
    onUpgradeDownloads: () -> Unit = {},
    crashReports: Int = 0,
    onOpenCrashLog: () -> Unit = {}
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    // Closing plays the slide-out first, then removes the dialog
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.targetState && visible.isIdle) onDismiss()
    }
    val close = { visible.targetState = false }

    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = visible, enter = fadeIn(HiFiMotion.standard()), exit = fadeOut(HiFiMotion.standard())) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                )
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(HiFiMotion.large()) { it },
                exit = slideOutVertically(HiFiMotion.standard()) { it },
                modifier = Modifier.align(if (isLandscape) Alignment.Center else Alignment.BottomCenter)
            ) {
                val sheetShape = if (isLandscape) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                Column(
                    modifier = Modifier
                        .then(if (isLandscape) Modifier.widthIn(max = 880.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f) else Modifier.fillMaxWidth().fillMaxHeight())
                        .statusBarsPadding()
                        .padding(top = if (isLandscape) 0.dp else 24.dp)
                        .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                        .clip(sheetShape)
                        .background(HiFi.SheetFill)
                        .hifiGrain(enabled = !LocalHiFiLite.current, alpha = 0.35f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                ) {
                    // Header: drag it down to close
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                                },
                                onDragStopped = {
                                    if (dragOffset.value > with(density) { 120.dp.toPx() }) close()
                                    else scope.launch { dragOffset.animateTo(0f, HiFiMotion.spring()) }
                                }
                            )
                    ) {
                        Box(
                            Modifier
                                .padding(top = 10.dp)
                                .size(width = 40.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.28f))
                                .align(Alignment.CenterHorizontally)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_title), style = HiFiType.LargeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp), modifier = Modifier.weight(1f))
                            GlassCircleButton(onClick = close, contentDescription = stringResource(R.string.cd_zakryt)) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    val account: @Composable ColumnScope.() -> Unit = {
                        SettingsGroup(stringResource(R.string.settings_group_account)) {
                            AccountCard(accountName, avatarUrl, hasPlus, onSwitch = {
                                onAccountManagementClick()
                                close()
                            })
                            SettingsRow(
                                icon = Icons.Rounded.Star,
                                title = stringResource(R.string.settings_startup_profile),
                                value = defaultAccountName ?: stringResource(R.string.settings_startup_ask),
                                onClick = {
                                    onAccountManagementClick()
                                    close()
                                }
                            )
                            SettingsRow(icon = Icons.AutoMirrored.Rounded.Login, title = stringResource(R.string.settings_add_account), onClick = {
                                onAddAccount()
                                close()
                            })
                            SettingsRow(icon = Icons.AutoMirrored.Rounded.Logout, title = stringResource(R.string.settings_logout), danger = true, chevron = false, onClick = { confirmLogout = true })
                        }
                    }
                    val playback: @Composable ColumnScope.() -> Unit = {
                        SettingsGroup(stringResource(R.string.settings_group_playback)) {
                            CrossfadeRow(crossfadeSeconds, onCrossfadeChange)
                            SettingsRow(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(R.string.settings_upgrade_downloads),
                                subtitle = stringResource(R.string.settings_upgrade_downloads_hint),
                                onClick = onUpgradeDownloads
                            )
                        }
                    }
                    val downloads: @Composable ColumnScope.() -> Unit = {
                        SettingsGroup(stringResource(R.string.settings_group_downloads)) {
                            SettingsRow(
                                icon = Icons.Rounded.Download,
                                title = stringResource(R.string.settings_auto_download),
                                subtitle = stringResource(R.string.settings_auto_download_hint),
                                chevron = false,
                                onClick = { onEnableAutoDownload(!autoDownloadEnabled) },
                                trailing = { HiFiSwitch(checked = autoDownloadEnabled, onCheckedChange = onEnableAutoDownload) }
                            )
                            SettingsRow(icon = Icons.Rounded.CloudDownload, title = stringResource(R.string.settings_download_all), onClick = {
                                onDownloadAllClick()
                                close()
                            })
                            SettingsRow(
                                icon = Icons.Rounded.Storage,
                                title = stringResource(R.string.settings_storage),
                                subtitle = stringResource(R.string.settings_storage_value, formatSize(cacheSizeBytes)),
                                chevron = false,
                                onClick = null,
                                trailing = {
                                    TextButton(onClick = { confirmClear = true }) {
                                        Text(stringResource(R.string.settings_clear), color = HiFi.Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            )
                        }
                    }
                    val car: @Composable ColumnScope.() -> Unit = {
                        SettingsGroup(stringResource(R.string.settings_group_car)) {
                            SegmentedRow(Icons.Rounded.DirectionsCar, stringResource(R.string.settings_steering)) {
                                HiFiSegmented(
                                    options = listOf(stringResource(R.string.settings_steering_left_short), stringResource(R.string.settings_steering_right_short)),
                                    selectedIndex = if (steeringWheelOnLeft) 0 else 1,
                                    onSelect = { onSteeringWheelChange(it == 0) }
                                )
                            }
                            UiScaleRow(uiScaleAuto, onUiScaleAutoChange, uiScaleManual, onUiScaleManualChange)
                        }
                    }
                    val about: @Composable ColumnScope.() -> Unit = {
                        SettingsGroup(stringResource(R.string.settings_group_about)) {
                            SettingsRow(
                                icon = Icons.Rounded.Info,
                                title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                subtitle = updateStatus ?: stringResource(R.string.settings_check_updates),
                                onClick = onCheckForUpdates
                            )
                            SettingsRow(
                                icon = Icons.Rounded.WarningAmber,
                                title = stringResource(R.string.crash_log_title),
                                value = if (crashReports == 0) stringResource(R.string.crash_log_none) else crashReports.toString(),
                                onClick = onOpenCrashLog
                            )
                        }
                    }

                    if (isLandscape) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(Modifier.weight(1f)) { account(); car() }
                            Column(Modifier.weight(1f)) { playback(); downloads(); about() }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                        ) {
                            account(); playback(); downloads(); car(); about()
                            Text(
                                "YANDEX MUSIC LITE",
                                style = HiFiType.SectionCaps.copy(color = Color.White.copy(alpha = 0.40f), fontSize = 12.sp),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            text = stringResource(R.string.settings_logout_confirm_text),
            confirm = stringResource(R.string.account_management_vyyti),
            onConfirm = {
                confirmLogout = false
                onLogout()
                close()
            },
            onDismiss = { confirmLogout = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_confirm_title),
            text = stringResource(R.string.settings_clear_confirm_text),
            confirm = stringResource(R.string.settings_clear),
            onConfirm = {
                confirmClear = false
                onClearCache()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.forLanguageTag("ru"), "%.1f ГБ", mb / 1024) else String.format(Locale.forLanguageTag("ru"), "%.0f МБ", mb)
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C24),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = HiFi.TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = HiFi.Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel), color = Color.White) } }
    )
}

@Composable
private fun ColumnScope.SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = HiFiType.SectionCaps.copy(fontSize = 12.sp),
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp)
    )
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
private fun SettingsRow(
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
            if (subtitle != null) Text(subtitle, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun AccountCard(name: String, avatarUrl: String?, hasPlus: Boolean, onSwitch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(HiFi.MetalEdge))
                .padding(2.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF3A4A6B), Color(0xFF1C2232)))),
            contentAlignment = Alignment.Center
        ) {
            Text((name.firstOrNull() ?: 'Я').uppercaseChar().toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifEmpty { "—" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (hasPlus) R.string.settings_account_plus else R.string.settings_account_no_plus),
                color = HiFi.TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    }
}

@Composable
private fun CrossfadeRow(seconds: Int, onChange: (Int) -> Unit) {
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

@Composable
private fun SegmentedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, control: @Composable () -> Unit) {
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

/** UI scale for screens whose reported density is wrong (dense car head units); applied in MainActivity. */
@Composable
private fun UiScaleRow(auto: Boolean, onAutoChange: (Boolean) -> Unit, manualScale: Float, onManualChange: (Float) -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val info = remember(configuration) { DisplayScale.analyze(context.resources.displayMetrics) }
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

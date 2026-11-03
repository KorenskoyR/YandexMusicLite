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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSwitch
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.hifiGrain
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import kotlinx.coroutines.launch
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

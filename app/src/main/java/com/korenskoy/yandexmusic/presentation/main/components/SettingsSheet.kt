package com.korenskoy.yandexmusic.presentation.main.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.AnimationMode
import com.korenskoy.yandexmusic.domain.model.StartMusic
import com.korenskoy.yandexmusic.domain.model.StartPage
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSegmented
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSwitch
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.hifiGrain
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/KorenskoyR/YandexMusicLite"
private const val LICENSE_URL = "https://github.com/KorenskoyR/YandexMusicLite/blob/main/LICENSE"

/**
 * Settings: a short root list (the account card and three sections), and one page per section. On a
 * side (head unit, phone on its side) the sections stay on the left and the open one fills the right.
 */
@Composable
fun SettingsSheet(
    state: SettingsUiState,
    actions: SettingsActions,
    onDismiss: () -> Unit,
    initialPage: SettingsPage? = null
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lite = LocalHiFiLite.current
    val scope = rememberCoroutineScope()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.targetState && visible.isIdle) onDismiss()
    }
    val close = { visible.targetState = false }

    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pageName by rememberSaveable { mutableStateOf(initialPage?.name) }
    val page = pageName?.let { SettingsPage.valueOf(it) }
    // On a side there is always an open section; in portrait Back first returns to the root list

    val pageActions = actions.copyWith(onLogout = { confirmLogout = true }, onClearDownloads = { confirmClear = true }, close = close)

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Inside the dialog's window: Back there first steps out of a page to the root list, then closes
        BackHandler(enabled = !isLandscape && page != null) { pageName = null }
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
                        .then(if (isLandscape) Modifier.widthIn(max = 980.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f) else Modifier.fillMaxWidth().fillMaxHeight())
                        .statusBarsPadding()
                        .padding(top = if (isLandscape) 0.dp else 24.dp)
                        .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                        .clip(sheetShape)
                        .background(HiFi.SheetFill)
                        .hifiGrain(enabled = !lite, alpha = 0.35f)
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
                            Modifier.fillMaxWidth().padding(start = if (!isLandscape && page != null) 12.dp else 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sub = page.takeIf { !isLandscape }
                            if (sub != null) {
                                GlassCircleButton(onClick = { pageName = null }, contentDescription = stringResource(R.string.cd_nazad)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = sub?.let { stringResource(it.titleRes) } ?: stringResource(R.string.settings_title),
                                style = HiFiType.LargeTitle.copy(fontSize = if (sub != null) 24.sp else 28.sp, lineHeight = 34.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            GlassCircleButton(onClick = close, contentDescription = stringResource(R.string.cd_zakryt)) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (isLandscape) {
                        val open = page ?: SettingsPage.PLAYBACK
                        Row(Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 16.dp)) {
                            Column(
                                Modifier
                                    .width(300.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 8.dp, bottom = 16.dp)
                                    .selectableGroup()
                            ) {
                                SectionNavItem(SettingsPage.ACCOUNT, state.accountName.ifEmpty { stringResource(R.string.settings_group_account) }, accountSummary(state), open == SettingsPage.ACCOUNT) { pageName = it.name }
                                SectionNavItem(SettingsPage.PLAYBACK, stringResource(R.string.settings_section_playback), playbackSummary(state), open == SettingsPage.PLAYBACK) { pageName = it.name }
                                SectionNavItem(SettingsPage.INTERFACE, stringResource(R.string.settings_section_interface), interfaceSummary(state), open == SettingsPage.INTERFACE) { pageName = it.name }
                                SectionNavItem(SettingsPage.ABOUT, stringResource(R.string.settings_group_about), aboutSummary(), open == SettingsPage.ABOUT) { pageName = it.name }
                            }
                            Spacer(Modifier.width(16.dp))
                            AnimatedContent(
                                targetState = open,
                                transitionSpec = { fadeIn(HiFiMotion.standard()) togetherWith fadeOut(HiFiMotion.quick()) },
                                label = "settings_section",
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) { shown ->
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .navigationBarsPadding()
                                        .padding(bottom = 20.dp)
                                ) { SettingsPageContent(shown, state, pageActions) }
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                val forward = targetState != null
                                if (lite) fadeIn(HiFiMotion.quick()) togetherWith fadeOut(HiFiMotion.quick())
                                else (slideInHorizontally(HiFiMotion.standard()) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(HiFiMotion.standard())) togetherWith
                                    (slideOutHorizontally(HiFiMotion.standard()) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(HiFiMotion.quick()))
                            },
                            label = "settings_page",
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) { shown ->
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                            ) {
                                if (shown == null) RootPage(state) { pageName = it.name }
                                else SettingsPageContent(shown, state, pageActions)
                            }
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
                actions.onLogout()
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
                actions.onClearDownloads()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

private val SettingsPage.titleRes: Int
    get() = when (this) {
        SettingsPage.ACCOUNT -> R.string.settings_group_account
        SettingsPage.PLAYBACK -> R.string.settings_section_playback
        SettingsPage.INTERFACE -> R.string.settings_section_interface
        SettingsPage.ABOUT -> R.string.settings_group_about
    }

private val SettingsPage.icon: ImageVector
    get() = when (this) {
        SettingsPage.ACCOUNT -> Icons.Rounded.Person
        SettingsPage.PLAYBACK -> Icons.Rounded.PlayArrow
        SettingsPage.INTERFACE -> Icons.Rounded.Dashboard
        SettingsPage.ABOUT -> Icons.Rounded.Info
    }

/** The actions a page uses, with the ones that need the sheet (confirmations, closing) wired to it. */
private fun SettingsActions.copyWith(onLogout: () -> Unit, onClearDownloads: () -> Unit, close: () -> Unit) = copy(
    onAddAccount = { onAddAccount(); close() },
    onLogout = onLogout,
    onClearDownloads = onClearDownloads
)

// region Summaries: one line under each section

@Composable
private fun accountSummary(state: SettingsUiState): String =
    stringResource(if (state.hasPlus) R.string.settings_account_plus else R.string.settings_account_no_plus)

@Composable
private fun playbackSummary(state: SettingsUiState): String {
    val quality = stringResource(if (state.compatibleSound) R.string.settings_sound_compatible else R.string.settings_sound_max)
    val crossfade = if (state.crossfadeSeconds == 0) stringResource(R.string.settings_summary_off)
    else stringResource(R.string.settings_crossfade_seconds, state.crossfadeSeconds)
    val cache = when {
        state.reserveMb == 0 -> stringResource(R.string.settings_summary_off)
        state.reserveMb >= 1024 -> stringResource(R.string.settings_reserve_gb, state.reserveMb / 1024)
        else -> stringResource(R.string.settings_reserve_mb, state.reserveMb)
    }
    return stringResource(R.string.settings_summary_playback, quality.unbroken(), crossfade.unbroken(), cache.unbroken())
}

@Composable
private fun interfaceSummary(state: SettingsUiState): String {
    val open = stringResource(
        when (state.startPage) {
            StartPage.LAST -> R.string.settings_open_last
            StartPage.WAVE -> R.string.settings_open_wave
            StartPage.CAR -> R.string.settings_open_car
        }
    )
    val wheel = stringResource(if (state.steeringWheelOnLeft) R.string.settings_summary_wheel_left else R.string.settings_summary_wheel_right)
    val scale = if (state.uiScaleAuto) stringResource(R.string.settings_summary_scale_auto)
    else stringResource(R.string.settings_summary_scale_percent, (state.uiScaleManual * 100).roundToInt())
    return stringResource(R.string.settings_summary_interface, open.unbroken(), wheel.unbroken(), scale.unbroken())
}

/** A value such as "100 МБ" never breaks across two lines. */
private fun String.unbroken(): String = replace(' ', ' ')

@Composable
private fun aboutSummary(): String = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)

// endregion

@Composable
private fun ColumnScope.RootPage(state: SettingsUiState, onOpen: (SettingsPage) -> Unit) {
    Spacer(Modifier.heightIn(min = 14.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f))) {
        AccountCard(state.accountName, state.avatarUrl, state.hasPlus, onSwitch = null, onClick = { onOpen(SettingsPage.ACCOUNT) })
    }
    Spacer(Modifier.heightIn(min = 14.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f)).selectableGroup()) {
        SectionRow(SettingsPage.PLAYBACK, stringResource(R.string.settings_section_playback), playbackSummary(state), onOpen)
        SectionRow(SettingsPage.INTERFACE, stringResource(R.string.settings_section_interface), interfaceSummary(state), onOpen)
        SectionRow(SettingsPage.ABOUT, stringResource(R.string.settings_group_about), aboutSummary(), onOpen)
    }
    Text(
        "YANDEX MUSIC LITE",
        style = HiFiType.SectionCaps.copy(color = Color.White.copy(alpha = 0.40f), fontSize = 12.sp),
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp)
    )
}

@Composable
private fun SectionRow(page: SettingsPage, title: String, summary: String, onOpen: (SettingsPage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button) { onOpen(page) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(page.icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionNavItem(page: SettingsPage, title: String, summary: String, selected: Boolean, onOpen: (SettingsPage) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color(0x4DC9CED8), shape) else Modifier)
            .selectable(selected = selected, role = Role.Tab) { onOpen(page) }
            .heightIn(min = 68.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(page.icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ColumnScope.SettingsPageContent(page: SettingsPage, state: SettingsUiState, actions: SettingsActions) {
    when (page) {
        SettingsPage.ACCOUNT -> AccountPage(state, actions)
        SettingsPage.PLAYBACK -> PlaybackPage(state, actions)
        SettingsPage.INTERFACE -> InterfacePage(state, actions)
        SettingsPage.ABOUT -> AboutPage(state, actions)
    }
}

@Composable
private fun ColumnScope.AccountPage(state: SettingsUiState, actions: SettingsActions) {
    SettingsGroup(stringResource(R.string.settings_g_profile)) {
        AccountCard(state.accountName, state.avatarUrl, state.hasPlus, onSwitch = actions.onAccountManagement)
        SettingsRow(
            icon = Icons.Rounded.Star,
            title = stringResource(R.string.settings_startup_profile),
            value = state.defaultAccountName ?: stringResource(R.string.settings_startup_ask),
            onClick = actions.onAccountManagement
        )
        SettingsRow(icon = Icons.AutoMirrored.Rounded.Login, title = stringResource(R.string.settings_add_account), onClick = actions.onAddAccount)
    }
    SettingsGroup(null) {
        SettingsRow(icon = Icons.AutoMirrored.Rounded.Logout, title = stringResource(R.string.settings_logout), danger = true, chevron = false, onClick = actions.onLogout)
    }
    SettingsNote(stringResource(R.string.settings_logout_note))
}

@Composable
private fun ColumnScope.PlaybackPage(state: SettingsUiState, actions: SettingsActions) {
    SettingsGroup(stringResource(R.string.settings_g_sound)) {
        SoundQualityRow(state.compatibleSound, actions.onCompatibleSoundChange)
        CrossfadeRow(state.crossfadeSeconds, actions.onCrossfadeChange)
        SwitchRow(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.settings_normalize), stringResource(R.string.settings_normalize_hint), state.normalizeVolume, actions.onNormalizeVolumeChange)
        if (state.equalizerAvailable) {
            SettingsRow(icon = Icons.Rounded.Equalizer, title = stringResource(R.string.settings_equalizer), subtitle = stringResource(R.string.settings_equalizer_hint), onClick = actions.onOpenEqualizer)
        }
    }
    SettingsGroup(stringResource(R.string.settings_g_start)) {
        SegmentedRow(Icons.Rounded.PowerSettingsNew, stringResource(R.string.settings_start_music)) {
            HiFiSegmented(
                options = listOf(
                    stringResource(R.string.settings_start_music_off),
                    stringResource(R.string.settings_start_music_continue),
                    stringResource(R.string.settings_start_music_wave)
                ),
                selectedIndex = state.startMusic.ordinal,
                onSelect = { actions.onStartMusicChange(StartMusic.entries[it]) },
                fontSize = 13.sp
            )
            Spacer(Modifier.heightIn(min = 10.dp))
            SettingsHint(stringResource(R.string.settings_start_music_note))
        }
        SwitchRow(Icons.Rounded.Bluetooth, stringResource(R.string.settings_bluetooth), stringResource(R.string.settings_bluetooth_hint), state.bluetoothAutoplay, actions.onBluetoothAutoplayChange)
    }
    SettingsGroup(stringResource(R.string.settings_g_content)) {
        SwitchRow(Icons.Rounded.Explicit, stringResource(R.string.settings_explicit), stringResource(R.string.settings_explicit_hint), state.skipExplicit, actions.onSkipExplicitChange)
    }
    SettingsNote(stringResource(R.string.settings_wave_tuning_note))
    SettingsGroup(stringResource(R.string.settings_group_reserve)) {
        RoadReserveRows(state.reserveMb, actions.onReserveChange, state.reserveUsedBytes, actions.onClearReserve)
    }
    SettingsGroup(stringResource(R.string.settings_group_downloads)) {
        SwitchRow(Icons.Rounded.Download, stringResource(R.string.settings_auto_download), stringResource(R.string.settings_auto_download_hint), state.autoDownload, actions.onAutoDownloadChange)
        SettingsRow(icon = Icons.Rounded.CloudDownload, title = stringResource(R.string.settings_download_all), onClick = actions.onDownloadAll)
        SettingsRow(
            icon = Icons.Rounded.UploadFile,
            title = stringResource(R.string.settings_upgrade_downloads),
            subtitle = stringResource(R.string.settings_upgrade_downloads_hint),
            onClick = actions.onUpgradeDownloads
        )
        if (state.storage.cardAvailable || state.storage.onCard) {
            SegmentedRow(Icons.Rounded.SdCard, stringResource(R.string.settings_storage_place)) {
                HiFiSegmented(
                    options = listOf(stringResource(R.string.settings_storage_internal), stringResource(R.string.settings_storage_card)),
                    selectedIndex = if (state.storage.onCard) 1 else 0,
                    onSelect = { if (state.storage.moving == null) actions.onDownloadsOnCardChange(it == 1) },
                    fontSize = 13.sp
                )
                Spacer(Modifier.heightIn(min = 10.dp))
                val moving = state.storage.moving
                SettingsHint(
                    when {
                        moving != null -> stringResource(R.string.settings_storage_moving, moving.first, moving.second)
                        !state.storage.cardAvailable -> stringResource(R.string.settings_storage_card_missing)
                        else -> stringResource(R.string.settings_storage_card_note, formatSize(state.storage.cardFreeBytes))
                    }
                )
            }
        }
        SettingsRow(
            icon = Icons.Rounded.Storage,
            title = stringResource(R.string.settings_storage),
            subtitle = stringResource(R.string.settings_storage_value, formatSize(state.cacheSizeBytes)),
            chevron = false,
            onClick = null,
            trailing = {
                TextButton(onClick = actions.onClearDownloads) {
                    Text(stringResource(R.string.settings_clear), color = HiFi.Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.InterfacePage(state: SettingsUiState, actions: SettingsActions) {
    SettingsGroup(stringResource(R.string.settings_g_start)) {
        RadioListRow(
            icon = Icons.Rounded.Home,
            title = stringResource(R.string.settings_open_page),
            options = listOf(
                stringResource(R.string.settings_open_last) to stringResource(R.string.settings_open_last_hint),
                stringResource(R.string.settings_open_wave) to null,
                stringResource(R.string.settings_open_car) to stringResource(R.string.settings_open_car_hint)
            ),
            selectedIndex = state.startPage.ordinal,
            onSelect = { actions.onStartPageChange(StartPage.entries[it]) }
        )
    }
    SettingsGroup(stringResource(R.string.settings_g_player)) {
        SwitchRow(Icons.Rounded.MusicNote, stringResource(R.string.settings_expand_player), stringResource(R.string.settings_expand_player_hint), state.expandPlayerOnPlay, actions.onExpandPlayerChange)
        SwitchRow(Icons.Rounded.SwipeLeft, stringResource(R.string.settings_swipe_close), stringResource(R.string.settings_swipe_close_hint), state.swipeToCloseMiniPlayer, actions.onSwipeToCloseChange)
    }
    SettingsGroup(stringResource(R.string.settings_group_car)) {
        SegmentedRow(Icons.Rounded.DirectionsCar, stringResource(R.string.settings_steering)) {
            HiFiSegmented(
                options = listOf(stringResource(R.string.settings_steering_left_short), stringResource(R.string.settings_steering_right_short)),
                selectedIndex = if (state.steeringWheelOnLeft) 0 else 1,
                onSelect = { actions.onSteeringWheelChange(it == 0) }
            )
        }
        SwitchRow(Icons.Rounded.Schedule, stringResource(R.string.settings_car_clock), stringResource(R.string.settings_car_clock_hint), state.carClock, actions.onCarClockChange)
    }
    SettingsGroup(stringResource(R.string.settings_g_look)) {
        UiScaleRow(state.uiScaleAuto, actions.onUiScaleAutoChange, state.uiScaleManual, actions.onUiScaleManualChange)
        SegmentedRow(Icons.Rounded.Animation, stringResource(R.string.settings_animations)) {
            HiFiSegmented(
                options = listOf(
                    stringResource(R.string.settings_animations_auto),
                    stringResource(R.string.settings_animations_full),
                    stringResource(R.string.settings_animations_min)
                ),
                selectedIndex = state.animationMode.ordinal,
                onSelect = { actions.onAnimationModeChange(AnimationMode.entries[it]) },
                fontSize = 13.sp
            )
            Spacer(Modifier.heightIn(min = 10.dp))
            SettingsHint(stringResource(R.string.settings_animations_note))
        }
    }
}

@Composable
private fun ColumnScope.AboutPage(state: SettingsUiState, actions: SettingsActions) {
    val uri = LocalUriHandler.current
    SettingsGroup(stringResource(R.string.settings_g_version)) {
        SettingsRow(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            subtitle = state.updateStatus ?: stringResource(R.string.settings_check_updates),
            onClick = actions.onCheckForUpdates
        )
    }
    SettingsGroup(stringResource(R.string.settings_g_help)) {
        SettingsRow(
            icon = Icons.Rounded.WarningAmber,
            title = stringResource(R.string.crash_log_title),
            value = if (state.crashReports == 0) stringResource(R.string.crash_log_none) else state.crashReports.toString(),
            onClick = actions.onOpenCrashLog
        )
    }
    SettingsGroup(stringResource(R.string.settings_g_project)) {
        SettingsRow(icon = Icons.Rounded.Code, title = stringResource(R.string.settings_source_code), subtitle = "github.com/KorenskoyR/YandexMusicLite", onClick = { runCatching { uri.openUri(SOURCE_URL) } })
        SettingsRow(icon = Icons.Rounded.Description, title = stringResource(R.string.settings_license), value = "GPL-3.0", onClick = { runCatching { uri.openUri(LICENSE_URL) } })
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = hint,
        chevron = false,
        onClick = { onChange(!checked) },
        trailing = { HiFiSwitch(checked = checked, onCheckedChange = onChange) }
    )
}

/** A choice of a few options with a line of explanation each: segments would cut the longer names. */
@Composable
private fun RadioListRow(icon: ImageVector, title: String, options: List<Pair<String, String?>>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlaque(icon, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.padding(start = 44.dp, top = 6.dp).selectableGroup()) {
            options.forEachIndexed { index, (label, hint) ->
                val selected = index == selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioMark(selected)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        if (hint != null) Text(hint, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(HiFi.Accent).padding(6.dp).clip(CircleShape).background(Color.White)
                else Modifier.border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            )
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(text, color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun SettingsNote(text: String) {
    Text(text, color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
}

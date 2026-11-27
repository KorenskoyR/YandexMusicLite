package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.presentation.hifi.hifiRecessed
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.VoiceSearchActivity
import com.korenskoy.yandexmusic.MainActivity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Code
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import android.widget.Toast
import android.os.Build
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.layout.ColumnScope
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.presentation.voice.VoiceInput
import com.korenskoy.yandexmusic.presentation.voice.VoiceRoute
import com.korenskoy.yandexmusic.presentation.voice.rememberVoiceRoute

/**
 * "Голосовой поиск" in the settings: whether the microphone works here and how, or what to install for it.
 * Asked again each time the app comes back, so it answers right after the "Google" app is installed.
 */
@Composable
internal fun ColumnScope.VoiceSearchGroup() {
    val context = LocalContext.current
    val route = rememberVoiceRoute()
    var mic by remember { mutableStateOf(VoiceInput.micGranted(context)) }
    LifecycleResumeEffect(Unit) {
        mic = VoiceInput.micGranted(context)
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        mic = granted
        // Refused for good: Android asks no more, the switch is on the app's page of the settings
        if (!granted) VoiceInput.openAppSettings(context)
    }
    SettingsGroup(stringResource(R.string.settings_g_voice)) {
        // Play on the device: the way to the "Google" app; without it there is none (RuStore has no such app)
        val play = remember { VoiceInput.hasPlayStore(context) }
        VoiceSearchRows(
            route = route,
            micGranted = mic,
            onInstall = if (play) ({ VoiceInput.installGoogle(context) }) else null,
            onAllowMic = { ask.launch(Manifest.permission.RECORD_AUDIO) }
        )
        // The steering wheel: only where the device can hear at all
        if (route == VoiceRoute.RECOGNIZER) RecognizerPickRow()
        if (route != VoiceRoute.NONE) WheelVoiceRows()
    }
}

/** Several speech services installed: the row names the one that listens, a tap passes to the next. */
@Composable
private fun RecognizerPickRow() {
    val context = LocalContext.current
    val all = remember { VoiceInput.recognizers(context) }
    if (all.size < 2) return
    var current by remember { mutableStateOf(VoiceInput.chosen(all, VoiceInput.savedRecognizer(context))) }
    SettingsRow(
        icon = Icons.Rounded.Mic,
        title = stringResource(R.string.settings_voice_engine),
        subtitle = current?.label.orEmpty(),
        onClick = {
            VoiceInput.next(all, current)?.let {
                current = it
                VoiceInput.saveRecognizer(context, it.id)
            }
        },
        chevron = false
    )
}

/** The row itself, for each way the device can hear. */
@Composable
internal fun VoiceSearchRows(route: VoiceRoute, micGranted: Boolean, onInstall: (() -> Unit)?, onAllowMic: () -> Unit) {
    val needsMic = route == VoiceRoute.RECOGNIZER && !micGranted
    val works = route != VoiceRoute.NONE && !needsMic
    val status = stringResource(
        when {
            route == VoiceRoute.NONE -> R.string.settings_voice_none
            needsMic -> R.string.settings_voice_need_mic
            else -> R.string.settings_voice_works
        }
    )
    SettingsRow(
        icon = Icons.Rounded.Mic,
        title = stringResource(R.string.settings_voice_title),
        onClick = null,
        chevron = false,
        trailing = {
            Text(
                status, color = if (works) HiFi.palette.success else HiFi.palette.warning, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 6.dp).widthIn(max = 150.dp)
            )
        }
    )
    Column(Modifier.fillMaxWidth().padding(start = 58.dp, end = 14.dp, bottom = 14.dp)) {
        Text(
            stringResource(
                when {
                    route == VoiceRoute.SYSTEM_WINDOW -> R.string.settings_voice_hint_window
                    route == VoiceRoute.NONE && onInstall == null -> R.string.settings_voice_hint_none_no_play
                    route == VoiceRoute.NONE -> R.string.settings_voice_hint_none
                    needsMic -> R.string.settings_voice_hint_service_mic
                    else -> R.string.settings_voice_hint_service
                }
            ),
            color = HiFi.palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp
        )
        val action = if (needsMic) onAllowMic else onInstall.takeIf { route == VoiceRoute.NONE }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            MetalPillButton(
                stringResource(if (needsMic) R.string.settings_voice_allow_mic else R.string.settings_voice_install),
                action,
                Modifier.widthIn(min = 180.dp),
                height = 44.dp
            )
        }
    }
}

/**
 * «Голос с руля»: how to put «Голосовой поиск» on a steering-wheel button, a check that runs the very same entry,
 * and, folded away, the command for programs that send intents or run ADB (Tasker, Key Mapper, MK+).
 */
@Composable
internal fun WheelVoiceRows() {
    val context = LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    val copied = stringResource(R.string.settings_copied)
    val copy: (String, String) -> Unit = { label, value ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own note of what was copied
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
    SettingsRow(
        icon = Icons.Rounded.SettingsRemote,
        title = stringResource(R.string.settings_wheel_voice),
        subtitle = stringResource(R.string.settings_wheel_voice_sub),
        onClick = null,
        chevron = false,
        trailing = {
            MetalPillButton(
                stringResource(R.string.settings_wheel_voice_check),
                { context.startActivity(Intent(context, VoiceSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                height = 44.dp
            )
        }
    )
    Text(
        stringResource(R.string.settings_wheel_voice_how),
        color = HiFi.palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 58.dp, end = 14.dp, bottom = 14.dp)
    )
    SettingsRow(
        icon = Icons.Rounded.Code,
        title = stringResource(R.string.settings_wheel_advanced),
        subtitle = stringResource(R.string.settings_wheel_advanced_sub),
        onClick = { advanced = !advanced },
        chevron = false,
        trailing = {
            Icon(
                if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, tint = HiFi.palette.textSecondary, modifier = Modifier.size(24.dp)
            )
        }
    )
    AnimatedVisibility(advanced) {
        Column(
            Modifier.fillMaxWidth().padding(start = 58.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_wheel_advanced_how), color = HiFi.palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            CopyField(stringResource(R.string.settings_wheel_action), MainActivity.ACTION_START_VOICE, copy)
            CopyField(stringResource(R.string.settings_wheel_package), context.packageName, copy)
            CopyField(stringResource(R.string.settings_wheel_adb), VoiceSearchActivity.adbCommand(context), copy)
        }
    }
}

/** A value to type into another program: its name over it, the value in a recessed field, a copy button inside. */
@Composable
private fun CopyField(label: String, value: String, onCopy: (String, String) -> Unit) {
    val palette = HiFi.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = HiFiType.SectionCaps)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .hifiRecessed(12.dp)
                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            val description = stringResource(R.string.settings_copy_cd, label)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = description) { onCopy(label, value) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

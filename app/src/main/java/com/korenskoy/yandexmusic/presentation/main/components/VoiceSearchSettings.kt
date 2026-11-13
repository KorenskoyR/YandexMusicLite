package com.korenskoy.yandexmusic.presentation.main.components

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
        VoiceSearchRows(
            route = route,
            micGranted = mic,
            onInstall = { VoiceInput.installGoogle(context) },
            onAllowMic = { ask.launch(Manifest.permission.RECORD_AUDIO) }
        )
    }
}

/** The row itself, for each way the device can hear. */
@Composable
internal fun VoiceSearchRows(route: VoiceRoute, micGranted: Boolean, onInstall: () -> Unit, onAllowMic: () -> Unit) {
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
                status, color = if (works) HiFi.Success else HiFi.Warning, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 6.dp).widthIn(max = 150.dp)
            )
        }
    )
    Column(Modifier.fillMaxWidth().padding(start = 58.dp, end = 14.dp, bottom = 14.dp)) {
        Text(
            stringResource(
                when {
                    route == VoiceRoute.SYSTEM_WINDOW -> R.string.settings_voice_hint_window
                    route == VoiceRoute.NONE -> R.string.settings_voice_hint_none
                    needsMic -> R.string.settings_voice_hint_service_mic
                    else -> R.string.settings_voice_hint_service
                }
            ),
            color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
        )
        if (route == VoiceRoute.NONE || needsMic) {
            Spacer(Modifier.height(12.dp))
            MetalPillButton(
                stringResource(if (needsMic) R.string.settings_voice_allow_mic else R.string.settings_voice_install),
                if (needsMic) onAllowMic else onInstall,
                Modifier.widthIn(min = 180.dp),
                height = 44.dp
            )
        }
    }
}

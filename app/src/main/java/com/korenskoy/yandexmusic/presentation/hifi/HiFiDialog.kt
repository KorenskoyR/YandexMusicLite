package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Hi-Fi dialog surface: the same dark sheet as Settings, a thin light rim, no tonal tint. */
object HiFiDialogDefaults {
    val Container = Color(0xFF15171F)
    val Shape = RoundedCornerShape(28.dp)
    val Rim = Color.White.copy(alpha = 0.10f)

    /** Text fields inside dialogs: recessed dark well, white text, a steel (not blue) focus rim. */
    @Composable
    fun textFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color.Black.copy(alpha = 0.30f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.30f),
        focusedBorderColor = Color.White.copy(alpha = 0.45f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        cursorColor = HiFi.Accent,
        focusedPlaceholderColor = HiFi.TextTertiary,
        unfocusedPlaceholderColor = HiFi.TextTertiary
    )
}

enum class HiFiDialogActionKind { Primary, Danger, Quiet }

/**
 * A window over the app that keeps the app's own scale. A dialog is a separate window: Compose gives it the
 * system's density, so "Масштаб интерфейса" (the app's density) did not reach it — on a head unit that
 * reports a wrong density the update window, Settings and the questions were drawn at the system's size
 * (and cut off where the screen is short). The density the dialog is opened from is handed inside.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val appDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalDensity provides appDensity, content = content)
    }
}

/** Popups and sheets (menus, the track's sheet) are windows too: their content gets the app's scale back. */
@Composable
fun AppScale(density: Density, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalDensity provides density, content = content)

/**
 * A dialog in the Hi-Fi language. [confirm] and [dismiss] are usually [HiFiDialogAction]s: the main
 * action is a metal capsule (a red glass one when it destroys something), the way out a quiet text.
 * Drawn at the app's scale; a long text scrolls, so the actions are never pushed out of the screen.
 */
@Composable
fun HiFiAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    AppDialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
                .clip(HiFiDialogDefaults.Shape)
                .background(HiFiDialogDefaults.Container)
                .border(1.dp, HiFiDialogDefaults.Rim, HiFiDialogDefaults.Shape)
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalContentColor provides HiFi.TextSecondary) { content() }
                }
            }
            Spacer(Modifier.height(24.dp))
            FlowRow(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                dismiss?.invoke()
                confirm()
            }
        }
    }
}

@Composable
fun HiFiDialogAction(
    text: String,
    onClick: () -> Unit,
    kind: HiFiDialogActionKind = HiFiDialogActionKind.Primary,
    enabled: Boolean = true
) {
    when (kind) {
        HiFiDialogActionKind.Primary ->
            MetalPillButton(text, onClick, Modifier.widthIn(min = 112.dp), height = 44.dp, enabled = enabled)
        HiFiDialogActionKind.Danger -> Box(
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 112.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                .hifiGlass(CircleShape, fill = HiFi.Danger.copy(alpha = 0.14f), border = HiFi.Danger.copy(alpha = 0.40f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = HiFi.Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        HiFiDialogActionKind.Quiet -> HiFiTextAction(text, onClick)
    }
}

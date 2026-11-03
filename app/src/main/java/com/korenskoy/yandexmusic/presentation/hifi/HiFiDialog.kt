package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
 * A dialog in the Hi-Fi language. [confirm] and [dismiss] are usually [HiFiDialogAction]s: the main
 * action is a metal capsule (a red glass one when it destroys something), the way out a quiet text.
 */
@Composable
fun HiFiAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.border(1.dp, HiFiDialogDefaults.Rim, HiFiDialogDefaults.Shape),
        shape = HiFiDialogDefaults.Shape,
        containerColor = HiFiDialogDefaults.Container,
        tonalElevation = 0.dp,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
        text = content,
        confirmButton = confirm,
        dismissButton = dismiss
    )
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

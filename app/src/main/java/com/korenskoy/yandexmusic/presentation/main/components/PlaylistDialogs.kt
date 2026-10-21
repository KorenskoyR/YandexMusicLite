package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R

private val DialogBackground = Color(0xFF1E1E26)
private val Accent = Color(0xFFFF3366)

/** Name input for a new or renamed playlist; the confirm button needs a non-blank name. */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = name.text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    cursorColor = Accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        containerColor = DialogBackground,
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.trim()) }, enabled = valid) {
                Text(confirmLabel, color = if (valid) Accent else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_management_otmena), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun DeletePlaylistDialog(
    playlistTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Text(
                stringResource(R.string.playlist_delete_confirm, playlistTitle),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.5.sp
            )
        },
        containerColor = DialogBackground,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.playlist_delete_button), color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_management_otmena), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

private const val MAX_NAME_LENGTH = 100

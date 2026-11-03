package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.R

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

    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) name = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                colors = HiFiDialogDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        confirm = { HiFiDialogAction(confirmLabel, { onConfirm(name.text.trim()) }, enabled = valid) },
        dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), onDismiss, HiFiDialogActionKind.Quiet) }
    )
}

@Composable
fun DeletePlaylistDialog(
    playlistTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.playlist_delete),
        content = {
            Text(stringResource(R.string.playlist_delete_confirm, playlistTitle), color = HiFi.TextSecondary, fontSize = 15.sp)
        },
        confirm = { HiFiDialogAction(stringResource(R.string.playlist_delete_button), onConfirm, HiFiDialogActionKind.Danger) },
        dismiss = { HiFiDialogAction(stringResource(R.string.account_management_otmena), onDismiss, HiFiDialogActionKind.Quiet) }
    )
}

private const val MAX_NAME_LENGTH = 100

package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.download.PathCheck
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton

/**
 * Where the system's folder window is missing (some head units): the flash drive's path typed by hand. The app
 * tries to write there and says how it went; a usable path is taken at once. [noDrive] — the system shows the
 * app no drive at all: said first, in amber.
 */
@Composable
internal fun StoragePathRow(check: PathCheck?, noDrive: Boolean, onCheck: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var path by rememberSaveable { mutableStateOf("") }
    // The answer belongs to the path it was given for: typing on hides it
    var checkedFor by rememberSaveable { mutableStateOf<String?>(null) }
    val submit = {
        if (path.isNotBlank()) {
            checkedFor = path
            onCheck(path)
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)) {
        if (noDrive) {
            Text(
                stringResource(R.string.settings_storage_no_drive),
                color = HiFi.Warning, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HiFi.Warning.copy(alpha = 0.12f))
                    .border(1.dp, HiFi.Warning.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
        if (!open) {
            GlassPillButton(stringResource(R.string.settings_storage_path), onClick = { open = true }, icon = Icons.Rounded.Edit, height = 44.dp)
            return@Column
        }
        Text(stringResource(R.string.settings_storage_path_title), color = HiFi.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val field: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it.take(MAX_PATH) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(stringResource(R.string.settings_storage_path_hint)) },
                    colors = HiFiDialogDefaults.textFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = modifier
                )
            }
            val button: @Composable (Modifier) -> Unit = { modifier ->
                MetalPillButton(stringResource(R.string.settings_storage_path_check), onClick = submit, modifier = modifier, height = 52.dp, enabled = path.isNotBlank())
            }
            // A narrow sheet: the button under the field, so neither is squeezed
            if (maxWidth >= 340.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    field(Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    button(Modifier.width(132.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    field(Modifier.fillMaxWidth())
                    button(Modifier.fillMaxWidth())
                }
            }
        }
        val shown = check.takeIf { checkedFor != null && checkedFor == path }
        if (shown != null) {
            val ok = shown is PathCheck.Ok
            Row(
                Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline, contentDescription = null,
                    tint = if (ok) HiFi.Success else HiFi.Warning, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (shown) {
                        is PathCheck.Ok -> stringResource(R.string.settings_storage_path_ok, formatSize(shown.freeBytes))
                        PathCheck.NotFound -> stringResource(R.string.settings_storage_path_not_found)
                        PathCheck.NotWritable -> stringResource(R.string.settings_storage_path_not_writable)
                    },
                    color = if (ok) HiFi.Success else HiFi.Warning, fontSize = 14.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

private const val MAX_PATH = 200

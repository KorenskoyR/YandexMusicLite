package com.korenskoy.yandexmusic.presentation.crash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.crash.CrashLog
import java.text.SimpleDateFormat

/** The recorded crashes (when and where), with sending and clearing. */
@Composable
fun CrashLogDialog(reports: List<CrashLog.Report>, onSend: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val format = androidx.compose.runtime.remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_log_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.crash_log_none), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                } else {
                    reports.take(5).forEach { report ->
                        Text(format.format(report.time), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(report.summary, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(stringResource(R.string.crash_log_privacy), color = Color.White.copy(alpha = 0.55f), fontSize = 11.5.sp)
                }
            }
        },
        containerColor = Color(0xFF1E1E26),
        confirmButton = {
            if (reports.isNotEmpty()) {
                TextButton(onClick = { onSend(); onDismiss() }) {
                    Text(stringResource(R.string.crash_log_send), color = Color(0xFFFF3366), fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_log_close), color = Color(0xFFFF3366)) }
            }
        },
        dismissButton = {
            if (reports.isNotEmpty()) {
                TextButton(onClick = { onClear(); onDismiss() }) {
                    Text(stringResource(R.string.crash_log_clear), color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    )
}

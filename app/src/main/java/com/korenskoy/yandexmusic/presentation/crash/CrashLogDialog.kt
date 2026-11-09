package com.korenskoy.yandexmusic.presentation.crash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.crash.CrashLog
import java.text.SimpleDateFormat

/** The recorded crashes (when and where), with sending and clearing. */
@Composable
fun CrashLogDialog(
    reports: List<CrashLog.Report>,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.crash_log_title)
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val format = androidx.compose.runtime.remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.crash_log_none), color = HiFi.TextSecondary, fontSize = 15.sp)
                } else {
                    reports.take(5).forEach { report ->
                        Text(format.format(report.time), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(report.summary, color = HiFi.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(stringResource(R.string.crash_log_privacy), color = HiFi.TextTertiary, fontSize = 12.sp)
                }
            }
        },
        confirm = {
            if (reports.isNotEmpty()) HiFiDialogAction(stringResource(R.string.crash_log_send), { onSend(); onDismiss() })
            else HiFiDialogAction(stringResource(R.string.crash_log_close), onDismiss)
        },
        dismiss = if (reports.isNotEmpty()) {
            { HiFiDialogAction(stringResource(R.string.crash_log_clear), { onClear(); onDismiss() }, HiFiDialogActionKind.Quiet) }
        } else null
    )
}

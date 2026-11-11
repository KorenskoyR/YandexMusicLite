package com.korenskoy.yandexmusic.presentation.main.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last hour of music: how long it played, its stops, the switches to the cache and light streams, the
 * crossfades. Shared as text with a report, so a "it stutters" can be told apart: the link, the track or the device.
 */
@Composable
fun PlaybackJournalDialog(
    snapshot: PlaybackJournal.Snapshot,
    onShare: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val s = snapshot.summary
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.journal_title),
        content = {
            Column {
                Text(stringResource(R.string.journal_hint), color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                summaryLines(resources, s).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (snapshot.events.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.journal_events), color = HiFi.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    snapshot.events.takeLast(SHOWN).reversed().forEach { event ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(time.format(Date(event.atMs)), color = HiFi.TextTertiary, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                            Text(eventText(resources, event), color = eventColor(event.kind), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        },
        confirm = { HiFiDialogAction(stringResource(R.string.journal_share), { onShare(journalText(resources, snapshot, time)) }) },
        dismiss = { HiFiDialogAction(stringResource(R.string.journal_clear), { onClear(); onDismiss() }, HiFiDialogActionKind.Quiet) }
    )
}

private const val SHOWN = 30

private fun eventColor(kind: PlaybackJournal.Kind): Color = when (kind) {
    PlaybackJournal.Kind.STALL, PlaybackJournal.Kind.CROSSFADE_LATE -> HiFi.Warning
    PlaybackJournal.Kind.COPY, PlaybackJournal.Kind.STEADY -> HiFi.Success
    else -> Color.White.copy(alpha = 0.82f)
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000f).removeSuffix(".0")

private fun clock(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

internal fun summaryLines(r: Resources, s: PlaybackJournal.Summary): List<Pair<String, String>> = listOf(
    r.getString(R.string.journal_played) to r.getString(R.string.journal_minutes, s.playedMs / 60_000),
    r.getString(R.string.journal_stalls) to if (s.stalls == 0) "0" else r.getString(R.string.journal_stalls_value, s.stalls, seconds(s.stalledMs)),
    r.getString(R.string.journal_copies) to s.copySwitches.toString(),
    r.getString(R.string.journal_light) to s.lightTracks.toString(),
    r.getString(R.string.journal_crossfades) to r.getString(R.string.journal_crossfades_value, s.crossfades - s.crossfadesLate, s.crossfades)
)

internal fun eventText(r: Resources, e: PlaybackJournal.Event): String {
    val title = e.title?.let { "«$it»" }.orEmpty()
    return when (e.kind) {
        PlaybackJournal.Kind.STALL -> r.getString(R.string.journal_event_stall, seconds(e.valueMs), title).removePrefix(" — ").trim()
        PlaybackJournal.Kind.UNEVEN -> r.getString(R.string.journal_event_uneven)
        PlaybackJournal.Kind.COPY -> r.getString(R.string.journal_event_copy, title, clock(e.valueMs)).trim()
        PlaybackJournal.Kind.LIGHT -> r.getString(R.string.journal_event_light, title).trim()
        PlaybackJournal.Kind.STEADY -> r.getString(R.string.journal_event_steady)
        PlaybackJournal.Kind.CROSSFADE -> r.getString(R.string.journal_event_crossfade, title).trim()
        PlaybackJournal.Kind.CROSSFADE_LATE -> r.getString(R.string.journal_event_crossfade_late, title).trim()
    }
}

/** The journal as plain text, for a message to the developer. */
internal fun journalText(r: Resources, snapshot: PlaybackJournal.Snapshot, time: SimpleDateFormat): String = buildString {
    appendLine(r.getString(R.string.journal_title))
    summaryLines(r, snapshot.summary).forEach { (label, value) -> appendLine("$label: $value") }
    if (snapshot.events.isNotEmpty()) {
        appendLine()
        snapshot.events.forEach { appendLine("${time.format(Date(it.atMs))}  ${eventText(r, it)}") }
    }
}.trimEnd()

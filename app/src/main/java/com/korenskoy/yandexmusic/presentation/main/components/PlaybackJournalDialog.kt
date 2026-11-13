package com.korenskoy.yandexmusic.presentation.main.components

import com.korenskoy.yandexmusic.presentation.hifi.hifiGlass
import com.korenskoy.yandexmusic.data.player.StopCause
import com.korenskoy.yandexmusic.data.player.SoundTaker
import com.korenskoy.yandexmusic.data.player.Recovery
import com.korenskoy.yandexmusic.data.player.ProcessExits
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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
import com.korenskoy.yandexmusic.data.player.LinkReason
import com.korenskoy.yandexmusic.data.player.PlaybackJournal
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last hour of music: how long it played, its stops and their causes, the switches to the cache and light
 * streams, the crossfades. Shared as text with a report, so a "it stops" can be told apart: the link, another app,
 * the headphones, the phone or the track. When the phone closed the app in the background and may do it again
 * ([backgroundRestricted]), it offers to let the app work in the background.
 */
@Composable
fun PlaybackJournalDialog(
    snapshot: PlaybackJournal.Snapshot,
    onShare: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    backgroundRestricted: Boolean = false,
    onAllowBackground: () -> Unit = {}
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
                if (backgroundRestricted && closedByThePhone(snapshot)) {
                    Spacer(Modifier.height(12.dp))
                    BackgroundHint(onAllowBackground)
                }
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
                            Text(eventText(resources, event), color = eventColor(event), fontSize = 13.sp, lineHeight = 18.sp)
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

/** The phone closed the app while the music played, in this hour. */
internal fun closedByThePhone(snapshot: PlaybackJournal.Snapshot): Boolean =
    snapshot.events.any { it.kind == PlaybackJournal.Kind.KILLED && ProcessExits.closedByThePhone(it.detail) }

@Composable
private fun BackgroundHint(onAllow: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .hifiGlass(shape, fill = HiFi.Warning.copy(alpha = 0.10f), border = HiFi.Warning.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(stringResource(R.string.journal_background_hint), color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button, onClick = onAllow),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(stringResource(R.string.journal_allow_background), color = HiFi.Warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun eventColor(event: PlaybackJournal.Event): Color = when (event.kind) {
    PlaybackJournal.Kind.STALL, PlaybackJournal.Kind.CROSSFADE_LATE, PlaybackJournal.Kind.LINK_WEAK, PlaybackJournal.Kind.LINK_NONE,
    PlaybackJournal.Kind.WAVE_REPEAT, PlaybackJournal.Kind.ERROR, PlaybackJournal.Kind.KILLED, PlaybackJournal.Kind.UNDERRUN,
    PlaybackJournal.Kind.SLOW_START -> HiFi.Warning
    PlaybackJournal.Kind.PAUSED -> if (event.cause?.byListener == true) Color.White.copy(alpha = 0.82f) else HiFi.Warning
    PlaybackJournal.Kind.COPY, PlaybackJournal.Kind.STEADY, PlaybackJournal.Kind.LINK_BACK, PlaybackJournal.Kind.RESUMED,
    PlaybackJournal.Kind.LINK_RENEWED -> HiFi.Success
    else -> Color.White.copy(alpha = 0.82f)
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000f).removeSuffix(".0")

private fun clock(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

internal fun summaryLines(r: Resources, s: PlaybackJournal.Summary): List<Pair<String, String>> = listOf(
    r.getString(R.string.journal_played) to r.getString(R.string.journal_minutes, s.playedMs / 60_000),
    r.getString(R.string.journal_stalls) to if (s.stalls == 0) "0" else r.getString(R.string.journal_stalls_value, s.stalls, seconds(s.stalledMs)),
    r.getString(R.string.journal_copies) to s.copySwitches.toString(),
    r.getString(R.string.journal_light) to s.lightTracks.toString(),
    r.getString(R.string.journal_crossfades) to r.getString(R.string.journal_crossfades_value, s.crossfades - s.crossfadesLate, s.crossfades),
    r.getString(R.string.journal_pauses) to s.pausesNotAsked.toString(),
    r.getString(R.string.journal_errors) to s.errors.toString()
) + listOfNotNull(
    // Shown when they happened: each tells a cause of its own
    (r.getString(R.string.journal_kills) to s.kills.toString()).takeIf { s.kills > 0 },
    (r.getString(R.string.journal_slow_starts) to s.slowStarts.toString()).takeIf { s.slowStarts > 0 },
    (r.getString(R.string.journal_underruns) to s.underruns.toString()).takeIf { s.underruns > 0 },
    (r.getString(R.string.journal_renewed) to s.renewedLinks.toString()).takeIf { s.renewedLinks > 0 }
) + if (!s.waveUsed) emptyList() else listOf(
    // Whether the service offers the same tracks again, and whether the app let any through
    r.getString(R.string.journal_wave_offered_heard) to r.getString(R.string.journal_crossfades_value, s.waveOfferedHeard, s.waveOffered),
    r.getString(R.string.journal_wave_repeats) to s.waveRepeats.toString()
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
        PlaybackJournal.Kind.LINK_WEAK -> r.getString(R.string.journal_event_link_weak, reason(r, e.valueMs))
        PlaybackJournal.Kind.LINK_NONE -> r.getString(R.string.journal_event_link_none, reason(r, e.valueMs))
        PlaybackJournal.Kind.LINK_BACK -> r.getString(R.string.journal_event_link_back, reason(r, e.valueMs))
        PlaybackJournal.Kind.WAVE_BATCH -> r.getString(R.string.journal_event_wave_batch, e.valueMs.toInt(), e.count)
        PlaybackJournal.Kind.WAVE_REPEAT -> r.getString(R.string.journal_event_wave_repeat, title).trim()
        PlaybackJournal.Kind.PAUSED -> r.getString(R.string.journal_event_paused, title, causeText(r, e.cause, e.detail)).removePrefix(" — ").trim()
        PlaybackJournal.Kind.RESUMED -> r.getString(R.string.journal_event_resumed, seconds(e.valueMs), causeText(r, e.cause, null))
        PlaybackJournal.Kind.ERROR -> r.getString(R.string.journal_event_error, title, e.detail.orEmpty(), recoveryText(r, e.count)).removePrefix(" — ").trim()
        PlaybackJournal.Kind.LINK_RENEWED -> r.getString(R.string.journal_event_link_renewed)
        PlaybackJournal.Kind.UNDERRUN -> r.getString(R.string.journal_event_underrun, title, e.count).removePrefix(" — ").trim()
        PlaybackJournal.Kind.KILLED -> r.getString(R.string.journal_event_killed, exitText(r, e.detail))
        PlaybackJournal.Kind.SLOW_START -> r.getString(R.string.journal_event_slow_start, title, seconds(e.valueMs)).removePrefix(" — ").trim()
    }
}

/** Why the music paused, in words; [detail] tells who (a device) or what (the sound that took it). */
private fun causeText(r: Resources, cause: StopCause?, detail: String?): String = when (cause) {
    StopCause.USER_APP -> r.getString(R.string.journal_cause_user_app)
    StopCause.USER_DEVICE -> r.getString(R.string.journal_cause_user_device, controllerText(r, detail))
    StopCause.FOCUS_LOST -> r.getString(R.string.journal_cause_focus_lost, takerText(r, detail))
    StopCause.FOCUS_TRANSIENT -> r.getString(R.string.journal_cause_focus_transient, takerText(r, detail))
    StopCause.FOCUS_DENIED -> r.getString(R.string.journal_cause_focus_denied, takerText(r, detail))
    StopCause.OUTPUT_GONE -> r.getString(R.string.journal_cause_output_gone)
    StopCause.SLEEP_TIMER -> r.getString(R.string.journal_cause_sleep)
    StopCause.NO_PLUS -> r.getString(R.string.journal_cause_no_plus)
    StopCause.OFFLINE -> r.getString(R.string.journal_cause_offline)
    StopCause.UNKNOWN, null -> r.getString(R.string.journal_cause_unknown)
}

/** Who paused from outside the app, by its package: a few are known by name. */
internal fun controllerText(r: Resources, packageName: String?): String = when (packageName) {
    null, "" -> r.getString(R.string.journal_by_unknown)
    "com.android.systemui" -> r.getString(R.string.journal_by_system_ui)
    "com.android.bluetooth" -> r.getString(R.string.journal_by_bluetooth)
    "com.google.android.projection.gearhead", "com.android.car.media" -> r.getString(R.string.journal_by_car)
    "android", "com.android.server.telecom" -> r.getString(R.string.journal_by_headset)
    else -> if (packageName.startsWith("YandexMusicLite")) r.getString(R.string.journal_by_widget) else packageName
}

private fun takerText(r: Resources, taker: String?): String = r.getString(
    when (taker) {
        SoundTaker.CALL -> R.string.journal_taker_call
        SoundTaker.VOICE_CHAT -> R.string.journal_taker_voice_chat
        SoundTaker.NAVIGATION -> R.string.journal_taker_navigation
        SoundTaker.ASSISTANT -> R.string.journal_taker_assistant
        SoundTaker.NOTIFICATION -> R.string.journal_taker_notification
        SoundTaker.ALARM -> R.string.journal_taker_alarm
        SoundTaker.GAME -> R.string.journal_taker_game
        else -> R.string.journal_taker_app
    }
)

private fun recoveryText(r: Resources, ordinal: Int): String = r.getString(
    when (Recovery.entries.getOrNull(ordinal)) {
        Recovery.RESUME_FROM_COPY -> R.string.journal_recovery_copy
        Recovery.CONTINUE_OFFLINE -> R.string.journal_recovery_offline
        Recovery.RETRY -> R.string.journal_recovery_retry
        Recovery.STOP -> R.string.journal_recovery_stop
        Recovery.SKIP -> R.string.journal_recovery_skip
        null -> R.string.journal_recovery_file
    }
)

private fun exitText(r: Resources, reason: String?): String = r.getString(
    when (reason) {
        ProcessExits.LOW_MEMORY -> R.string.journal_exit_low_memory
        ProcessExits.CRASH -> R.string.journal_exit_crash
        ProcessExits.ANR -> R.string.journal_exit_anr
        ProcessExits.USER -> R.string.journal_exit_user
        ProcessExits.UPDATED -> R.string.journal_exit_updated
        ProcessExits.BATTERY -> R.string.journal_exit_battery
        ProcessExits.SYSTEM -> R.string.journal_exit_system
        else -> R.string.journal_exit_unknown
    }
)

/** Why the link's state changed, in words. */
private fun reason(r: Resources, ordinal: Long): String = r.getString(
    when (LinkReason.entries.getOrNull(ordinal.toInt())) {
        LinkReason.STREAM_STALLED -> R.string.journal_why_stalled
        LinkReason.REQUEST_FAILED -> R.string.journal_why_request
        LinkReason.SERVER_SILENT -> R.string.journal_why_server_silent
        LinkReason.NETWORK_GONE -> R.string.journal_why_network_gone
        LinkReason.NETWORK_BACK -> R.string.journal_why_network_back
        LinkReason.STREAM_SOUNDS -> R.string.journal_why_sounds
        LinkReason.SERVER_ANSWERED, null -> R.string.journal_why_server_answered
    }
)

/** The journal as plain text, for a message to the developer. */
internal fun journalText(r: Resources, snapshot: PlaybackJournal.Snapshot, time: SimpleDateFormat): String = buildString {
    appendLine(r.getString(R.string.journal_title))
    summaryLines(r, snapshot.summary).forEach { (label, value) -> appendLine("$label: $value") }
    if (snapshot.events.isNotEmpty()) {
        appendLine()
        snapshot.events.forEach { appendLine("${time.format(Date(it.atMs))}  ${eventText(r, it)}") }
    }
}.trimEnd()

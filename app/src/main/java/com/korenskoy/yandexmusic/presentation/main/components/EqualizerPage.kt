package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.player.AppEqualizer
import com.korenskoy.yandexmusic.data.player.eq.EqChain
import com.korenskoy.yandexmusic.data.player.eq.EqDesign
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiAlertDialog
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogAction
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogActionKind
import com.korenskoy.yandexmusic.presentation.hifi.HiFiDialogDefaults
import com.korenskoy.yandexmusic.presentation.hifi.HiFiFilterPill
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSlider
import com.korenskoy.yandexmusic.presentation.hifi.HiFiSwitch
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the equalizer page can do; each goes straight to [AppEqualizer], in order. */
data class EqualizerActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onGain: (Int, Float) -> Unit = { _, _ -> },
    val onBass: (Float) -> Unit = {},
    val onTreble: (Float) -> Unit = {},
    val onPreamp: (Float) -> Unit = {},
    val onNoClipping: (Boolean) -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onSaveCustom: (String) -> Unit = {},
    val onRenameCustom: (String, String) -> Unit = { _, _ -> },
    val onDeleteCustom: (String) -> Unit = {},
    val onReset: () -> Unit = {},
    val onComparing: (Boolean) -> Unit = {},
    val onOpenSystem: () -> Unit = {}
)

/**
 * Settings → Воспроизведение → Эквалайзер: the app's own, made of the settings' own parts (groups, rows, switches,
 * the sliders of «Кроссфейд»), so it reads as the rest of the settings and scales with them on every screen. The curve
 * over the ten bands is drawn in the app's blue with the metal knobs of its sliders. The device's equalizer, when
 * there is one, is a row at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.EqualizerPage(state: AppEqualizer.State, actions: EqualizerActions) {
    val settings = state.settings
    val palette = HiFi.palette
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppEqualizer.Preset?>(null) }
    // Leaving the page while «Сравнить» is held: the equalizer is heard again
    val compare by rememberUpdatedState(actions.onComparing)
    DisposableEffect(Unit) { onDispose { compare(false) } }

    SettingsGroup(null) {
        SettingsRow(
            icon = Icons.Rounded.Equalizer,
            title = stringResource(R.string.settings_equalizer),
            subtitle = stringResource(if (settings.enabled) R.string.equalizer_on_hint else R.string.equalizer_off_hint),
            chevron = false,
            onClick = { actions.onEnabled(!settings.enabled) },
            trailing = { HiFiSwitch(checked = settings.enabled, onCheckedChange = actions.onEnabled) }
        )
        CompareRow(enabled = settings.enabled, comparing = state.comparing, onComparing = actions.onComparing)
    }

    SettingsGroup(stringResource(R.string.equalizer_bands)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // About two thirds of the width tall, as a phone shows it; never a strip, never the whole screen
            EqCurve(state, actions, Modifier.fillMaxWidth().height((maxWidth * 0.62f).coerceIn(200.dp, 320.dp)))
        }
        Text(
            stringResource(R.string.equalizer_bands_hint), color = palette.textTertiary, fontSize = 13.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
        )
    }

    SettingsGroup(stringResource(R.string.equalizer_presets)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.presets.forEach { p ->
                if (p.custom) {
                    // The user's own: a long press renames or deletes it
                    @OptIn(ExperimentalFoundationApi::class)
                    Box(Modifier.combinedClickable(onClick = { actions.onPreset(p.id) }, onLongClick = { editing = p }, onLongClickLabel = stringResource(R.string.equalizer_edit_preset))) {
                        HiFiFilterPill(p.name, selected = p.id == state.presetId, onClick = { actions.onPreset(p.id) })
                    }
                } else {
                    HiFiFilterPill(p.name, selected = p.id == state.presetId, onClick = { actions.onPreset(p.id) })
                }
            }
            HiFiFilterPill("+ " + stringResource(R.string.equalizer_save_mine), selected = false, onClick = { saving = true })
        }
        if (state.presetId == null) {
            Text(stringResource(R.string.equalizer_own_curve), color = palette.textTertiary, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
        }
    }

    SettingsGroup(stringResource(R.string.equalizer_quick)) {
        LevelRow(Icons.Rounded.Speaker, stringResource(R.string.equalizer_bass), settings.bass, actions.onBass)
        LevelRow(Icons.Rounded.Waves, stringResource(R.string.equalizer_treble), settings.treble, actions.onTreble)
    }

    SettingsGroup(stringResource(R.string.equalizer_loudness)) {
        SettingsRow(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.equalizer_no_clipping),
            subtitle = stringResource(R.string.equalizer_no_clipping_hint),
            chevron = false,
            onClick = { actions.onNoClipping(!settings.noClipping) },
            trailing = { HiFiSwitch(checked = settings.noClipping, onCheckedChange = actions.onNoClipping) }
        )
        val autoPreamp = remember(settings) { EqChain.of(settings.copy(noClipping = true), CURVE_RATE).preampDb.toFloat() }
        LevelRow(
            Icons.AutoMirrored.Rounded.VolumeUp,
            stringResource(R.string.equalizer_preamp),
            if (settings.noClipping) autoPreamp else settings.preamp,
            actions.onPreamp,
            range = -EqDesign.MAX_DB..PREAMP_MAX,
            hint = stringResource(if (settings.noClipping) R.string.equalizer_preamp_auto else R.string.equalizer_preamp_hand)
        )
    }

    SettingsGroup(null) {
        if (state.systemAvailable) {
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.equalizer_system),
                subtitle = state.systemName?.let { stringResource(R.string.equalizer_system_app, it) } ?: stringResource(R.string.equalizer_system_hint),
                onClick = actions.onOpenSystem
            )
        }
        SettingsRow(
            icon = Icons.Rounded.Restore,
            title = stringResource(R.string.equalizer_reset),
            subtitle = stringResource(R.string.equalizer_reset_hint),
            chevron = false,
            onClick = actions.onReset
        )
    }

    if (saving) {
        NameDialog(
            title = stringResource(R.string.equalizer_save_mine),
            initial = "",
            onDone = { name -> actions.onSaveCustom(name); saving = false },
            onDismiss = { saving = false }
        )
    }
    editing?.let { p ->
        NameDialog(
            title = p.name,
            initial = p.name,
            onDone = { name -> actions.onRenameCustom(p.id, name); editing = null },
            onDismiss = { editing = null },
            onDelete = { actions.onDeleteCustom(p.id); editing = null }
        )
    }
}

/** Held — the music without the equalizer; let go — with it. A screen reader's tap turns it on and off. */
@Composable
private fun CompareRow(enabled: Boolean, comparing: Boolean, onComparing: (Boolean) -> Unit) {
    val palette = HiFi.palette
    val current by rememberUpdatedState(onComparing)
    val label = stringResource(R.string.equalizer_compare)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    current(true)
                    waitForUpOrCancellation()
                    current(false)
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (comparing) "без эквалайзера" else "с эквалайзером"
                onClick { current(!comparing); true }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPlaque(Icons.Rounded.GraphicEq, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = palette.textPrimary.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(if (comparing) R.string.equalizer_comparing else R.string.equalizer_compare_hint),
                color = if (comparing) palette.textPrimary else palette.textTertiary, fontSize = 13.sp, lineHeight = 17.sp
            )
        }
    }
}

/** A level in dB with a slider: as «Кроссфейд» is laid out. */
@Composable
private fun LevelRow(
    icon: ImageVector,
    title: String,
    value: Float,
    onValue: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = -EqDesign.MAX_DB..EqDesign.MAX_DB,
    hint: String? = null
) {
    val palette = HiFi.palette
    var shown by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlaque(icon, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(dbLong(shown), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        HiFiSlider(
            value = shown,
            onValueChange = { shown = snap(it).coerceIn(range.start, range.endInclusive); onValue(shown) },
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = title }
        )
        if (hint != null) Text(hint, color = palette.textTertiary, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** Where band [i] sits across a width: equal steps, an octave each. */
private fun bandX(i: Int, width: Float, n: Int = EqDesign.BANDS.size) = width * (i + 0.5f) / n

@Composable
private fun EqCurve(state: AppEqualizer.State, actions: EqualizerActions, modifier: Modifier) {
    val palette = HiFi.palette
    val curveColor = if (palette.isDark) Color(0xFFE6EAF2) else Color(0xFF181B24)
    val curveLine = if (palette.isDark) Color.White else Color(0xFF11131A)
    val settings = state.settings
    val dim = if (settings.enabled) 1f else 0.45f
    // The bands' curve goes through the knobs; with «Басы» or «Верха» moved, what is heard in all is dashed over it
    val bands = remember(settings.gains) { EqChain.of(settings.copy(enabled = true, bass = 0f, treble = 0f, noClipping = false, preamp = 0f), CURVE_RATE) }
    val whole = remember(settings.gains, settings.bass, settings.treble) {
        if (settings.bass == 0f && settings.treble == 0f) null else EqChain.of(settings.copy(enabled = true, noClipping = false, preamp = 0f), CURVE_RATE)
    }
    Column(modifier.padding(horizontal = 6.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            settings.gains.forEach { g ->
                Text(dbShort(g), color = palette.textPrimary.copy(alpha = 0.86f * dim), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val inset = KNOB_RADIUS.toPx()
                fun y(db: Double) = (inset + (h - 2 * inset) * (0.5 - db.coerceIn(-14.0, 14.0) / (2 * EqDesign.MAX_DB))).toFloat()
                for (db in listOf(12.0, 6.0, 0.0, -6.0, -12.0)) {
                    drawLine(palette.textPrimary.copy(alpha = if (db == 0.0) 0.22f else 0.08f), Offset(0f, y(db)), Offset(w, y(db)), 1.dp.toPx())
                }
                val mid = y(0.0)
                for (i in settings.gains.indices) {
                    val x = bandX(i, w)
                    drawLine(palette.textPrimary.copy(alpha = 0.12f), Offset(x, inset), Offset(x, h - inset), 4.dp.toPx(), StrokeCap.Round)
                    drawLine(curveColor.copy(alpha = 0.85f * dim), Offset(x, mid), Offset(x, y(settings.gains[i].toDouble())), 4.dp.toPx(), StrokeCap.Round)
                }
                fun curveOf(chain: EqChain): Path {
                    val path = Path()
                    for (k in 0..CURVE_STEPS) {
                        val x = w * k / CURVE_STEPS
                        val hz = EqDesign.BANDS[0] * 2.0.pow(x / w * EqDesign.BANDS.size - 0.5)
                        val py = y(chain.filtersDb(hz.coerceAtMost(CURVE_RATE * 0.48)))
                        if (k == 0) path.moveTo(x, py) else path.lineTo(x, py)
                    }
                    return path
                }
                val line = curveOf(bands)
                val fill = Path().apply {
                    addPath(line)
                    lineTo(w, mid)
                    lineTo(0f, mid)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(curveColor.copy(alpha = 0.24f * dim), Color.Transparent)))
                drawPath(line, curveLine.copy(alpha = dim), style = Stroke(2.5.dp.toPx()))
                whole?.let {
                    drawPath(
                        curveOf(it), palette.textPrimary.copy(alpha = 0.55f * dim),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
                    )
                }
                // The knobs of the app's sliders: a metal rim, a face matching the active theme
                val shadowCol = if (palette.isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF11141C).copy(alpha = 0.18f)
                for (i in settings.gains.indices) {
                    val c = Offset(bandX(i, w), y(settings.gains[i].toDouble()))
                    val r = KNOB_RADIUS.toPx()
                    drawCircle(shadowCol, r + 2.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
                    drawCircle(Brush.sweepGradient(palette.metalSweep, c), r, c)
                    drawCircle(Brush.radialGradient(palette.knobFace, c, r), r - 1.5.dp.toPx(), c)
                }
            }
            // One touch column per band: drag, tap to set, double tap — back to 0; a slider for the screen reader
            Row(Modifier.fillMaxSize()) {
                settings.gains.forEachIndexed { i, g -> BandHandle(i, g, actions.onGain, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            EqDesign.BANDS.forEach { hz ->
                Text(hertzShort(hz), color = palette.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BandHandle(index: Int, gain: Float, onGain: (Int, Float) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(gain)
    val set by rememberUpdatedState(onGain)
    val name = hertzLong(EqDesign.BANDS[index])
    Box(
        modifier
            .pointerInput(index) {
                fun dbAt(y: Float): Float {
                    val inset = KNOB_RADIUS.toPx()
                    val f = ((y - inset) / (size.height - 2 * inset)).coerceIn(0f, 1f)
                    return snap(EqDesign.MAX_DB * (1 - 2 * f))
                }
                detectTapGestures(onDoubleTap = { set(index, 0f) }, onTap = { set(index, dbAt(it.y)) })
            }
            .pointerInput(index) {
                var value = 0f
                detectVerticalDragGestures(
                    onDragStart = { value = current },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val inset = KNOB_RADIUS.toPx()
                        value = (value - dy / (size.height - 2 * inset) * 2 * EqDesign.MAX_DB).coerceIn(-EqDesign.MAX_DB, EqDesign.MAX_DB)
                        set(index, snap(value))
                    }
                )
            }
            .semantics {
                contentDescription = name
                stateDescription = dbLong(gain)
                progressBarRangeInfo = ProgressBarRangeInfo(gain, -EqDesign.MAX_DB..EqDesign.MAX_DB, steps = (EqDesign.MAX_DB * 4).toInt() - 1)
                setProgress { v -> set(index, snap(v)); true }
            }
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial) }
    HiFiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(NAME_MAX) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.equalizer_name_hint)) },
                    colors = HiFiDialogDefaults.textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    HiFiDialogAction(stringResource(R.string.equalizer_delete_preset), onDelete, HiFiDialogActionKind.Danger)
                }
            }
        },
        confirm = { HiFiDialogAction(stringResource(R.string.equalizer_done), { onDone(name) }, enabled = name.isNotBlank() || onDelete == null) },
        dismiss = { HiFiDialogAction(stringResource(R.string.equalizer_cancel), onDismiss, HiFiDialogActionKind.Quiet) }
    )
}

private val KNOB_RADIUS: Dp = 10.dp
private const val CURVE_STEPS = 120
private const val PREAMP_MAX = 6f
private const val NAME_MAX = 24

/** The curve is drawn for this rate: the device's own one makes no visible difference. */
private const val CURVE_RATE = 48_000

/** Half a decibel steps: fine enough, and a value one can read. */
private fun snap(db: Float): Float = ((db * 2).roundToInt() / 2f).coerceIn(-EqDesign.MAX_DB, EqDesign.MAX_DB)

/** "+3", "−1,5", "0" over a band. */
internal fun dbShort(db: Float): String = when {
    abs(db) < 0.05f -> "0"
    else -> (if (db > 0) "+" else "−") + String.format(Locale.US, "%.1f", abs(db)).removeSuffix(".0").replace('.', ',')
}

/** "+3 дБ", "−1,5 дБ", "0 дБ". */
internal fun dbLong(db: Float): String = dbShort(db) + " дБ"

/** "31", "1к", "16к" under a band. */
internal fun hertzShort(hz: Int): String = if (hz >= 1000) "${hz / 1000}к" else "$hz"

/** "31 Гц", "1 кГц" for the screen reader. */
internal fun hertzLong(hz: Int): String = if (hz >= 1000) "${hz / 1000} кГц" else "$hz Гц"

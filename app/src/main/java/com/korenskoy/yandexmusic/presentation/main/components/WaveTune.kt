package com.korenskoy.yandexmusic.presentation.main.components

import androidx.compose.material.icons.rounded.RestartAlt
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.WaveDiversity
import com.korenskoy.yandexmusic.domain.model.WaveLanguage
import com.korenskoy.yandexmusic.domain.model.WaveMood
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.hifi.GlassCircleButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiMotion
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType

// My Wave's character, mood and language: chips under the wave's title, a sheet on a phone, a panel
// on a side and in the car. A wave by a track gets "Back to My Wave" next to them.

@Composable
private fun WaveDiversity.label(): String = stringResource(
    when (this) {
        WaveDiversity.FAVORITE -> R.string.wave_diversity_favorite
        WaveDiversity.DISCOVER -> R.string.wave_diversity_discover
        WaveDiversity.POPULAR -> R.string.wave_diversity_popular
        WaveDiversity.ANY -> R.string.wave_any_neuter
    }
)

@Composable
private fun WaveMood.label(): String = stringResource(
    when (this) {
        WaveMood.ACTIVE -> R.string.wave_mood_active
        WaveMood.FUN -> R.string.wave_mood_fun
        WaveMood.CALM -> R.string.wave_mood_calm
        WaveMood.SAD -> R.string.wave_mood_sad
        WaveMood.ANY -> R.string.wave_any_neuter
    }
)

@Composable
private fun WaveLanguage.label(): String = stringResource(
    when (this) {
        WaveLanguage.RUSSIAN -> R.string.wave_language_russian
        WaveLanguage.FOREIGN -> R.string.wave_language_foreign
        WaveLanguage.NO_WORDS -> R.string.wave_language_no_words
        WaveLanguage.ANY -> R.string.wave_any_masculine
    }
)

/** "Незнакомое · Бодрое · Русский": only what differs from "any"; null when nothing does. */
@Composable
fun waveTuningSummary(tuning: WaveTuning): String? {
    val parts = buildList {
        if (tuning.diversity != WaveDiversity.ANY) add(tuning.diversity.label())
        if (tuning.mood != WaveMood.ANY) add(tuning.mood.label())
        if (tuning.language != WaveLanguage.ANY) add(tuning.language.label())
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * The line under the wave's title. My Wave: one chip with the current choice (or "Настроить волну").
 * A wave by a track: "К моей волне" and "Настроить".
 */
@Composable
fun WaveTuneChips(
    tuning: WaveTuning,
    trackWave: Boolean,
    onTune: () -> Unit,
    onBackToMyWave: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    fontSize: TextUnit = 14.sp
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (trackWave) {
            WavePill(Icons.Rounded.Waves, stringResource(R.string.wave_back_to_mine), active = false, height = height, fontSize = fontSize, onClick = onBackToMyWave)
            WavePill(Icons.Rounded.Tune, stringResource(R.string.wave_tune_short), active = !tuning.isDefault, height = height, fontSize = fontSize, onClick = onTune,
                description = stringResource(R.string.wave_tune_title))
        } else {
            val summary = waveTuningSummary(tuning)
            WavePill(
                icon = Icons.Rounded.Tune,
                text = summary ?: stringResource(R.string.wave_tune_title),
                active = summary != null,
                height = height,
                fontSize = fontSize,
                onClick = onTune,
                description = summary?.let { stringResource(R.string.wave_tune_cd, it) },
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
fun WavePill(
    icon: ImageVector,
    text: String,
    active: Boolean,
    height: Dp,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .then(
                if (active) Modifier.background(Brush.radialGradient(listOf(Color(0xFF2F333E), Color(0xFF16181E)))).border(1.dp, Color(0x8CC9CED8), shape)
                else Modifier.background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(start = height * 0.3f, end = height * 0.42f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(height * 0.5f))
        Spacer(Modifier.width(height * 0.2f))
        Text(text, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Three choices, each a row of options: [segments] true lays each row as one strip (side panels, the car). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveTuneChoices(
    tuning: WaveTuning,
    onChange: (WaveTuning) -> Unit,
    segments: Boolean,
    optionHeight: Dp = 44.dp,
    fontSize: TextUnit = 15.sp,
    captionSize: TextUnit = 12.sp,
    gap: Dp = 18.dp
) {
    val groups: List<Triple<String, List<String>, Pair<Int, (Int) -> Unit>>> = listOf(
        Triple(
            stringResource(R.string.wave_group_diversity),
            WaveDiversity.entries.map { it.label() },
            WaveDiversity.entries.indexOf(tuning.diversity) to { i: Int -> onChange(tuning.copy(diversity = WaveDiversity.entries[i])) }
        ),
        Triple(
            stringResource(R.string.wave_group_mood),
            WaveMood.entries.map { it.label() },
            WaveMood.entries.indexOf(tuning.mood) to { i: Int -> onChange(tuning.copy(mood = WaveMood.entries[i])) }
        ),
        Triple(
            stringResource(R.string.wave_group_language),
            WaveLanguage.entries.map { it.label() },
            WaveLanguage.entries.indexOf(tuning.language) to { i: Int -> onChange(tuning.copy(language = WaveLanguage.entries[i])) }
        )
    )
    groups.forEach { (title, options, choice) ->
        val (selected, select) = choice
        Text(title.uppercase(), style = HiFiType.SectionCaps.copy(fontSize = captionSize), modifier = Modifier.padding(top = gap, bottom = 10.dp))
        if (segments) {
            TuneStrip(options, selected, select, optionHeight, fontSize, title)
        } else {
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option -> TuneChip(option, index == selected, optionHeight, fontSize) { select(index) } }
            }
        }
    }
}

@Composable
private fun TuneChip(text: String, selected: Boolean, height: Dp, fontSize: TextUnit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        Modifier
            .heightIn(min = height)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.radialGradient(listOf(Color(0xFF2F333E), Color(0xFF16181E)))).border(1.dp, Color(0x8CC9CED8), shape)
                else Modifier.background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = height * 0.38f),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else Color.White.copy(alpha = 0.82f), fontSize = fontSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
    }
}

/** One strip; each option takes room by the length of its word, so long ones are not squeezed. */
@Composable
private fun TuneStrip(options: List<String>, selected: Int, onSelect: (Int) -> Unit, height: Dp, fontSize: TextUnit, label: String) {
    val inner = height - 8.dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            val shape = RoundedCornerShape(inner / 2)
            Box(
                Modifier
                    .weight(option.length + 3f)
                    .fillMaxHeight()
                    .clip(shape)
                    .then(if (on) Modifier.background(Brush.radialGradient(listOf(Color(0xFF2F333E), Color(0xFF16181E)))).border(1.dp, Color(0x8CC9CED8), shape) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.74f),
                    fontSize = fontSize,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * "Настроить волну": a sheet from the bottom on a phone; on a side (head unit, phone on its side) a panel
 * on the right, so the wave stays in view.
 */
@Composable
fun WaveTuneSheet(tuning: WaveTuning, onChange: (WaveTuning) -> Unit, onDismiss: () -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.targetState && visible.isIdle) onDismiss()
    }
    val close = { visible.targetState = false }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = visible, enter = fadeIn(HiFiMotion.standard()), exit = fadeOut(HiFiMotion.standard())) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isLandscape) 0.35f else 0.55f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                )
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = if (isLandscape) slideInHorizontally(HiFiMotion.large()) { it } else slideInVertically(HiFiMotion.large()) { it },
                exit = if (isLandscape) slideOutHorizontally(HiFiMotion.standard()) { it } else slideOutVertically(HiFiMotion.standard()) { it },
                modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
            ) {
                val shape = if (isLandscape) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                Column(
                    Modifier
                        .then(if (isLandscape) Modifier.width(460.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(12.dp) else Modifier.widthIn(max = 640.dp).fillMaxWidth())
                        .clip(shape)
                        .background(HiFi.SheetFill)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .verticalScroll(rememberScrollState())
                        .then(if (isLandscape) Modifier else Modifier.navigationBarsPadding())
                        .padding(start = 20.dp, end = 16.dp, top = if (isLandscape) 18.dp else 10.dp, bottom = 22.dp)
                ) {
                    if (!isLandscape) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.28f))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.wave_tune_title), style = HiFiType.LargeTitle.copy(fontSize = 24.sp, lineHeight = 30.sp), modifier = Modifier.weight(1f))
                        WavePill(Icons.Rounded.RestartAlt, stringResource(R.string.wave_tune_reset), active = false, height = 36.dp, fontSize = 14.sp, onClick = { onChange(WaveTuning()) })
                        Spacer(Modifier.width(8.dp))
                        GlassCircleButton(onClick = close, contentDescription = stringResource(R.string.cd_zakryt)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    WaveTuneChoices(tuning, onChange, segments = isLandscape, optionHeight = if (isLandscape) 52.dp else 44.dp, fontSize = if (isLandscape) 14.sp else 15.sp)
                    Text(
                        stringResource(R.string.wave_tune_note),
                        color = HiFi.TextTertiary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }
    }
}

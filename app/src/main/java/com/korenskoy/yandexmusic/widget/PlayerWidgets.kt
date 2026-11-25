package com.korenskoy.yandexmusic.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.main.components.MainTab
import kotlin.math.roundToInt

/**
 * «Плеер» 4×2: cover & track info at the top, progress bar + times in the middle, full-width buttons at the foot.
 * Scales seamlessly from compact 180×110 widgets to large tablet grids without dead gaps.
 */
class PlayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { PlayerWidgetContent(rememberWidgetNow(context)) }
    }
}

class PlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()
}

@Composable
fun PlayerWidgetContent(now: WidgetNow) {
    val context = LocalContext.current
    val (w, h) = LocalSize.current.safeOrDefault(250f, 130f)
    val fs = context.resources.configuration.fontScale

    Box(GlanceModifier.fillMaxSize().widgetCardBackground(R.drawable.widget_bg_28, 28.dp)) {
        if (now.hasTrack) {
            WidgetGlowBackdrop(now.glow, w, h)
        }
        if (!now.hasTrack) {
            WaveInvite(context, inviteSpec(w, h, fs))
            return@Box
        }

        val spec = playerSpec(w, h, fs, now.canReact, now.isRadio)
        Column(GlanceModifier.fillMaxSize().padding(spec.pad.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(WidgetActions.openPlayer(context)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (spec.showCover) {
                    val noteDp = (spec.cover * 0.44f).roundToInt().coerceAtLeast(24).toFloat()
                    CoverArtBox(now, side = spec.cover, cornerDp = spec.coverRadius, noteSize = noteDp)
                    Spacer(GlanceModifier.width(if (spec.cover >= 140f) 18.dp else 14.dp))
                }
                Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    if (spec.showSource) {
                        now.source?.let { WidgetText(it, spec.sourceSp.sp, WidgetColors.caps, bold = true) }
                    }
                    WidgetText(now.title.orEmpty(), spec.titleSp.sp, bold = true, lines = spec.titleLines)
                    if (spec.showArtist) {
                        WidgetText(now.artist.orEmpty(), spec.artistSp.sp, WidgetColors.textSoft)
                    }
                }
                if (spec.headerLike) {
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetRound(
                        icon = if (now.isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
                        description = context.getString(R.string.cd_nravitsya),
                        action = WidgetActions.like(context),
                        size = 38.dp,
                        iconSize = 20.dp,
                        fill = WidgetFill.CHIP,
                        tint = if (now.isLiked) WidgetColors.love else Color.White
                    )
                }
            }
            if (spec.showBar && now.progress != null) {
                Spacer(GlanceModifier.height(4.dp))
                WidgetProgressBar(now.progress, 4.dp)
                if (spec.showTimes) {
                    Spacer(GlanceModifier.height(4.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetText(widgetClock(now.positionMs), 11.sp, WidgetColors.textSoft, modifier = GlanceModifier.defaultWeight())
                        val rem = (now.durationMs - now.positionMs).coerceAtLeast(0L)
                        WidgetText("−" + widgetClock(rem), 11.sp, WidgetColors.textSoft)
                    }
                }
            }
            Spacer(GlanceModifier.height(spec.sectionGap.dp))
            PlayerControlsRow(context, now, spec)
        }
    }
}

@Composable
private fun WidgetGlowBackdrop(glow: Bitmap?, w: Float, h: Float) {
    val glowBmp = rememberWidgetBitmap(glow, w, h, 28f, cropToAspect = false, maxSidePx = 192) ?: return
    val glowMod = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.fillMaxSize().cornerRadius(28.dp) else GlanceModifier.fillMaxSize()
    Image(ImageProvider(glowBmp), contentDescription = null, modifier = glowMod, contentScale = ContentScale.FillBounds)
    val shadeMod = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_shade_28))
    Box(if (Build.VERSION.SDK_INT >= 31) shadeMod.cornerRadius(28.dp) else shadeMod) {}
}

@Composable
private fun CoverArtBox(
    now: WidgetNow,
    side: Float,
    cornerDp: Float,
    noteSize: Float,
    onOpen: Action? = null
) {
    val bmp = rememberWidgetBitmap(now.cover, side, side, cornerDp, cropToAspect = true)
    val chipDrawable = if (cornerDp <= 15f) R.drawable.widget_cover_chip_14 else R.drawable.widget_cover_chip_18
    var base = GlanceModifier.size(side.dp).background(ImageProvider(chipDrawable))
    if (onOpen != null) base = base.clickable(onOpen)
    val boxMod = if (Build.VERSION.SDK_INT >= 31) base.cornerRadius(cornerDp.dp) else base
    Box(modifier = boxMod, contentAlignment = Alignment.Center) {
        if (bmp != null) {
            val imgBase = GlanceModifier.size(side.dp)
            val imgMod = if (Build.VERSION.SDK_INT >= 31) imgBase.cornerRadius(cornerDp.dp) else imgBase
            val scale = if (Build.VERSION.SDK_INT >= 31) ContentScale.Crop else ContentScale.FillBounds
            Image(ImageProvider(bmp), contentDescription = now.title, modifier = imgMod, contentScale = scale)
        } else {
            Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(noteSize.dp))
        }
    }
}

@Composable
private fun PlayerControlsRow(context: Context, now: WidgetNow, spec: PlayerSpec) {
    val reactIcon = (spec.react * 0.46f).roundToInt().dp
    val chipIcon = (spec.chip * 0.46f).roundToInt().dp
    val knobIcon = (spec.knob * 0.46f).roundToInt().dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (spec.showDislike) {
            WidgetRound(
                icon = R.drawable.ic_notification_dislike,
                description = context.getString(R.string.cd_ne_nravitsya),
                action = WidgetActions.dislike(context),
                size = spec.react.dp,
                iconSize = reactIcon,
                fill = WidgetFill.CHIP
            )
            Spacer(GlanceModifier.defaultWeight())
        }
        WidgetRound(
            icon = R.drawable.ic_widget_prev,
            description = context.getString(R.string.cd_predyduschiy),
            action = WidgetActions.previous(context),
            size = spec.chip.dp,
            iconSize = chipIcon,
            fill = WidgetFill.CHIP
        )
        Spacer(GlanceModifier.defaultWeight())
        WidgetRound(
            icon = if (now.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = context.getString(R.string.cd_vosproizvedenie_ili_pauza),
            action = WidgetActions.playPause(context),
            size = spec.knob.dp,
            iconSize = knobIcon,
            fill = WidgetFill.KNOB,
            tint = WidgetColors.onKnob
        )
        Spacer(GlanceModifier.defaultWeight())
        WidgetRound(
            icon = R.drawable.ic_widget_next,
            description = context.getString(R.string.cd_sleduyuschiy),
            action = WidgetActions.next(context),
            size = spec.chip.dp,
            iconSize = chipIcon,
            fill = WidgetFill.CHIP
        )
        if (spec.showLike) {
            Spacer(GlanceModifier.defaultWeight())
            WidgetRound(
                icon = if (now.isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
                description = context.getString(R.string.cd_nravitsya),
                action = WidgetActions.like(context),
                size = spec.react.dp,
                iconSize = reactIcon,
                fill = WidgetFill.CHIP,
                tint = if (now.isLiked) WidgetColors.love else Color.White
            )
        }
    }
}

/** Nothing plays: the wave to start, and — where there is room — the app's pages in a balanced row below. */
@Composable
private fun WaveInvite(context: Context, spec: WaveInviteSpec) {
    val iconDp = (spec.knob * 0.42f).roundToInt().dp
    if (!spec.showChips) {
        Row(
            GlanceModifier.fillMaxSize().padding(spec.pad.dp).clickable(WidgetActions.startWave(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetRound(
                icon = R.drawable.ic_widget_play,
                description = context.getString(R.string.widget_wave_start),
                action = WidgetActions.startWave(context),
                size = spec.knob.dp,
                iconSize = iconDp,
                fill = WidgetFill.KNOB,
                tint = WidgetColors.onKnob
            )
            Spacer(GlanceModifier.width(spec.gap.dp))
            Column(GlanceModifier.defaultWeight()) {
                WidgetText(context.getString(R.string.widget_wave_title), 20.sp, bold = true)
                WidgetText(context.getString(R.string.widget_wave_hint), 13.sp, WidgetColors.textSoft, lines = spec.hintLines)
            }
        }
        return
    }
    Column(
        GlanceModifier.fillMaxSize().padding(spec.pad.dp).clickable(WidgetActions.startWave(context)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetRound(
                icon = R.drawable.ic_widget_play,
                description = context.getString(R.string.widget_wave_start),
                action = WidgetActions.startWave(context),
                size = spec.knob.dp,
                iconSize = iconDp,
                fill = WidgetFill.KNOB,
                tint = WidgetColors.onKnob
            )
            Spacer(GlanceModifier.width(spec.gap.dp))
            Column(GlanceModifier.defaultWeight()) {
                WidgetText(context.getString(R.string.widget_wave_title), 20.sp, bold = true)
                WidgetText(context.getString(R.string.widget_wave_hint), 13.sp, WidgetColors.textSoft, lines = spec.hintLines)
            }
        }
        Spacer(GlanceModifier.height(if (spec.knob >= 68f) 20.dp else 14.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageChip(context.getString(R.string.widget_page_liked), WidgetActions.open(context, MainTab.LIBRARY.route))
            Spacer(GlanceModifier.width(8.dp))
            PageChip(context.getString(R.string.widget_page_radio), WidgetActions.open(context, MainTab.RADIO.route))
            Spacer(GlanceModifier.width(8.dp))
            PageChip(context.getString(R.string.widget_page_car), WidgetActions.open(context, MainTab.CAR.route))
        }
    }
}

@Composable
private fun PageChip(label: String, action: Action) {
    val base = GlanceModifier.height(44.dp).background(ImageProvider(R.drawable.widget_pill_chip)).padding(horizontal = 12.dp)
    val mod = (if (Build.VERSION.SDK_INT >= 31) base.cornerRadius(22.dp) else base).clickable(action)
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        WidgetText(label, 13.sp, bold = true)
    }
}

/**
 * «Обложка» 2×2: adapts seamlessly between SQUARE full-bleed, TALL framed card, and WIDE side-by-side layout.
 */
class CoverWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { CoverWidgetContent(rememberWidgetNow(context)) }
    }
}

class CoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CoverWidget()
}

@Composable
fun CoverWidgetContent(now: WidgetNow) {
    val context = LocalContext.current
    val (w, h) = LocalSize.current.safeOrDefault(150f, 150f)
    val fs = context.resources.configuration.fontScale
    val spec = coverSpec(w, h, fs, now.hasTrack)

    Box(GlanceModifier.fillMaxSize().widgetCardBackground(R.drawable.widget_bg_28, 28.dp)) {
        when (spec.mode) {
            CoverMode.EMPTY -> CoverEmptyContent(context, spec)
            CoverMode.SQUARE -> CoverSquareContent(context, now, w, h, spec)
            CoverMode.TALL -> CoverTallContent(context, now, w, h, spec)
            CoverMode.WIDE -> CoverWideContent(context, now, w, h, spec)
        }
    }
}

@Composable
private fun CoverEmptyContent(context: Context, spec: CoverSpec) {
    val iconDp = (spec.knob * 0.42f).roundToInt().dp
    Column(
        GlanceModifier.fillMaxSize().padding(spec.pad.dp).clickable(WidgetActions.startWave(context)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetRound(
            icon = R.drawable.ic_widget_play,
            description = context.getString(R.string.widget_wave_start),
            action = WidgetActions.startWave(context),
            size = spec.knob.dp,
            iconSize = iconDp,
            fill = WidgetFill.KNOB,
            tint = WidgetColors.onKnob
        )
        if (spec.showTitleWhenEmpty) {
            Spacer(GlanceModifier.height(10.dp))
            WidgetText(context.getString(R.string.widget_wave_title), 15.sp, bold = true)
        }
    }
}

@Composable
private fun CoverSquareContent(context: Context, now: WidgetNow, w: Float, h: Float, spec: CoverSpec) {
    val bmp = rememberWidgetBitmap(now.cover, w, h, 28f, cropToAspect = true)
    if (bmp != null) {
        val imgBase = GlanceModifier.fillMaxSize().clickable(WidgetActions.openPlayer(context))
        val imgMod = if (Build.VERSION.SDK_INT >= 31) imgBase.cornerRadius(28.dp) else imgBase
        val scale = if (Build.VERSION.SDK_INT >= 31) ContentScale.Crop else ContentScale.FillBounds
        Image(ImageProvider(bmp), contentDescription = now.title, modifier = imgMod, contentScale = scale)
    } else {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(bottom = (spec.knob * 0.55f).dp).clickable(WidgetActions.openPlayer(context)),
            contentAlignment = Alignment.Center
        ) {
            Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(spec.noteSize.dp))
        }
    }

    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
        Column(
            GlanceModifier.fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_scrim))
                .padding(start = spec.pad.dp, end = spec.pad.dp, top = 20.dp, bottom = spec.pad.dp)
        ) {
            if (spec.showText) {
                WidgetText(now.title.orEmpty(), spec.titleSp.sp, bold = true)
                WidgetText(now.artist.orEmpty(), spec.artistSp.sp, WidgetColors.textSoft)
                Spacer(GlanceModifier.height(8.dp))
            }
            CoverControlsRow(context, now, spec, sideFill = WidgetFill.SHADE)
        }
    }
}

@Composable
private fun CoverTallContent(context: Context, now: WidgetNow, w: Float, h: Float, spec: CoverSpec) {
    WidgetGlowBackdrop(now.glow, w, h)
    val openPlayer = WidgetActions.openPlayer(context)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(spec.pad.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtBox(now = now, side = spec.artSide, cornerDp = spec.artCorner, noteSize = spec.noteSize, onOpen = openPlayer)
        Spacer(GlanceModifier.height(spec.blockGap.dp))
        if (spec.showText) {
            Column(GlanceModifier.fillMaxWidth().clickable(openPlayer)) {
                WidgetText(now.title.orEmpty(), spec.titleSp.sp, bold = true, lines = spec.titleLines)
                WidgetText(now.artist.orEmpty(), spec.artistSp.sp, WidgetColors.textSoft)
            }
            Spacer(GlanceModifier.height(spec.blockGap.dp))
        }
        CoverControlsRow(context, now, spec, sideFill = WidgetFill.CHIP)
    }
}

@Composable
private fun CoverWideContent(context: Context, now: WidgetNow, w: Float, h: Float, spec: CoverSpec) {
    WidgetGlowBackdrop(now.glow, w, h)
    val openPlayer = WidgetActions.openPlayer(context)
    Row(
        GlanceModifier.fillMaxSize().padding(spec.pad.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtBox(
            now = now,
            side = spec.artSide,
            cornerDp = spec.artCorner,
            noteSize = spec.noteSize,
            onOpen = openPlayer
        )
        Spacer(GlanceModifier.width(spec.artGap.dp))
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (spec.showText) {
                Column(GlanceModifier.fillMaxWidth().clickable(openPlayer)) {
                    WidgetText(now.title.orEmpty(), spec.titleSp.sp, bold = true, lines = spec.titleLines)
                    WidgetText(now.artist.orEmpty(), spec.artistSp.sp, WidgetColors.textSoft)
                }
                Spacer(GlanceModifier.height(spec.blockGap.dp))
            }
            CoverControlsRow(context, now, spec, sideFill = WidgetFill.CHIP)
        }
    }
}

@Composable
private fun CoverControlsRow(context: Context, now: WidgetNow, spec: CoverSpec, sideFill: WidgetFill) {
    val isSolo = spec.prev <= 0f && spec.next <= 0f
    val knobIcon = (spec.knob * 0.46f).roundToInt().dp
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = if (isSolo) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (spec.prev > 0f) {
            WidgetRound(
                icon = R.drawable.ic_widget_prev,
                description = context.getString(R.string.cd_predyduschiy),
                action = WidgetActions.previous(context),
                size = spec.prev.dp,
                iconSize = (spec.prev * 0.46f).roundToInt().dp,
                fill = sideFill
            )
            Spacer(GlanceModifier.defaultWeight())
        }
        WidgetRound(
            icon = if (now.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = context.getString(R.string.cd_vosproizvedenie_ili_pauza),
            action = WidgetActions.playPause(context),
            size = spec.knob.dp,
            iconSize = knobIcon,
            fill = WidgetFill.KNOB,
            tint = WidgetColors.onKnob
        )
        if (spec.next > 0f) {
            Spacer(GlanceModifier.defaultWeight())
            WidgetRound(
                icon = R.drawable.ic_widget_next,
                description = context.getString(R.string.cd_sleduyuschiy),
                action = WidgetActions.next(context),
                size = spec.next.dp,
                iconSize = (spec.next * 0.46f).roundToInt().dp,
                fill = sideFill
            )
        }
    }
}

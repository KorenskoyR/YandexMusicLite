package com.korenskoy.yandexmusic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
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

/**
 * «Плеер» 4×2: the glow of the cover behind everything, the cover, the source, the title on two lines, the artist,
 * the progress with its times, and dislike, back, play, forward, like. Narrowed to three cells the cover gives way
 * and the buttons keep their size. With nothing playing: «Моя волна» to start, and the app's pages a tap away.
 */
class PlayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(200.dp, 130.dp), DpSize(280.dp, 130.dp), DpSize(320.dp, 180.dp)))

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
    val size = LocalSize.current
    val withCover = size.width >= 260.dp
    val coverSide = (size.height - 32.dp).coerceIn(90.dp, 160.dp)
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(28.dp).background(WidgetColors.ground)) {
        if (now.glow != null) {
            Image(ImageProvider(now.glow), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Box(GlanceModifier.fillMaxSize().background(WidgetColors.shade)) {}
        }
        if (!now.hasTrack) {
            WaveInvite(context, size.height >= 150.dp)
            return@Box
        }
        Row(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (withCover) {
                Box(GlanceModifier.size(coverSide).cornerRadius(18.dp).background(WidgetColors.chip).clickable(WidgetActions.openPlayer(context)), contentAlignment = Alignment.Center) {
                    if (now.cover != null) {
                        Image(ImageProvider(now.cover), contentDescription = now.title, modifier = GlanceModifier.size(coverSide).cornerRadius(18.dp), contentScale = ContentScale.Crop)
                    } else {
                        Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(36.dp))
                    }
                }
                Spacer(GlanceModifier.width(16.dp))
            }
            Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(WidgetActions.openPlayer(context)), verticalAlignment = Alignment.CenterVertically) {
                now.source?.let { WidgetText(it, 11.sp, WidgetColors.caps, bold = true) }
                WidgetText(now.title.orEmpty(), 18.sp, bold = true, lines = 2)
                WidgetText(now.artist.orEmpty(), 13.sp, WidgetColors.textSoft)
                Spacer(GlanceModifier.height(8.dp))
                now.progress?.let { p ->
                    LinearProgressIndicator(progress = p, modifier = GlanceModifier.fillMaxWidth().height(4.dp), color = WidgetColors.text, backgroundColor = WidgetColors.track)
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetText(widgetClock(now.positionMs), 11.sp, WidgetColors.textSoft, modifier = GlanceModifier.defaultWeight())
                        WidgetText("−" + widgetClock(now.durationMs - now.positionMs), 11.sp, WidgetColors.textSoft)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // A station is liked (made a favourite), never disliked
                    if (now.canReact && !now.isRadio) {
                        WidgetRound(R.drawable.ic_notification_dislike, context.getString(R.string.cd_ne_nravitsya), WidgetActions.dislike(context), 44.dp, 20.dp, WidgetColors.chip)
                        Spacer(GlanceModifier.defaultWeight())
                    }
                    WidgetRound(R.drawable.ic_widget_prev, context.getString(R.string.cd_predyduschiy), WidgetActions.previous(context), 48.dp, 22.dp, WidgetColors.chip)
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetRound(
                        if (now.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        context.getString(R.string.cd_vosproizvedenie_ili_pauza), WidgetActions.playPause(context), 56.dp, 26.dp, WidgetColors.knob, WidgetColors.onKnob
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetRound(R.drawable.ic_widget_next, context.getString(R.string.cd_sleduyuschiy), WidgetActions.next(context), 48.dp, 22.dp, WidgetColors.chip)
                    if (now.canReact) {
                        Spacer(GlanceModifier.defaultWeight())
                        WidgetRound(
                            if (now.isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
                            context.getString(R.string.cd_nravitsya), WidgetActions.like(context), 44.dp, 20.dp, WidgetColors.chip,
                            if (now.isLiked) WidgetColors.love else Color.White
                        )
                    }
                }
            }
        }
    }
}

/** Nothing plays: the wave to start, and — where there is room — the app's pages a tap away. */
@Composable
private fun WaveInvite(context: Context, withPages: Boolean) {
    Row(GlanceModifier.fillMaxSize().padding(18.dp).clickable(WidgetActions.startWave(context)), verticalAlignment = Alignment.CenterVertically) {
        WidgetRound(R.drawable.ic_widget_play, context.getString(R.string.widget_wave_start), WidgetActions.startWave(context), 88.dp, 36.dp, WidgetColors.knob, WidgetColors.onKnob)
        Spacer(GlanceModifier.width(18.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetText(context.getString(R.string.widget_wave_title), 20.sp, bold = true)
            WidgetText(context.getString(R.string.widget_wave_hint), 13.sp, WidgetColors.textSoft, lines = 2)
            if (withPages) {
                Spacer(GlanceModifier.height(10.dp))
                Row {
                    PageChip(context.getString(R.string.widget_page_liked), WidgetActions.open(context, MainTab.LIBRARY.route))
                    Spacer(GlanceModifier.width(8.dp))
                    PageChip(context.getString(R.string.widget_page_radio), WidgetActions.open(context, MainTab.RADIO.route))
                    Spacer(GlanceModifier.width(8.dp))
                    PageChip(context.getString(R.string.widget_page_car), WidgetActions.open(context, MainTab.CAR.route))
                }
            }
        }
    }
}

@Composable
private fun PageChip(label: String, action: androidx.glance.action.Action) {
    Box(GlanceModifier.height(44.dp).cornerRadius(22.dp).background(WidgetColors.chip).padding(horizontal = 14.dp).clickable(action), contentAlignment = Alignment.Center) {
        WidgetText(label, 13.sp, bold = true)
    }
}

/**
 * «Обложка» 2×2: the cover over all of it, the title and the artist over a shade at its foot, back, play, forward.
 * The cover opens the player. With nothing playing: «Моя волна» to start.
 */
class CoverWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(170.dp, 170.dp)))

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
    val small = LocalSize.current.width < 150.dp
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(28.dp).background(WidgetColors.ground)) {
        if (!now.hasTrack) {
            Column(GlanceModifier.fillMaxSize().clickable(WidgetActions.startWave(context)), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                WidgetRound(R.drawable.ic_widget_play, context.getString(R.string.widget_wave_start), WidgetActions.startWave(context), if (small) 64.dp else 88.dp, if (small) 28.dp else 36.dp, WidgetColors.knob, WidgetColors.onKnob)
                Spacer(GlanceModifier.height(10.dp))
                WidgetText(context.getString(R.string.widget_wave_title), 15.sp, bold = true)
            }
            return@Box
        }
        if (now.cover != null) {
            Image(ImageProvider(now.cover), contentDescription = now.title, modifier = GlanceModifier.fillMaxSize().clickable(WidgetActions.openPlayer(context)), contentScale = ContentScale.Crop)
        }
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_scrim)).padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 10.dp)) {
                if (!small) {
                    WidgetText(now.title.orEmpty(), 15.sp, bold = true)
                    WidgetText(now.artist.orEmpty(), 12.sp, WidgetColors.textSoft)
                    Spacer(GlanceModifier.height(8.dp))
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetRound(R.drawable.ic_widget_prev, context.getString(R.string.cd_predyduschiy), WidgetActions.previous(context), 44.dp, 20.dp, WidgetColors.shade)
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetRound(
                        if (now.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        context.getString(R.string.cd_vosproizvedenie_ili_pauza), WidgetActions.playPause(context), 52.dp, 24.dp, WidgetColors.knob, WidgetColors.onKnob
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetRound(R.drawable.ic_widget_next, context.getString(R.string.cd_sleduyuschiy), WidgetActions.next(context), 44.dp, 20.dp, WidgetColors.shade)
                }
            }
        }
    }
}

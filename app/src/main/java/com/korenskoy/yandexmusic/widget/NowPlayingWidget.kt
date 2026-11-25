package com.korenskoy.yandexmusic.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.OptIn
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
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.korenskoy.yandexmusic.R
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt

/**
 * «Строка» 4×1: responsive to exact launcher bounds across phones, head units, and tall tablet cells.
 * Play is an ordinary media button for [MediaButtonReceiver], so it resumes even after process death.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { LineWidget(rememberWidgetNow(context)) }
    }
}

@Composable
fun LineWidget(now: WidgetNow) {
    val context = LocalContext.current
    val (w, h) = LocalSize.current.safeOrDefault(250f, 64f)
    val fs = context.resources.configuration.fontScale
    val spec = lineSpec(w, h, fs, now.hasTrack, now.canReact)
    val clickAction = if (now.hasTrack) WidgetActions.openPlayer(context) else WidgetActions.startWave(context)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(spec.cardH.dp)
                .widgetCardBackground(R.drawable.widget_bg_24, 24.dp)
                .padding(horizontal = spec.padH.dp, vertical = spec.padV.dp)
                .clickable(clickAction),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (!now.hasTrack && !spec.showText) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (!now.hasTrack) {
                LineEmptyContent(context, spec)
                return@Row
            }
            if (spec.showCover) {
                LineCover(now, spec.cover)
                Spacer(GlanceModifier.width(spec.coverGap.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetText(now.title.orEmpty(), 15.sp, bold = true)
                if (spec.showArtist) {
                    WidgetText(now.artist.orEmpty(), 12.sp, WidgetColors.textSoft)
                }
                if (spec.showProgress && now.progress != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    WidgetProgressBar(now.progress, 3.dp)
                }
            }
            Spacer(GlanceModifier.width(6.dp))
            LineButtons(context, now, spec)
        }
    }
}

@Composable
private fun RowScope.LineEmptyContent(context: Context, spec: LineSpec) {
    if (spec.showText) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(context.getString(R.string.widget_wave_title), 15.sp, bold = true)
            if (spec.showArtist) {
                WidgetText(context.getString(R.string.widget_wave_hint), 12.sp, WidgetColors.textSoft)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
    }
    val iconDp = (spec.knob * 0.46f).roundToInt().dp
    WidgetRound(
        icon = R.drawable.ic_widget_play,
        description = context.getString(R.string.widget_wave_start),
        action = WidgetActions.startWave(context),
        size = spec.knob.dp,
        iconSize = iconDp,
        fill = WidgetFill.KNOB,
        tint = WidgetColors.onKnob
    )
}

@Composable
private fun LineCover(now: WidgetNow, coverDp: Float) {
    val bmp = rememberWidgetBitmap(now.cover, coverDp, coverDp, 14f, cropToAspect = true)
    val base = GlanceModifier.size(coverDp.dp).background(ImageProvider(R.drawable.widget_cover_chip_14))
    val boxMod = if (Build.VERSION.SDK_INT >= 31) base.cornerRadius(14.dp) else base
    Box(modifier = boxMod, contentAlignment = Alignment.Center) {
        if (bmp != null) {
            val imgBase = GlanceModifier.size(coverDp.dp)
            val imgMod = if (Build.VERSION.SDK_INT >= 31) imgBase.cornerRadius(14.dp) else imgBase
            val scale = if (Build.VERSION.SDK_INT >= 31) ContentScale.Crop else ContentScale.FillBounds
            Image(ImageProvider(bmp), contentDescription = now.title, modifier = imgMod, contentScale = scale)
        } else {
            val noteDp = (coverDp * 0.46f).roundToInt().coerceAtLeast(18).dp
            Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(noteDp))
        }
    }
}

@Composable
private fun LineButtons(context: Context, now: WidgetNow, spec: LineSpec) {
    val chipIcon = (spec.chip * 0.50f).roundToInt().dp
    val knobIcon = (spec.knob * 0.46f).roundToInt().dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spec.showHeart) {
            WidgetRound(
                icon = if (now.isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
                description = context.getString(R.string.cd_nravitsya),
                action = WidgetActions.like(context),
                size = spec.chip.dp,
                iconSize = chipIcon,
                fill = null,
                tint = if (now.isLiked) WidgetColors.love else Color.White
            )
            Spacer(GlanceModifier.width(spec.gap.dp))
        }
        if (spec.showPrev) {
            WidgetRound(
                icon = R.drawable.ic_widget_prev,
                description = context.getString(R.string.cd_predyduschiy),
                action = WidgetActions.previous(context),
                size = spec.chip.dp,
                iconSize = chipIcon,
                fill = WidgetFill.CHIP
            )
            Spacer(GlanceModifier.width(spec.gap.dp))
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
        if (spec.showNext) {
            Spacer(GlanceModifier.width(spec.gap.dp))
            WidgetRound(
                icon = R.drawable.ic_widget_next,
                description = context.getString(R.string.cd_sleduyuschiy),
                action = WidgetActions.next(context),
                size = spec.chip.dp,
                iconSize = chipIcon,
                fill = WidgetFill.CHIP
            )
        }
    }
}

// MediaButtonReceiver is marked unstable in media3; it is the documented receiver for media buttons
@OptIn(markerClass = [UnstableApi::class])
internal fun mediaButtonIntent(context: Context, keyCode: Int): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON)
        .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

internal fun skipIntent(context: Context, next: Boolean): Intent =
    Intent(context, WidgetSkipReceiver::class.java)
        .setAction(if (next) WidgetSkipReceiver.ACTION_NEXT else WidgetSkipReceiver.ACTION_PREVIOUS)

/** "Next" / "previous" from the widget: skips within the current session (nothing to skip without one). */
class WidgetSkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, WidgetDependencies::class.java)
            .playbackController()
        if (controller.currentTrack.value == null) return
        when (intent.action) {
            ACTION_NEXT -> controller.playNext()
            ACTION_PREVIOUS -> controller.playPrev()
        }
    }

    companion object {
        const val ACTION_NEXT = "YandexMusicLite.widget.NEXT"
        const val ACTION_PREVIOUS = "YandexMusicLite.widget.PREVIOUS"
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

package com.korenskoy.yandexmusic.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
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

/**
 * «Строка» 4×1: the cover, the track over its progress, back, play, forward and — from five cells — the heart.
 * Play is an ordinary media button for [MediaButtonReceiver] (like a Bluetooth headset), so it works after the app's
 * process was closed: the player service starts and resumes the saved session. Its name stays as it was: the widgets
 * people have placed keep working after the update.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 64.dp), DpSize(320.dp, 64.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { LineWidget(rememberWidgetNow(context)) }
    }
}

@Composable
fun LineWidget(now: WidgetNow) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= 300.dp
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(WidgetColors.ground)
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .clickable(if (now.hasTrack) WidgetActions.openPlayer(context) else WidgetActions.startWave(context)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(GlanceModifier.size(56.dp).cornerRadius(14.dp).background(WidgetColors.chip), contentAlignment = Alignment.Center) {
            if (now.cover != null) {
                Image(ImageProvider(now.cover), contentDescription = null, modifier = GlanceModifier.size(56.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
            } else {
                Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(26.dp))
            }
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(now.title ?: context.getString(R.string.widget_wave_title), 15.sp, bold = true)
            WidgetText(now.artist ?: context.getString(R.string.widget_wave_hint), 12.sp, WidgetColors.textSoft)
            now.progress?.let {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(progress = it, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = WidgetColors.text, backgroundColor = WidgetColors.track)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        if (!now.hasTrack) {
            WidgetRound(R.drawable.ic_widget_play, context.getString(R.string.widget_wave_start), WidgetActions.startWave(context), 52.dp, 24.dp, WidgetColors.knob, WidgetColors.onKnob)
            return@Row
        }
        if (wide && now.canReact) {
            WidgetRound(
                if (now.isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
                context.getString(R.string.cd_nravitsya), WidgetActions.like(context), 44.dp, 22.dp, null,
                if (now.isLiked) WidgetColors.love else androidx.compose.ui.graphics.Color.White
            )
        }
        WidgetRound(R.drawable.ic_widget_prev, context.getString(R.string.cd_predyduschiy), WidgetActions.previous(context), 44.dp, 22.dp, WidgetColors.chip)
        Spacer(GlanceModifier.width(4.dp))
        WidgetRound(
            if (now.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            context.getString(R.string.cd_vosproizvedenie_ili_pauza), WidgetActions.playPause(context), 52.dp, 24.dp, WidgetColors.knob, WidgetColors.onKnob
        )
        Spacer(GlanceModifier.width(4.dp))
        WidgetRound(R.drawable.ic_widget_next, context.getString(R.string.cd_sleduyuschiy), WidgetActions.next(context), 44.dp, 22.dp, WidgetColors.chip)
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

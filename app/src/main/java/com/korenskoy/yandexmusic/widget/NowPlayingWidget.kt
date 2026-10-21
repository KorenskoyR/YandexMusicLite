package com.korenskoy.yandexmusic.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaButtonReceiver
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.utils.toCoverUrl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Home screen widget with the current track. Play/pause is an ordinary media button event for
 * [MediaButtonReceiver] — like a Bluetooth headset — so it works even after the app process was closed:
 * the player service starts and resumes the saved session. media3 lets only "play" keys through there,
 * so skipping goes to [WidgetSkipReceiver].
 */
class NowPlayingWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun playbackController(): PlaybackController
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).playbackController()
        provideContent {
            val track by controller.currentTrack.collectAsState()
            val isPlaying by controller.isPlaying.collectAsState()
            var cover by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(track?.idString) { cover = track?.let { loadCover(context, it) } }
            NowPlayingContent(track?.title, track?.artists?.joinToString { it.name.orEmpty() }, isPlaying, cover)
        }
    }

    private suspend fun loadCover(context: Context, track: TrackDetailsDto): Bitmap? {
        val uri = track.coverUri ?: return null
        val data = if (uri.startsWith("android.resource") || uri.startsWith("http")) uri else uri.toCoverUrl("200x200")
        val request = ImageRequest.Builder(context).data(data).size(200).allowHardware(false).build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}

/** The widget's look; separate from the data so it can be tested with any state. */
@Composable
fun NowPlayingContent(title: String?, artist: String?, isPlaying: Boolean, cover: Bitmap?) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(0xFF1E1E26)))
            .padding(8.dp)
            .clickable(actionStartActivity(openPlayerIntent(context))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(48.dp).cornerRadius(10.dp).background(ColorProvider(Color(0xFF3A2A4A))),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(ImageProvider(cover), contentDescription = null, modifier = GlanceModifier.size(48.dp).cornerRadius(10.dp))
            } else {
                Image(ImageProvider(R.drawable.ic_notification), contentDescription = null, modifier = GlanceModifier.size(24.dp))
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title ?: context.getString(R.string.widget_nothing_playing),
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = artist ?: context.getString(R.string.widget_tap_to_open),
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.65f)), fontSize = 12.sp)
            )
        }
        ControlButton(R.drawable.ic_widget_prev, context.getString(R.string.cd_predyduschiy), skipIntent(context, next = false))
        ControlButton(
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            context.getString(R.string.cd_vosproizvedenie_ili_pauza),
            mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
        ControlButton(R.drawable.ic_widget_next, context.getString(R.string.cd_sleduyuschiy), skipIntent(context, next = true))
    }
}

@Composable
private fun ControlButton(icon: Int, description: String, intent: Intent) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .clickable(actionSendBroadcast(intent))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(26.dp))
    }
}

// MediaButtonReceiver is marked unstable in media3; it is the documented receiver for media buttons
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun mediaButtonIntent(context: Context, keyCode: Int): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON)
        .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

internal fun skipIntent(context: Context, next: Boolean): Intent =
    Intent(context, WidgetSkipReceiver::class.java)
        .setAction(if (next) WidgetSkipReceiver.ACTION_NEXT else WidgetSkipReceiver.ACTION_PREVIOUS)

private fun openPlayerIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)

/** "Next" / "previous" from the widget: skips within the current session (nothing to skip without one). */
class WidgetSkipReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, NowPlayingWidget.Dependencies::class.java)
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

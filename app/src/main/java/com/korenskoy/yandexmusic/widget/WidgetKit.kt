package com.korenskoy.yandexmusic.widget

import android.content.BroadcastReceiver
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import androidx.core.graphics.scale
import coil3.toBitmap
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.TrackReactions
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.toCoverUrl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What every widget shows: the track, its cover and a soft glow made of it, the state of play. */
data class WidgetNow(
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val isLiked: Boolean,
    val isRadio: Boolean,
    val canReact: Boolean,
    /** 0..1; null for a station or nothing. */
    val progress: Float?,
    val positionMs: Long,
    val durationMs: Long,
    /** «МОЯ ВОЛНА», «РАДИО»; null for the rest. */
    val source: String?,
    val cover: Bitmap?,
    val glow: Bitmap?
) {
    val hasTrack: Boolean get() = title != null
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun playbackController(): PlaybackController
    fun reactions(): TrackReactions
}

/** The widgets' clock: moved every half-minute while music plays (see [NowPlayingWidgetUpdater]), never faster. */
object WidgetClock {
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()
    fun advance() {
        _tick.value = _tick.value + 1
    }
}

/** The state of play for a widget, followed as it changes; the position read only at each tick of [WidgetClock]. */
@Composable
internal fun rememberWidgetNow(context: Context): WidgetNow {
    val deps = remember { EntryPointAccessors.fromApplication(context.applicationContext, WidgetDependencies::class.java) }
    val controller = deps.playbackController()
    val reactions = deps.reactions()
    val track by controller.currentTrack.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val source by controller.currentSource.collectAsState()
    val liked by reactions.likedTrackIds.collectAsState()
    val stations by reactions.favoriteStationIds.collectAsState()
    val tick by WidgetClock.tick.collectAsState()
    var art by remember { mutableStateOf<Pair<Bitmap?, Bitmap?>>(null to null) }
    LaunchedEffect(track?.idString) { art = track?.let { loadArt(context, it) } ?: (null to null) }
    val shown = track
    // Read at a tick, a change of track or of play: the widget is not redrawn every second
    val position = remember(tick, shown?.idString, isPlaying) { controller.progress.value }
    val duration = shown?.durationMs ?: 0L
    return WidgetNow(
        title = shown?.title,
        artist = shown?.let { if (it.isRadio) context.getString(R.string.mini_player_live) else it.artists.joinToString { a -> a.name.orEmpty() } },
        isPlaying = isPlaying,
        isLiked = shown != null && (if (shown.isRadio) shown.idString.removePrefix("radio_") in stations else shown.idString in liked),
        isRadio = shown?.isRadio == true,
        canReact = shown != null && !shown.isLocal,
        progress = if (shown == null || shown.isRadio || duration <= 0) null else (position.toFloat() / duration).coerceIn(0f, 1f),
        positionMs = position,
        durationMs = duration,
        source = when {
            shown == null -> null
            shown.isRadio -> context.getString(R.string.widget_source_radio)
            source == PlaylistSource.VIBE -> context.getString(R.string.widget_source_wave)
            else -> null
        },
        cover = art.first,
        glow = art.second
    )
}

/**
 * The cover, and a glow made of it: the cover brought down to a few pixels and drawn large, which smooths it into
 * its own colours (a widget cannot blur).
 */
private suspend fun loadArt(context: Context, track: TrackDetailsDto): Pair<Bitmap?, Bitmap?> {
    val uri = track.coverUri ?: return null to null
    val data = if (uri.startsWith("android.resource") || uri.startsWith("http")) uri else uri.toCoverUrl(CoverSize.Small)
    val request = ImageRequest.Builder(context).data(data).size(COVER_PX).allowHardware(false).build()
    val cover = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null to null
    val glow = runCatching {
        val tiny = cover.scale(GLOW_TINY, GLOW_TINY)
        tiny.scale(GLOW_PX, GLOW_PX)
    }.getOrNull()
    return cover to glow
}

private const val COVER_PX = 360
private const val GLOW_TINY = 6
private const val GLOW_PX = 96

/** The widgets' colours: the app's dark ground, its white and its heart. */
internal object WidgetColors {
    val ground = ColorProvider(Color(0xFF141821))
    /** Over the cover's glow: the app's ground, so a white cover glows softly instead of turning the widget grey. */
    val shade = ColorProvider(Color(0xD9141821))
    val text = ColorProvider(Color.White)
    val textSoft = ColorProvider(Color.White.copy(alpha = 0.72f))
    val caps = ColorProvider(Color.White.copy(alpha = 0.66f))
    val chip = ColorProvider(Color.White.copy(alpha = 0.12f))
    val knob = ColorProvider(Color(0xFFF2F4F7))
    val onKnob = Color(0xFF141821)
    val love = Color(0xFFFF5470)
    val track = ColorProvider(Color.White.copy(alpha = 0.22f))
}

/** A round button with an icon: [size] to touch, never under 44 dp. */
@Composable
internal fun WidgetRound(icon: Int, description: String, action: Action, size: Dp, iconSize: Dp, fill: ColorProvider?, tint: Color = Color.White) {
    val base = GlanceModifier.size(size).cornerRadius(size / 2).clickable(action).semantics { contentDescription = description }
    Box(modifier = if (fill != null) base.background(fill) else base, contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(iconSize), colorFilter = ColorFilter.tint(ColorProvider(tint)))
    }
}

@Composable
internal fun WidgetText(text: String, size: TextUnit, color: ColorProvider = WidgetColors.text, bold: Boolean = false, modifier: GlanceModifier = GlanceModifier, lines: Int = 1) {
    Text(text, modifier = modifier, maxLines = lines, style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
}

/** The actions every widget offers. */
internal object WidgetActions {
    fun openPlayer(context: Context): Action = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
    )

    /** "Моя волна" from the widget with nothing playing: the app opens and the wave starts. */
    fun startWave(context: Context): Action = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_START_WAVE, true)
    )

    fun open(context: Context, tab: String): Action = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
    )

    fun playPause(context: Context): Action = actionSendBroadcast(mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    fun next(context: Context): Action = actionSendBroadcast(skipIntent(context, next = true))
    fun previous(context: Context): Action = actionSendBroadcast(skipIntent(context, next = false))
    fun like(context: Context): Action = actionSendBroadcast(Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_LIKE))
    fun dislike(context: Context): Action = actionSendBroadcast(Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_DISLIKE))
}

/** Like and dislike from a widget: the same as anywhere in the app (see [TrackReactions]). */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, WidgetDependencies::class.java)
        val track = deps.playbackController().currentTrack.value ?: return
        when (intent.action) {
            ACTION_LIKE -> deps.reactions().toggleLike(track)
            ACTION_DISLIKE -> deps.reactions().dislike(track)
        }
    }

    companion object {
        const val ACTION_LIKE = "YandexMusicLite.widget.LIKE"
        const val ACTION_DISLIKE = "YandexMusicLite.widget.DISLIKE"
    }
}

/** "1:22" of a position. Pure. */
internal fun widgetClock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

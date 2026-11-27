package com.korenskoy.yandexmusic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
import coil3.toBitmap
import com.korenskoy.yandexmusic.MainActivity
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.TrackReactions
import com.korenskoy.yandexmusic.domain.model.AppThemeMode
import com.korenskoy.yandexmusic.domain.model.ThemePolicy
import com.korenskoy.yandexmusic.domain.model.ThemeScheduleType
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import com.korenskoy.yandexmusic.utils.CoverSize
import com.korenskoy.yandexmusic.utils.toCoverUrl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlin.math.roundToInt

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
    val glow: Bitmap?,
    val isLightTheme: Boolean = false
) {
    val hasTrack: Boolean get() = title != null
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun playbackController(): PlaybackController
    fun reactions(): TrackReactions
    fun settingsRepository(): SettingsRepository
}

/** The widgets' clock: moved every half-minute while music plays (see [NowPlayingWidgetUpdater]), never faster. */
object WidgetClock {
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()
    fun advance() {
        _tick.value = _tick.value + 1
    }
}

/** Keeps the latest decoded cover & glow in memory so new Glance sessions avoid flashing blank boxes. */
internal object WidgetArtCache {
    @Volatile
    private var entry: Pair<String, Pair<Bitmap?, Bitmap?>>? = null

    fun get(trackId: String): Pair<Bitmap?, Bitmap?>? =
        entry?.takeIf { it.first == trackId }?.second

    fun put(trackId: String, art: Pair<Bitmap?, Bitmap?>) {
        entry = trackId to art
    }
}

/** The state of play for a widget, followed as it changes; the position read only at each tick of [WidgetClock]. */
@Composable
internal fun rememberWidgetNow(context: Context): WidgetNow {
    val deps = remember { EntryPointAccessors.fromApplication(context.applicationContext, WidgetDependencies::class.java) }
    val controller = deps.playbackController()
    val reactions = deps.reactions()
    val settings = deps.settingsRepository()
    val track by controller.currentTrack.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val source by controller.currentSource.collectAsState()
    val liked by reactions.likedTrackIds.collectAsState()
    val stations by reactions.favoriteStationIds.collectAsState()
    val themeMode by settings.themeMode.collectAsState(initial = AppThemeMode.DARK)
    val scheduleType by settings.themeScheduleType.collectAsState(initial = ThemeScheduleType.SUNSET_TO_SUNRISE)
    val lightMinutes by settings.themeLightMinutes.collectAsState(initial = ThemePolicy.DEFAULT_LIGHT_MINUTES)
    val darkMinutes by settings.themeDarkMinutes.collectAsState(initial = ThemePolicy.DEFAULT_DARK_MINUTES)
    val tick by WidgetClock.tick.collectAsState()
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val nowMinutes = remember(tick) {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    val isLightTheme = ThemePolicy.isLightTheme(
        mode = themeMode,
        scheduleType = scheduleType,
        lightMinutes = lightMinutes,
        darkMinutes = darkMinutes,
        nowMinutes = nowMinutes,
        systemDark = systemDark
    )
    val trackId = track?.idString
    val cached = remember(trackId) { trackId?.let { WidgetArtCache.get(it) } ?: (null to null) }
    var art by remember(trackId) { mutableStateOf(cached) }
    LaunchedEffect(trackId) {
        val current = track
        if (current == null || trackId == null) {
            art = null to null
        } else {
            val hit = WidgetArtCache.get(trackId)
            if (hit != null && hit.first != null) {
                art = hit
            } else {
                val loaded = loadArt(context, current)
                if (loaded.first != null) WidgetArtCache.put(trackId, loaded)
                art = loaded
            }
        }
    }
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
        glow = art.second,
        isLightTheme = isLightTheme
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

/** Oval shape drawables so buttons stay round on Android 7.1–11 (API < 31) where cornerRadius is ignored. */
internal enum class WidgetFill(val drawableRes: Int, val lightDrawableRes: Int) {
    CHIP(R.drawable.widget_round_chip, R.drawable.widget_round_chip_light),
    KNOB(R.drawable.widget_round_knob, R.drawable.widget_round_knob_light),
    SHADE(R.drawable.widget_round_shade, R.drawable.widget_round_shade);

    fun forTheme(isLight: Boolean): Int = if (isLight) lightDrawableRes else drawableRes
}

/** Light & dark widget palette tokens paired with API 25–37 compatible XML shape drawables. */
internal class WidgetTheme private constructor(
    val isLight: Boolean,
    val text: ColorProvider,
    val textSoft: ColorProvider,
    val caps: ColorProvider,
    val iconTint: Color,
    val onKnob: Color,
    val love: Color,
    val bg24: Int,
    val bg28: Int,
    val shade28: Int,
    val coverChip14: Int,
    val coverChip18: Int,
    val pillChip: Int
) {
    companion object {
        val Dark = WidgetTheme(
            isLight = false,
            text = ColorProvider(Color.White),
            textSoft = ColorProvider(Color.White.copy(alpha = 0.72f)),
            caps = ColorProvider(Color.White.copy(alpha = 0.66f)),
            iconTint = Color.White,
            onKnob = Color(0xFF141821),
            love = Color(0xFFFF5470),
            bg24 = R.drawable.widget_bg_24,
            bg28 = R.drawable.widget_bg_28,
            shade28 = R.drawable.widget_shade_28,
            coverChip14 = R.drawable.widget_cover_chip_14,
            coverChip18 = R.drawable.widget_cover_chip_18,
            pillChip = R.drawable.widget_pill_chip
        )

        val Light = WidgetTheme(
            isLight = true,
            text = ColorProvider(Color(0xFF11141C)),
            textSoft = ColorProvider(Color(0xB311141C)),
            caps = ColorProvider(Color(0x9E11141C)),
            iconTint = Color(0xFF11141C),
            onKnob = Color(0xFFF4F6FA),
            love = Color(0xFFE0244D),
            bg24 = R.drawable.widget_bg_24_light,
            bg28 = R.drawable.widget_bg_28_light,
            shade28 = R.drawable.widget_shade_28_light,
            coverChip14 = R.drawable.widget_cover_chip_14_light,
            coverChip18 = R.drawable.widget_cover_chip_18_light,
            pillChip = R.drawable.widget_pill_chip_light
        )

        fun of(isLight: Boolean): WidgetTheme = if (isLight) Light else Dark
    }
}

/** Pre-rounds (and optionally center-crops) a bitmap for API < 31 where Glance cornerRadius is ignored. */
internal fun roundedBitmap(
    src: Bitmap,
    wDp: Float,
    hDp: Float,
    radiusDp: Float,
    cropToAspect: Boolean,
    maxSidePx: Int = 360
): Bitmap {
    val safeW = wDp.coerceAtLeast(16f)
    val safeH = hDp.coerceAtLeast(16f)
    val aspect = (safeW / safeH).coerceIn(0.2f, 5f)
    val outW: Int
    val outH: Int
    if (safeW >= safeH) {
        outW = (safeW * 2f).roundToInt().coerceIn(24, maxSidePx)
        outH = (outW / aspect).roundToInt().coerceIn(16, maxSidePx)
    } else {
        outH = (safeH * 2f).roundToInt().coerceIn(24, maxSidePx)
        outW = (outH * aspect).roundToInt().coerceIn(16, maxSidePx)
    }
    val out = createBitmap(outW, outH)
    val canvas = Canvas(out)
    val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val matrix = Matrix()
    if (cropToAspect) {
        val scale = maxOf(outW.toFloat() / src.width.coerceAtLeast(1), outH.toFloat() / src.height.coerceAtLeast(1))
        val dx = (outW - src.width * scale) * 0.5f
        val dy = (outH - src.height * scale) * 0.5f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
    } else {
        matrix.setScale(outW.toFloat() / src.width.coerceAtLeast(1), outH.toFloat() / src.height.coerceAtLeast(1))
    }
    shader.setLocalMatrix(matrix)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = shader
        isFilterBitmap = true
    }
    val rx = radiusDp * (outW / safeW)
    val ry = radiusDp * (outH / safeH)
    canvas.drawRoundRect(RectF(0f, 0f, outW.toFloat(), outH.toFloat()), rx, ry, paint)
    return out
}

@Composable
internal fun rememberWidgetBitmap(
    src: Bitmap?,
    wDp: Float,
    hDp: Float,
    radiusDp: Float,
    cropToAspect: Boolean,
    maxSidePx: Int = 360
): Bitmap? {
    if (src == null) return null
    if (Build.VERSION.SDK_INT >= 31) return src
    return remember(src, wDp.roundToInt(), hDp.roundToInt(), radiusDp.roundToInt(), cropToAspect, maxSidePx) {
        roundedBitmap(src, wDp, hDp, radiusDp, cropToAspect, maxSidePx)
    }
}

/** Progress strip cached by percentage so progress colours work identically on API 25–37 in dark and light themes. */
internal object WidgetProgressStrips {
    private const val WIDTH_PX = 240
    private const val HEIGHT_PX = 6
    private const val TRACK_COLOR = 0x38FFFFFF
    private const val FILL_COLOR = 0xFFFFFFFF.toInt()
    private const val TRACK_COLOR_LIGHT = 0x2611141C
    private const val FILL_COLOR_LIGHT = 0xFF141821.toInt()
    private val cacheDark = arrayOfNulls<Bitmap>(101)
    private val cacheLight = arrayOfNulls<Bitmap>(101)
    private val lock = Any()

    fun forProgress(progress: Float, isLight: Boolean = false): Bitmap {
        val pct = (progress.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
        val cache = if (isLight) cacheLight else cacheDark
        val trackColor = if (isLight) TRACK_COLOR_LIGHT else TRACK_COLOR
        val fillColor = if (isLight) FILL_COLOR_LIGHT else FILL_COLOR
        synchronized(lock) {
            cache[pct]?.let { return it }
            val bmp = createBitmap(WIDTH_PX, HEIGHT_PX)
            val canvas = Canvas(bmp)
            val r = HEIGHT_PX / 2f
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
            canvas.drawRoundRect(RectF(0f, 0f, WIDTH_PX.toFloat(), HEIGHT_PX.toFloat()), r, r, trackPaint)
            if (pct > 0) {
                val fillW = (WIDTH_PX * pct / 100f).coerceAtLeast(HEIGHT_PX.toFloat())
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
                canvas.drawRoundRect(RectF(0f, 0f, fillW, HEIGHT_PX.toFloat()), r, r, fillPaint)
            }
            cache[pct] = bmp
            return bmp
        }
    }
}

@Composable
internal fun WidgetProgressBar(progress: Float, height: Dp, isLight: Boolean = false) {
    Image(
        provider = ImageProvider(WidgetProgressStrips.forProgress(progress, isLight)),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(height),
        contentScale = ContentScale.FillBounds
    )
}

/** Applies a rounded XML background (API < 31 safe) and native cornerRadius on Android 12+. */
internal fun GlanceModifier.widgetCardBackground(drawableRes: Int, radius: Dp): GlanceModifier {
    val base = this.appWidgetBackground().background(ImageProvider(drawableRes))
    return if (Build.VERSION.SDK_INT >= 31) base.cornerRadius(radius) else base
}

/** Guards against launchers passing zero/tiny LocalSize on pre-S devices during initial layout. */
internal fun DpSize.safeOrDefault(fallbackW: Float, fallbackH: Float): Pair<Float, Float> {
    val w = width.value
    val h = height.value
    val invalid = w.isNaN() || h.isNaN() || w < 80f || h < 32f
    return if (invalid) fallbackW to fallbackH else w to h
}

/** The widgets' colours: kept for dark compatibility, prefer [WidgetTheme]. */
internal object WidgetColors {
    val text = WidgetTheme.Dark.text
    val textSoft = WidgetTheme.Dark.textSoft
    val caps = WidgetTheme.Dark.caps
    val onKnob = WidgetTheme.Dark.onKnob
    val love = WidgetTheme.Dark.love
}

/** A round button with an icon, staying circular across Android 7.1–17. */
@Composable
internal fun WidgetRound(
    icon: Int,
    description: String,
    action: Action,
    size: Dp,
    iconSize: Dp,
    fill: WidgetFill?,
    tint: Color = Color.White,
    isLight: Boolean = false
) {
    val sized = GlanceModifier.size(size)
    val shaped = if (fill != null) sized.background(ImageProvider(fill.forTheme(isLight))) else sized
    val rounded = if (Build.VERSION.SDK_INT >= 31) shaped.cornerRadius(size / 2) else shaped
    val base = rounded.clickable(action).semantics { contentDescription = description }
    Box(modifier = base, contentAlignment = Alignment.Center) {
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

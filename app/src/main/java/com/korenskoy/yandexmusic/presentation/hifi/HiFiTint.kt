package com.korenskoy.yandexmusic.presentation.hifi

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Color math for the app tint. Pure Kotlin (no Android classes) so it is unit-tested. */
object TintMath {
    private const val MIN_SATURATION = 0.35f
    private const val MIN_VALUE = 0.42f
    private const val MAX_VALUE = 0.80f

    /**
     * Turns a palette swatch into a glow color: never muddy-dark, never washed-out white, and with
     * enough color to be felt. Near-grey covers stay near-grey (a hint of blue keeps them alive).
     */
    fun normalize(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hsv = rgbToHsv(r, g, b)
        val grey = hsv[1] < 0.08f
        val h = if (grey) 220f else hsv[0]
        val s = if (grey) 0.14f else max(hsv[1], MIN_SATURATION)
        val v = hsv[2].coerceIn(MIN_VALUE, MAX_VALUE)
        return hsvToArgb(h, s, v)
    }

    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (mx == 0f) 0f else d / mx
        return floatArrayOf(h, s, mx)
    }

    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

/** Covers are re-shown constantly (mini-player, wave, car): their tint is computed once. */
private object TintCache {
    private val cache = LruCache<String, ArtworkTint>(64)
    @Synchronized fun get(key: String): ArtworkTint? = cache.get(key)
    @Synchronized fun put(key: String, tint: ArtworkTint) { cache.put(key, tint) }
}

private suspend fun extractTint(context: Context, coverUrl: String): ArtworkTint? = withContext(Dispatchers.IO) {
    runCatching {
        // A 64px copy is plenty for a palette and costs almost nothing to decode
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(64)
            .allowHardware(false)
            .build()
        val bitmap = SingletonImageLoader.get(context).execute(request).image?.toBitmap() ?: return@runCatching null
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val fallback = palette.getDominantColor(0xFF3A4152.toInt())
        val primary = palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb ?: fallback
        val secondary = palette.darkVibrantSwatch?.rgb ?: palette.darkMutedSwatch?.rgb ?: fallback
        ArtworkTint(Color(TintMath.normalize(primary)), Color(TintMath.normalize(secondary)).copy(alpha = 1f))
    }.getOrNull()
}

/**
 * The tint of [coverUrl] (or [override], e.g. a radio station's brand colors), animated slowly so the
 * whole app changes color together when the track changes.
 */
@Composable
fun rememberArtworkTint(coverUrl: String?, override: ArtworkTint? = null): ArtworkTint {
    val context = LocalContext.current
    var target by remember {
        mutableStateOf(override ?: coverUrl?.let { TintCache.get(it) } ?: ArtworkTint.Neutral)
    }
    LaunchedEffect(coverUrl, override) {
        target = when {
            override != null -> override
            coverUrl.isNullOrEmpty() -> ArtworkTint.Neutral
            else -> TintCache.get(coverUrl) ?: extractTint(context, coverUrl)?.also { TintCache.put(coverUrl, it) } ?: target
        }
    }
    val primary by animateColorAsState(target.primary, HiFiMotion.tint(), label = "tint_primary")
    val secondary by animateColorAsState(target.secondary, HiFiMotion.tint(), label = "tint_secondary")
    return remember(primary, secondary) { ArtworkTint(primary, secondary) }
}

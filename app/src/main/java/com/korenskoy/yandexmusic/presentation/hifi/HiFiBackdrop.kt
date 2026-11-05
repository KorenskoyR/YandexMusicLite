package com.korenskoy.yandexmusic.presentation.hifi

import com.korenskoy.yandexmusic.utils.CoverSize
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.max
import kotlin.random.Random

/** Film grain: one small noise tile made once per process and repeated, never per frame. */
private object GrainTexture {
    private const val SIZE = 96
    val bitmap: ImageBitmap by lazy {
        val random = Random(7)
        // Light and dark specks in equal measure, so the grain adds texture without lifting the black
        val pixels = IntArray(SIZE * SIZE) {
            val alpha = random.nextInt(0x38)
            val v = if (random.nextBoolean()) 0xFF else 0x00
            (alpha shl 24) or (v shl 16) or (v shl 8) or v
        }
        android.graphics.Bitmap.createBitmap(pixels, SIZE, SIZE, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Grain on top of whatever was drawn before; skipped in light mode. */
fun Modifier.hifiGrain(enabled: Boolean, alpha: Float = 0.5f): Modifier = if (!enabled) this else drawWithCache {
    val brush = ShaderBrush(ImageShader(GrainTexture.bitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}

/**
 * The living background of every page: the app tint as a soft glow (or, on Android 12+ outside light
 * mode, the real cover blurred), a vignette and film grain. Static: drawn once, then only when the
 * tint changes, so it costs nothing while scrolling.
 *
 * [glowCenter] is a fraction of the size; [bottomFade] darkens the lower part for overlays.
 */
@Composable
fun HiFiBackdrop(
    modifier: Modifier = Modifier,
    tint: ArtworkTint = LocalArtworkTint.current,
    coverUrl: String? = null,
    glowCenter: Offset = Offset(0.5f, 0.24f),
    intensity: Float = 1f,
    bottomFade: Boolean = true
) {
    val lite = LocalHiFiLite.current
    val liveBlur = !lite && coverUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HiFi.Ground)
    ) {
        if (liveBlur) {
            val context = LocalContext.current
            AsyncImage(
                // The small cover, shared with the mini-player and the rows: it is blurred anyway
                model = ImageRequest.Builder(context).data(coverUrl).size(CoverSize.Small.px).memoryCacheKey(CoverSize.Small.memoryKey(coverUrl)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f * intensity }
                    .blur(90.dp, BlurredEdgeTreatment.Rectangle)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val maxDim = max(size.width, size.height)
                    val center = Offset(size.width * glowCenter.x, size.height * glowCenter.y)
                    val glowAlpha = if (liveBlur) 0.30f else 0.55f
                    val glow = Brush.radialGradient(
                        colors = listOf(
                            tint.primary.copy(alpha = glowAlpha * intensity),
                            tint.primary.copy(alpha = glowAlpha * 0.3f * intensity),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxDim * 0.72f
                    )
                    val second = Brush.radialGradient(
                        colors = listOf(tint.secondary.copy(alpha = 0.40f * intensity), Color.Transparent),
                        center = Offset(size.width * (1f - glowCenter.x * 0.3f), size.height * (glowCenter.y + 0.32f)),
                        radius = maxDim * 0.5f
                    )
                    val vignette = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Transparent, HiFi.Ground.copy(alpha = 0.82f)),
                        center = Offset(size.width * 0.5f, size.height * 0.36f),
                        radius = maxDim * 0.85f
                    )
                    val fade = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to HiFi.Ground.copy(alpha = 0.92f)
                    )
                    onDrawBehind {
                        drawRect(glow)
                        drawRect(second)
                        drawRect(vignette)
                        if (bottomFade) drawRect(fade)
                    }
                }
                .hifiGrain(enabled = !lite, alpha = 0.45f)
        )
    }
}

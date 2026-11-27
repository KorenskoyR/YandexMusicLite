package com.korenskoy.yandexmusic.presentation.player.components

import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.height
import coil3.request.crossfade
import com.korenskoy.yandexmusic.utils.browserUserAgent
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

@Composable
fun RadioCoverCard(
    coverUrl: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    showLiveBadge: Boolean = true,
    /** Shown as an initial when the station has no (verified) logo. */
    stationName: String = ""
) {
    val context = LocalContext.current
    val palette = HiFi.palette
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .hifiSoftShadow(
                shape,
                28.dp,
                color = if (palette.isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF11141C).copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(palette.placeholder)
            .border(
                BorderStroke(1.dp, if (palette.isDark) Color.White.copy(alpha = 0.10f) else palette.glassBorder),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .browserUserAgent()
                .build(),
            contentDescription = stringResource(R.string.cd_logotip_radiostantsii),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(palette.anodizedLight, palette.anodizedDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stationName.take(1).uppercase(),
                        color = palette.textPrimary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 64.sp
                    )
                }
            }
        )

        // Subtle studio glass sheen matching CarCover
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.14f)
                    )
                )
        )

        // "В ЭФИРЕ" in the top-left corner; the dot blinks only while effects are on
        if (showLiveBadge) {
            val lite = LocalHiFiLite.current
            val blink = if (!lite) {
                rememberInfiniteTransition(label = "live_pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                    label = "alpha_live"
                )
            } else null

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x99080A14))
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = blink?.value ?: 1f }
                        .clip(CircleShape)
                        .background(palette.live)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.radio_on_air),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em
                )
            }
        }
    }
}

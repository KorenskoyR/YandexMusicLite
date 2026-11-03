package com.korenskoy.yandexmusic.presentation.player.components

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

    Box(
        modifier = modifier
            .hifiSoftShadow(RoundedCornerShape(24.dp), 28.dp, Color.Black.copy(alpha = 0.55f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HiFi.AnodizedLight,
                        HiFi.AnodizedDark
                    ),
                    radius = 900f
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {

        // "В ЭФИРЕ" in the top-left corner; the dot blinks only while effects are on
        if (showLiveBadge) {
            val lite = com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite.current
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
                    .background(Color(0x73080A14))
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = blink?.value ?: 1f }
                        .clip(CircleShape)
                        .background(com.korenskoy.yandexmusic.presentation.hifi.HiFi.Live)
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

        // Centered Round Logo with subtle glowing aura
        Box(
            modifier = Modifier
                .fillMaxSize(0.58f)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .hifiSoftShadow(CircleShape, 16.dp, Color.Black.copy(alpha = 0.6f))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .browserUserAgent()
                    .build(),
                contentDescription = stringResource(R.string.cd_logotip_radiostantsii),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stationName.take(1).uppercase(),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        )
                    }
                }
            )
        }
    }
}

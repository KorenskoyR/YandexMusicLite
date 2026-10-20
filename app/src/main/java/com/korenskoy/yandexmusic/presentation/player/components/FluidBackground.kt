package com.korenskoy.yandexmusic.presentation.player.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.toBitmap
import androidx.palette.graphics.Palette
import coil3.request.allowHardware
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FluidBackground(
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(Color(0xFF121212)) }
    var vibrantColor by remember { mutableStateOf(Color(0xFF121212)) }

    val animatedDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1500, easing = FastOutSlowInEasing)
    )
    
    val animatedVibrant by animateColorAsState(
        targetValue = vibrantColor,
        animationSpec = tween(1500, easing = FastOutSlowInEasing)
    )

    LaunchedEffect(coverUrl) {
        if (coverUrl.isEmpty()) return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            // Shared loader: reuses the memory/disk cache of the cover already shown on screen
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false)
                .build()
            
            val result = loader.execute(request)
            val bitmap = result.image?.toBitmap()
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                dominantColor = palette.getDominantColor(0xFF121212.toInt()).let { Color(it) }
                vibrantColor = palette.getVibrantColor(0xFF121212.toInt()).let { Color(it) }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    animatedVibrant.copy(alpha = 0.3f),
                    animatedDominant.copy(alpha = 0.5f),
                    Color(0xFF121212)
                )
            ),
            size = size
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animatedVibrant.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(width * 0.2f, height * 0.1f),
                radius = width * 0.8f
            ),
            radius = width * 0.8f,
            center = Offset(width * 0.2f, height * 0.1f)
        )
    }
}

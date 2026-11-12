package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * A QR code drawn from its matrix on a white plate with a quiet zone: sharp at any size, made on the device
 * (no network). [modifier] goes before the plate, so a click there covers all of it.
 */
@Composable
fun HiFiQrCode(content: String, size: Dp, description: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF4F6FA).copy(alpha = if (dimmed) 0.35f else 1f))
            .padding(size * 0.08f)
            .semantics { contentDescription = description }
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = this.size.minDimension / matrix.width
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) {
                            // A hair wider than the cell, so no seams show between neighbours
                            drawRect(Color(0xFF0A0B0F), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
            }
        }
    }
}

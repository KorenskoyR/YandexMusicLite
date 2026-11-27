package com.korenskoy.yandexmusic.presentation.main.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.security.AppIntegrity
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiQrCode
import com.korenskoy.yandexmusic.presentation.hifi.hifiSoftShadow
import com.korenskoy.yandexmusic.utils.currentWindowSizeDp
import com.korenskoy.yandexmusic.utils.isLandscapeWindow

/**
 * Monochrome Hi-Fi anti-scam notice shown permanently when an unofficial repackaged build is detected.
 * Matches both Night and Day themes without unaligned accent colors, and offers one-tap browser open + QR.
 */
@Composable
fun TamperBannerOverlay(modifier: Modifier = Modifier) {
    BackHandler(enabled = true) {}
    val context = LocalContext.current
    val palette = HiFi.palette
    val officialUrl = remember { AppIntegrity.officialUrl() }
    val displayHost = remember(officialUrl) { officialUrl.removePrefix("https://") }
    val window = currentWindowSizeDp()
    val twoColumn = isLandscapeWindow() && window.widthDp >= 560
    val noBrowserText = stringResource(R.string.tamper_no_browser)
    val copiedText = stringResource(R.string.tamper_copied)

    val openGitHub: () -> Unit = {
        val intent = Intent(Intent.ACTION_VIEW, officialUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            Toast.makeText(context, noBrowserText, Toast.LENGTH_LONG).show()
        }
    }

    val copyUrl: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("GitHub", officialUrl))
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    val backdrop = Color.Black.copy(alpha = if (palette.isDark) 0.84f else 0.56f)
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdrop)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (twoColumn) 640.dp else 440.dp)
                .fillMaxWidth()
                .hifiSoftShadow(
                    cardShape,
                    28.dp,
                    color = if (palette.isDark) Color.Black.copy(alpha = 0.56f) else Color(0xFF11141C).copy(alpha = 0.20f)
                )
                .clip(cardShape)
                .background(palette.sheetFill)
                .border(1.dp, palette.glassBorder, cardShape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            if (twoColumn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TamperTextBlock(
                        displayHost = displayHost,
                        onCopy = copyUrl,
                        onOpenGitHub = openGitHub,
                        modifier = Modifier.weight(1f)
                    )
                    TamperQrBlock(
                        officialUrl = officialUrl,
                        onCopy = copyUrl,
                        sizeDp = if (window.heightDp < 440) 118 else 140
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TamperTextBlock(
                        displayHost = displayHost,
                        onCopy = copyUrl,
                        onOpenGitHub = openGitHub,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))
                    TamperQrBlock(
                        officialUrl = officialUrl,
                        onCopy = copyUrl,
                        sizeDp = if (window.heightDp < 600) 116 else 136
                    )
                }
            }
        }
    }
}

@Composable
private fun TamperTextBlock(
    displayHost: String,
    onCopy: () -> Unit,
    onOpenGitHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = HiFi.palette
    val ctaBrush = Brush.verticalGradient(palette.dialogButtonFill)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.pillFill)
                .border(1.dp, palette.glassBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.tamper_badge),
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.tamper_title),
            color = palette.textPrimary,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.tamper_body),
            color = palette.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.pillFill)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = displayHost,
                style = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.settings_copy_cd, displayHost),
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(ctaBrush)
                .border(1.dp, palette.glassBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenGitHub)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(R.string.tamper_cta),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun TamperQrBlock(
    officialUrl: String,
    onCopy: () -> Unit,
    sizeDp: Int
) {
    val palette = HiFi.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HiFiQrCode(
            content = officialUrl,
            size = sizeDp.dp,
            description = officialUrl,
            modifier = Modifier
                .border(1.dp, palette.glassBorder, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onCopy)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tamper_qr_hint),
            color = palette.textTertiary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width((sizeDp + 24).dp)
        )
    }
}

package com.korenskoy.yandexmusic.presentation.main.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiQrCode
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton
import com.korenskoy.yandexmusic.presentation.hifi.hifiAnodized
import com.korenskoy.yandexmusic.utils.Donations
import kotlinx.coroutines.delay

/** «Поддержать разработчика»: the anodized card at the top of «О приложении». */
@Composable
internal fun SupportEntryRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .hifiAnodized(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Plaque(40.dp) { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = HiFi.Love, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.support_title), color = Color.White, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.support_row_hint), color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
    }
}

/** The note under the support page and «О приложении»: unofficial, not affiliated, support is voluntary. */
@Composable
internal fun SupportDisclaimer(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.support_disclaimer),
        color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, lineHeight = 15.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

/**
 * «Поддержать разработчика»: ЮMoney and USDT (TRC20), each by a QR code made on the device. Side by side where
 * the page is wide (a head unit: the code is scanned with a phone), one under the other on a phone.
 */
@Composable
internal fun ColumnScope.SupportPage() {
    Text(
        stringResource(R.string.support_intro),
        color = HiFi.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 14.dp)
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= WIDE) {
            // Side by side, each a column: the code on top, big enough to scan from a phone
            // No intrinsic heights here: the address and the buttons measure their room themselves
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                YooMoneyCard(QR_WIDE, stacked = true, modifier = Modifier.weight(1f))
                UsdtCard(QR_WIDE, stacked = true, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                YooMoneyCard(QR_NARROW, stacked = false, modifier = Modifier.fillMaxWidth())
                UsdtCard(QR_NARROW, stacked = false, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    SupportDisclaimer(Modifier.padding(top = 16.dp))
}

@Composable
private fun YooMoneyCard(qr: Dp, stacked: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var noBrowser by remember { mutableStateOf(false) }
    val open = { noBrowser = !openPage(context, Donations.YOOMONEY_URL) }
    SupportCard("₽", stringResource(R.string.support_yoomoney), stringResource(R.string.support_yoomoney_sub), modifier) {
        CodeBeside(
            stacked,
            code = {
                HiFiQrCode(
                    Donations.YOOMONEY_URL, qr, stringResource(R.string.support_yoomoney_qr_cd),
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = open)
                )
            }
        ) {
            Text(
                stringResource(if (noBrowser) R.string.support_no_browser else R.string.support_yoomoney_hint),
                color = if (noBrowser) HiFi.Warning else HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            MetalPillButton(stringResource(R.string.support_open), onClick = open, icon = Icons.AutoMirrored.Rounded.OpenInNew, height = 46.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UsdtCard(qr: Dp, stacked: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var copies by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_MS)
        copied = false
    }
    SupportCard("₮", stringResource(R.string.support_usdt), stringResource(R.string.support_usdt_sub), modifier) {
        CodeBeside(stacked, code = { HiFiQrCode(Donations.USDT_TRC20, qr, stringResource(R.string.support_usdt_qr_cd)) }) {
            Text(stringResource(R.string.support_usdt_hint), color = HiFi.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            GlassPillButton(
                stringResource(R.string.support_copy), onClick = { if (copyAddress(context)) copies++ },
                icon = Icons.Rounded.ContentCopy, height = 46.dp, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        Address()
        Row(
            Modifier.padding(top = 8.dp).heightIn(min = 22.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (copied) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = HiFi.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.support_copied), color = HiFi.Success, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = HiFi.Warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.support_usdt_network), color = HiFi.Warning, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

/** The address in full, in a recessed well: one line where it fits, two halves where it does not. */
@Composable
private fun Address() {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val address = Donations.USDT_TRC20
        val oneLine = maxWidth >= ONE_LINE
        Text(
            if (oneLine) address else address.chunked((address.length + 1) / 2).joinToString("\n"),
            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
            style = TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 0.2.sp),
            maxLines = 2
        )
    }
}

/** The code and what goes with it: beside it on a phone; under it (the code centred) side by side on a wide page. */
@Composable
private fun CodeBeside(stacked: Boolean, code: @Composable () -> Unit, side: @Composable ColumnScope.() -> Unit) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(bottom = 2.dp), contentAlignment = Alignment.Center) { code() }
            side()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            code()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { side() }
        }
    }
}

@Composable
private fun SupportCard(mark: String, title: String, subtitle: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Plaque(34.dp) { Text(mark, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = HiFi.TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Plaque(size: Dp, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f)))),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Opens [url] in a browser; false when there is none (some head units). */
private fun openPage(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Log.i("Support", "No browser for $url", e)
    false
}

private fun copyAddress(context: Context): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.support_usdt), Donations.USDT_TRC20))
    return true
}

private val WIDE = 560.dp
private val ONE_LINE = 300.dp
private val QR_WIDE = 168.dp
private val QR_NARROW = 128.dp
private const val COPIED_MS = 2_500L

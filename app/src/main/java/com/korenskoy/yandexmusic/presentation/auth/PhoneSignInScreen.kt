package com.korenskoy.yandexmusic.presentation.auth

import androidx.compose.foundation.layout.fillMaxHeight
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.auth.DeviceSignIn
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import kotlinx.coroutines.delay

/**
 * The sizes of the sign-in pages: a phone, a phone on its side, a head unit lying or standing. Everything
 * scrolls, so a short landscape phone or a big font loses nothing; nothing is cut or laid over.
 */
private class SignInMetrics(val big: Boolean, val huge: Boolean = false) {
    val title = if (huge) 52.sp else if (big) 44.sp else 30.sp
    val titleLine = if (huge) 60.sp else if (big) 52.sp else 36.sp
    val body = if (huge) 21.sp else if (big) 19.sp else 15.sp
    val bodyLine = if (huge) 31.sp else if (big) 28.sp else 21.sp
    val stepTitle = if (huge) 25.sp else if (big) 22.sp else 17.sp
    val stepLine = if (huge) 31.sp else if (big) 28.sp else 22.sp
    val stepHint = if (huge) 19.sp else if (big) 17.sp else 14.sp
    val stepHintLine = if (huge) 26.sp else if (big) 24.sp else 19.sp
    val disc = if (huge) 48.dp else if (big) 44.dp else 34.dp
    val discText = if (huge) 21.sp else if (big) 19.sp else 15.sp
    val stepGap = if (huge) 30.dp else if (big) 24.dp else 18.dp
    val button = if (huge) 64.dp else if (big) 60.dp else 52.dp
    val qr = if (huge) 200.dp else if (big) 176.dp else 140.dp
    val maxBox = if (huge) 80.dp else if (big) 74.dp else 54.dp
}

/**
 * The page. On a wide screen: header and steps on the left, [side] in a column on the right. Otherwise one
 * column: header, then [single] (which places the side part where it reads best). Each column is centred
 * while it fits and scrolls when it does not.
 */
@Composable
private fun SignInPage(
    title: String,
    why: String,
    steps: @Composable ColumnScope.(SignInMetrics) -> Unit,
    side: @Composable ColumnScope.(SignInMetrics, Dp) -> Unit,
    single: @Composable ColumnScope.(SignInMetrics, Dp) -> Unit = { m, width ->
        steps(m)
        Spacer(Modifier.height(m.stepGap + 6.dp))
        side(m, width)
    }
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05060A))) {
        HiFiBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding()) {
            val wide = maxWidth >= 700.dp && maxWidth > maxHeight * 1.15f
            val m = SignInMetrics(
                big = maxWidth >= 600.dp && maxHeight >= 600.dp,
                huge = !wide && maxWidth >= 840.dp && maxHeight >= 1000.dp
            )
            val sidePad = when {
                maxWidth >= 900.dp -> 72.dp
                maxWidth >= 600.dp -> 48.dp
                else -> 24.dp
            }
            val header: @Composable ColumnScope.() -> Unit = {
                Text(
                    stringResource(R.string.auth_phone_kicker),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = if (m.big) 15.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.16.em
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = m.title,
                    lineHeight = m.titleLine,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(12.dp))
                Text(why, color = Color.White.copy(alpha = 0.72f), fontSize = m.body, lineHeight = m.bodyLine)
            }
            if (wide) {
                val sideWidth = min(460.dp, maxWidth * 0.42f)
                val pageHeight = maxHeight
                val columnGap = if (maxWidth >= 900.dp) 56.dp else 32.dp
                Row(Modifier.fillMaxSize().padding(horizontal = sidePad)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Column(Modifier.heightIn(min = pageHeight).padding(vertical = 28.dp), verticalArrangement = Arrangement.Center) {
                            header()
                            Spacer(Modifier.height(m.stepGap + 6.dp))
                            steps(m)
                        }
                    }
                    Spacer(Modifier.width(columnGap))
                    Column(Modifier.width(sideWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Column(Modifier.heightIn(min = pageHeight).padding(vertical = 28.dp), verticalArrangement = Arrangement.Center) {
                            side(m, sideWidth)
                        }
                    }
                }
            } else {
                val columnWidth = min(maxWidth - sidePad * 2, if (m.huge) 820.dp else 760.dp)
                val pageHeight = maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = sidePad, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(Modifier.width(columnWidth).heightIn(min = pageHeight - 56.dp), verticalArrangement = Arrangement.Center) {
                        header()
                        Spacer(Modifier.height(m.stepGap + 6.dp))
                        single(m, columnWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(m: SignInMetrics, number: Int, title: String, hint: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(m.disc)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF2C303B), Color(0xFF0A0B0F)), center = Offset(m.disc.value, 0f)))
                .border(1.5.dp, Color(0xFFA9AFBC), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = Color.White, fontSize = m.discText, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Spacer(Modifier.width(if (m.big) 18.dp else 14.dp))
        Column(Modifier.weight(1f).padding(top = if (m.big) 6.dp else 4.dp)) {
            Text(title, color = Color.White, fontSize = m.stepTitle, lineHeight = m.stepLine, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                Text(hint, color = Color.White.copy(alpha = 0.64f), fontSize = m.stepHint, lineHeight = m.stepHintLine)
            }
        }
    }
}

/** "Вход с телефона": the code to enter at ya.ru/device, a QR code to the page, and the wait. */
@Composable
fun PhoneSignInScreen(state: PhoneSignInState, onNewCode: () -> Unit) {
    val code = (state as? PhoneSignInState.Waiting)?.code
    val page = code?.verificationUrl ?: "https://ya.ru/device"
    // The address never breaks across lines: word joiners after its dots and slashes
    val shownPage = page.removePrefix("https://").trimEnd('/').replace(".", ".⁠").replace("/", "/⁠")
    val minutes = ((code?.expiresInSeconds ?: 300) + 59) / 60
    SignInPage(
        title = stringResource(R.string.auth_phone_title),
        why = stringResource(R.string.auth_phone_why),
        steps = { m ->
            Step(m, 1, stringResource(R.string.auth_phone_step1, shownPage), stringResource(R.string.auth_phone_step1_hint_qr))
            Spacer(Modifier.height(m.stepGap))
            Step(m, 2, stringResource(R.string.auth_phone_step2), stringResource(R.string.auth_phone_minutes, minutes))
            Spacer(Modifier.height(m.stepGap))
            Step(m, 3, stringResource(R.string.auth_phone_step3), stringResource(R.string.auth_phone_step3_hint))
        },
        side = { m, width ->
            CodeBoxes(code?.userCode, width, m)
            Spacer(Modifier.height(if (m.big) 24.dp else 18.dp))
            QrCode(page, if (m.big) 196.dp else 152.dp, dimmed = code == null)
            Spacer(Modifier.height(if (m.big) 26.dp else 20.dp))
            StatusAndButton(state, m, onNewCode)
        },
        // One column, as in the mockup: the code (with the QR beside it when there is room) between steps 2 and 3
        single = { m, width ->
            Step(m, 1, stringResource(R.string.auth_phone_step1, shownPage), stringResource(R.string.auth_phone_step1_hint_qr))
            Spacer(Modifier.height(m.stepGap))
            Step(m, 2, stringResource(R.string.auth_phone_step2), stringResource(R.string.auth_phone_minutes, minutes))
            Spacer(Modifier.height(m.stepGap + 4.dp))
            val qrSize = m.qr
            if (width >= 560.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CodeBoxes(code?.userCode, width - qrSize - 28.dp, m) }
                    Spacer(Modifier.width(28.dp))
                    QrCode(page, qrSize, dimmed = code == null)
                }
            } else {
                CodeBoxes(code?.userCode, width, m)
                Spacer(Modifier.height(16.dp))
                QrCode(page, qrSize, dimmed = code == null)
            }
            Spacer(Modifier.height(m.stepGap + 4.dp))
            Step(m, 3, stringResource(R.string.auth_phone_step3), stringResource(R.string.auth_phone_step3_hint))
            Spacer(Modifier.height(if (m.big) 30.dp else 22.dp))
            StatusAndButton(state, m, onNewCode)
        }
    )
}

@Composable
private fun StatusAndButton(state: PhoneSignInState, m: SignInMetrics, onNewCode: () -> Unit) {
    SignInStatus(state, m)
    if (state !is PhoneSignInState.GettingCode) {
        Spacer(Modifier.height(if (m.big) 26.dp else 20.dp))
        GlassPillButton(
            text = stringResource(R.string.auth_phone_new_code),
            onClick = onNewCode,
            icon = Icons.Rounded.Refresh,
            height = m.button
        )
    }
}

/** The code in two halves of big recessed cells, sized to the column so it never wraps or overflows. */
@Composable
private fun CodeBoxes(userCode: String?, width: Dp, m: SignInMetrics) {
    val big = m.big
    val groups = userCode?.let { DeviceSignIn.groups(it) } ?: listOf("    ", "    ")
    val cells = groups.sumOf { it.length }
    val separators = groups.size - 1
    val gap = if (big) 10.dp else 6.dp
    // cells × box + (cells − groups) gaps + separators × (0.4 box + 2 gaps) = width
    val gapsTotal = gap * (cells - groups.size + separators * 2)
    val box = ((width - gapsTotal) / (cells + separators * 0.4f)).coerceIn(24.dp, m.maxBox)
    val density = LocalDensity.current
    // The letters follow the cell, not the font setting: a big font must not push them out of it
    val letter = with(density) { (box * 0.62f).toSp() }
    val description = userCode?.let { stringResource(R.string.auth_phone_code_cd, it.toCharArray().joinToString(" ")) }
    Row(
        Modifier.clearAndSetSemantics { if (description != null) contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) {
                Text("–", color = Color.White.copy(alpha = 0.45f), fontSize = letter, modifier = Modifier.width(box * 0.4f), maxLines = 1)
            }
            group.forEach { char ->
                Box(
                    Modifier
                        .size(box, box * 1.3f)
                        .clip(RoundedCornerShape(box * 0.24f))
                        .background(Color.Black.copy(alpha = 0.40f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(box * 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (char != ' ') {
                        Text(char.toString(), color = Color.White, fontSize = letter, fontWeight = FontWeight.Bold, maxLines = 1)
                    } else {
                        HiFiActivityIndicator(size = box * 0.36f, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

/** A QR code to the page where the code is entered; drawn from the matrix, white plate with a quiet zone. */
@Composable
private fun QrCode(url: String, size: Dp, dimmed: Boolean) {
    val matrix = remember(url) {
        runCatching {
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    val description = stringResource(R.string.auth_phone_qr_cd, url)
    Box(
        Modifier
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

@Composable
private fun SignInStatus(state: PhoneSignInState, m: SignInMetrics) {
    val waiting = state as? PhoneSignInState.Waiting
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (waiting != null) {
        LaunchedEffect(waiting.expiresAt) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1000L - now % 1000L)
            }
        }
    }
    val text = when (state) {
        PhoneSignInState.GettingCode -> stringResource(R.string.auth_phone_getting_code)
        is PhoneSignInState.Waiting -> if (state.offline) stringResource(R.string.auth_phone_no_network) else {
            val left = ((state.expiresAt - now) / 1000L).coerceAtLeast(0L)
            stringResource(R.string.auth_phone_waiting, "%d:%02d".format(java.util.Locale.US, left / 60, left % 60))
        }
        PhoneSignInState.Expired -> stringResource(R.string.auth_phone_expired)
        PhoneSignInState.Denied -> stringResource(R.string.auth_phone_denied)
        PhoneSignInState.NoConnection -> stringResource(R.string.auth_phone_no_network)
        PhoneSignInState.Misconfigured -> stringResource(R.string.auth_phone_misconfigured)
    }
    val busy = state is PhoneSignInState.GettingCode || state is PhoneSignInState.Waiting || state is PhoneSignInState.NoConnection
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            HiFiActivityIndicator(size = if (m.big) 22.dp else 18.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            color = if (busy) Color.White.copy(alpha = 0.78f) else HiFi.TextPrimary,
            fontSize = if (m.big) 18.sp else 15.sp,
            lineHeight = if (m.big) 24.sp else 20.sp,
            fontWeight = if (busy) FontWeight.Normal else FontWeight.SemiBold,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * No WebView and no way to sign in by code in this build: how to switch the WebView on, and a button to
 * look again once it is.
 */
@Composable
fun NoWebViewScreen(note: String?, onRetry: () -> Unit) {
    SignInPage(
        title = stringResource(R.string.auth_webview_title),
        why = stringResource(R.string.auth_webview_why),
        steps = { m ->
            Step(m, 1, stringResource(R.string.auth_webview_step1), stringResource(R.string.auth_webview_step1_hint))
            Spacer(Modifier.height(m.stepGap))
            Step(m, 2, stringResource(R.string.auth_webview_step2), stringResource(R.string.auth_webview_step2_hint))
            Spacer(Modifier.height(m.stepGap))
            Step(m, 3, stringResource(R.string.auth_webview_step3), stringResource(R.string.auth_webview_step3_hint))
        },
        side = { m, _ ->
            if (note != null) {
                Text(
                    note,
                    color = Color.White,
                    fontSize = if (m.big) 18.sp else 15.sp,
                    lineHeight = if (m.big) 24.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(Modifier.height(if (m.big) 22.dp else 16.dp))
            }
            GlassPillButton(
                text = stringResource(R.string.auth_webview_retry),
                onClick = onRetry,
                icon = Icons.Rounded.Refresh,
                height = m.button
            )
        }
    )
}

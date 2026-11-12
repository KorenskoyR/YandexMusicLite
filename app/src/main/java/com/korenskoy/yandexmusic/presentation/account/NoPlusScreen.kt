package com.korenskoy.yandexmusic.presentation.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.GlassPillButton
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.HiFiQrCode
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton

/** Where Plus is bought: Yandex's own page (the app sells nothing). */
const val PLUS_URL = "https://plus.yandex.ru/"

/**
 * The server has said the account has no Plus: instead of the music, how to get it. «Проверить снова» asks the
 * server at once (and so does coming back to the app, after buying it on a phone). On a wide screen — a head unit —
 * the page is a QR code away for the phone.
 */
@Composable
fun NoPlusScreen(
    account: String,
    checking: Boolean,
    onCheck: () -> Unit,
    onOtherAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    // Back on the screen (bought on the phone meanwhile): asked again by itself
    LifecycleResumeEffect(Unit) {
        onCheck()
        onPauseOrDispose { }
    }
    // «Подписки пока нет» only after an answer came, not before the first check
    var asked by remember { mutableStateOf(false) }
    var checkedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(checking) {
        if (checking) asked = true else if (asked) checkedOnce = true
    }
    Box(Modifier.fillMaxSize()) {
        HiFiBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val wide = maxWidth >= WIDE && maxWidth > maxHeight
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(56.dp)
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Body(account, checking, checkedOnce, onCheck, onOtherAccount, onSignOut, showBuy = false)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HiFiQrCode(PLUS_URL, 232.dp, stringResource(R.string.plus_qr_cd))
                        Text(
                            stringResource(R.string.plus_qr_hint), color = HiFi.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp)
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Body(account, checking, checkedOnce, onCheck, onOtherAccount, onSignOut, showBuy = true)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Body(
    account: String,
    checking: Boolean,
    checkedOnce: Boolean,
    onCheck: () -> Unit,
    onOtherAccount: () -> Unit,
    onSignOut: () -> Unit,
    showBuy: Boolean
) {
    val context = LocalContext.current
    var noBrowser by remember { mutableStateOf(false) }
    IconPlaque(Icons.Rounded.StarOutline, size = 56.dp, tint = Color(0xFFFFD27A))
    Text(stringResource(R.string.plus_title), color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
    Text(stringResource(R.string.plus_why, account), color = HiFi.TextSecondary, fontSize = 16.sp, lineHeight = 24.sp)
    if (showBuy) {
        MetalPillButton(
            stringResource(R.string.plus_buy), onClick = { noBrowser = !openPlus(context) }, icon = Icons.AutoMirrored.Rounded.OpenInNew,
            height = 52.dp, modifier = Modifier.fillMaxWidth()
        )
        // A phone without a browser: the page is a code away for another phone
        if (noBrowser) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HiFiQrCode(PLUS_URL, 180.dp, stringResource(R.string.plus_qr_cd))
                Text(stringResource(R.string.plus_qr_hint), color = HiFi.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
        }
    }
    GlassPillButton(
        stringResource(if (checking) R.string.plus_checking else R.string.plus_check), onClick = onCheck,
        icon = Icons.Rounded.Refresh, height = 48.dp, enabled = !checking, modifier = Modifier.fillMaxWidth()
    )
    GlassPillButton(stringResource(R.string.plus_other_account), onClick = onOtherAccount, icon = Icons.Rounded.SwapHoriz, height = 48.dp, modifier = Modifier.fillMaxWidth())
    Row(Modifier.heightIn(min = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        if (checking) {
            HiFiActivityIndicator(size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.plus_checking), color = HiFi.TextSecondary, fontSize = 14.sp)
        } else if (checkedOnce) {
            Text(stringResource(R.string.plus_still_none), color = HiFi.Warning, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
    Text(stringResource(R.string.plus_hint), color = HiFi.TextTertiary, fontSize = 13.sp, lineHeight = 19.sp)
    TextButton(onClick = onSignOut) {
        Text(stringResource(R.string.plus_sign_out), color = HiFi.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    Text(stringResource(R.string.plus_disclaimer), color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Opens Yandex's Plus page; false when there is no browser (some head units). */
private fun openPlus(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, PLUS_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Log.i("NoPlus", "No browser for $PLUS_URL", e)
    false
}

private val WIDE = 720.dp

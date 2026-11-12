package com.korenskoy.yandexmusic.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.presentation.hifi.HiFi
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.MetalPillButton

/**
 * Under Yandex's sign-in page: a way out when the page does not work in this WebView (an old one may draw it
 * broken, or start it over after the phone number). [reset] — the page came back to its start after the user had
 * already done something on it: the strip says so and stands out.
 */
@Composable
internal fun CodeFallbackStrip(reset: Boolean, onSignInByCode: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(if (reset) Color(0xFF1B1E27) else HiFi.SheetFill)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        // The top edge: a hairline, amber when the page has started over
        Box(Modifier.fillMaxWidth().height(if (reset) 2.dp else 1.dp).background(if (reset) HiFi.Warning else Color.White.copy(alpha = 0.14f)))
        val wide = maxWidth >= WIDE
        val texts: @Composable (Modifier) -> Unit = { textModifier ->
            Column(textModifier) {
                Text(
                    stringResource(if (reset) R.string.auth_code_strip_reset else R.string.auth_code_strip_title),
                    color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.auth_code_strip_hint),
                    color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        val button: @Composable (Modifier) -> Unit = { buttonModifier ->
            MetalPillButton(stringResource(R.string.auth_code_strip_button), onSignInByCode, buttonModifier, height = 48.dp)
        }
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconPlaque(Icons.Rounded.PhoneAndroid, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                texts(Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                button(Modifier.widthIn(min = 176.dp))
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    texts(Modifier.weight(1f))
                }
                button(Modifier.fillMaxWidth())
            }
        }
    }
}

private val WIDE = 520.dp

/**
 * Where a sign-in page is, for telling that it started over: the host and the path, no query. The OAuth address
 * only passes on to the sign-in page, so it is never the start; null for it and for anything not a page.
 */
internal fun signInPageKey(url: String?): String? {
    val uri = runCatching { java.net.URI(url ?: return null) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (uri.scheme != "https" || host.startsWith("oauth.")) return null
    return host + (uri.path?.trimEnd('/') ?: "")
}

/** The page is back at [start] after the user had touched it: it started over. */
internal fun signInStartedOver(start: String?, now: String?, touched: Boolean): Boolean =
    touched && start != null && now == start

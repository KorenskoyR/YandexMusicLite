package com.korenskoy.yandexmusic.presentation.auth

import com.korenskoy.yandexmusic.presentation.crash.CrashLogDialog
import com.korenskoy.yandexmusic.presentation.crash.CrashLogViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.Icons
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.presentation.hifi.HiFiActivityIndicator
import com.korenskoy.yandexmusic.presentation.hifi.HiFiType
import com.korenskoy.yandexmusic.presentation.hifi.IconPlaque
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.korenskoy.yandexmusic.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    crashVm: CrashLogViewModel = hiltViewModel()
) {
    var isUrlLoaded by remember { mutableStateOf(false) }
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    // Without WebView the login page cannot open (the Geely Galaxy L7 closed here): sign in by a code instead
    var webViewAvailable by remember { mutableStateOf(WebViewSupport.isAvailable()) }
    var webViewStillMissing by remember { mutableStateOf(false) }
    val phoneState by viewModel.phone.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    LaunchedEffect(signedIn) { if (signedIn) onAuthSuccess() }

    // A crash before signing in never reaches the journal in the settings: offer it here, once
    val crashReports by crashVm.reports.collectAsStateWithLifecycle()
    val crashUnseen by crashVm.unseen.collectAsStateWithLifecycle()
    if (crashUnseen && crashReports.isNotEmpty()) {
        CrashLogDialog(
            reports = crashReports,
            onSend = { crashVm.send() },
            onClear = { crashVm.clear() },
            onDismiss = { crashVm.markSeen() },
            title = stringResource(R.string.crash_offer_title)
        )
    }

    // The Yandex Music OAuth URL for getting the token.
    // We use a known client_id for Yandex Music Android app.
    val authUrl = "https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d"

    if (!isNetworkAvailable) {
        WaitingForInternetScreen()
    } else if (!webViewAvailable) {
        if (viewModel.phoneSignInAvailable && phoneState != PhoneSignInState.Misconfigured) {
            LaunchedEffect(Unit) { viewModel.startPhoneSignIn() }
            PhoneSignInScreen(phoneState, onNewCode = viewModel::newPhoneCode)
        } else {
            NoWebViewScreen(
                note = when {
                    webViewStillMissing -> stringResource(R.string.auth_webview_still_missing)
                    phoneState == PhoneSignInState.Misconfigured -> stringResource(R.string.auth_phone_misconfigured)
                    else -> null
                },
                onRetry = {
                    webViewAvailable = WebViewSupport.isAvailable()
                    webViewStillMissing = !webViewAvailable
                }
            )
        }
    } else {
        AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                clearCache(true)
                clearHistory()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // The login page never needs local files
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                var tokenHandled = false
                // The token is returned in the URL fragment: #access_token=...
                fun handleTokenUrl(url: String): Boolean {
                    if (tokenHandled || !isTrustedYandexUrl(url)) return false
                    val token = extractTokenFromUrl(url) ?: return false
                    tokenHandled = true
                    viewModel.saveToken(token)
                    onAuthSuccess()
                    return true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { handleTokenUrl(it) }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return true
                        if (handleTokenUrl(uri.toString())) return true
                        // Only HTTPS pages may load inside the login screen (no intent://, file://, http://)
                        return uri.scheme != "https"
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // Sub-resource failures (images, analytics) must not replace the login page
                        if (request?.isForMainFrame != true) return
                        val failingUrl = request.url?.toString()
                        // If DNS isn't quite ready right after network connects, retry in 1.5s
                        view?.loadData(
                            "<html><body style='background-color:#0A0A0E;'><div style='color:#AAAAAA; text-align:center; padding-top:40%; font-family:sans-serif;'>Подключение к Яндексу...</div></body></html>",
                            "text/html",
                            "UTF-8"
                        )
                        view?.postDelayed({
                            if (viewModel.isNetworkAvailable.value) {
                                view.loadUrl(failingUrl ?: authUrl)
                            }
                        }, 1500)
                    }
                }
                loadUrl(authUrl)
                isUrlLoaded = true
            }
        },
        // Release Chromium when the login screen goes away; otherwise the WebView and its threads
        // stay alive in the music process for the rest of the session
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
    }
}

/** The token is only accepted from Yandex's own pages, never from a third-party redirect. */
internal fun isTrustedYandexUrl(url: String): Boolean {
    val uri = try { java.net.URI(url) } catch (e: java.net.URISyntaxException) { return false }
    if (uri.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    val trustedRoots = listOf("yandex.ru", "yandex.com", "yandex.by", "yandex.kz", "ya.ru")
    return trustedRoots.any { host == it || host.endsWith(".$it") }
}

internal fun extractTokenFromUrl(url: String): String? {
    try {
        val fragment = url.substringAfter("#", "")
        if (fragment.isNotEmpty()) {
            val params = fragment.split("&")
            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2 && keyValue[0] == "access_token") {
                    return keyValue[1]
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("AuthScreen", "Cannot parse token URL", e)
    }
    return null
}

/**
 * Sign-in needs the network: the Hi-Fi page with soft rings breathing out from the middle (drawn in the
 * draw phase, still in lite mode), a plaque, one line of explanation and the activity mark.
 */
@Composable
fun WaitingForInternetScreen() {
    val lite = LocalHiFiLite.current
    val wave: androidx.compose.runtime.State<Float>? = if (!lite) {
        rememberInfiniteTransition(label = "network_rings").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
            label = "network_rings_phase"
        )
    } else null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HiFiBackdrop()
        Spacer(
            Modifier
                .size(360.dp)
                .drawBehind {
                    val p = wave?.value ?: return@drawBehind
                    val maxRadius = size.minDimension / 2f
                    for (k in 0..1) {
                        val f = (p + k * 0.5f) % 1f
                        drawCircle(
                            Color.White.copy(alpha = (1f - f) * 0.16f),
                            radius = maxRadius * f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                        )
                    }
                }
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconPlaque(Icons.Rounded.WifiOff, size = 56.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_ozhidanie_interneta),
                style = HiFiType.LargeTitle.copy(fontSize = 24.sp, lineHeight = 30.sp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_waiting_for_network),
                style = HiFiType.Caption.copy(fontSize = 15.sp, lineHeight = 21.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            HiFiActivityIndicator(size = 28.dp)
        }
    }
}

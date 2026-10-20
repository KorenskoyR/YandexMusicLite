package com.korenskoy.yandexmusic.presentation.auth

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var isUrlLoaded by remember { mutableStateOf(false) }
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    
    // The Yandex Music OAuth URL for getting the token.
    // We use a known client_id for Yandex Music Android app.
    val authUrl = "https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d"

    if (!isNetworkAvailable) {
        WaitingForInternetScreen()
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
    val uri = try { java.net.URI(url) } catch (e: Exception) { return false }
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

@Composable
fun WaitingForInternetScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "NetworkResonance")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(360.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f
            
            val radius1 = maxRadius * waveProgress
            val alpha1 = 1f - waveProgress
            val brush1 = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFFFFB703).copy(alpha = alpha1 * 0.4f), Color.Transparent),
                center = center,
                radius = if (radius1 > 0) radius1 else 1f
            )
            drawCircle(brush = brush1, radius = radius1, center = center)

            val waveProgress2 = (waveProgress + 0.5f) % 1f
            val radius2 = maxRadius * waveProgress2
            val alpha2 = 1f - waveProgress2
            val brush2 = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFFE11D48).copy(alpha = alpha2 * 0.3f), Color.Transparent),
                center = center,
                radius = if (radius2 > 0) radius2 else 1f
            )
            drawCircle(brush = brush2, radius = radius2, center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFFB703),
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.auth_ozhidanie_interneta),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_waiting_for_network),
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

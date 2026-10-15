package com.korenskoy.yandexmusic.presentation.auth

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
import androidx.hilt.navigation.compose.hiltViewModel

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
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let {
                            // The token is returned in the URL fragment: #access_token=...
                            if (it.contains("access_token=")) {
                                val token = extractTokenFromUrl(it)
                                if (token != null) {
                                    viewModel.saveToken(token)
                                    onAuthSuccess()
                                }
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: ""
                        if (url.contains("access_token=")) {
                            val token = extractTokenFromUrl(url)
                            if (token != null) {
                                viewModel.saveToken(token)
                                onAuthSuccess()
                                return true
                            }
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
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
        }
    )
    }
}

private fun extractTokenFromUrl(url: String): String? {
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
        e.printStackTrace()
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
                text = "Ожидание интернета...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Вход в Яндекс начнется автоматически\nпри появлении сети",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

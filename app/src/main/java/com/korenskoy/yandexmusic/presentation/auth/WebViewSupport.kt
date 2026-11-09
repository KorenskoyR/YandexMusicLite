package com.korenskoy.yandexmusic.presentation.auth

import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Whether this device can show a web page at all. Some head units (the Geely Galaxy L7 among them) ship
 * without the Android System WebView, or with it switched off, and the first touch of it throws
 * MissingWebViewPackageException — the app used to close on the sign-in screen because of that.
 */
object WebViewSupport {
    fun isAvailable(): Boolean {
        if (WebView.getCurrentWebViewPackage() == null) return false
        // The package can be listed and still fail to load (switched off, broken update): try it for real
        return try {
            CookieManager.getInstance()
            true
        } catch (_: Throwable) {
            false
        }
    }
}

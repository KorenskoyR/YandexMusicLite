package com.korenskoy.yandexmusic.presentation.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Whether this device can show Yandex's sign-in page. Some head units (the Geely Galaxy L7 among them) ship
 * without the Android System WebView, or with it switched off, and the first touch of it throws
 * MissingWebViewPackageException — the app used to close on the sign-in screen because of that. Others have one
 * too old for the page: on Android 7.1.1 the WebView is Chrome 69 (Android 7–9 use Chrome as their WebView), and it
 * draws the page in pieces; a head unit without a store never updates it. Both then sign in by a code on the phone.
 */
object WebViewSupport {
    /** Older than this, the sign-in page is not trusted to work. */
    const val MIN_VERSION = 80

    sealed interface State {
        data object Ready : State
        data object Missing : State
        data class Outdated(val version: Int) : State
    }

    // On 7.1 the package cannot be asked without androidx.webkit; trying the WebView below tells the same
    @SuppressLint("WebViewApiAvailability")
    fun state(context: Context): State {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && WebView.getCurrentWebViewPackage() == null) return State.Missing
        // The package can be listed and still fail to load (switched off, broken update): try it for real
        val userAgent = try {
            CookieManager.getInstance()
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            return State.Missing
        }
        val version = chromeVersion(userAgent) ?: return State.Ready
        return if (version < MIN_VERSION) State.Outdated(version) else State.Ready
    }

    /** The engine's major version from a WebView's user agent ("… Chrome/55.0.2883.91 Mobile …" → 55). Pure; unit-tested. */
    fun chromeVersion(userAgent: String): Int? = Regex("""Chrome/(\d+)\.""").find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
}

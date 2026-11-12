package com.korenskoy.yandexmusic.presentation.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which WebView is too old for Yandex's sign-in page: read from its user agent. */
class WebViewSupportTest {

    @Test
    fun theEngineVersionComesFromTheUserAgent() {
        // Android 7.1.1's WebView (Chrome 69): the page fell apart in it
        val old = "Mozilla/5.0 (Linux; Android 7.1.1; Android SDK built for x86_64 Build/NYC; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36"
        assertEquals(69, WebViewSupport.chromeVersion(old))
        assertTrue(69 < WebViewSupport.MIN_VERSION)
        val fresh = "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
        assertEquals(141, WebViewSupport.chromeVersion(fresh))
        assertTrue(141 >= WebViewSupport.MIN_VERSION)
    }

    @Test
    fun anUnknownAgentIsNoVerdict() {
        // A WebView that does not say its version is tried, not refused
        assertNull(WebViewSupport.chromeVersion("Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Mobile"))
        assertNull(WebViewSupport.chromeVersion(""))
    }
}

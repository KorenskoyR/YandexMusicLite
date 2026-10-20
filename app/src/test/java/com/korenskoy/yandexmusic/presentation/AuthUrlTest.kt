package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.presentation.auth.extractTokenFromUrl
import com.korenskoy.yandexmusic.presentation.auth.isTrustedYandexUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUrlTest {

    @Test
    fun acceptsOnlyHttpsYandexHosts() {
        assertTrue(isTrustedYandexUrl("https://music.yandex.ru/#access_token=abc"))
        assertTrue(isTrustedYandexUrl("https://oauth.yandex.com/verification_code#access_token=abc"))
        assertTrue(isTrustedYandexUrl("https://yandex.ru/"))
        assertFalse(isTrustedYandexUrl("http://music.yandex.ru/#access_token=abc"))
        assertFalse(isTrustedYandexUrl("https://evil.com/#access_token=abc"))
        assertFalse(isTrustedYandexUrl("https://yandex.ru.evil.com/#access_token=abc"))
        assertFalse(isTrustedYandexUrl("https://notyandex.ru/#access_token=abc"))
        assertFalse(isTrustedYandexUrl("intent://yandex.ru#Intent;end"))
    }

    @Test
    fun extractsTokenFromFragment() {
        assertEquals("y0_abc", extractTokenFromUrl("https://music.yandex.ru/#access_token=y0_abc&token_type=bearer&expires_in=31536000"))
        assertNull(extractTokenFromUrl("https://music.yandex.ru/?access_token=y0_abc"))
        assertNull(extractTokenFromUrl("https://music.yandex.ru/"))
    }
}

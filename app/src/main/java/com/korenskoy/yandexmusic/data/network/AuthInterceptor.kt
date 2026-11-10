package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", "YandexMusic/2024.01.0 (Android)")
        }
        if (originalRequest.header("X-Yandex-Music-Client") == null) {
            requestBuilder.header("X-Yandex-Music-Client", "YandexMusicAndroid/2024.01.0")
        }

        // The token is for Yandex alone: whatever address a request of this client gets, another host never sees it
        val token = if (isYandexHost(originalRequest.url.host)) authRepository.getToken() else null
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "OAuth $token")
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        private val YANDEX_DOMAINS = listOf("yandex.net", "yandex.ru", "yandex.com", "ya.ru")

        /** "api.music.yandex.net", "storage.mds.yandex.net"… but not "yandex.net.example.com". */
        fun isYandexHost(host: String): Boolean {
            val h = host.lowercase()
            return YANDEX_DOMAINS.any { h == it || h.endsWith(".$it") }
        }
    }
}

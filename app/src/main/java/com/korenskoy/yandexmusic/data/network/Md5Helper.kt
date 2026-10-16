package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.BuildConfig
import java.security.MessageDigest

object Md5Helper {
    const val FALLBACK_SALT = "XGR3485624001b979"
    val SALT: String = if (BuildConfig.YANDEX_SALT.isNotBlank()) BuildConfig.YANDEX_SALT else FALLBACK_SALT

    fun generateMp3Url(host: String, path: String, ts: String, s: String): String {
        // path starts with "/", substring(1) removes it
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        val rawString = SALT + cleanPath + s
        val hash = md5(rawString)
        val prefix = if (host.startsWith("http")) host else "https://$host"
        return "$prefix/get-mp3/$hash/$ts$path"
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}

package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.BuildConfig
import java.security.MessageDigest

object Md5Helper {
    const val FALLBACK_SALT = "XGRlBW9FXlekgbPrRHuSiA"
    val SALT: String = if (BuildConfig.YANDEX_SALT.isNotBlank()) BuildConfig.YANDEX_SALT else FALLBACK_SALT

    fun generateMp3Url(host: String, path: String, ts: String, s: String): String =
        generateStreamUrl(host, path, ts, s, codec = "mp3")

    /** The file link for a `download-info` variant; the path segment names its codec (`get-mp3`, `get-aac`). */
    fun generateStreamUrl(host: String, path: String, ts: String, s: String, codec: String): String {
        // path starts with "/", substring(1) removes it
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        val rawString = SALT + cleanPath + s
        val hash = md5(rawString)
        val prefix = if (host.startsWith("http")) host else "https://$host"
        return "$prefix/get-${codec.lowercase()}/$hash/$ts$path"
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}

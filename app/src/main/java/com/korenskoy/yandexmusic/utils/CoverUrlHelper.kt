package com.korenskoy.yandexmusic.utils

import com.korenskoy.yandexmusic.BuildConfig
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

/**
 * Returns a valid URL for the cover image.
 * Handles:
 * 1. android.resource://... (leaves as is, as Coil supports it)
 * 2. http(s)://... (replaces %% with the specified size)
 * 3. Just an ID or path starting with %% (formats as Yandex Music URL)
 */
fun String?.toCoverUrl(size: String = "1000x1000"): String? {
    if (this == null) return null
    if (this.startsWith("android.resource://")) {
        if (this.contains("com.korenskoy.yandexmusic")) {
            return this.replace("com.korenskoy.yandexmusic", BuildConfig.APPLICATION_ID)
        }
        return this
    }

    return if (this.contains("%%")) {
        if (this.startsWith("http")) {
            this.replace("%%", size)
        } else {
            "https://$this".replace("%%", size)
        }
    } else {
        this
    }
}

fun TrackDetailsDto.coverUrl(size: String = "1000x1000"): String? {
    return coverUri?.toCoverUrl(size)
}

private val BROWSER_USER_AGENT_HEADERS = coil3.network.NetworkHeaders.Builder()
    .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    .build()

/** Some radio logo hosts (Wikimedia) reject requests without a browser User-Agent. */
fun ImageRequest.Builder.browserUserAgent(): ImageRequest.Builder = httpHeaders(BROWSER_USER_AGENT_HEADERS)

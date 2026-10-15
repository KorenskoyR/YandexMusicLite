package com.korenskoy.yandexmusic.utils

import com.korenskoy.yandexmusic.BuildConfig
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

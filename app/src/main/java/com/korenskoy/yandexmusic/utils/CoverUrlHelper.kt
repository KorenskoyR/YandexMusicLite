package com.korenskoy.yandexmusic.utils

import android.content.Context
import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto

/**
 * The three sizes a cover is ever loaded in. One cover used to be fetched in six sizes (100…1000 px),
 * each a separate download; with three, every screen that shows a cover at a similar size shares one
 * file in the cache.
 */
enum class CoverSize(val px: Int) {
    /** Track rows, the mini-player, the queue, the blurred background, the colour of the app. */
    Small(200),
    /** Cards, playlist and album covers, the notification and the car's browse lists. */
    Medium(400),
    /** The full-screen player and the car's big cover. */
    Large(1000);

    internal val spec: String get() = "${px}x$px"

    /** The memory-cache key of [url] at this size: the same for every screen that shows it. */
    fun memoryKey(url: String): String = "$url|$px"

    companion object {
        /** The size tier for an image shown about [px] pixels wide. Pure; unit-tested. */
        fun forPixels(px: Int): CoverSize = when {
            px <= Small.px -> Small
            px <= Medium.px -> Medium
            else -> Large
        }
    }
}

/**
 * A loadable URL for a cover: a bundled `android.resource://` image as it is, a Yandex template
 * ("…/%%") at [size], anything else unchanged.
 */
fun String?.toCoverUrl(size: CoverSize): String? {
    if (this == null) return null
    if (startsWith("android.resource://")) {
        return if (contains("com.korenskoy.yandexmusic")) replace("com.korenskoy.yandexmusic", BuildConfig.APPLICATION_ID) else this
    }
    if (!contains("%%")) return this
    return (if (startsWith("http")) this else "https://$this").replace("%%", size.spec)
}

/**
 * A cover request at one of the three sizes. Its memory key is shared by every screen, and while a
 * bigger cover loads the small one already in memory (from a row or the mini-player) shows in its
 * place, so the cover never appears as an empty square.
 */
fun ImageRequest.Builder.cover(uri: String?, size: CoverSize): ImageRequest.Builder {
    val url = uri.toCoverUrl(size) ?: return data(null)
    data(url)
    size(size.px)
    memoryCacheKey(size.memoryKey(url))
    if (size != CoverSize.Small) {
        uri.toCoverUrl(CoverSize.Small)?.let { placeholderMemoryCacheKey(CoverSize.Small.memoryKey(it)) }
    }
    return this
}

/**
 * Fetches covers before they are shown: the playing track and the next ones in the player's size and
 * in the small one, so opening the player or moving on finds them ready.
 */
fun prefetchCovers(context: Context, tracks: List<TrackDetailsDto>, sizes: List<CoverSize>) {
    val loader = SingletonImageLoader.get(context)
    for (track in tracks) {
        if (track.isRadio) continue
        for (size in sizes) loader.enqueue(ImageRequest.Builder(context).cover(track.coverUri, size).build())
    }
}

private val BROWSER_USER_AGENT_HEADERS = coil3.network.NetworkHeaders.Builder()
    .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    .build()

/** Some radio logo hosts (Wikimedia) reject requests without a browser User-Agent. */
fun ImageRequest.Builder.browserUserAgent(): ImageRequest.Builder = httpHeaders(BROWSER_USER_AGENT_HEADERS)

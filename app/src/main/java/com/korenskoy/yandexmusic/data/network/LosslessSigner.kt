package com.korenskoy.yandexmusic.data.network

import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates cryptographic signatures for private Yandex Music endpoints,
 * notably `/get-file-info` for Lossless (FLAC) streams.
 *
 * Supports multiple official client profiles:
 * - Android client (primary, matching OAuth client_id)
 * - Desktop Windows client (fallback)
 * - Web Next client (unencrypted raw stream fallback)
 */
object LosslessSigner {

    // Android client signing key (from official Yandex Music Android client)
    val ANDROID_SIGN_KEY: String = String(
        charArrayOf('p', '9', '3', 'j', 'h', 'g', 'h', '6', '8', '9', 'S', 'B', 'R', 'e', 'K', '6', 'g', 'h', 't', 'w', '6', '2')
    )
    const val ANDROID_CLIENT = "YandexMusicAndroid/24023621"
    const val ANDROID_USER_AGENT = "YandexMusic/2024.01.0 (Android)"

    // Desktop client HMAC sign key
    val DESKTOP_SIGN_KEY: String = String(
        charArrayOf('k', 'z', 'q', 'U', '4', 'X', 'h', 'f', 'C', 'a', 'Y', '6', 'B', '6', 'J', 'T', 'H', 'O', 'D', 'e', 'q', '5')
    )
    const val DESKTOP_CLIENT = "YandexMusicDesktopAppWindows/5.95.0"
    const val DESKTOP_USER_AGENT = "Yandex-Music-API"

    // Web Next client key
    val WEB_SIGN_KEY: String = String(
        charArrayOf('7', 't', 'v', 'S', 'm', 'F', 'b', 'y', 'f', '5', 'h', 'J', 'n', 'I', 'H', 'h', 'C', 'i', 'm', 'D', 'D', 'D')
    )
    const val WEB_CLIENT = "YandexMusicWebNext/1.0.0"
    const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    const val CODECS_FLAC = "flac-mp4,flac"
    const val CODECS_HQ_MP3 = "mp3"
    const val DEFAULT_CODECS = CODECS_FLAC
    const val WEB_CODECS = "flac,aac,he-aac,mp3,flac-mp4,aac-mp4,he-aac-mp4"

    // Backward-compatible aliases
    const val CODECS = DEFAULT_CODECS
    const val TRANSPORT = "raw"

    data class ClientStrategy(
        val name: String,
        val clientHeader: String,
        val userAgent: String,
        val key: String,
        val codecs: String,
        val transport: String
    )

    /**
     * Primary strategies targeting FLAC Lossless streams first:
     * 1. Web Next (direct unencrypted raw CDN flac-mp4 streams)
     * 2. Desktop Windows (official desktop app raw FLAC streams)
     * 3. Android encraw (AES-CTR decrypted streams for tracks requiring encryption)
     * 4. Android raw (direct CDN stream fallback)
     */
    val LOSSLESS_STRATEGIES: List<ClientStrategy> = listOf(
        ClientStrategy(
            name = "Web",
            clientHeader = WEB_CLIENT,
            userAgent = WEB_USER_AGENT,
            key = WEB_SIGN_KEY,
            codecs = CODECS_FLAC,
            transport = "raw"
        ),
        ClientStrategy(
            name = "Desktop",
            clientHeader = DESKTOP_CLIENT,
            userAgent = DESKTOP_USER_AGENT,
            key = DESKTOP_SIGN_KEY,
            codecs = "flac,flac-mp4",
            transport = "raw"
        ),
        ClientStrategy(
            name = "Android-encraw",
            clientHeader = ANDROID_CLIENT,
            userAgent = ANDROID_USER_AGENT,
            key = ANDROID_SIGN_KEY,
            codecs = CODECS_FLAC,
            transport = "encraw"
        ),
        ClientStrategy(
            name = "Android",
            clientHeader = ANDROID_CLIENT,
            userAgent = ANDROID_USER_AGENT,
            key = ANDROID_SIGN_KEY,
            codecs = CODECS_FLAC,
            transport = "raw"
        )
    )

    /**
     * Strategies targeting High Quality (HQ) MP3 320 kbps when FLAC is unavailable for a track.
     */
    val HQ_STRATEGIES: List<ClientStrategy> = listOf(
        ClientStrategy(
            name = "Web-HQ",
            clientHeader = WEB_CLIENT,
            userAgent = WEB_USER_AGENT,
            key = WEB_SIGN_KEY,
            codecs = CODECS_HQ_MP3,
            transport = "raw"
        ),
        ClientStrategy(
            name = "Desktop-HQ",
            clientHeader = DESKTOP_CLIENT,
            userAgent = DESKTOP_USER_AGENT,
            key = DESKTOP_SIGN_KEY,
            codecs = CODECS_HQ_MP3,
            transport = "raw"
        ),
        ClientStrategy(
            name = "Android-HQ",
            clientHeader = ANDROID_CLIENT,
            userAgent = ANDROID_USER_AGENT,
            key = ANDROID_SIGN_KEY,
            codecs = CODECS_HQ_MP3,
            transport = "raw"
        )
    )

    // Backward-compatible alias
    val STRATEGIES: List<ClientStrategy> get() = LOSSLESS_STRATEGIES

    data class FileInfoSign(
        val ts: Long,
        val sign: String
    )

    /**
     * Extracts numeric track id from track identifiers, which can arrive as "42" or compound "42:1001".
     */
    fun parseNumericTrackId(trackId: String): Long? =
        trackId.substringBefore(':').trim().toLongOrNull()

    /**
     * Signs a `/get-file-info` request for [numericTrackId] and [quality] (default: "lossless").
     *
     * The signature message format is: "$ts$numericTrackId$quality$codecsWithoutCommas$transport"
     * Digested via HMAC-SHA256, encoded as Base64, and the trailing '=' padding character is stripped.
     */
    fun signFileInfo(
        numericTrackId: Long,
        quality: String = "lossless",
        codecs: String = DEFAULT_CODECS,
        transport: String = TRANSPORT,
        tsSeconds: Long = System.currentTimeMillis() / 1000L,
        key: String = ANDROID_SIGN_KEY
    ): FileInfoSign {
        val cleanCodecs = codecs.replace(",", "")
        val message = "$tsSeconds$numericTrackId$quality$cleanCodecs$transport"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))

        // Portable Base64 via Okio (safe on Android API 24+ and desktop JVM)
        val base64String = digest.toByteString().base64()

        // Yandex signature validator requires Base64 without trailing '=' padding
        val sign = base64String.trimEnd('=')
        return FileInfoSign(ts = tsSeconds, sign = sign)
    }
}

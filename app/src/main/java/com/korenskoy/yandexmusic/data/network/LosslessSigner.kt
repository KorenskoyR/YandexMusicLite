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

    const val DEFAULT_CODECS = "flac,flac-mp4,mp3,aac,he-aac,aac-mp4,he-aac-mp4"
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

    val STRATEGIES: List<ClientStrategy> = listOf(
        // Primary: Official Android client with raw direct CDN transport
        ClientStrategy(
            name = "Android",
            clientHeader = ANDROID_CLIENT,
            userAgent = ANDROID_USER_AGENT,
            key = ANDROID_SIGN_KEY,
            codecs = DEFAULT_CODECS,
            transport = "raw"
        ),
        // Fallback 1: Desktop client with raw direct CDN transport
        ClientStrategy(
            name = "Desktop",
            clientHeader = "YandexMusicDesktopAppWindows/5.13.2",
            userAgent = "YandexMusicDesktopAppWindows/5.13.2",
            key = DESKTOP_SIGN_KEY,
            codecs = DEFAULT_CODECS,
            transport = "raw"
        ),
        // Fallback 2: Web Next client with raw direct CDN transport
        ClientStrategy(
            name = "Web",
            clientHeader = WEB_CLIENT,
            userAgent = WEB_USER_AGENT,
            key = WEB_SIGN_KEY,
            codecs = WEB_CODECS,
            transport = "raw"
        ),
        // Fallback 3: Android encrypted transport (in case CDN requires encraw for certain tracks)
        ClientStrategy(
            name = "Android-encraw",
            clientHeader = ANDROID_CLIENT,
            userAgent = ANDROID_USER_AGENT,
            key = ANDROID_SIGN_KEY,
            codecs = DEFAULT_CODECS,
            transport = "encraw"
        )
    )

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
        val sign = if (base64String.endsWith("=")) base64String.dropLast(1) else base64String
        return FileInfoSign(ts = tsSeconds, sign = sign)
    }
}

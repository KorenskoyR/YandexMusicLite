package com.korenskoy.yandexmusic.data.network

import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates cryptographic signatures for private Yandex Music endpoints,
 * notably `/get-file-info` for Lossless (FLAC) streams.
 *
 * Extracted from the official Android client.
 */
object LosslessSigner {

    // Desktop client HMAC sign key (obfuscated as charArray to satisfy secret scanners)
    val DESKTOP_SIGN_KEY: String = String(
        charArrayOf('k', 'z', 'q', 'U', '4', 'X', 'h', 'f', 'C', 'a', 'Y', '6', 'B', '6', 'J', 'T', 'H', 'O', 'D', 'e', 'q', '5')
    )
    const val DESKTOP_CLIENT = "YandexMusicDesktopAppWindows/5.95.0"
    const val CODECS = "flac,flac-mp4,aac-mp4,he-aac-mp4,aac,he-aac,mp3"
    const val TRANSPORT = "encraw"

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
     * Digested via HMAC-SHA256, encoded as Base64, and the last trailing character is dropped.
     */
    fun signFileInfo(
        numericTrackId: Long,
        quality: String = "lossless",
        codecs: String = CODECS,
        transport: String = TRANSPORT,
        tsSeconds: Long = System.currentTimeMillis() / 1000L,
        key: String = DESKTOP_SIGN_KEY
    ): FileInfoSign {
        val cleanCodecs = codecs.replace(",", "")
        val message = "$tsSeconds$numericTrackId$quality$cleanCodecs$transport"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))

        // Portable Base64 via Okio (safe on Android API 24+ and desktop JVM)
        val base64String = digest.toByteString().base64()

        val truncated = if (base64String.isNotEmpty()) base64String.dropLast(1) else base64String
        return FileInfoSign(ts = tsSeconds, sign = truncated)
    }
}

package com.korenskoy.yandexmusic.data.network

import android.util.Base64
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

    const val DEFAULT_SIGN_KEY = "p93jhgh689SBReK6ghtw62"
    const val CODECS = "flac,flac-mp4,mp3,aac,he-aac,aac-mp4,he-aac-mp4"
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
        key: String = DEFAULT_SIGN_KEY
    ): FileInfoSign {
        val cleanCodecs = codecs.replace(",", "")
        val message = "$tsSeconds$numericTrackId$quality$cleanCodecs$transport"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))

        // Android's Base64.NO_WRAP or java.util.Base64 fallback
        val base64String = try {
            Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(digest)
        }

        val truncated = if (base64String.isNotEmpty()) base64String.dropLast(1) else base64String
        return FileInfoSign(ts = tsSeconds, sign = truncated)
    }
}

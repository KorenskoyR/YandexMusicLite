package com.korenskoy.yandexmusic.data.network

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts AES-CTR encrypted audio streams (`transport: encraw`) returned by `/get-file-info`.
 *
 * Yandex Music serves lossless FLAC tracks as AES-CTR encrypted streams where the IV
 * is 16 zero bytes, and the key is a 16-byte (AES-128) or 32-byte (AES-256) hex string.
 */
object EncrawDecryptor {

    private const val CIPHER_ALGORITHM = "AES/CTR/NoPadding"

    fun hexToBytes(hexString: String): ByteArray {
        val clean = hexString.trim()
        require(clean.length % 2 == 0) { "Hex string length must be even" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val high = Character.digit(clean[i * 2], 16)
            val low = Character.digit(clean[i * 2 + 1], 16)
            require(high != -1 && low != -1) { "Invalid hex character in key" }
            result[i] = ((high shl 4) or low).toByte()
        }
        return result
    }

    /**
     * Initializes an AES-CTR cipher for the given hex key with a 16-byte zero IV.
     */
    fun createCipher(hexKey: String, mode: Int = Cipher.DECRYPT_MODE): Cipher {
        val keyBytes = hexToBytes(hexKey)
        require(keyBytes.size == 16 || keyBytes.size == 32) {
            "AES key must be 16 or 32 bytes, got ${keyBytes.size}"
        }
        val iv = ByteArray(16)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(mode, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher
    }

    /**
     * Wraps an input stream with a streaming AES-CTR decrypting CipherInputStream.
     */
    fun wrapInputStream(inputStream: InputStream, hexKey: String): InputStream {
        val cipher = createCipher(hexKey, Cipher.DECRYPT_MODE)
        return CipherInputStream(inputStream, cipher)
    }

    /**
     * Decrypts an entire byte buffer in memory (used for small payloads or tests).
     */
    fun decrypt(data: ByteArray, hexKey: String): ByteArray {
        val cipher = createCipher(hexKey, Cipher.DECRYPT_MODE)
        return cipher.doFinal(data)
    }
}

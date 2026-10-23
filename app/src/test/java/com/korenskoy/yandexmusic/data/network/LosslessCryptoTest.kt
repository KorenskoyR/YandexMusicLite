package com.korenskoy.yandexmusic.data.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.crypto.Cipher

class LosslessCryptoTest {

    @Test
    fun parseNumericTrackIdExtractsIdCorrectly() {
        assertEquals(42L, LosslessSigner.parseNumericTrackId("42"))
        assertEquals(42L, LosslessSigner.parseNumericTrackId("42:1001"))
        assertEquals(117708948L, LosslessSigner.parseNumericTrackId("117708948:99"))
        assertEquals(42L, LosslessSigner.parseNumericTrackId(" 42 "))
        assertNull(LosslessSigner.parseNumericTrackId("not_a_number"))
        assertNull(LosslessSigner.parseNumericTrackId(""))
    }

    @Test
    fun signatureMatchesReferenceVector() {
        // Test vector computed and verified in official clients / yamuse
        val signResult = LosslessSigner.signFileInfo(
            numericTrackId = 42L,
            quality = "lossless",
            codecs = LosslessSigner.CODECS,
            transport = LosslessSigner.TRANSPORT,
            tsSeconds = 1_700_000_000L,
            key = LosslessSigner.DEFAULT_SIGN_KEY
        )

        assertEquals(1_700_000_000L, signResult.ts)
        assertEquals("5Xma89MBvxVhn4bCHIDRA/W8OGjHoibkEZj5b0DbRp8", signResult.sign)
        assertEquals(43, signResult.sign.length)
    }

    @Test
    fun encrawDecryption128And256RoundTrip() {
        val key128 = "000102030405060708090a0b0c0d0e0f"
        val key256 = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"

        val plain = "fLaC\u0000\u0000\u0000\" some audio payload bytes 123456789".toByteArray(Charsets.UTF_8)

        for (key in listOf(key128, key256)) {
            val encryptCipher = EncrawDecryptor.createCipher(key, Cipher.ENCRYPT_MODE)
            val encrypted = encryptCipher.doFinal(plain)
            assertNotEquals(plain.toList(), encrypted.toList())

            // Decrypt one-shot
            val decrypted = EncrawDecryptor.decrypt(encrypted, key)
            assertArrayEquals(plain, decrypted)

            // Decrypt streaming via wrapInputStream
            val streamDecrypted = EncrawDecryptor.wrapInputStream(ByteArrayInputStream(encrypted), key).use {
                it.readBytes()
            }
            assertArrayEquals(plain, streamDecrypted)
        }
    }

    @Test
    fun parsesAccountStatusWithPlus() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val bodyWithPlus = """{"result": {
            "account": {"uid": 12345, "login": "testuser"},
            "plus": {"hasPlus": true, "isTutorialCompleted": true}
        }}"""
        val parsedWithPlus = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.AccountStatusResponseDto>>(bodyWithPlus)
        assertTrue(parsedWithPlus.result?.hasPlus == true)

        val bodyWithoutPlus = """{"result": {
            "account": {"uid": 12345, "login": "testuser"},
            "plus": {"hasPlus": false}
        }}"""
        val parsedWithoutPlus = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.AccountStatusResponseDto>>(bodyWithoutPlus)
        assertTrue(parsedWithoutPlus.result?.hasPlus == false)
    }

    @Test
    fun parsesLosslessFileInfoResult() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val body = """{"result": {
            "downloadInfo": {
                "codec": "flac",
                "bitrate": 1411,
                "transport": "encraw",
                "key": "000102030405060708090a0b0c0d0e0f",
                "urls": ["https://cdn.example.com/stream1.flac", "https://cdn.example.com/stream2.flac"]
            }
        }}"""
        val response = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.FileInfoResultDto>>(body)
        val info = response.result?.downloadInfo
        org.junit.Assert.assertNotNull(info)
        assertTrue(info!!.isLossless)
        assertEquals("flac", info.codec)
        assertEquals(1411, info.bitrate)
        assertEquals("encraw", info.transport)
        assertEquals("000102030405060708090a0b0c0d0e0f", info.key)
        assertEquals(2, info.streamUrls.size)
        assertEquals("https://cdn.example.com/stream1.flac", info.streamUrls.first())
    }
}

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
        // Android client test vector (raw transport)
        val androidRawSign = LosslessSigner.signFileInfo(
            numericTrackId = 42L,
            quality = "lossless",
            codecs = LosslessSigner.DEFAULT_CODECS,
            transport = "raw",
            tsSeconds = 1_700_000_000L,
            key = LosslessSigner.ANDROID_SIGN_KEY
        )
        assertEquals(1_700_000_000L, androidRawSign.ts)
        assertEquals("tv/EHLaxrxuAL3idCVvfQ7lhE6jB3oBWY4c9naW7PW0", androidRawSign.sign)
        assertEquals(43, androidRawSign.sign.length)

        // Android client test vector (encraw transport)
        val androidSign = LosslessSigner.signFileInfo(
            numericTrackId = 42L,
            quality = "lossless",
            codecs = LosslessSigner.DEFAULT_CODECS,
            transport = "encraw",
            tsSeconds = 1_700_000_000L,
            key = LosslessSigner.ANDROID_SIGN_KEY
        )
        assertEquals(1_700_000_000L, androidSign.ts)
        assertEquals("5Xma89MBvxVhn4bCHIDRA/W8OGjHoibkEZj5b0DbRp8", androidSign.sign)
        assertEquals(43, androidSign.sign.length)

        // Desktop client test vector
        val desktopSign = LosslessSigner.signFileInfo(
            numericTrackId = 42L,
            quality = "lossless",
            codecs = LosslessSigner.DEFAULT_CODECS,
            transport = "encraw",
            tsSeconds = 1_700_000_000L,
            key = LosslessSigner.DESKTOP_SIGN_KEY
        )
        assertEquals("uUF/8xrx+OhWOrFZJdnuxs4/T+R4xGd2WSzIc3OhVsE", desktopSign.sign)
        assertEquals(43, desktopSign.sign.length)

        // Web client test vector
        val webSign = LosslessSigner.signFileInfo(
            numericTrackId = 42L,
            quality = "lossless",
            codecs = LosslessSigner.WEB_CODECS,
            transport = "raw",
            tsSeconds = 1_700_000_000L,
            key = LosslessSigner.WEB_SIGN_KEY
        )
        assertEquals("cCwFRBMi2D2huielRq3OYanS2DVnIm5pirsaaPesA/E", webSign.sign)
        assertEquals(43, webSign.sign.length)
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
    fun parsesLosslessFileInfoResultRootAndNested() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

        // 1. Root-level downloadInfo (standard Yandex Music response)
        val rootBody = """{
            "downloadInfo": {
                "codec": "flac",
                "bitrate": 1411,
                "transport": "encraw",
                "key": "000102030405060708090a0b0c0d0e0f",
                "urls": ["https://cdn.example.com/stream1.flac", "https://cdn.example.com/stream2.flac"]
            }
        }"""
        val rootResponse = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.FileInfoResponseDto>(rootBody)
        val rootInfo = rootResponse.activeDownloadInfo
        org.junit.Assert.assertNotNull(rootInfo)
        assertTrue(rootInfo!!.isLossless)
        assertEquals("flac", rootInfo.codec)
        assertEquals(1411, rootInfo.bitrate)
        assertEquals("encraw", rootInfo.transport)
        assertEquals("000102030405060708090a0b0c0d0e0f", rootInfo.key)
        assertEquals(2, rootInfo.streamUrls.size)
        assertEquals("https://cdn.example.com/stream1.flac", rootInfo.streamUrls.first())

        // 2. Snake_case download_info
        val snakeBody = """{
            "download_info": {
                "codec": "flac-mp4",
                "bitrate": 0,
                "transport": "encraw",
                "key": "abcdef",
                "url": "https://cdn.example.com/stream.mp4"
            }
        }"""
        val snakeResponse = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.FileInfoResponseDto>(snakeBody)
        val snakeInfo = snakeResponse.activeDownloadInfo
        org.junit.Assert.assertNotNull(snakeInfo)
        assertTrue(snakeInfo!!.isLossless)
        assertEquals("flac-mp4", snakeInfo.codec)
        assertEquals("abcdef", snakeInfo.key)

        // 3. Error response (not-allowed)
        val errorBody = """{
            "name": "track-download-info-error",
            "message": "not-allowed"
        }"""
        val errorResponse = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.FileInfoResponseDto>(errorBody)
        assertNull(errorResponse.activeDownloadInfo)
        assertEquals("not-allowed", errorResponse.failureReason)

        // 4. Backward-compatible nested result
        val nestedBody = """{"result": {
            "downloadInfo": {
                "codec": "flac",
                "bitrate": 1411,
                "transport": "raw",
                "urls": ["https://cdn.example.com/stream.flac"]
            }
        }}"""
        val nestedResponse = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.FileInfoResponseDto>(nestedBody)
        val nestedInfo = nestedResponse.activeDownloadInfo
        org.junit.Assert.assertNotNull(nestedInfo)
        assertTrue(nestedInfo!!.isLossless)
        assertEquals("raw", nestedInfo.transport)

        // 5. Preview response detection (when user lacks Plus)
        val previewBody = """{"result": {
            "downloadInfo": {
                "quality": "preview",
                "codec": "mp3",
                "bitrate": 128,
                "transport": "raw",
                "urls": ["https://cdn.example.com/preview.mp3"]
            }
        }}"""
        val previewResponse = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.FileInfoResponseDto>(previewBody)
        val previewInfo = previewResponse.activeDownloadInfo
        org.junit.Assert.assertNotNull(previewInfo)
        assertTrue(previewInfo!!.isPreview)
        assertEquals("preview", previewInfo.quality)
    }
}

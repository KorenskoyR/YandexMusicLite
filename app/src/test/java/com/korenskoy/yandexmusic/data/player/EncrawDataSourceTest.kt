package com.korenskoy.yandexmusic.data.player

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import com.korenskoy.yandexmusic.data.network.EncrawDecryptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EncrawDataSourceTest {

    @Test
    fun transparentlyDecryptsEncryptedStreamWhenKeyInFragment() {
        val key = "000102030405060708090a0b0c0d0e0f"
        val plain = "fLaC test data stream payload for lossless playback 12345".toByteArray(Charsets.UTF_8)
        val encryptCipher = EncrawDecryptor.createCipher(key, Cipher.ENCRYPT_MODE)
        val encrypted = encryptCipher.doFinal(plain)

        val upstream = ByteArrayDataSource(encrypted)
        val encrawSource = EncrawDataSourceFactory.EncrawDataSource(upstream)

        val uriWithKey = Uri.parse("https://cdn.example.com/track.flac#encraw_key=$key")
        val dataSpec = DataSpec(uriWithKey)

        val openedLength = encrawSource.open(dataSpec)
        assertEquals(encrypted.size.toLong(), openedLength)

        val buffer = ByteArray(plain.size)
        val readBytes = encrawSource.read(buffer, 0, buffer.size)
        assertEquals(plain.size, readBytes)
        assertArrayEquals(plain, buffer)

        encrawSource.close()
    }

    @Test
    fun passesThroughUnencryptedStreamWhenNoKeyInFragment() {
        val plain = "plain mp3 audio data without encryption".toByteArray(Charsets.UTF_8)
        val upstream = ByteArrayDataSource(plain)
        val encrawSource = EncrawDataSourceFactory.EncrawDataSource(upstream)

        val regularUri = Uri.parse("https://cdn.example.com/track.mp3")
        val dataSpec = DataSpec(regularUri)

        val openedLength = encrawSource.open(dataSpec)
        assertEquals(plain.size.toLong(), openedLength)

        val buffer = ByteArray(plain.size)
        val readBytes = encrawSource.read(buffer, 0, buffer.size)
        assertEquals(plain.size, readBytes)
        assertArrayEquals(plain, buffer)

        encrawSource.close()
    }
}

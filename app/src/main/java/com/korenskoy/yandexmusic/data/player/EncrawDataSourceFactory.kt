package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.korenskoy.yandexmusic.data.network.EncrawDecryptor

/**
 * A Media3 DataSource.Factory that intercepts streams with `#encraw_key=<hexKey>` in their URI
 * and transparently decrypts the AES-CTR encrypted stream (used by Yandex Music FLAC encraw).
 * For all other streams (regular MP3/AAC, radio, local files), it acts as a normal DefaultDataSource.
 */
@UnstableApi
class EncrawDataSourceFactory(
    context: Context
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return EncrawDataSource(defaultFactory.createDataSource())
    }

    internal class EncrawDataSource(
        private val upstream: DataSource
    ) : DataSource {

        private var activeDataSource: DataSource = upstream

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            val fragment = uri.fragment
            if (fragment != null && fragment.startsWith("encraw_key=")) {
                val hexKey = fragment.substringAfter("encraw_key=")
                val keyBytes = EncrawDecryptor.hexToBytes(hexKey)
                val cleanUri = uri.buildUpon().fragment(null).build()
                val cleanDataSpec = dataSpec.buildUpon().setUri(cleanUri).build()
                val cipherSource = AesCipherDataSource(keyBytes, upstream)
                activeDataSource = cipherSource
                return cipherSource.open(cleanDataSpec)
            }
            activeDataSource = upstream
            return upstream.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeDataSource.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = activeDataSource.uri

        override fun getResponseHeaders(): Map<String, List<String>> = activeDataSource.responseHeaders

        override fun close() {
            try {
                activeDataSource.close()
            } finally {
                activeDataSource = upstream
            }
        }
    }
}

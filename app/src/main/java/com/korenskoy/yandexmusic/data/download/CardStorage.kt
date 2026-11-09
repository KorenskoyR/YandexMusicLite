package com.korenskoy.yandexmusic.data.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads on the removable SD card. A card can be read by any computer, and the tracks are music by
 * subscription, so on the card they are encrypted (AES-CTR, a random IV at the head of each file) with a
 * key that never leaves the phone: the key itself is kept wrapped by the Android Keystore. Only this app
 * can play them. The internal storage keeps plain files, as before.
 */
@Singleton
class CardStorage(
    private val context: Context,
    wantsCard: kotlinx.coroutines.flow.Flow<Boolean>,
    private val keyProvider: () -> ByteArray?,
    private val cardRootOverride: (() -> File?)? = null
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: SettingsRepository) :
        this(context, settings.downloadsOnSdCard, KeystoreKey(context)::key)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The user's choice; whether the card is there is checked separately each time. */
    val wanted: StateFlow<Boolean> = wantsCard.stateIn(scope, SharingStarted.Eagerly, false)

    /** The app's own folder on a mounted removable card; null without a card. */
    fun cardRoot(): File? {
        cardRootOverride?.let { return it() }
        return runCatching {
            context.getExternalFilesDirs(null).drop(1).firstOrNull { dir ->
                dir != null && Environment.isExternalStorageRemovable(dir) && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            }
        }.getOrNull()
    }

    fun cardTracksDir(): File? = cardRoot()?.let { File(it, "tracks").apply { mkdirs() } }?.takeIf { it.isDirectory }

    fun freeBytes(): Long = cardRoot()?.let { runCatching { StatFs(it.path).availableBytes }.getOrDefault(0L) } ?: 0L

    /** Null when this device cannot keep the key safely: then the card is not offered. */
    fun key(): ByteArray? = runCatching { keyProvider() }.onFailure { Log.w(TAG, "No key for the card", it) }.getOrNull()

    val cardUsable: Boolean
        get() = cardTracksDir() != null && key() != null

    companion object {
        const val TAG = "CardStorage"
        const val EXTENSION = "enc"
        private const val IV_BYTES = 16

        /** Wraps [out] so what is written lands encrypted; writes the IV first. */
        fun encrypting(key: ByteArray, out: OutputStream): OutputStream {
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            out.write(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
            return CipherOutputStream(out, cipher)
        }

        /** Reads an encrypted file from [position] (of the plain content) on. */
        fun decrypting(key: ByteArray, file: RandomAccessFile, position: Long): CipherReader {
            val iv = ByteArray(IV_BYTES)
            file.seek(0)
            file.readFully(iv)
            file.seek(IV_BYTES + position)
            // CTR: the counter for the block holding [position], then the bytes before it within the block are skipped
            val counter = BigInteger(1, iv).add(BigInteger.valueOf(position / 16)).toByteArray().takeLast(IV_BYTES).toByteArray()
            val ivAt = ByteArray(IV_BYTES - counter.size) + counter
            val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivAt)) }
            val skip = (position % 16).toInt()
            if (skip > 0) cipher.update(ByteArray(skip))
            return CipherReader(file, cipher)
        }

        fun plainLength(file: File): Long = (file.length() - IV_BYTES).coerceAtLeast(0L)

        /** The whole plain content, for moving a track back to the internal storage. */
        fun decryptingStream(key: ByteArray, file: File): InputStream {
            val raf = RandomAccessFile(file, "r")
            val reader = decrypting(key, raf, 0)
            return object : InputStream() {
                override fun read(): Int {
                    val one = ByteArray(1)
                    return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int = reader.read(b, off, len)
                override fun close() = raf.close()
            }
        }
    }

    class CipherReader(private val file: RandomAccessFile, private val cipher: Cipher) {
        private val buffer = ByteArray(16 * 1024)

        fun read(target: ByteArray, offset: Int, length: Int): Int {
            val read = file.read(buffer, 0, minOf(length, buffer.size))
            if (read <= 0) return -1
            return cipher.update(buffer, 0, read, target, offset)
        }
    }
}

/** The key for the card, wrapped by a Keystore key so it is useless off this phone. */
private class KeystoreKey(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("card_storage", Context.MODE_PRIVATE) }

    @Synchronized
    fun key(): ByteArray? {
        val wrapper = wrapper() ?: return null
        val saved = prefs.getString(KEY, null)
        if (saved != null) {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, wrapper, GCMParameterSpec(128, iv))
                doFinal(bytes, 12, bytes.size - 12)
            }
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapper) }
        val wrapped = cipher.iv + cipher.doFinal(fresh)
        prefs.edit(commit = true) { putString(KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)) }
        return fresh
    }

    private fun wrapper(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "yandex_music_lite_card"
        const val KEY = "wrapped_key"
    }
}

/** Plays an encrypted track from the card; everything else goes to [upstream] untouched. */
@OptIn(UnstableApi::class)
class CardAwareDataSourceFactory(private val upstream: DataSource.Factory, private val card: CardStorage) : DataSource.Factory {
    override fun createDataSource(): DataSource = Router(upstream.createDataSource(), card)

    private class Router(private val other: DataSource, private val card: CardStorage) : DataSource {
        private var active: DataSource? = null
        private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            listeners += transferListener
            other.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val path = dataSpec.uri.takeIf { it.scheme == "file" }?.path
            val source = if (path != null && path.endsWith(".${CardStorage.EXTENSION}")) {
                EncryptedFileDataSource(card).also { encrypted -> listeners.forEach(encrypted::addTransferListener) }
            } else other
            active = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
            }
        }
    }
}

@OptIn(UnstableApi::class)
internal class EncryptedFileDataSource(private val card: CardStorage) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var reader: CardStorage.CipherReader? = null
    private var uri: Uri? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val key = card.key() ?: throw DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
        val f = File(requireNotNull(dataSpec.uri.path))
        val length = CardStorage.plainLength(f)
        if (dataSpec.position > length) throw DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        val raf = try {
            RandomAccessFile(f, "r")
        } catch (e: java.io.IOException) {
            throw DataSourceException(e, androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        }
        file = raf
        reader = CardStorage.decrypting(key, raf, dataSpec.position)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, length - dataSpec.position) else length - dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val read = reader?.read(buffer, offset, minOf(length.toLong(), remaining).toInt()) ?: return C.RESULT_END_OF_INPUT
        if (read == -1) return C.RESULT_END_OF_INPUT
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } finally {
            file = null
            reader = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/** For tests: a card with a fixed key and folder. */
internal fun testCard(context: Context, root: File, key: ByteArray, wanted: Boolean = true) =
    CardStorage(context, flowOf(wanted), { key }, { root })

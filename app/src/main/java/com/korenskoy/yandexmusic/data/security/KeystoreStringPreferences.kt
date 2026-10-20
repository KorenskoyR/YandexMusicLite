package com.korenskoy.yandexmusic.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * String-only [SharedPreferences] whose values are encrypted with an AES-256-GCM key that lives in the
 * Android Keystore and never leaves it. Replaces the deprecated androidx.security EncryptedSharedPreferences.
 *
 * Keys are stored in clear (they are constant names, not secrets); values are `base64(iv || ciphertext)`.
 */
class KeystoreStringPreferences(
    context: Context,
    fileName: String,
    private val keyAlias: String
) : SharedPreferences {

    private val backing: SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    @Volatile
    private var secretKey: SecretKey = loadOrCreateKey()

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    internal fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        } catch (e: java.security.InvalidKeyException) {
            // The Keystore entry was removed or invalidated (restore, keystore reset): start over with a new
            // key. Values sealed with the old one were unreadable already.
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
            secretKey = loadOrCreateKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    internal fun decrypt(encoded: String): String? = try {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        // Tampered value or a key lost after restore: behave as if the value is absent
        null
    }

    override fun getString(key: String?, defValue: String?): String? =
        backing.getString(key, null)?.let { decrypt(it) } ?: defValue

    override fun contains(key: String?): Boolean = backing.contains(key)

    override fun getAll(): MutableMap<String, *> =
        backing.all.mapValues { (_, v) -> (v as? String)?.let { decrypt(it) } }.toMutableMap()

    override fun edit(): SharedPreferences.Editor = Editor(backing.edit())

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        backing.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        backing.unregisterOnSharedPreferenceChangeListener(listener)

    // Only strings are stored here; the other types are not needed and would be stored unencrypted.
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = unsupported()
    override fun getInt(key: String?, defValue: Int): Int = unsupported()
    override fun getLong(key: String?, defValue: Long): Long = unsupported()
    override fun getFloat(key: String?, defValue: Float): Float = unsupported()
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = unsupported()

    private inner class Editor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply {
            if (value == null) delegate.remove(key) else delegate.putString(key, encrypt(value))
        }
        override fun remove(key: String?) = apply { delegate.remove(key) }
        override fun clear() = apply { delegate.clear() }
        override fun commit(): Boolean = delegate.commit()
        override fun apply() = delegate.apply()
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = unsupported()
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = unsupported()
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = unsupported()
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = unsupported()
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = unsupported()
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("KeystoreStringPreferences stores strings only")

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

package com.korenskoy.yandexmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.repository.AuthRepositoryImpl
import com.korenskoy.yandexmusic.data.security.KeystoreStringPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Users updating from 1.4.1 have their accounts in the deprecated EncryptedSharedPreferences file.
 * The new Keystore storage must take them over without signing anyone out.
 * Note: wipes the app's auth storage — run on a test device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class AuthStorageMigrationInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun wipe() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) {
            context.deleteSharedPreferences(name)
        }
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("yandex_music_lite_auth")
    }

    @Suppress("DEPRECATION")
    @Test
    fun accountsAreMigratedFromEncryptedSharedPreferences() {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val legacy = androidx.security.crypto.EncryptedSharedPreferences.create(
            context, "auth_prefs", masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        legacy.edit().putString("yandex_token", "y0_legacy_token").commit()

        val repo = AuthRepositoryImpl(context)

        assertEquals("y0_legacy_token", repo.getToken())
        assertFalse("legacy file is removed after migration",
            File(context.applicationInfo.dataDir, "shared_prefs/auth_prefs.xml").exists())
        // A second start reads from the new storage only
        assertEquals("y0_legacy_token", AuthRepositoryImpl(context).getToken())
    }

    @Test
    fun savingWorksAfterTheKeystoreKeyIsLost() {
        val prefs = KeystoreStringPreferences(context, "auth_secure", "yandex_music_lite_auth")
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("yandex_music_lite_auth")

        // Used to throw KeyPermanentlyInvalidatedException, which broke signing in
        prefs.edit().putString("yandex_token", "y0_after_reset").commit()
        assertEquals("y0_after_reset", prefs.getString("yandex_token", null))
    }

    @Test
    fun valuesAreEncryptedAtRest() {
        val prefs = KeystoreStringPreferences(context, "auth_secure", "yandex_music_lite_auth")
        prefs.edit().putString("yandex_token", "y0_secret").commit()
        val raw = context.getSharedPreferences("auth_secure", android.content.Context.MODE_PRIVATE).getString("yandex_token", null)
        assertNotEquals("y0_secret", raw)
        assertFalse(raw!!.contains("y0_secret"))
        assertEquals("y0_secret", prefs.getString("yandex_token", null))
    }
}

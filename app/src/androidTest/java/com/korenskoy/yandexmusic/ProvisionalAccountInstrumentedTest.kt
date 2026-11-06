package com.korenskoy.yandexmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.repository.AuthRepositoryImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Right after sign-in an account has a provisional id, and the server refuses it in a playlist's address.
 * The real id replaces it, in the real encrypted storage. Note: wipes the app's auth storage.
 */
@RunWith(AndroidJUnit4::class)
class ProvisionalAccountInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun wipe() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
    }

    @Test
    fun theRealIdReplacesTheProvisionalOneForGood() {
        val auth = AuthRepositoryImpl(context)
        auth.saveToken("y0_instrumentation_fake_token")
        val provisional = auth.getActiveAccount()!!
        assertTrue(provisional.uid.startsWith("temp_"))
        assertFalse(provisional.hasRealUid)
        auth.setDefaultAccountUid(provisional.uid)

        auth.saveOrUpdateAccount(provisional.copy(uid = "414787002", login = "driver"))

        assertEquals("414787002", auth.getActiveAccount()?.uid)
        assertTrue(auth.getActiveAccount()!!.hasRealUid)
        assertEquals("one account, not two", listOf("414787002"), auth.getAllAccounts().map { it.uid })
        assertEquals("the default account follows", "414787002", auth.getDefaultAccountUid())

        val afterRestart = AuthRepositoryImpl(context)
        assertEquals("414787002", afterRestart.getActiveAccount()?.uid)
        assertEquals(listOf("414787002"), afterRestart.getAllAccounts().map { it.uid })
    }

    @Test
    fun anotherAccountIsLeftAlone() {
        val auth = AuthRepositoryImpl(context)
        auth.saveToken("y0_first")
        val first = auth.getActiveAccount()!!
        auth.saveOrUpdateAccount(first.copy(uid = "1", login = "first"))
        auth.saveToken("y0_second")
        val second = auth.getActiveAccount()!!

        auth.saveOrUpdateAccount(second.copy(uid = "2", login = "second"))

        assertEquals(setOf("1", "2"), auth.getAllAccounts().map { it.uid }.toSet())
        assertEquals("2", auth.getActiveAccount()?.uid)
    }
}

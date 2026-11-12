package com.korenskoy.yandexmusic.data.account

import com.korenskoy.yandexmusic.data.updater.SessionInstaller
import com.korenskoy.yandexmusic.domain.model.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Without Plus the app does not play — but only on the server's clear word, never for want of an answer. */
class PlusGateTest {

    private val account = UserAccount(uid = "1", login = "driver", token = "t")

    @Test
    fun onlyAClearNoBlocks() {
        assertFalse("never asked (an old account, no network yet): it plays", account.plusMissing)
        assertTrue("the server said: no Plus", account.copy(hasPlus = false, plusCheckedAt = 1L).plusMissing)
        assertFalse(account.copy(hasPlus = true, plusCheckedAt = 1L).plusMissing)
    }

    @Test
    fun anOldSavedAccountReadsAsNotAsked() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(UserAccount.serializer(), """{"uid":"1","login":"driver","token":"t","hasPlus":false}""")
        assertEquals(0L, old.plusCheckedAt)
        assertFalse("an account saved before 1.14.5 is not locked out on the update", old.plusMissing)
    }

    @Test
    fun theSavedUpdateIsNamedForItsVersion() {
        assertEquals("YandexMusicLite-1.14.5.apk", SessionInstaller.fileNameFor("1.14.5"))
        assertEquals("YandexMusicLite-1.14.5.apk", SessionInstaller.fileNameFor("v1.14.5"))
    }
}

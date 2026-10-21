package com.korenskoy.yandexmusic.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchHistoryRepositoryTest {

    private val anna = UserAccount(uid = "1", login = "anna", token = "t")
    private val boris = UserAccount(uid = "2", login = "boris", token = "t")
    private val auth = FakeAuth(anna)
    private val repository = SearchHistoryRepository(ApplicationProvider.getApplicationContext(), auth)

    @Test
    fun newestFirstWithoutRepeatsAndAtMostTen() = runBlocking {
        repository.clear()
        for (q in listOf("кино", "ария", "  Кино ", "a")) repository.add(q) // "a" is too short to be a search
        assertEquals(listOf("Кино", "ария"), repository.history.first())

        repeat(12) { repository.add("запрос $it") }
        val list = repository.history.first()
        assertEquals(10, list.size)
        assertEquals("запрос 11", list.first())

        repository.remove("запрос 11")
        assertEquals("запрос 10", repository.history.first().first())
    }

    @Test
    fun everyAccountHasItsOwnHistory() = runBlocking {
        repository.clear()
        repository.add("кино")
        auth.activeAccount.value = boris
        repository.clear()
        repository.add("ария")
        assertEquals(listOf("ария"), repository.history.first())
        auth.activeAccount.value = anna
        assertEquals(listOf("кино"), repository.history.first())
    }

    private class FakeAuth(account: UserAccount) : AuthRepository {
        override val activeAccount = MutableStateFlow<UserAccount?>(account)
        override val allAccounts = MutableStateFlow(listOf(account))
        override val defaultAccountUid = MutableStateFlow<String?>(null)
        override fun getActiveAccount() = activeAccount.value
        override fun getAllAccounts() = allAccounts.value
        override fun getToken() = activeAccount.value?.token
        override fun isAuthorized() = flowOf(true)
        override fun getDefaultAccountUid(): String? = null
        override fun saveToken(token: String) = Unit
        override fun clearToken() = Unit
        override fun saveOrUpdateAccount(account: UserAccount) = Unit
        override fun switchAccount(uid: String) = false
        override fun removeAccount(uid: String): UserAccount? = null
        override fun clearAllAccounts() = Unit
        override fun setDefaultAccountUid(uid: String?) = Unit
    }
}

package com.korenskoy.yandexmusic.domain.repository

import com.korenskoy.yandexmusic.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    fun getToken(): String?
    fun saveToken(token: String)
    fun isAuthorized(): Flow<Boolean>
    fun clearToken()

    fun getActiveAccount(): UserAccount?
    fun getAllAccounts(): List<UserAccount>
    fun saveOrUpdateAccount(account: UserAccount)
    fun switchAccount(uid: String): Boolean
    fun removeAccount(uid: String): UserAccount?
    fun clearAllAccounts()

    val activeAccount: StateFlow<UserAccount?>
    val allAccounts: StateFlow<List<UserAccount>>
}

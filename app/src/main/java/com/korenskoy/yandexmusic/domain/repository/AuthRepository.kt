package com.korenskoy.yandexmusic.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getToken(): String?
    fun saveToken(token: String)
    fun isAuthorized(): Flow<Boolean>
    fun clearToken()
}

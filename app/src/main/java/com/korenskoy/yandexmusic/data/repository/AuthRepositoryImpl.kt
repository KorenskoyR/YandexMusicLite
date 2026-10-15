package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    context: Context
) : AuthRepository {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback to plain SharedPreferences if Keystore is broken on this device
        context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _isAuthorized = MutableStateFlow(!getToken().isNullOrEmpty())

    override fun getToken(): String? {
        return sharedPreferences.getString("yandex_token", null)
    }

    override fun saveToken(token: String) {
        sharedPreferences.edit().putString("yandex_token", token).apply()
        _isAuthorized.value = true
    }

    override fun isAuthorized(): Flow<Boolean> = _isAuthorized.asStateFlow()

    override fun clearToken() {
        sharedPreferences.edit().remove("yandex_token").apply()
        _isAuthorized.value = false
    }
}

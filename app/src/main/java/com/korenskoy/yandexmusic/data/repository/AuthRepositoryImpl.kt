package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    context: Context
) : AuthRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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

    private val _allAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
    override val allAccounts: StateFlow<List<UserAccount>> = _allAccounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<UserAccount?>(null)
    override val activeAccount: StateFlow<UserAccount?> = _activeAccount.asStateFlow()

    private val _defaultAccountUid = MutableStateFlow<String?>(null)
    override val defaultAccountUid: StateFlow<String?> = _defaultAccountUid.asStateFlow()

    private val _isAuthorized = MutableStateFlow(false)
    override fun isAuthorized(): Flow<Boolean> = _isAuthorized.asStateFlow()

    init {
        loadAccountsFromStorage()
    }

    @Synchronized
    private fun loadAccountsFromStorage() {
        val accountsRaw = sharedPreferences.getString(KEY_ACCOUNTS_JSON, null)
        val loadedAccounts = if (!accountsRaw.isNullOrEmpty()) {
            try {
                json.decodeFromString<List<UserAccount>>(accountsRaw)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }.toMutableList()

        // Seamless migration: If legacy single token exists and no accounts recorded
        val legacyToken = sharedPreferences.getString(KEY_LEGACY_TOKEN, null)
        if (loadedAccounts.isEmpty() && !legacyToken.isNullOrEmpty()) {
            val migratedAccount = UserAccount(
                uid = "me",
                login = "",
                displayName = "Яндекс Аккаунт",
                token = legacyToken,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            loadedAccounts.add(migratedAccount)
            persistAccounts(loadedAccounts)
            sharedPreferences.edit().putString(KEY_ACTIVE_UID, "me").apply()
        }

        val defaultUid = sharedPreferences.getString(KEY_DEFAULT_ACCOUNT_UID, null)
        val activeUid = sharedPreferences.getString(KEY_ACTIVE_UID, null)

        // Prioritize explicit default UID if configured and valid, otherwise activeUid, otherwise last active
        val defaultAccount = loadedAccounts.firstOrNull { it.uid == defaultUid }
        val active = defaultAccount
            ?: (loadedAccounts.firstOrNull { it.uid == activeUid } ?: loadedAccounts.maxByOrNull { it.lastActiveTimestamp })

        _defaultAccountUid.value = defaultAccount?.uid
        _allAccounts.value = loadedAccounts.sortedByDescending { it.lastActiveTimestamp }
        _activeAccount.value = active
        _isAuthorized.value = active != null && active.token.isNotEmpty()
    }

    private fun persistAccounts(accounts: List<UserAccount>) {
        try {
            val raw = json.encodeToString(accounts)
            sharedPreferences.edit().putString(KEY_ACCOUNTS_JSON, raw).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getToken(): String? {
        return _activeAccount.value?.token ?: sharedPreferences.getString(KEY_LEGACY_TOKEN, null)
    }

    @Synchronized
    override fun saveToken(token: String) {
        if (token.isBlank()) return
        sharedPreferences.edit().putString(KEY_LEGACY_TOKEN, token).apply()

        // Check if an existing account already has this token
        val currentAccounts = _allAccounts.value.toMutableList()
        val existingIndex = currentAccounts.indexOfFirst { it.token == token }
        
        val active: UserAccount
        if (existingIndex >= 0) {
            val updated = currentAccounts[existingIndex].copy(lastActiveTimestamp = System.currentTimeMillis())
            currentAccounts[existingIndex] = updated
            active = updated
        } else {
            // Create a provisional account until getAccountStatus() resolves the real UID and Name
            val tempUid = "temp_${System.currentTimeMillis()}"
            val newAccount = UserAccount(
                uid = tempUid,
                login = "",
                displayName = "Яндекс Аккаунт",
                token = token,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            currentAccounts.add(newAccount)
            active = newAccount
        }

        sharedPreferences.edit().putString(KEY_ACTIVE_UID, active.uid).apply()
        persistAccounts(currentAccounts)

        _allAccounts.value = currentAccounts.sortedByDescending { it.lastActiveTimestamp }
        _activeAccount.value = active
        _isAuthorized.value = true
    }

    override fun getActiveAccount(): UserAccount? {
        return _activeAccount.value
    }

    override fun getAllAccounts(): List<UserAccount> {
        return _allAccounts.value
    }

    @Synchronized
    override fun saveOrUpdateAccount(account: UserAccount) {
        if (account.token.isBlank()) return
        val currentAccounts = _allAccounts.value.toMutableList()

        // Remove any temporary provisional account that carried the same token
        currentAccounts.removeAll { it.token == account.token && it.uid != account.uid && it.uid.startsWith("temp_") }

        val existingIndex = currentAccounts.indexOfFirst { it.uid == account.uid }
        val updated = if (existingIndex >= 0) {
            currentAccounts[existingIndex].copy(
                login = account.login.ifBlank { currentAccounts[existingIndex].login },
                displayName = account.displayName.ifBlank { currentAccounts[existingIndex].displayName },
                token = account.token.ifBlank { currentAccounts[existingIndex].token },
                avatarUrl = account.avatarUrl ?: currentAccounts[existingIndex].avatarUrl,
                lastActiveTimestamp = System.currentTimeMillis()
            )
        } else {
            account.copy(lastActiveTimestamp = System.currentTimeMillis())
        }

        if (existingIndex >= 0) {
            currentAccounts[existingIndex] = updated
        } else {
            currentAccounts.add(updated)
        }

        sharedPreferences.edit()
            .putString(KEY_ACTIVE_UID, updated.uid)
            .putString(KEY_LEGACY_TOKEN, updated.token)
            .apply()
        persistAccounts(currentAccounts)

        _allAccounts.value = currentAccounts.sortedByDescending { it.lastActiveTimestamp }
        _activeAccount.value = updated
        _isAuthorized.value = true
    }

    @Synchronized
    override fun switchAccount(uid: String): Boolean {
        val currentAccounts = _allAccounts.value.toMutableList()
        val targetIndex = currentAccounts.indexOfFirst { it.uid == uid }
        if (targetIndex < 0) return false

        val target = currentAccounts[targetIndex].copy(lastActiveTimestamp = System.currentTimeMillis())
        currentAccounts[targetIndex] = target

        sharedPreferences.edit()
            .putString(KEY_ACTIVE_UID, target.uid)
            .putString(KEY_LEGACY_TOKEN, target.token)
            .apply()
        persistAccounts(currentAccounts)

        _allAccounts.value = currentAccounts.sortedByDescending { it.lastActiveTimestamp }
        _activeAccount.value = target
        _isAuthorized.value = target.token.isNotEmpty()
        return true
    }

    @Synchronized
    override fun removeAccount(uid: String): UserAccount? {
        val currentAccounts = _allAccounts.value.toMutableList()
        val index = currentAccounts.indexOfFirst { it.uid == uid }
        if (index < 0) return null

        val removed = currentAccounts.removeAt(index)
        var newActive: UserAccount? = _activeAccount.value

        if (newActive?.uid == uid) {
            newActive = currentAccounts.maxByOrNull { it.lastActiveTimestamp }
            sharedPreferences.edit().apply {
                if (newActive != null) {
                    putString(KEY_ACTIVE_UID, newActive.uid)
                    putString(KEY_LEGACY_TOKEN, newActive.token)
                } else {
                    remove(KEY_ACTIVE_UID)
                    remove(KEY_LEGACY_TOKEN)
                }
            }.apply()
        }

        if (_defaultAccountUid.value == uid) {
            setDefaultAccountUid(null)
        }

        persistAccounts(currentAccounts)
        _allAccounts.value = currentAccounts.sortedByDescending { it.lastActiveTimestamp }
        _activeAccount.value = newActive
        _isAuthorized.value = newActive != null && newActive.token.isNotEmpty()
        return removed
    }

    @Synchronized
    override fun clearAllAccounts() {
        sharedPreferences.edit()
            .remove(KEY_ACCOUNTS_JSON)
            .remove(KEY_ACTIVE_UID)
            .remove(KEY_DEFAULT_ACCOUNT_UID)
            .remove(KEY_LEGACY_TOKEN)
            .apply()

        _defaultAccountUid.value = null
        _allAccounts.value = emptyList()
        _activeAccount.value = null
        _isAuthorized.value = false
    }

    override fun getDefaultAccountUid(): String? {
        return sharedPreferences.getString(KEY_DEFAULT_ACCOUNT_UID, null)
    }

    @Synchronized
    override fun setDefaultAccountUid(uid: String?) {
        sharedPreferences.edit().apply {
            if (uid != null) {
                putString(KEY_DEFAULT_ACCOUNT_UID, uid)
            } else {
                remove(KEY_DEFAULT_ACCOUNT_UID)
            }
        }.apply()
        _defaultAccountUid.value = uid
    }

    override fun clearToken() {
        val activeUid = _activeAccount.value?.uid
        if (activeUid != null) {
            removeAccount(activeUid)
        } else {
            clearAllAccounts()
        }
    }

    companion object {
        private const val KEY_LEGACY_TOKEN = "yandex_token"
        private const val KEY_ACCOUNTS_JSON = "accounts_list_json"
        private const val KEY_ACTIVE_UID = "active_account_uid"
        private const val KEY_DEFAULT_ACCOUNT_UID = "default_account_uid"
    }
}

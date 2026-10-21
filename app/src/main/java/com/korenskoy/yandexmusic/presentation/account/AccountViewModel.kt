package com.korenskoy.yandexmusic.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.account.AccountSessionManager
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Saved accounts: who is signed in, switching, signing out, the account opened at launch. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val session: AccountSessionManager
) : ViewModel() {

    val allAccounts: StateFlow<List<UserAccount>> = authRepository.allAccounts
    val activeAccount: StateFlow<UserAccount?> = authRepository.activeAccount
    val defaultAccountUid: StateFlow<String?> = authRepository.defaultAccountUid

    val accountName: StateFlow<String> = authRepository.activeAccount
        .map { it?.displayLabel.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.getActiveAccount()?.displayLabel.orEmpty())

    val accountAvatarUrl: StateFlow<String?> = authRepository.activeAccount
        .map { it?.avatarUrl }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val defaultAccountName: StateFlow<String?> = combine(
        authRepository.defaultAccountUid,
        authRepository.allAccounts
    ) { defUid, accounts ->
        accounts.firstOrNull { it.uid == defUid }?.displayLabel
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setDefaultAccount(uid: String?) = authRepository.setDefaultAccountUid(uid)

    fun switchAccount(uid: String) {
        session.launch { switchAccount(uid) }
    }

    fun logoutCurrentAccount() {
        session.launch { logoutCurrent() }
    }

    fun logoutAllAccounts() {
        session.launch { logoutAll() }
    }

    fun removeAccount(uid: String) {
        session.launch { removeAccount(uid) }
    }

    private val UserAccount.displayLabel: String
        get() = displayName.ifBlank { login }.ifBlank { "UID: $uid" }
}

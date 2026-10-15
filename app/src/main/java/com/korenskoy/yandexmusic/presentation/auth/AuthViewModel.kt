package com.korenskoy.yandexmusic.presentation.auth

import androidx.lifecycle.ViewModel
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _isNetworkAvailable = MutableStateFlow(networkMonitor.isCurrentlyConnected())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isConnectedFlow.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }
    }

    fun saveToken(token: String) {
        authRepository.saveToken(token)
    }
}

package com.korenskoy.yandexmusic.presentation.auth

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.auth.DeviceSignIn
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the sign-in by a code on the phone stands. */
sealed interface PhoneSignInState {
    data object GettingCode : PhoneSignInState
    /** [expiresAt] is on the [SystemClock.elapsedRealtime] clock, so a changed system time cannot break it. */
    data class Waiting(val code: DeviceSignIn.Code, val expiresAt: Long, val offline: Boolean = false) : PhoneSignInState
    data object Expired : PhoneSignInState
    data object Denied : PhoneSignInState
    /** The code could not even be asked for (no network to Yandex): retried on "Новый код" or when the network returns. */
    data object NoConnection : PhoneSignInState
    data object Misconfigured : PhoneSignInState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val deviceSignIn: DeviceSignIn
) : ViewModel() {

    private val _isNetworkAvailable = MutableStateFlow(networkMonitor.isCurrentlyConnected())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    /** Whether the sign-in by code can work in this build (it needs a secret only the release build has). */
    val phoneSignInAvailable: Boolean get() = deviceSignIn.available

    private val _phone = MutableStateFlow<PhoneSignInState>(PhoneSignInState.GettingCode)
    val phone: StateFlow<PhoneSignInState> = _phone.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private var phoneJob: Job? = null

    init {
        viewModelScope.launch {
            networkMonitor.isConnectedFlow.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
                // The code could not be fetched without the network: fetch it as soon as the network is back
                if (isConnected && phoneJob != null && _phone.value == PhoneSignInState.NoConnection) newPhoneCode()
            }
        }
    }

    fun saveToken(token: String) {
        authRepository.saveToken(token)
    }

    /** Starts the sign-in by code once; the screen calls it whenever it is shown. */
    fun startPhoneSignIn() {
        if (phoneJob == null) newPhoneCode()
    }

    fun newPhoneCode() {
        phoneJob?.cancel()
        phoneJob = viewModelScope.launch { runPhoneSignIn() }
    }

    private suspend fun runPhoneSignIn() {
        _phone.value = PhoneSignInState.GettingCode
        val code = try {
            deviceSignIn.requestCode()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _phone.value = PhoneSignInState.NoConnection
            return
        }
        val expiresAt = SystemClock.elapsedRealtime() + code.expiresInSeconds * 1000L
        var interval = code.intervalSeconds
        _phone.value = PhoneSignInState.Waiting(code, expiresAt)
        while (SystemClock.elapsedRealtime() < expiresAt) {
            delay(interval * 1000L)
            val answer = try {
                deviceSignIn.poll(code, interval)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A lost connection on the way is not the end: the code is still good, keep asking
                _phone.value = PhoneSignInState.Waiting(code, expiresAt, offline = true)
                continue
            }
            when (answer) {
                is DeviceSignIn.Poll.Wait -> {
                    interval = answer.delaySeconds
                    _phone.value = PhoneSignInState.Waiting(code, expiresAt)
                }
                is DeviceSignIn.Poll.Token -> {
                    saveToken(answer.token)
                    _signedIn.value = true
                    return
                }
                DeviceSignIn.Poll.Expired -> {
                    _phone.value = PhoneSignInState.Expired
                    return
                }
                DeviceSignIn.Poll.Denied -> {
                    _phone.value = PhoneSignInState.Denied
                    return
                }
                DeviceSignIn.Poll.Misconfigured -> {
                    _phone.value = PhoneSignInState.Misconfigured
                    return
                }
            }
        }
        _phone.value = PhoneSignInState.Expired
    }
}

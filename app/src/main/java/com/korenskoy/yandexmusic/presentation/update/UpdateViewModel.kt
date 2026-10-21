package com.korenskoy.yandexmusic.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.updater.ApkInstallPreCheck
import com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager
import com.korenskoy.yandexmusic.data.updater.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** In-app updates from GitHub releases: check, download (with the SHA-256 check) and install. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: GitHubUpdateManager
) : ViewModel() {

    private val _checkResult = MutableStateFlow<UpdateCheckResult?>(null)
    val checkResult: StateFlow<UpdateCheckResult?> = _checkResult.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1f)
    /** -1 idle, 0..1 downloading, 2 downloaded and ready to install. */
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSignatureMismatch = MutableStateFlow(false)
    val isSignatureMismatch: StateFlow<Boolean> = _isSignatureMismatch.asStateFlow()

    private var downloadedApkFile: java.io.File? = null

    init {
        // Silent check on launch, after a delay for network readiness
        viewModelScope.launch {
            delay(5000L)
            checkForUpdates(silent = true)
        }
        // Leftover update files from an earlier session
        viewModelScope.launch(Dispatchers.IO) { updateManager.cleanupUpdateFiles() }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _statusMessage.value = "Проверка обновлений..."
            }
            val result = updateManager.checkForUpdates()
            if (!silent || result is UpdateCheckResult.UpdateAvailable) {
                _checkResult.value = result
            }
            if (result is UpdateCheckResult.UpToDate && !silent) {
                _statusMessage.value = "У вас установлена актуальная версия"
            }
        }
    }

    fun startDownload(apkUrl: String) {
        viewModelScope.launch {
            _downloadProgress.value = 0f
            _statusMessage.value = null
            _isSignatureMismatch.value = false
            val expectedSha256 = (_checkResult.value as? UpdateCheckResult.UpdateAvailable)
                ?.takeIf { it.apkDownloadUrl == apkUrl }
                ?.apkSha256
            val result = updateManager.downloadApk(apkUrl, expectedSha256) { progress ->
                _downloadProgress.value = progress
            }
            result.onSuccess { file ->
                downloadedApkFile = file
                _downloadProgress.value = 2f
                installDownloaded()
            }.onFailure { err ->
                _downloadProgress.value = -1f
                _statusMessage.value = "Ошибка загрузки: ${err.localizedMessage}"
            }
        }
    }

    fun installDownloaded(forceSystemInstall: Boolean = false) {
        val file = downloadedApkFile ?: return

        // A broken, foreign or older package is never installed; forceSystemInstall only lets the
        // system installer handle a signing-key change (it will ask the user to reinstall)
        val preCheck = updateManager.checkApkCompatibility(file)
        if (preCheck is ApkInstallPreCheck.CorruptedApk) {
            _statusMessage.value = "Ошибка пакета: ${preCheck.reason}"
            return
        }
        if (!forceSystemInstall && preCheck is ApkInstallPreCheck.SignatureMismatch) {
            _isSignatureMismatch.value = true
            _statusMessage.value = "Обновление готово! Требуется разовая чистая переустановка из-за смены ключа подписи на официальный."
            return
        }

        updateManager.installApk(file).onFailure { err ->
            _statusMessage.value = err.localizedMessage
        }
    }

    fun shareOrSaveDownloadedApk() {
        downloadedApkFile?.let { updateManager.shareOrSaveApk(it) }
    }

    val canRequestPackageInstalls: Boolean
        get() = updateManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        updateManager.openInstallPermissionSettings()
    }

    fun dismiss() {
        _checkResult.value = null
        _downloadProgress.value = -1f
        _statusMessage.value = null
        _isSignatureMismatch.value = false
    }
}

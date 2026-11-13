package com.korenskoy.yandexmusic.presentation.update

import com.korenskoy.yandexmusic.data.updater.InstallGate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.core.content.edit
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.updater.InstallState
import com.korenskoy.yandexmusic.data.updater.SessionInstaller
import android.content.Context
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import com.korenskoy.yandexmusic.R
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
    private val updateManager: GitHubUpdateManager,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context,
    private val installer: SessionInstaller,
    private val playbackController: PlaybackController
) : ViewModel() {

    /** What Android's installer said about the update being installed; null while nothing is installed. */
    val installState: StateFlow<InstallState?> = installer.state

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** An earlier try at installing this very version did not finish: the file is offered at once. */
    fun failedBefore(versionName: String): Boolean = prefs.getString(FAILED_VERSION, null) == versionName

    private var watchdog: Job? = null

    private val _checkResult = MutableStateFlow<UpdateCheckResult?>(null)
    val checkResult: StateFlow<UpdateCheckResult?> = _checkResult.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1f)
    /** -1 idle, 0..1 downloading, 2 downloaded and ready to install. */
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSignatureMismatch = MutableStateFlow(false)
    val isSignatureMismatch: StateFlow<Boolean> = _isSignatureMismatch.asStateFlow()

    private var downloadedApkFile: File? = null

    init {
        // Silent check on launch, after a delay for network readiness
        viewModelScope.launch {
            delay(5000L)
            checkForUpdates(silent = true)
        }
        // Leftover update files and install sessions from an earlier run
        viewModelScope.launch(Dispatchers.IO) {
            updateManager.cleanupUpdateFiles()
            if (!installer.busy) installer.abandonLeftovers()
        }
        // A refused install is remembered too: next time the file is offered at once
        installer.state.onEach { state ->
            val version = (_checkResult.value as? UpdateCheckResult.UpdateAvailable)?.versionName
            if (state is InstallState.Failed && version != null) prefs.edit { putString(FAILED_VERSION, version) }
        }.launchIn(viewModelScope)
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _statusMessage.value = context.getString(R.string.update_status_checking)
            }
            val result = updateManager.checkForUpdates()
            val skipped = settingsRepository.skippedUpdateVersion.first()
            val available = result as? UpdateCheckResult.UpdateAvailable
            val status = UpdatePolicy.statusAfterCheck(
                available = available?.versionName,
                upToDate = result is UpdateCheckResult.UpToDate,
                error = (result as? UpdateCheckResult.Error)?.message,
                skipped = skipped,
                silent = silent
            )
            // The launch check keeps quiet about a skipped version; Settings say it is there
            if (status !is UpdatePolicy.Status.Skipped && (!silent || available != null)) _checkResult.value = result
            when (status) {
                UpdatePolicy.Status.Keep -> Unit
                UpdatePolicy.Status.Clear -> _statusMessage.value = null
                UpdatePolicy.Status.Latest -> _statusMessage.value = context.getString(R.string.update_status_latest)
                is UpdatePolicy.Status.Skipped -> _statusMessage.value = context.getString(R.string.update_status_skipped, status.version)
                is UpdatePolicy.Status.Failed -> _statusMessage.value =
                    // "Нет связи" already says it could not check
                    status.reason.takeIf { it == context.getString(R.string.update_err_network) }
                        ?: context.getString(R.string.update_status_check_failed, status.reason)
            }
        }
    }

    private var downloadJob: Job? = null

    fun startDownload(apkUrl: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
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
                _statusMessage.value = context.getString(R.string.update_status_download_failed, err.localizedMessage.orEmpty())
            }
        }
    }

    fun installDownloaded(forceSystemInstall: Boolean = false) {
        val file = downloadedApkFile ?: return
        // One update, one install: a second press or event while one is under way starts nothing
        if (installer.busy) return

        // A broken, foreign or older package is never installed; forceSystemInstall only lets the
        // system installer handle a signing-key change (it will ask the user to reinstall)
        val preCheck = updateManager.checkApkCompatibility(file)
        if (preCheck is ApkInstallPreCheck.CorruptedApk) {
            _statusMessage.value = context.getString(R.string.update_status_package_failed, preCheck.reason)
            return
        }
        if (!forceSystemInstall && preCheck is ApkInstallPreCheck.SignatureMismatch) {
            _isSignatureMismatch.value = true
            _statusMessage.value = context.getString(R.string.update_status_reinstall)
            return
        }

        // A signing-key change needs the system's own "reinstall" path; otherwise the session, which answers back
        if (preCheck is ApkInstallPreCheck.SignatureMismatch) {
            updateManager.installApk(file).onFailure { err -> _statusMessage.value = err.localizedMessage }
            return
        }
        if (!updateManager.canRequestPackageInstalls()) {
            openInstallPermissionSettings()
            _statusMessage.value = context.getString(R.string.update_err_allow_install)
            return
        }
        // Nothing playing holds the app while Android replaces it (a head unit's media source may restart it)
        playbackController.pause()
        playbackController.persistNow()
        _statusMessage.value = null
        viewModelScope.launch {
            installer.install(file)
                .onSuccess {
                    // No confirmation asked within a moment: it installs by itself, so the clock starts now
                    delay(CONFIRM_GRACE_MS)
                    if (installer.state.value == InstallState.Started) startWatchdog()
                }
                .onFailure {
                    // A unit that refuses sessions: the system installer the old way
                    updateManager.installApk(file).onFailure { err -> _statusMessage.value = err.localizedMessage }
                }
        }
    }

    /**
     * The app is back on the screen after the system's "install?" window: if it was confirmed, the install runs now,
     * and [NO_ANSWER_MS] later without an answer it has hung (the app is replaced on success, so no answer means
     * it did not finish).
     */
    fun onAppResumed() {
        if (installer.state.value == InstallState.Confirming) startWatchdog()
        // Back from the permission screen the app sent the user to: the install goes on, once
        if (InstallGate.installOnReturn(awaitingPermission, _downloadProgress.value >= 2f, updateManager.canRequestPackageInstalls(), installer.busy)) {
            awaitingPermission = false
            installDownloaded()
        }
    }

    /** The app sent the user to "allow installs from this source": only that return continues the install. */
    private var awaitingPermission = false

    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        val version = (_checkResult.value as? UpdateCheckResult.UpdateAvailable)?.versionName
        watchdog = viewModelScope.launch {
            delay(NO_ANSWER_MS)
            installer.noAnswer()
            if (installer.state.value == InstallState.NoAnswer && version != null) prefs.edit { putString(FAILED_VERSION, version) }
        }
    }

    /** «Ещё раз» after a failed install. */
    fun retryInstall() {
        watchdog?.cancel()
        installer.reset()
        installDownloaded()
    }

    /** «Сохранить файл»: into «Загрузки» (Android 10+), else the share window (a file manager, «Сохранить»). */
    fun saveDownloadedApk() {
        val file = downloadedApkFile ?: return
        val version = (_checkResult.value as? UpdateCheckResult.UpdateAvailable)?.versionName ?: return
        viewModelScope.launch {
            val saved = installer.saveToDownloads(file, version)
            if (saved != null) {
                _statusMessage.value = context.getString(R.string.update_saved, SessionInstaller.fileNameFor(version))
            } else {
                updateManager.shareOrSaveApk(file)
            }
        }
    }

    fun shareOrSaveDownloadedApk() {
        downloadedApkFile?.let { updateManager.shareOrSaveApk(it) }
    }

    val canRequestPackageInstalls: Boolean
        get() = updateManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        awaitingPermission = true
        updateManager.openInstallPermissionSettings()
    }

    /** "Пропустить эту версию": no more offers of it at launch; a newer version is offered again. */
    fun skip() {
        val version = (_checkResult.value as? UpdateCheckResult.UpdateAvailable)?.versionName ?: return
        viewModelScope.launch { settingsRepository.setSkippedUpdateVersion(version) }
        dismiss()
        _statusMessage.value = context.getString(R.string.update_status_skipped, version)
    }

    fun dismiss() {
        // Closing the window stops a download in flight: it used to run on to the end unseen
        awaitingPermission = false
        watchdog?.cancel()
        installer.reset()
        downloadJob?.cancel()
        downloadJob = null
        _checkResult.value = null
        _downloadProgress.value = -1f
        _statusMessage.value = null
        _isSignatureMismatch.value = false
    }

    private companion object {
        const val PREFS = "update_install"
        const val FAILED_VERSION = "failed_version"

        /** Without a confirmation window within this, the installer is working on its own. */
        const val CONFIRM_GRACE_MS = 5_000L

        /** An install that has not finished this long after it was confirmed has hung. */
        const val NO_ANSWER_MS = 120_000L
    }
}

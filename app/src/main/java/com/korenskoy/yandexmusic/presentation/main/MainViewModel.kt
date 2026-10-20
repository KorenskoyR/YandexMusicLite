package com.korenskoy.yandexmusic.presentation.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val playbackController: PlaybackController,
    val settingsRepository: SettingsRepository,
    private val downloadManager: TrackDownloadManager,
    private val cacheManager: com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager,
    private val radioRepository: RadioRepository,
    private val libraryRepository: com.korenskoy.yandexmusic.data.repository.LibraryRepository,
    private val downloadScheduler: com.korenskoy.yandexmusic.data.download.DownloadScheduler,
    private val updateManager: com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager
) : ViewModel() {

    val isAuthorized = authRepository.isAuthorized()
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.getToken() != null)

    val allAccounts: StateFlow<List<com.korenskoy.yandexmusic.domain.model.UserAccount>> = authRepository.allAccounts
    val activeAccount: StateFlow<com.korenskoy.yandexmusic.domain.model.UserAccount?> = authRepository.activeAccount
    val defaultAccountUid: StateFlow<String?> = authRepository.defaultAccountUid

    val defaultAccountName: StateFlow<String?> = combine(
        authRepository.defaultAccountUid,
        authRepository.allAccounts
    ) { defUid, accounts ->
        if (defUid == null) return@combine null
        val account = accounts.firstOrNull { it.uid == defUid }
        account?.displayName?.ifBlank { account.login }?.ifBlank { "UID: ${account.uid}" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setDefaultAccount(uid: String?) {
        authRepository.setDefaultAccountUid(uid)
    }

    private val activeUserFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    private val _accountName = MutableStateFlow<String>("")
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    val accountAvatarUrl: StateFlow<String?> = authRepository.activeAccount
        .map { it?.avatarUrl }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentSource: StateFlow<PlaylistSource> = playbackController.currentSource

    // Library and charts live in the process-wide LibraryRepository (single-flight, incremental sync)
    val tracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.likedTracks
    val likedTrackIds: StateFlow<Set<String>> = libraryRepository.likedIds
    val weeklyHits: StateFlow<List<TrackDetailsDto>> = libraryRepository.weeklyHits
    val trendTracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.trendTracks
    val popularTracks: StateFlow<List<TrackDetailsDto>> = libraryRepository.popularTracks
    val isLoading: StateFlow<Boolean> = libraryRepository.isSyncingLikes

    val isDownloadingAll: StateFlow<Boolean> = downloadScheduler.isDownloadingAll
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val downloadedTrackIds: StateFlow<Set<String>> = downloadManager.downloadedTrackIds
    
    val cacheSizeBytes: StateFlow<Long> = downloadManager.cacheSizeBytes

    val autoDownloadEnabled: StateFlow<Boolean> = settingsRepository.autoDownloadLikedTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val downloadProgress = downloadManager.downloadProgress

    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = playbackController.currentPlaylist
    val currentTrack: StateFlow<TrackDetailsDto?> = playbackController.currentTrack
    val currentTrackId: StateFlow<String?> = playbackController.currentTrack
        .map { it?.idString }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playbackController.currentTrack.value?.idString)
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying
    val isBuffering: StateFlow<Boolean> = playbackController.isBuffering
    val progress: StateFlow<Long> = playbackController.progress

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val searchResults: StateFlow<List<TrackDetailsDto>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val radioStations = MutableStateFlow(radioRepository.getBuiltinStations()).asStateFlow()
    
    val radioFavoriteStationIds: StateFlow<Set<String>> = radioRepository.favoriteStationIds
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _updateCheckResult = MutableStateFlow<com.korenskoy.yandexmusic.data.updater.UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<com.korenskoy.yandexmusic.data.updater.UpdateCheckResult?> = _updateCheckResult.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(-1f)
    val updateDownloadProgress: StateFlow<Float> = _updateDownloadProgress.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _isSignatureMismatch = MutableStateFlow(false)
    val isSignatureMismatch: StateFlow<Boolean> = _isSignatureMismatch.asStateFlow()

    private var downloadedApkFile: java.io.File? = null

    val isFetchingMoreQueue: StateFlow<Boolean> = playbackController.isFetchingMoreQueue
    val streamQuality: StateFlow<String?> = playbackController.streamQuality
    val sleepTimer: StateFlow<com.korenskoy.yandexmusic.data.player.SleepTimerState?> = playbackController.sleepTimer

    fun setSleepTimer(minutes: Int) = playbackController.setSleepTimer(minutes)
    fun setSleepAtEndOfTrack() = playbackController.setSleepAtEndOfTrack()
    fun cancelSleepTimer() = playbackController.cancelSleepTimer()
    val playbackMessages: kotlinx.coroutines.flow.SharedFlow<String> = playbackController.messages

    init {
        // The controller outlives this ViewModel; give it the library for "play liked" fallbacks.
        tracks.onEach { playbackController.updateLibrary(it) }.launchIn(viewModelScope)

        // Keep display name synchronized with active account
        viewModelScope.launch {
            authRepository.activeAccount.collect { account ->
                if (account != null) {
                    val name = account.displayName.ifBlank { account.login }.ifBlank { "UID: ${account.uid}" }
                    _accountName.value = name
                }
            }
        }

        // Background profile & avatar refresh if missing
        viewModelScope.launch(Dispatchers.IO) {
            val token = authRepository.getToken()
            if (!token.isNullOrEmpty()) {
                val active = authRepository.getActiveAccount()
                if (active != null && (active.avatarUrl.isNullOrEmpty() || active.displayName.isBlank() || active.displayName.startsWith("UID:"))) {
                    try {
                        val status = api.getAccountStatus().result
                        if (status != null) {
                            var avatar = status.avatarUrl
                            if (avatar == null) {
                                try {
                                    avatar = api.getYandexLoginInfo().avatarUrl
                                } catch (_: Exception) {}
                            }
                            val bestName = status.displayUserName
                            authRepository.saveOrUpdateAccount(
                                active.copy(
                                    displayName = bestName,
                                    login = status.account?.login?.ifBlank { active.login } ?: active.login,
                                    avatarUrl = avatar ?: active.avatarUrl
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.d("MainViewModel", "Initial silent avatar refresh: ${e.message}")
                    }
                }
            }
        }

        // Cached library instantly, then exactly one network sync per authorization
        viewModelScope.launch {
            libraryRepository.loadCached()
            authRepository.isAuthorized().collect { isAuth ->
                if (isAuth) syncLibrary()
            }
        }

        // Silent update check on launch after 5-second delay for network readiness
        viewModelScope.launch {
            delay(5000L)
            checkForUpdates(silent = true)
        }

        // Clean up any remaining update files in cache asynchronously on launch
        viewModelScope.launch(Dispatchers.IO) {
            updateManager.cleanupUpdateFiles()
        }
    }
    
    /** Likes, charts and the My Wave queue. Concurrent calls join the requests already running. */
    private fun syncLibrary() {
        viewModelScope.launch {
            val likesChanged = libraryRepository.syncLikes()
            if (likesChanged && settingsRepository.autoDownloadLikedTracks.first()) downloadAllLikedTracks()
        }
        viewModelScope.launch { libraryRepository.refreshCharts() }
        playbackController.primeWave()
    }

    fun clearMusicCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val userFolder = activeUserFolder
            val activeUid = authRepository.getActiveAccount()?.uid

            // 1. Clear active user's isolated playlist and recent cache
            cacheManager.clearUserCache(userFolder)

            // 2. Protect tracks of other saved accounts
            val otherAccounts = authRepository.getAllAccounts().filter { it.uid != activeUid }
            val protectedTrackIds = mutableSetOf<String>()
            for (other in otherAccounts) {
                val otherTracks = cacheManager.loadLikedTracks(other.folderName)
                protectedTrackIds.addAll(otherTracks.map { it.idString })
            }

            // 3. Remove downloaded tracks belonging to active user only
            val activeTrackIds = tracks.value.map { it.idString }
            for (trackId in activeTrackIds) {
                if (!protectedTrackIds.contains(trackId)) {
                    downloadManager.removeTrack(trackId)
                }
            }

            withContext(Dispatchers.Main) {
                libraryRepository.resetForAccountChange()
                playbackController.clearWaveAndHistory()
                loadLikedTracks()
            }
        }
    }

    fun setKeepScreenOnInCarMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOnInCarMode(enabled)
        }
    }

    fun setAutoStartCarModeVibe(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartCarModeVibe(enabled)
        }
    }

    val crossfadeSeconds: StateFlow<Int> = settingsRepository.crossfadeSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_CROSSFADE_SECONDS)

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setCrossfadeSeconds(seconds)
        }
    }

    fun setAutoDownloadLikedTracks(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadLikedTracks(enabled)
        }
    }

    fun stopAndClearPlayer() {
        playbackController.stopAndClear()
    }

    fun logout() {
        logoutCurrentAccount()
    }

    fun switchAccount(uid: String) {
        viewModelScope.launch {
            stopAndClearPlayer()

            val switched = authRepository.switchAccount(uid)
            if (!switched) return@launch

            val newAccount = authRepository.getActiveAccount()
            val userFolder = newAccount?.folderName ?: "default"
            _accountName.value = newAccount?.displayName?.ifBlank { newAccount.login } ?: "Яндекс Аккаунт"
            // Wave queue, listening history and likes are per user
            playbackController.resetForAccount(userFolder)
            libraryRepository.resetForAccountChange()
            libraryRepository.loadCached()
            syncLibrary()
        }
    }

    fun logoutCurrentAccount() {
        viewModelScope.launch {
            val currentAccount = authRepository.getActiveAccount()
            val currentUid = currentAccount?.uid
            val currentFolder = currentAccount?.folderName ?: "default"

            playbackController.resetForAccount(null)

            // Delete only this user's cache folder
            withContext(Dispatchers.IO) {
                cacheManager.clearUserCache(currentFolder)
            }

            if (currentUid != null) {
                authRepository.removeAccount(currentUid)
            }

            val nextAccount = authRepository.getActiveAccount()
            if (nextAccount != null) {
                switchAccount(nextAccount.uid)
            } else {
                libraryRepository.resetForAccountChange()
                _accountName.value = ""
            }
        }
    }

    fun logoutAllAccounts() {
        viewModelScope.launch {
            playbackController.resetForAccount(null)

            withContext(Dispatchers.IO) {
                cacheManager.clearAllUserCaches()
            }
            authRepository.clearAllAccounts()

            libraryRepository.resetForAccountChange()
            _accountName.value = ""
        }
    }

    fun removeAccount(uid: String) {
        viewModelScope.launch {
            val target = authRepository.getAllAccounts().firstOrNull { it.uid == uid }
            if (target != null) {
                withContext(Dispatchers.IO) {
                    cacheManager.clearUserCache(target.folderName)
                }
            }
            val currentActiveUid = authRepository.getActiveAccount()?.uid
            if (currentActiveUid == uid) {
                logoutCurrentAccount()
            } else {
                authRepository.removeAccount(uid)
            }
        }
    }

    fun clearActiveUserCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val userFolder = activeUserFolder
            cacheManager.clearUserCache(userFolder)
            withContext(Dispatchers.Main) {
                libraryRepository.resetForAccountChange()
                playbackController.clearWaveAndHistory()
                loadLikedTracks()
            }
        }
    }

    /** Pull-to-refresh and the Library tab: sync likes (joins a sync already running). */
    fun loadLikedTracks() {
        viewModelScope.launch {
            libraryRepository.loadCached()
            val changed = libraryRepository.syncLikes()
            if (changed && settingsRepository.autoDownloadLikedTracks.first()) downloadAllLikedTracks()
        }
    }

    fun isTrackLiked(trackId: String): Boolean = trackId in likedTrackIds.value

    fun toggleLike(track: TrackDetailsDto) {
        val trackId = track.idString
        val like = !isTrackLiked(trackId)
        libraryRepository.setLiked(track, like) { ok ->
            if (!ok) return@setLiked
            viewModelScope.launch {
                if (!like) {
                    downloadManager.removeTrack(trackId)
                } else if (settingsRepository.autoDownloadLikedTracks.first()) {
                    downloadTrackBackground(trackId)
                }
            }
        }
    }

    fun dislikeTrack(track: TrackDetailsDto) {
        libraryRepository.dislike(track)
        playbackController.onTrackDisliked(track.idString)
        viewModelScope.launch { downloadManager.removeTrack(track.idString) }
    }

    /** Offline downloads run in WorkManager: they continue after the app is closed. */
    fun downloadAllLikedTracks() {
        viewModelScope.launch { downloadScheduler.downloadAllLiked() }
    }

    fun cancelDownloadAll() {
        downloadScheduler.cancelAll()
        downloadManager.setDownloadProgress(null)
    }

    fun enableAutoDownloadAndStart() {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadLikedTracks(true)
            downloadAllLikedTracks()
        }
    }

    val downloadOverWifiOnly: StateFlow<Boolean> = settingsRepository.downloadOverWifiOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDownloadOverWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDownloadOverWifiOnly(enabled) }
    }

    private suspend fun downloadTrackBackground(trackId: String) = downloadScheduler.downloadTrack(trackId)

    /** Charts are loaded once per session by [syncLibrary]; this only fills an empty screen. */
    fun loadPopularTracks() {
        viewModelScope.launch { libraryRepository.refreshCharts() }
    }

    fun loadMoreHits() = libraryRepository.loadMoreHits()

    fun loadMoreTrends() = libraryRepository.loadMoreTrends()

    private var searchJob: Job? = null

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length > 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(300L)
                    _isSearching.value = true
                    val response = api.search(trimmed)
                    // Lazy lists key items by id, so duplicates from the API must not reach the UI
                    _searchResults.value = (response.result?.tracks?.results ?: emptyList()).distinctBy { it.idString }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Search error", e)
                } finally {
                    _isSearching.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
            _isSearching.value = false
        }
    }

    // --- Playback: delegated to the process-wide PlaybackController ---

    fun playMyVibe() = playbackController.playMyVibe()

    fun loadMoreQueue() = playbackController.loadMoreQueue()

    fun playAllLikedTracks() = playbackController.playAllLikedTracks()

    fun shuffleLikedTracks() = playbackController.playAllLikedTracks(shuffle = true)

    fun playHits(shuffle: Boolean = true) = playbackController.playHits(weeklyHits.value, shuffle)

    fun playTrack(trackId: String, playlist: List<TrackDetailsDto>, source: PlaylistSource? = null) =
        playbackController.playTrack(trackId, playlist, source)

    fun playNext() = playbackController.playNext()

    fun playPrev() = playbackController.playPrev()

    fun resumeOrPlayCurrent() = playbackController.play()

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun reorderQueue(from: Int, to: Int) = playbackController.reorderQueue(from, to)

    fun removeFromQueue(trackId: String) = playbackController.removeFromQueue(trackId)

    fun playRadioStation(station: RadioStation) = playbackController.playRadioStation(station)

    // --- Radio Favorites ---

    fun toggleRadioFavorite(stationId: String) {
        viewModelScope.launch {
            radioRepository.toggleFavorite(stationId)
        }
    }

    fun addRadioFavorite(stationId: String) {
        viewModelScope.launch {
            radioRepository.addFavorite(stationId)
        }
    }

    fun removeRadioFavorite(stationId: String) {
        viewModelScope.launch {
            radioRepository.removeFavorite(stationId)
        }
    }

    // --- In-App Updater Logic ---

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _updateStatusMessage.value = "Проверка обновлений..."
            }
            val result = updateManager.checkForUpdates()
            if (!silent || result is com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable) {
                _updateCheckResult.value = result
            }
            if (result is com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpToDate && !silent) {
                _updateStatusMessage.value = "У вас установлена актуальная версия"
            }
        }
    }

    fun startUpdateDownload(apkUrl: String) {
        viewModelScope.launch {
            _updateDownloadProgress.value = 0f
            _updateStatusMessage.value = null
            _isSignatureMismatch.value = false
            val expectedSha256 = (_updateCheckResult.value as? com.korenskoy.yandexmusic.data.updater.UpdateCheckResult.UpdateAvailable)
                ?.takeIf { it.apkDownloadUrl == apkUrl }
                ?.apkSha256
            val result = updateManager.downloadApk(apkUrl, expectedSha256) { progress ->
                _updateDownloadProgress.value = progress
            }
            result.onSuccess { file ->
                downloadedApkFile = file
                _updateDownloadProgress.value = 2f // 2f signifies download complete, ready to install
                installDownloadedUpdate()
            }.onFailure { err ->
                _updateDownloadProgress.value = -1f
                _updateStatusMessage.value = "Ошибка загрузки: ${err.localizedMessage}"
            }
        }
    }

    fun installDownloadedUpdate(forceSystemInstall: Boolean = false) {
        val file = downloadedApkFile ?: return

        // A broken, foreign or older package is never installed; forceSystemInstall only lets the
        // system installer handle a signing-key change (it will ask the user to reinstall)
        val preCheck = updateManager.checkApkCompatibility(file)
        if (preCheck is com.korenskoy.yandexmusic.data.updater.ApkInstallPreCheck.CorruptedApk) {
            _updateStatusMessage.value = "Ошибка пакета: ${preCheck.reason}"
            return
        }
        if (!forceSystemInstall && preCheck is com.korenskoy.yandexmusic.data.updater.ApkInstallPreCheck.SignatureMismatch) {
            _isSignatureMismatch.value = true
            _updateStatusMessage.value = "Обновление готово! Требуется разовая чистая переустановка из-за смены ключа подписи на официальный."
            return
        }

        val result = updateManager.installApk(file)
        result.onFailure { err ->
            _updateStatusMessage.value = err.localizedMessage
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

    fun dismissUpdateDialog() {
        _updateCheckResult.value = null
        _updateDownloadProgress.value = -1f
        _updateStatusMessage.value = null
        _isSignatureMismatch.value = false
    }
}

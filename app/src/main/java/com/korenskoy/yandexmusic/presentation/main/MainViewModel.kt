package com.korenskoy.yandexmusic.presentation.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.DownloadInfoParser
import com.korenskoy.yandexmusic.data.network.Md5Helper
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

    private val _tracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val tracks: StateFlow<List<TrackDetailsDto>> = _tracks.asStateFlow()
    
    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    private val _weeklyHits = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val weeklyHits: StateFlow<List<TrackDetailsDto>> = _weeklyHits.asStateFlow()

    private val _trendTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val trendTracks: StateFlow<List<TrackDetailsDto>> = _trendTracks.asStateFlow()

    private val _popularTracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val popularTracks: StateFlow<List<TrackDetailsDto>> = _popularTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isDownloadingAll = MutableStateFlow(false)
    val isDownloadingAll: StateFlow<Boolean> = _isDownloadingAll.asStateFlow()

    val downloadedTrackIds: StateFlow<Set<String>> = downloadManager.downloadedTrackIds
    
    val cacheSizeBytes: StateFlow<Long> = downloadManager.cacheSizeBytes

    val autoDownloadEnabled: StateFlow<Boolean> = settingsRepository.autoDownloadLikedTracks
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val downloadProgress = downloadManager.downloadProgress

    private var downloadJob: Job? = null
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
    val playbackMessages: kotlinx.coroutines.flow.SharedFlow<String> = playbackController.messages

    init {
        // The controller outlives this ViewModel; give it the library for "play liked" fallbacks.
        _tracks.onEach { playbackController.updateLibrary(it) }.launchIn(viewModelScope)

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

        // Instant Startup from Cache & Saved History
        viewModelScope.launch(Dispatchers.IO) {
            val userFolder = activeUserFolder
            val cachedTracks = cacheManager.loadLikedTracks(userFolder)
            val cachedHits = cacheManager.loadWeeklyHits()
            val cachedTrends = cacheManager.loadTrendTracks()
            val cachedPopular = cacheManager.loadPopularTracks()
            
            withContext(Dispatchers.Main) {
                if (cachedTracks.isNotEmpty()) {
                    _tracks.value = cachedTracks
                    _likedTrackIds.value = cachedTracks.map { it.idString }.toSet()
                }
                if (cachedHits.isNotEmpty()) _weeklyHits.value = cachedHits
                if (cachedTrends.isNotEmpty()) _trendTracks.value = cachedTrends
                if (cachedPopular.isNotEmpty()) _popularTracks.value = cachedPopular
            }
            
            refreshAllDataSilently()
        }

        // Reactively load data when authorization succeeds (e.g. on fresh install)
        viewModelScope.launch {
            authRepository.isAuthorized().collect { isAuth ->
                if (isAuth) {
                    loadLikedTracks()
                    loadPopularTracks()
                    refreshAllDataSilently()
                }
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
    
    private fun refreshAllDataSilently() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = authRepository.getToken()
            if (token.isNullOrEmpty()) return@launch // DO NOT fetch if not authorized

            try {
                kotlinx.coroutines.supervisorScope {
                    val accountStatus = try { api.getAccountStatus() } catch (e: Exception) { null }
                    val uid = accountStatus?.result?.uidString?.takeIf { it.isNotEmpty() } ?: "me"
                    
                    // Silent refresh liked
                    try {
                        Log.d("LikedTracks", "[SilentRefresh] uid='$uid', starting getLikedTracks")
                        val tracksResponse = try {
                            api.getLikedTracks(uid)
                        } catch (e: Exception) {
                            Log.w("LikedTracks", "[SilentRefresh] getLikedTracks('$uid') failed: ${e.message}")
                            if (uid != "me") api.getLikedTracks("me") else throw e
                        }
                        val resultDto = tracksResponse.result
                        Log.d("LikedTracks", "[SilentRefresh] result null: ${resultDto == null}, error: ${tracksResponse.error?.message ?: "none"}")
                        if (resultDto != null) {
                            Log.d("LikedTracks", "[SilentRefresh] library null: ${resultDto.library == null}, library.tracks: ${resultDto.library?.tracks?.size ?: "N/A"}, directTracks: ${resultDto.directTracks.size}")
                        }
                        val libraryTracks = resultDto?.allTracks ?: emptyList()
                        Log.d("LikedTracks", "[SilentRefresh] allTracks count = ${libraryTracks.size}")
                        if (libraryTracks.isNotEmpty()) {
                            val allIds = libraryTracks.mapNotNull { it.idString.takeIf { id -> id.isNotEmpty() } }.toSet()
                            val embeddedTracks = libraryTracks.mapNotNull { it.track }
                            
                            val fullTracks = mutableListOf<TrackDetailsDto>()
                            if (embeddedTracks.isNotEmpty()) {
                                fullTracks.addAll(embeddedTracks)
                            }
                            
                            val existingIds = fullTracks.map { it.idString }.toSet()
                            val missingIds = allIds - existingIds
                            
                            if (missingIds.isNotEmpty()) {
                                val chunkedIds = missingIds.chunked(100)
                                for (chunk in chunkedIds) {
                                    try {
                                        var metaResponse = api.getTracksMetadata(chunk)
                                        var meta = metaResponse.result ?: emptyList()
                                        if (meta.isEmpty()) {
                                            val idsString = chunk.joinToString(",")
                                            metaResponse = api.getTracksMetadataQuery(idsString)
                                            meta = metaResponse.result ?: emptyList()
                                        }
                                        fullTracks.addAll(meta)
                                    } catch (e: Exception) {
                                        Log.e("MainViewModel", "Silent refresh: error fetching chunk of metadata", e)
                                        try {
                                            val idsString = chunk.joinToString(",")
                                            val fallback = api.getTracksMetadataQuery(idsString)
                                            val meta = fallback.result ?: emptyList()
                                            fullTracks.addAll(meta)
                                        } catch (e2: Exception) {
                                            Log.e("MainViewModel", "Silent refresh fallback also failed", e2)
                                        }
                                    }
                                }
                            }
                            
                            // Fallback for any IDs that still didn't receive metadata
                            val resolvedIds = fullTracks.map { it.idString }.toSet()
                            for (id in allIds) {
                                if (id !in resolvedIds) {
                                    fullTracks.add(
                                        TrackDetailsDto(
                                            id = kotlinx.serialization.json.JsonPrimitive(id),
                                            title = "Трек $id"
                                        )
                                    )
                                }
                            }

                            if (fullTracks.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _tracks.value = fullTracks.distinctBy { it.idString }
                                    _likedTrackIds.value = allIds
                                }
                                cacheManager.saveLikedTracks(activeUserFolder, fullTracks)
                            }
                        } else {
                            Log.d("LikedTracks", "[SilentRefresh] Library has 0 tracks, resetting state.")
                            withContext(Dispatchers.Main) {
                                _tracks.value = emptyList()
                                _likedTrackIds.value = emptySet()
                            }
                            cacheManager.saveLikedTracks(activeUserFolder, emptyList())
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Silent refresh liked failed", e)
                    }
                    
                    // Silent refresh hits & trends with rotating dynamic queries
                    try {
                        val hitsQueries = listOf("чарт", "топ 100", "главные хиты", "популярные треки").shuffled()
                        val trendsQueries = listOf("новинки", "премьеры", "свежие релизы", "тренды").shuffled()

                        val hitsDeferred0 = async { api.search(hitsQueries[0], page = 0) }
                        val hitsDeferred1 = async { api.search(hitsQueries[1], page = 0) }
                        val trendsDeferred0 = async { api.search(trendsQueries[0], page = 0) }
                        val trendsDeferred1 = async { api.search(trendsQueries[1], page = 0) }
                        
                        val hits0 = try { hitsDeferred0.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                        val hits1 = try { hitsDeferred1.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                        val trends0 = try { trendsDeferred0.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                        val trends1 = try { trendsDeferred1.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                        
                        val hits = (hits0 + hits1).distinctBy { it.idString }
                        val trends = (trends0 + trends1).distinctBy { it.idString }
                        
                        withContext(Dispatchers.Main) {
                            if (hits.isNotEmpty()) _weeklyHits.value = hits
                            if (trends.isNotEmpty()) {
                                _trendTracks.value = trends
                                _popularTracks.value = trends
                            } else if (hits.isNotEmpty()) {
                                _popularTracks.value = hits
                            }
                        }
                        
                        if (hits.isNotEmpty()) cacheManager.saveWeeklyHits(hits)
                        if (trends.isNotEmpty()) cacheManager.saveTrendTracks(trends)
                        if (trends.isNotEmpty() || hits.isNotEmpty()) cacheManager.savePopularTracks(if (trends.isNotEmpty()) trends else hits)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Silent refresh trends failed", e)
                    }

                    // Silent prime wave queue with fresh unplayed tracks
                    withContext(Dispatchers.Main) { playbackController.primeWave() }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Silent refresh overall failed", e)
            }
        }
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
            val activeTrackIds = _tracks.value.map { it.idString }
            for (trackId in activeTrackIds) {
                if (!protectedTrackIds.contains(trackId)) {
                    downloadManager.removeTrack(trackId)
                }
            }

            withContext(Dispatchers.Main) {
                _tracks.value = emptyList()
                _likedTrackIds.value = emptySet()
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
            // Wave queue and listening history are per user
            playbackController.resetForAccount(userFolder)

            // Load isolated cache for this switched user immediately
            val cachedTracks = cacheManager.loadLikedTracks(userFolder)
            _tracks.value = cachedTracks
            _likedTrackIds.value = cachedTracks.map { it.idString }.toSet()

            // Silently refresh from API in background
            loadLikedTracks()
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
                _tracks.value = emptyList()
                _likedTrackIds.value = emptySet()
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

            _tracks.value = emptyList()
            _likedTrackIds.value = emptySet()
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
                _tracks.value = emptyList()
                _likedTrackIds.value = emptySet()
                playbackController.clearWaveAndHistory()
                loadLikedTracks()
            }
        }
    }

    fun loadLikedTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val token = authRepository.getToken()
                Log.d("LikedTracks", "=== loadLikedTracks START ===")
                Log.d("LikedTracks", "token present: ${!token.isNullOrEmpty()}")

                // Step 1: Get UID and profile info from account status
                var uid: String? = null
                for (attempt in 1..3) {
                    try {
                        val accountStatus = api.getAccountStatus()
                        val resultObj = accountStatus.result
                        Log.d("LikedTracks", "Attempt $attempt: accountStatus.result is null: ${resultObj == null}")
                        Log.d("LikedTracks", "Attempt $attempt: accountStatus.error: ${accountStatus.error?.message ?: "none"}")
                        if (resultObj != null) {
                            val bestName = resultObj.displayUserName
                            val login = resultObj.account?.login ?: ""
                            val uidStr = resultObj.uidString
                            var avatarUrl = resultObj.avatarUrl
                            if (avatarUrl == null) {
                                try {
                                    val loginInfo = api.getYandexLoginInfo()
                                    avatarUrl = loginInfo.avatarUrl
                                } catch (e: Exception) {
                                    Log.d("LikedTracks", "getYandexLoginInfo failed: ${e.message}")
                                }
                            }
                            _accountName.value = bestName

                            val currentToken = authRepository.getToken() ?: ""
                            if (currentToken.isNotEmpty() && uidStr.isNotEmpty()) {
                                authRepository.saveOrUpdateAccount(
                                    com.korenskoy.yandexmusic.domain.model.UserAccount(
                                        uid = uidStr,
                                        login = login,
                                        displayName = bestName,
                                        token = currentToken,
                                        avatarUrl = avatarUrl
                                    )
                                )
                            }
                        }
                        uid = resultObj?.uidString?.takeIf { it.isNotEmpty() }
                        if (uid != null) break
                    } catch (e: Exception) {
                        Log.w("LikedTracks", "Attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                        kotlinx.coroutines.delay(600L * attempt)
                    }
                }

                val targetUid = uid ?: "me"
                Log.d("LikedTracks", "Using targetUid = '$targetUid'")

                // Step 2: Fetch liked tracks
                val tracksResponse = try {
                    api.getLikedTracks(targetUid)
                } catch (e: Exception) {
                    Log.w("LikedTracks", "getLikedTracks('$targetUid') failed: ${e.javaClass.simpleName}: ${e.message}")
                    if (targetUid != "me") {
                        Log.d("LikedTracks", "Retrying with uid='me'")
                        api.getLikedTracks("me")
                    } else throw e
                }

                // Step 3: Diagnose the response structure
                val resultDto = tracksResponse.result
                Log.d("LikedTracks", "tracksResponse.error: ${tracksResponse.error?.message ?: "none"}")
                Log.d("LikedTracks", "tracksResponse.result is null: ${resultDto == null}")
                if (resultDto != null) {
                    Log.d("LikedTracks", "result.library is null: ${resultDto.library == null}")
                    Log.d("LikedTracks", "result.library?.tracks?.size: ${resultDto.library?.tracks?.size ?: "N/A"}")
                    Log.d("LikedTracks", "result.directTracks.size: ${resultDto.directTracks.size}")
                    Log.d("LikedTracks", "result.allTracks.size: ${resultDto.allTracks.size}")
                    Log.d("LikedTracks", "result.uid: ${resultDto.uid}")
                    Log.d("LikedTracks", "result.revision: ${resultDto.revision}")
                }

                val libraryTracks = resultDto?.allTracks ?: emptyList()
                Log.d("LikedTracks", "libraryTracks count = ${libraryTracks.size}")
                
                var allIds = libraryTracks.mapNotNull { it.idString.takeIf { id -> id.isNotEmpty() } }.toSet()
                val embeddedTracks = libraryTracks.mapNotNull { it.track }.toMutableList()

                if (allIds.isEmpty()) {
                    Log.d("LikedTracks", "libraryTracks was empty, trying fallback 1: getPlaylistRaw(targetUid, '3')")
                    try {
                        val p3 = api.getPlaylistRaw(targetUid, "3")
                        Log.d("LikedTracks", "p3 raw JSON: ${p3.result?.toString()?.take(500)}")
                        val p3Ids = extractTrackIdsFromJson(p3.result)
                        Log.d("LikedTracks", "p3 extracted track IDs: ${p3Ids.size}")
                        if (p3Ids.isNotEmpty()) {
                            allIds = p3Ids.toSet()
                        }
                    } catch (e: Exception) {
                        Log.w("LikedTracks", "Fallback 1 (playlist 3) failed: ${e.message}")
                    }
                }

                if (allIds.isNotEmpty()) {
                    Log.d("LikedTracks", "=== Total Liked Track IDs found = ${allIds.size} ===")
                    withContext(Dispatchers.Main) {
                        _likedTrackIds.value = allIds
                    }

                    val fullTracks = mutableListOf<TrackDetailsDto>()
                    if (embeddedTracks.isNotEmpty()) {
                        fullTracks.addAll(embeddedTracks)
                        withContext(Dispatchers.Main) {
                            _tracks.value = fullTracks.distinctBy { it.idString }
                        }
                    }

                    val existingIds = fullTracks.map { it.idString }.toSet()
                    val missingIds = allIds - existingIds
                    Log.d("LikedTracks", "missingIds (need metadata fetch) = ${missingIds.size}")
                    
                    if (missingIds.isNotEmpty()) {
                        val chunkedIds = missingIds.chunked(100)
                        for (chunk in chunkedIds) {
                            try {
                                var metaResponse = api.getTracksMetadata(chunk)
                                var meta = metaResponse.result ?: emptyList()
                                if (meta.isEmpty()) {
                                    val idsString = chunk.joinToString(",")
                                    metaResponse = api.getTracksMetadataQuery(idsString)
                                    meta = metaResponse.result ?: emptyList()
                                }
                                Log.d("LikedTracks", "Fetched metadata for chunk: got ${meta.size} tracks")
                                fullTracks.addAll(meta)
                            } catch (e: Exception) {
                                Log.e("LikedTracks", "Error fetching metadata chunk", e)
                                try {
                                    val idsString = chunk.joinToString(",")
                                    val fallback = api.getTracksMetadataQuery(idsString)
                                    val meta = fallback.result ?: emptyList()
                                    fullTracks.addAll(meta)
                                } catch (e2: Exception) {
                                    Log.e("LikedTracks", "Fallback query also failed for chunk", e2)
                                }
                            }
                        }
                    }

                    // Fallback for any IDs that still didn't receive metadata
                    val resolvedIds = fullTracks.map { it.idString }.toSet()
                    for (id in allIds) {
                        if (id !in resolvedIds) {
                            fullTracks.add(
                                TrackDetailsDto(
                                    id = kotlinx.serialization.json.JsonPrimitive(id),
                                    title = "Трек $id"
                                )
                            )
                        }
                    }

                    Log.d("LikedTracks", "=== FINAL fullTracks count = ${fullTracks.size} ===")
                    if (fullTracks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            _tracks.value = fullTracks.distinctBy { it.idString }
                        }
                        cacheManager.saveLikedTracks(activeUserFolder, fullTracks)
                    }
                    
                    val autoDownload = settingsRepository.autoDownloadLikedTracks.first()
                    if (autoDownload) {
                        downloadAllLikedTracks()
                    }
                } else {
                    Log.d("LikedTracks", "=== User has 0 liked tracks. Resetting state. ===")
                    withContext(Dispatchers.Main) {
                        _likedTrackIds.value = emptySet()
                        _tracks.value = emptyList()
                    }
                    cacheManager.saveLikedTracks(activeUserFolder, emptyList())
                }
            } catch (e: Exception) {
                Log.e("LikedTracks", "=== loadLikedTracks EXCEPTION ===", e)
            } finally {
                _isLoading.value = false
                Log.d("LikedTracks", "=== loadLikedTracks END, _tracks.value.size = ${_tracks.value.size} ===")
            }
        }
    }

    private fun extractTrackIdsFromJson(element: kotlinx.serialization.json.JsonElement?): List<String> {
        if (element == null) return emptyList()
        val ids = mutableListOf<String>()
        
        fun search(el: kotlinx.serialization.json.JsonElement) {
            when (el) {
                is kotlinx.serialization.json.JsonObject -> {
                    val directId = el["id"] ?: el["trackId"]
                    if (directId is kotlinx.serialization.json.JsonPrimitive && directId.content.isNotEmpty()) {
                        val content = directId.content
                        if (content.all { it.isDigit() || it == ':' || it == '-' } && content.length > 2) {
                            if (el.containsKey("albums") || el.containsKey("title") || el.containsKey("albumId") || el.containsKey("timestamp") || el.containsKey("track")) {
                                ids.add(content)
                            }
                        }
                    }
                    val trackObj = el["track"]
                    if (trackObj is kotlinx.serialization.json.JsonObject) {
                        val trackId = trackObj["id"]
                        if (trackId is kotlinx.serialization.json.JsonPrimitive && trackId.content.isNotEmpty()) {
                            ids.add(trackId.content)
                        }
                    }
                    for ((_, child) in el) {
                        search(child)
                    }
                }
                is kotlinx.serialization.json.JsonArray -> {
                    for (child in el) {
                        if (child is kotlinx.serialization.json.JsonPrimitive && child.content.all { it.isDigit() } && child.content.length > 2) {
                            ids.add(child.content)
                        } else {
                            search(child)
                        }
                    }
                }
                else -> {}
            }
        }
        search(element)
        return ids.distinct()
    }

    fun isTrackLiked(trackId: String): Boolean = _likedTrackIds.value.contains(trackId)

    fun toggleLike(track: TrackDetailsDto) {
        val trackId = track.idString
        val isCurrentlyLiked = isTrackLiked(trackId)
        val previousLikedIds = _likedTrackIds.value
        val previousTracks = _tracks.value
        
        // Optimistic UI update for likedTrackIds and library tracks
        _likedTrackIds.value = if (isCurrentlyLiked) {
            _likedTrackIds.value - trackId
        } else {
            _likedTrackIds.value + trackId
        }
        
        _tracks.value = if (isCurrentlyLiked) {
            _tracks.value.filter { it.idString != trackId }
        } else {
            // Add to the top of the library
            listOf(track) + _tracks.value.filter { it.idString != trackId }
        }

        viewModelScope.launch {
            cacheManager.saveLikedTracks(activeUserFolder, _tracks.value)
            try {
                val accountStatus = try { api.getAccountStatus() } catch (e: Exception) { null }
                val uid = accountStatus?.result?.uidString?.takeIf { it.isNotEmpty() } ?: "me"

                if (isCurrentlyLiked) {
                    api.unlikeTrack(uid, trackId)
                    downloadManager.removeTrack(trackId)
                } else {
                    api.likeTrack(uid, trackId)
                    val autoDownload = settingsRepository.autoDownloadLikedTracks.first()
                    if (autoDownload) {
                        downloadTrackBackground(trackId)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling like", e)
                // Full revert of both IDs and tracks on error
                withContext(Dispatchers.Main) {
                    _likedTrackIds.value = previousLikedIds
                    _tracks.value = previousTracks
                }
                cacheManager.saveLikedTracks(activeUserFolder, previousTracks)
            }
        }
    }

    fun dislikeTrack(track: TrackDetailsDto) {
        val trackId = track.idString
        val wasLiked = isTrackLiked(trackId)
        
        // Optimistically remove from likes and library
        _likedTrackIds.value = _likedTrackIds.value - trackId
        _tracks.value = _tracks.value.filter { it.idString != trackId }
        playbackController.onTrackDisliked(trackId)

        viewModelScope.launch {
            cacheManager.saveLikedTracks(activeUserFolder, _tracks.value)
            try {
                val accountStatus = try { api.getAccountStatus() } catch (e: Exception) { null }
                val uid = accountStatus?.result?.uidString?.takeIf { it.isNotEmpty() } ?: "me"

                if (wasLiked) {
                    api.unlikeTrack(uid, trackId)
                }
                api.dislikeTrack(uid, trackId)
                downloadManager.removeTrack(trackId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error disliking track", e)
            }
        }
    }

    fun downloadAllLikedTracks() {
        if (_isDownloadingAll.value) return
        
        downloadJob = viewModelScope.launch {
            _isDownloadingAll.value = true
            val tracksToDownload = _tracks.value
            val total = tracksToDownload.size
            for ((index, track) in tracksToDownload.withIndex()) {
                val title = track.title ?: "Без названия"
                val artist = track.artists.firstOrNull()?.name ?: "Неизвестно"
                downloadManager.setDownloadProgress(
                    com.korenskoy.yandexmusic.data.network.DownloadProgressInfo(
                        current = index,
                        total = total,
                        currentTrackTitle = title,
                        currentArtistName = artist
                    )
                )
                downloadTrackBackground(track.idString)
            }
            downloadManager.setDownloadProgress(null)
            _isDownloadingAll.value = false
        }
    }
    
    fun cancelDownloadAll() {
        downloadJob?.cancel()
        downloadManager.setDownloadProgress(null)
        _isDownloadingAll.value = false
    }
    
    fun enableAutoDownloadAndStart() {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadLikedTracks(true)
            downloadAllLikedTracks()
        }
    }

    private suspend fun downloadTrackBackground(trackId: String) {
        if (downloadManager.isTrackDownloaded(trackId)) return
        try {
            val downloadInfoResponse = api.getTrackDownloadInfo(trackId)
            val infoList = downloadInfoResponse.result ?: emptyList()
            val preferredInfo = infoList.filter { !it.preview && it.codec.equals("mp3", ignoreCase = true) }
                .maxByOrNull { it.bitrateInKbps }
                ?: infoList.filter { !it.preview }.maxByOrNull { it.bitrateInKbps }
                ?: infoList.firstOrNull()
            val infoUrl = preferredInfo?.downloadInfoUrl
            
            if (infoUrl != null) {
                val xmlResponse = withContext(Dispatchers.IO) {
                    java.net.URL(infoUrl).readText()
                }
                val xmlData = DownloadInfoParser.parseXml(xmlResponse)
                if (xmlData != null) {
                    val mp3Url = Md5Helper.generateMp3Url(
                        xmlData.host,
                        xmlData.path,
                        xmlData.ts,
                        xmlData.s
                    )
                    downloadManager.downloadTrack(trackId, mp3Url)
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error downloading track $trackId", e)
        }
    }

    private var hitsPage = 0
    private var trendsPage = 0
    private var isFetchingMorePopular = false

    fun loadPopularTracks() {
        hitsPage = 1 // Start at 1 since we fetch 0 and 1
        trendsPage = 1
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.supervisorScope {
                    val hitsDeferred0 = async { api.search("чарт", page = 0) }
                    val hitsDeferred1 = async { api.search("топ 100", page = 0) }
                    val trendsDeferred0 = async { api.search("новинки", page = 0) }
                    val trendsDeferred1 = async { api.search("премьеры", page = 0) }
                    
                    val hits0 = try { hitsDeferred0.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                    val hits1 = try { hitsDeferred1.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                    
                    val trends0 = try { trendsDeferred0.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }
                    val trends1 = try { trendsDeferred1.await().result?.tracks?.results ?: emptyList() } catch (e: Exception) { emptyList() }

                    val hits = (hits0 + hits1).distinctBy { it.idString }
                    val trends = (trends0 + trends1).distinctBy { it.idString }
                    
                    withContext(Dispatchers.Main) {
                        _weeklyHits.value = hits
                        _trendTracks.value = trends
                        _popularTracks.value = if (trends.isNotEmpty()) trends else hits
                    }
                    
                    cacheManager.saveWeeklyHits(hits)
                    cacheManager.saveTrendTracks(trends)
                    cacheManager.savePopularTracks(if (trends.isNotEmpty()) trends else hits)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading popular tracks", e)
            }
        }
    }

    fun loadMoreHits() {
        if (isFetchingMorePopular) return
        isFetchingMorePopular = true
        viewModelScope.launch {
            try {
                hitsPage++
                val queries = listOf("чарт", "топ 100", "главные хиты", "суперхиты")
                val query = queries[hitsPage % queries.size]
                val page = hitsPage / queries.size
                val hitsResponse = api.search(query, page = page)
                val newHits = hitsResponse.result?.tracks?.results ?: emptyList()
                if (newHits.isNotEmpty()) {
                    val combined = (_weeklyHits.value + newHits).distinctBy { it.idString }
                    _weeklyHits.value = combined
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading more hits", e)
            } finally {
                isFetchingMorePopular = false
            }
        }
    }

    fun loadMoreTrends() {
        if (isFetchingMorePopular) return
        isFetchingMorePopular = true
        viewModelScope.launch {
            try {
                trendsPage++
                val queries = listOf("новинки", "в тренде", "горячие новинки", "премьеры")
                val query = queries[trendsPage % queries.size]
                val page = trendsPage / queries.size
                val trendsResponse = api.search(query, page = page)
                val newTrends = trendsResponse.result?.tracks?.results ?: emptyList()
                if (newTrends.isNotEmpty()) {
                    val combined = (_trendTracks.value + newTrends).distinctBy { it.idString }
                    _trendTracks.value = combined
                    _popularTracks.value = combined
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading more trends", e)
            } finally {
                isFetchingMorePopular = false
            }
        }
    }

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

    fun playHits(shuffle: Boolean = true) = playbackController.playHits(_weeklyHits.value, shuffle)

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

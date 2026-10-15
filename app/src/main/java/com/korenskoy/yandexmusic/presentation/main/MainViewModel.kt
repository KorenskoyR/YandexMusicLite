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
import com.korenskoy.yandexmusic.domain.repository.PlayerRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.domain.model.RadioStation
import com.korenskoy.yandexmusic.domain.model.RadioCategory
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    val playerRepository: PlayerRepository,
    val settingsRepository: SettingsRepository,
    private val downloadManager: TrackDownloadManager,
    private val preloadManager: TrackPreloadManager,
    private val cacheManager: com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager,
    private val radioRepository: RadioRepository,
    private val updateManager: com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager
) : ViewModel() {

    enum class PlaylistSource {
        LIBRARY, VIBE, SEARCH, TRENDS, NONE
    }

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

    private val _currentSource = MutableStateFlow(PlaylistSource.LIBRARY)
    val currentSource: StateFlow<PlaylistSource> = _currentSource.asStateFlow()

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

    private val _wavePlaylist = MutableStateFlow<List<TrackDetailsDto>>(emptyList())

    private val _currentPlaylist = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    val currentPlaylist: StateFlow<List<TrackDetailsDto>> = _currentPlaylist.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackDetailsDto?>(null)
    val currentTrack: StateFlow<TrackDetailsDto?> = _currentTrack.asStateFlow()

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

    private var downloadedApkFile: java.io.File? = null

    private val _isFetchingMoreQueue = MutableStateFlow(false)
    val isFetchingMoreQueue: StateFlow<Boolean> = _isFetchingMoreQueue.asStateFlow()
    private var isFetchingMoreVibe = false
    private var consecutiveTrackErrors = 0
    private val recentPlayedTrackIds = ArrayDeque<String>(100)

    init {
        playerRepository.trackEndedEvent
            .onEach { playNext() }
            .launchIn(viewModelScope)
            
        playerRepository.skipNextEvent
            .onEach { playNext() }
            .launchIn(viewModelScope)
            
        playerRepository.skipPrevEvent
            .onEach { playPrev() }
            .launchIn(viewModelScope)

        // Keep display name synchronized with active account
        viewModelScope.launch {
            authRepository.activeAccount.collect { account ->
                if (account != null) {
                    val name = account.displayName.ifBlank { account.login }.ifBlank { "UID: ${account.uid}" }
                    _accountName.value = name
                }
            }
        }

        // Instant Startup from Cache & Saved History
        viewModelScope.launch(Dispatchers.IO) {
            val userFolder = activeUserFolder
            val savedRecent = cacheManager.loadRecentTrackIds(userFolder)
            if (savedRecent.isNotEmpty()) {
                recentPlayedTrackIds.addAll(savedRecent.takeLast(100))
            }

            val cachedTracks = cacheManager.loadLikedTracks(userFolder)
            val cachedHits = cacheManager.loadWeeklyHits()
            val cachedTrends = cacheManager.loadTrendTracks()
            val cachedPopular = cacheManager.loadPopularTracks()
            val cachedWave = cacheManager.loadWaveQueue(userFolder)
            
            withContext(Dispatchers.Main) {
                if (cachedTracks.isNotEmpty()) {
                    _tracks.value = cachedTracks
                    _likedTrackIds.value = cachedTracks.map { it.idString }.toSet()
                }
                if (cachedHits.isNotEmpty()) _weeklyHits.value = cachedHits
                if (cachedTrends.isNotEmpty()) _trendTracks.value = cachedTrends
                if (cachedPopular.isNotEmpty()) _popularTracks.value = cachedPopular
                if (cachedWave.isNotEmpty()) {
                    // Filter out already played tracks to guarantee a fresh first track on launch
                    val freshWave = cachedWave.filter { it.idString !in recentPlayedTrackIds }
                    _wavePlaylist.value = if (freshWave.isNotEmpty()) freshWave else cachedWave
                }
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
    
    private fun recordTrackPlayed(trackId: String) {
        if (trackId.isEmpty() || trackId.startsWith("radio_")) return
        if (trackId !in recentPlayedTrackIds) {
            recentPlayedTrackIds.addLast(trackId)
            if (recentPlayedTrackIds.size > 100) {
                recentPlayedTrackIds.removeFirst()
            }
            viewModelScope.launch(Dispatchers.IO) {
                cacheManager.saveRecentTrackIds(activeUserFolder, recentPlayedTrackIds.toList())
            }
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
                                    _tracks.value = fullTracks
                                    _likedTrackIds.value = allIds
                                }
                                cacheManager.saveLikedTracks(activeUserFolder, fullTracks)
                            }
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
                    try {
                        val batch1 = async { api.getMyWaveTracks() }
                        val batch2 = async { api.getMyWaveTracks() }
                        val batch3 = async { api.getMyWaveTracks() }
                        val batch4 = async { api.getMyWaveTracks() }
                        
                        val w1 = try { batch1.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                        val w2 = try { batch2.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                        val w3 = try { batch3.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                        val w4 = try { batch4.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                        
                        val combined = (w1 + w2 + w3 + w4).distinctBy { it.idString }
                        if (combined.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                val freshTracks = combined.filter { it.idString !in recentPlayedTrackIds }
                                val resultList = if (freshTracks.isNotEmpty()) freshTracks else combined
                                 
                                if (_wavePlaylist.value.isEmpty()) {
                                    _wavePlaylist.value = resultList
                                } else {
                                    val currentIds = _wavePlaylist.value.map { it.idString }.toSet()
                                    val toAdd = resultList.filter { it.idString !in currentIds }
                                    _wavePlaylist.value = _wavePlaylist.value + toAdd
                                }
                                if (_currentSource.value == PlaylistSource.VIBE) {
                                    _currentPlaylist.value = _wavePlaylist.value
                                }
                            }
                            cacheManager.saveWaveQueue(activeUserFolder, _wavePlaylist.value)
                            
                            // Proactively fetch top 5 MP3 URLs for instant start
                            val top5 = _wavePlaylist.value.take(5).map { it.idString }
                            preloadManager.preloadTracks(top5)
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Silent prime wave failed", e)
                    }
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
                _wavePlaylist.value = emptyList()
                recentPlayedTrackIds.clear()
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

    fun setAutoDownloadLikedTracks(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDownloadLikedTracks(enabled)
        }
    }

    fun stopAndClearPlayer() {
        playerRepository.stop()
        _currentTrack.value = null
        _currentPlaylist.value = emptyList()
        _currentSource.value = PlaylistSource.NONE
    }

    fun logout() {
        logoutCurrentAccount()
    }

    fun switchAccount(uid: String) {
        viewModelScope.launch {
            stopAndClearPlayer()
            recentPlayedTrackIds.clear()
            _wavePlaylist.value = emptyList()

            val switched = authRepository.switchAccount(uid)
            if (!switched) return@launch

            val newAccount = authRepository.getActiveAccount()
            val userFolder = newAccount?.folderName ?: "default"
            _accountName.value = newAccount?.displayName?.ifBlank { newAccount.login } ?: "Яндекс Аккаунт"

            // Load isolated cache for this switched user immediately
            withContext(Dispatchers.IO) {
                val cachedTracks = cacheManager.loadLikedTracks(userFolder)
                val cachedWave = cacheManager.loadWaveQueue(userFolder)
                val cachedRecent = cacheManager.loadRecentTrackIds(userFolder)

                recentPlayedTrackIds.addAll(cachedRecent.takeLast(100))
                withContext(Dispatchers.Main) {
                    _tracks.value = cachedTracks
                    _likedTrackIds.value = cachedTracks.map { it.idString }.toSet()
                    if (cachedWave.isNotEmpty()) {
                        _wavePlaylist.value = cachedWave
                    }
                }
            }

            // Silently refresh from API in background
            loadLikedTracks()
        }
    }

    fun logoutCurrentAccount() {
        viewModelScope.launch {
            val currentAccount = authRepository.getActiveAccount()
            val currentUid = currentAccount?.uid
            val currentFolder = currentAccount?.folderName ?: "default"

            stopAndClearPlayer()
            recentPlayedTrackIds.clear()
            _wavePlaylist.value = emptyList()

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
            stopAndClearPlayer()
            recentPlayedTrackIds.clear()
            _wavePlaylist.value = emptyList()

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
                _wavePlaylist.value = emptyList()
                recentPlayedTrackIds.clear()
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
                Log.d("LikedTracks", "token present: ${!token.isNullOrEmpty()}, token prefix: ${token?.take(8) ?: "NULL"}")

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
                            _accountName.value = bestName

                            val currentToken = authRepository.getToken() ?: ""
                            if (currentToken.isNotEmpty() && uidStr.isNotEmpty()) {
                                authRepository.saveOrUpdateAccount(
                                    com.korenskoy.yandexmusic.domain.model.UserAccount(
                                        uid = uidStr,
                                        login = login,
                                        displayName = bestName,
                                        token = currentToken
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

                if (allIds.isEmpty()) {
                    Log.d("LikedTracks", "Trying landing personal-playlists...")
                    try {
                        val landing = api.getRawUrl("https://api.music.yandex.net/landing3?blocks=personal-playlists")
                        Log.d("LikedTracks", "landing raw JSON: ${landing.result?.toString()?.take(600)}")
                        val landingIds = extractTrackIdsFromJson(landing.result)
                        Log.d("LikedTracks", "landing extracted track IDs: ${landingIds.size}")
                        if (landingIds.isNotEmpty()) {
                            allIds = landingIds.toSet()
                        }
                    } catch (e: Exception) {
                        Log.w("LikedTracks", "Landing personal-playlists failed: ${e.message}")
                    }
                }

                if (allIds.isEmpty()) {
                    Log.d("LikedTracks", "Trying user playlists list...")
                    try {
                        val plList = api.getPlaylistsListRaw(targetUid)
                        Log.d("LikedTracks", "plList full JSON: ${plList.result?.toString()}")
                        val plIds = extractTrackIdsFromJson(plList.result)
                        Log.d("LikedTracks", "plList extracted track IDs: ${plIds.size}")
                        if (plIds.isNotEmpty()) {
                            allIds = plIds.toSet()
                        }
                    } catch (e: Exception) {
                        Log.w("LikedTracks", "User playlists list failed: ${e.message}")
                    }
                }

                if (allIds.isEmpty()) {
                    Log.d("LikedTracks", "Trying liked playlists...")
                    try {
                        val likedPl = api.getRawUrl("https://api.music.yandex.net/users/$targetUid/likes/playlists")
                        Log.d("LikedTracks", "likedPl JSON: ${likedPl.result?.toString()?.take(600)}")
                        val likedPlIds = extractTrackIdsFromJson(likedPl.result)
                        Log.d("LikedTracks", "likedPl extracted track IDs: ${likedPlIds.size}")
                        if (likedPlIds.isNotEmpty()) {
                            allIds = likedPlIds.toSet()
                        }
                    } catch (e: Exception) {
                        Log.w("LikedTracks", "Liked playlists failed: ${e.message}")
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
                            _tracks.value = fullTracks.toList()
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
                            _tracks.value = fullTracks
                        }
                        cacheManager.saveLikedTracks(activeUserFolder, fullTracks)
                    }
                    
                    val autoDownload = settingsRepository.autoDownloadLikedTracks.first()
                    if (autoDownload) {
                        downloadAllLikedTracks()
                    }
                } else {
                    Log.w("LikedTracks", "=== API RETURNED NO TRACKS ACROSS ALL ENDPOINTS ===")
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
                // Revert on error
                _likedTrackIds.value = if (isCurrentlyLiked) {
                    _likedTrackIds.value + trackId
                } else {
                    _likedTrackIds.value - trackId
                }
                cacheManager.saveLikedTracks(activeUserFolder, _tracks.value)
            }
        }
    }

    fun dislikeTrack(track: TrackDetailsDto) {
        val trackId = track.idString
        val wasLiked = isTrackLiked(trackId)
        
        // Optimistically remove from likes and library
        _likedTrackIds.value = _likedTrackIds.value - trackId
        _tracks.value = _tracks.value.filter { it.idString != trackId }

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
                val artist = track.artists?.firstOrNull()?.name ?: "Неизвестно"
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length > 2) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                _isSearching.value = true
                val response = api.search(query)
                _searchResults.value = response.result?.tracks?.results ?: emptyList()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search error", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playMyVibe(forceRestart: Boolean = false) {
        _currentSource.value = PlaylistSource.VIBE
        
        val waveList = _wavePlaylist.value
        val unplayedTrack = waveList.firstOrNull { it.idString !in recentPlayedTrackIds } ?: waveList.firstOrNull()
        
        if (unplayedTrack != null && !forceRestart) {
            _currentPlaylist.value = waveList
            playTrack(unplayedTrack.idString, waveList)
            if (waveList.size < 30) {
                fetchMoreVibeTracks()
            }
            return
        }

        // Instant audio playback from cache: don't let user sit in silence
        if (waveList.isNotEmpty()) {
            _currentPlaylist.value = waveList
            val toPlay = unplayedTrack ?: waveList.first()
            playTrack(toPlay.idString, waveList)
        }

        // Fetch fresh tracks in background without blocking audio
        fetchMoreVibeTracks(forceStartIfEmpty = waveList.isEmpty())
    }

    private fun fetchMoreVibeTracks(
        prefetchOnly: Boolean = false,
        forceStartIfEmpty: Boolean = false,
        onFetched: ((Boolean) -> Unit)? = null
    ) {
        if (_isFetchingMoreQueue.value) {
            onFetched?.invoke(false)
            return
        }
        _isFetchingMoreQueue.value = true
        isFetchingMoreVibe = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.supervisorScope {
                    val batch1 = async { api.getMyWaveTracks() }
                    val batch2 = async { api.getMyWaveTracks() }
                    val batch3 = async { api.getMyWaveTracks() }
                    val batch4 = async { api.getMyWaveTracks() }

                    val w1 = try { batch1.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                    val w2 = try { batch2.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                    val w3 = try { batch3.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }
                    val w4 = try { batch4.await().result?.sequence?.mapNotNull { it.track } ?: emptyList() } catch (e: Exception) { emptyList() }

                    val waveTracks = (w1 + w2 + w3 + w4).distinctBy { it.idString }
                    var hasNew = false
                    
                    if (waveTracks.isNotEmpty()) {
                        val tracksToAdd = mutableListOf<TrackDetailsDto>()
                        withContext(Dispatchers.Main) {
                            val newPlaylist = _wavePlaylist.value.toMutableList()
                            val existingIds = newPlaylist.map { it.idString }.toSet()
                            
                            // 1. First priority: strictly fresh tracks not in existing queue and not recently played
                            var freshTracks = waveTracks.filter { it.idString !in existingIds && it.idString !in recentPlayedTrackIds }
                            
                            // 2. Adaptive fallback: if all were recently played, still append any track not in active queue
                            if (freshTracks.isEmpty()) {
                                freshTracks = waveTracks.filter { it.idString !in existingIds }
                            }

                            if (freshTracks.isNotEmpty()) {
                                tracksToAdd.addAll(freshTracks)
                                newPlaylist.addAll(freshTracks)
                                _wavePlaylist.value = newPlaylist
                                if (_currentSource.value == PlaylistSource.VIBE) {
                                    _currentPlaylist.value = newPlaylist
                                }
                                hasNew = true
                            }
                            
                            if (forceStartIfEmpty && _currentTrack.value == null && newPlaylist.isNotEmpty()) {
                                playTrack(newPlaylist.first().idString, newPlaylist)
                            }
                        }
                        if (hasNew) {
                            cacheManager.saveWaveQueue(activeUserFolder, _wavePlaylist.value)
                            preloadManager.preloadTracks(tracksToAdd.take(5).map { it.idString })
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onFetched?.invoke(hasNew)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching more vibe tracks", e)
                withContext(Dispatchers.Main) { onFetched?.invoke(false) }
            } finally {
                _isFetchingMoreQueue.value = false
                isFetchingMoreVibe = false
            }
        }
    }
    
    private fun preloadCoilCovers(tracks: List<TrackDetailsDto>) {
    }
    
    fun loadMoreQueue() {
        if (_currentSource.value == PlaylistSource.VIBE) {
            fetchMoreVibeTracks()
        }
    }

    fun playAllLikedTracks() {
        _currentSource.value = PlaylistSource.LIBRARY
        val playlist = tracks.value
        if (playlist.isNotEmpty()) {
            playTrack(playlist.first().idString, playlist)
        }
    }

    fun shuffleLikedTracks() {
        _currentSource.value = PlaylistSource.LIBRARY
        val playlist = tracks.value.shuffled()
        if (playlist.isNotEmpty()) {
            playTrack(playlist.first().idString, playlist)
        }
    }

    fun playHits(shuffle: Boolean = true) {
        _currentSource.value = PlaylistSource.TRENDS
        val hitsList = if (shuffle) _weeklyHits.value.shuffled() else _weeklyHits.value
        if (hitsList.isNotEmpty()) {
            val trackToPlay = hitsList.firstOrNull { it.idString !in recentPlayedTrackIds } ?: hitsList.first()
            playTrack(trackToPlay.idString, hitsList)
        }
    }

    fun playTrack(trackId: String, playlist: List<TrackDetailsDto>) {
        _currentPlaylist.value = playlist
        val track = playlist.find { it.idString == trackId }
        _currentTrack.value = track
        
        recordTrackPlayed(trackId)
        
        val currentIndex = playlist.indexOfFirst { it.idString == trackId }
        if (currentIndex != -1) {
            val upcoming = playlist.drop(currentIndex + 1).take(3).map { it.idString }
            if (upcoming.isNotEmpty()) {
                preloadManager.preloadTracks(upcoming)
            }
            
            // Continuous Queue Auto-Refill
            // If less than 20 tracks remain in the queue, quietly fetch more in the background
            if (_currentSource.value == PlaylistSource.VIBE && playlist.size - currentIndex <= 20 && !_isFetchingMoreQueue.value) {
                fetchMoreVibeTracks()
            }
        }
        
        if (_currentSource.value == PlaylistSource.VIBE && track?.isRadio != true) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    api.sendRotorFeedback("trackStarted", trackId)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error sending rotor feedback", e)
                }
            }
        }
        
        val title = track?.title
        val artist = track?.artists?.firstOrNull()?.name
        val coverUrl = track?.coverUri?.let { uri ->
            if (uri.startsWith("http")) uri else "https://${uri.replace("%%", "1000x1000")}"
        }

        viewModelScope.launch {
            try {
                if (downloadManager.isTrackDownloaded(trackId)) {
                    consecutiveTrackErrors = 0
                    // Play directly from cache
                    playerRepository.playTrack(trackId, "", title, artist, coverUrl)
                    return@launch
                }

                // Wait for high priority fetch of url if not loaded yet
                val mp3Url = preloadManager.preloadSingleTrackPriority(trackId)
                if (mp3Url != null) {
                    consecutiveTrackErrors = 0
                    playerRepository.playTrack(trackId, mp3Url, title, artist, coverUrl)
                } else {
                    // Fallback to on demand resolve
                    val demandUrl = preloadManager.getOrFetchUrl(trackId)
                    if (demandUrl != null) {
                        consecutiveTrackErrors = 0
                        playerRepository.playTrack(trackId, demandUrl, title, artist, coverUrl)
                    } else {
                        Log.e("MainViewModel", "Failed to resolve mp3 URL for track $trackId, auto-skipping")
                        if (++consecutiveTrackErrors < 5) {
                            playNext()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error playing track", e)
                // Fallback attempt to play local if network failed
                if (downloadManager.isTrackDownloaded(trackId)) {
                    consecutiveTrackErrors = 0
                    playerRepository.playTrack(trackId, "", title, artist, coverUrl)
                } else if (++consecutiveTrackErrors < 5) {
                    playNext()
                }
            }
        }
    }

    fun playNext() {
        val currentTrack = _currentTrack.value ?: return
        if (currentTrack.isRadio) {
            val currentRadioId = currentTrack.idString.removePrefix("radio_")
            val stations = radioStations.value
            val currentIndex = stations.indexOfFirst { it.id == currentRadioId }
            if (currentIndex != -1) {
                val nextIndex = (currentIndex + 1) % stations.size
                playRadioStation(stations[nextIndex])
            }
            return
        }

        val currentId = currentTrack.idString
        
        // Notify Rotor track finished
        if (_currentSource.value == PlaylistSource.VIBE) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    api.sendRotorFeedback("trackFinished", currentId)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error sending rotor trackFinished", e)
                }
            }
        }

        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == currentId }
        
        if (currentIndex != -1 && currentIndex + 1 < playlist.size) {
            playTrack(playlist[currentIndex + 1].idString, playlist)
        } else if (_currentSource.value == PlaylistSource.VIBE) {
            // We are at the end of the wave playlist! Don't stop.
            // Try to play from cached library or wait for fetch.
            fetchMoreVibeTracks(prefetchOnly = false) { success ->
                val updatedPlaylist = _currentPlaylist.value
                val newIndex = updatedPlaylist.indexOfFirst { it.idString == currentId }
                if (newIndex != -1 && newIndex + 1 < updatedPlaylist.size) {
                    playTrack(updatedPlaylist[newIndex + 1].idString, updatedPlaylist)
                } else {
                    // Fallback to library if we totally failed to get new vibe tracks
                    playAllLikedTracks()
                }
            }
        } else if (playlist.isNotEmpty()) {
            // Wrap around: Loop playlist back to start so playback continues seamlessly
            playTrack(playlist.first().idString, playlist)
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(positionMs)
        }
    }

    fun playPrev() {
        val currentTrack = _currentTrack.value ?: return
        
        // Return to start if played > 3 seconds
        val currentProgress = playerRepository.progress.value
        if (currentProgress > 3000L) {
            playerRepository.seekTo(0)
            return
        }

        if (currentTrack.isRadio) {
            val currentRadioId = currentTrack.idString.removePrefix("radio_")
            val stations = radioStations.value
            val currentIndex = stations.indexOfFirst { it.id == currentRadioId }
            if (currentIndex != -1) {
                val prevIndex = if (currentIndex - 1 < 0) stations.size - 1 else currentIndex - 1
                playRadioStation(stations[prevIndex])
            }
            return
        }

        val currentId = currentTrack.idString
        val playlist = _currentPlaylist.value
        val currentIndex = playlist.indexOfFirst { it.idString == currentId }
        
        if (currentIndex > 0) {
            playTrack(playlist[currentIndex - 1].idString, playlist)
        }
    }
    
    fun reorderQueue(from: Int, to: Int) {
        val currentList = _currentPlaylist.value.toMutableList()
        val item = currentList.removeAt(from)
        currentList.add(to, item)
        _currentPlaylist.value = currentList
    }
    
    fun removeFromQueue(trackId: String) {
        val currentList = _currentPlaylist.value.toMutableList()
        currentList.removeAll { it.idString == trackId }
        _currentPlaylist.value = currentList
    }

    // --- Radio Logic ---

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

    fun playRadioStation(station: RadioStation) {
        // Create virtual TrackDetailsDto for Radio
        val radioTrack = TrackDetailsDto(
            id = kotlinx.serialization.json.JsonPrimitive("radio_${station.id}"),
            title = station.name,
            artists = listOf(com.korenskoy.yandexmusic.data.dto.ArtistDto(id = kotlinx.serialization.json.JsonPrimitive("0"), name = "Прямой эфир • ${station.frequency}")),
            coverUri = station.iconRes?.let { "android.resource://${BuildConfig.APPLICATION_ID}/$it" } ?: station.logoUrl.ifEmpty { null },
            durationMs = 0,
            isRadio = true
        )
        
        _currentTrack.value = radioTrack
        _currentPlaylist.value = emptyList() // Clear normal queue
        _currentSource.value = PlaylistSource.LIBRARY // or some other source
        
        viewModelScope.launch {
            try {
                playerRepository.playTrack(
                    trackId = radioTrack.idString,
                    url = station.streamUrl,
                    title = radioTrack.title,
                    artist = radioTrack.artists?.firstOrNull()?.name,
                    coverUrl = radioTrack.coverUri
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error playing radio station", e)
            }
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
            val result = updateManager.downloadApk(apkUrl) { progress ->
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

    fun installDownloadedUpdate() {
        val file = downloadedApkFile ?: return
        val result = updateManager.installApk(file)
        result.onFailure { err ->
            _updateStatusMessage.value = err.localizedMessage
        }
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
    }
}

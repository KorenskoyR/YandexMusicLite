package com.korenskoy.yandexmusic.data.account

import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.download.DownloadScheduler
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in session: syncing the user's data, and switching or leaving accounts. Everything that
 * is per user (likes, playlists, wave queue, history, caches) is reset here, in one place, so the
 * screens only ask for an action.
 */
@Singleton
class AccountSessionManager @Inject constructor(
    private val api: YandexMusicApi,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackController,
    private val libraryRepository: LibraryRepository,
    private val playlistsRepository: UserPlaylistsRepository,
    private val cacheManager: PlaylistCacheManager,
    private val downloadManager: TrackDownloadManager,
    private val downloadScheduler: DownloadScheduler
) {
    /** Syncs outlive any screen. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Session job failed", e) }
    )

    /** Runs [action] in the session scope: an account change is never cut short by a closing screen. */
    fun launch(action: suspend AccountSessionManager.() -> Unit) {
        scope.launch { action() }
    }

    /** Cached library and playlists, shown before any network. */
    suspend fun loadCached() {
        libraryRepository.loadCached()
        playlistsRepository.loadCached()
    }

    /** Likes, charts, playlists and the My Wave queue. Concurrent calls join the requests already running. */
    fun syncAll() {
        scope.launch { syncLikes() }
        scope.launch { libraryRepository.refreshCharts() }
        scope.launch {
            playlistsRepository.loadCached()
            playlistsRepository.refresh()
        }
        playbackController.primeWave()
    }

    /** Likes only (pull-to-refresh); new likes are downloaded when auto-download is on. */
    suspend fun syncLikes() {
        libraryRepository.loadCached()
        val changed = libraryRepository.syncLikes()
        if (changed && settingsRepository.autoDownloadLikedTracks.first()) downloadScheduler.downloadAllLiked()
    }

    /** Fills in the name and avatar of an account saved before they were known. */
    suspend fun refreshProfileIfIncomplete() = withContext(Dispatchers.IO) {
        if (authRepository.getToken().isNullOrEmpty()) return@withContext
        val active = authRepository.getActiveAccount() ?: return@withContext
        if (!active.avatarUrl.isNullOrEmpty() && active.displayName.isNotBlank() && !active.displayName.startsWith("UID:")) {
            return@withContext
        }
        try {
            val status = api.getAccountStatus().result ?: return@withContext
            val avatar = status.avatarUrl ?: runCatching { api.getYandexLoginInfo().avatarUrl }.getOrNull()
            authRepository.saveOrUpdateAccount(
                active.copy(
                    displayName = status.displayUserName,
                    login = status.account?.login?.ifBlank { active.login } ?: active.login,
                    avatarUrl = avatar ?: active.avatarUrl
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "Profile refresh: ${e.message}")
        }
    }

    suspend fun switchAccount(uid: String) {
        playbackController.stopAndClear()
        if (!authRepository.switchAccount(uid)) return
        // Wave queue, listening history, likes and playlists are per user
        playbackController.resetForAccount(authRepository.getActiveAccount()?.folderName ?: "default")
        resetUserData()
        libraryRepository.loadCached()
        syncAll()
    }

    /** Signs out of the active account (its cache goes too) and moves to the next saved one, if any. */
    suspend fun logoutCurrent() {
        val current = authRepository.getActiveAccount()
        playbackController.resetForAccount(null)
        withContext(Dispatchers.IO) { cacheManager.clearUserCache(current?.folderName ?: "default") }
        current?.uid?.let { authRepository.removeAccount(it) }

        val next = authRepository.getActiveAccount()
        if (next != null) switchAccount(next.uid) else resetUserData()
    }

    suspend fun logoutAll() {
        playbackController.resetForAccount(null)
        withContext(Dispatchers.IO) { cacheManager.clearAllUserCaches() }
        authRepository.clearAllAccounts()
        resetUserData()
    }

    suspend fun removeAccount(uid: String) {
        val target = authRepository.getAllAccounts().firstOrNull { it.uid == uid }
        if (target != null) withContext(Dispatchers.IO) { cacheManager.clearUserCache(target.folderName) }
        if (authRepository.getActiveAccount()?.uid == uid) logoutCurrent() else authRepository.removeAccount(uid)
    }

    /** Drops the active user's cached lists and downloaded tracks no other saved account has liked, then reloads. */
    suspend fun clearMusicCache() {
        withContext(Dispatchers.IO) {
            val activeUid = authRepository.getActiveAccount()?.uid
            cacheManager.clearUserCache(activeFolder)
            val protectedTrackIds = authRepository.getAllAccounts()
                .filter { it.uid != activeUid }
                .flatMapTo(mutableSetOf()) { other -> cacheManager.loadLikedTracks(other.folderName).map { it.idString } }
            libraryRepository.likedTracks.value.map { it.idString }
                .filterNot { it in protectedTrackIds }
                .forEach { downloadManager.removeTrack(it) }
        }
        reloadAfterCacheClear()
    }

    private suspend fun reloadAfterCacheClear() {
        resetUserData()
        playbackController.clearWaveAndHistory()
        scope.launch { syncLikes() }
    }

    private fun resetUserData() {
        libraryRepository.resetForAccountChange()
        // Also closes an opened playlist: it is no longer in the list
        playlistsRepository.resetForAccountChange()
    }

    private val activeFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    private companion object {
        const val TAG = "AccountSession"
    }
}

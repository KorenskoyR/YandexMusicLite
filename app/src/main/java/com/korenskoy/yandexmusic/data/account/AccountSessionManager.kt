package com.korenskoy.yandexmusic.data.account

import com.korenskoy.yandexmusic.data.download.OfflineDownloads
import com.korenskoy.yandexmusic.data.download.OfflineSync
import kotlinx.coroutines.CancellationException
import android.util.Log
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.download.DownloadScheduler
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.data.repository.LikedAlbumsRepository
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import com.korenskoy.yandexmusic.data.player.LinkState
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    private val albumsRepository: LikedAlbumsRepository,
    private val networkMonitor: NetworkMonitor,
    private val cacheManager: PlaylistCacheManager,
    private val downloadManager: TrackDownloadManager,
    private val downloadScheduler: DownloadScheduler,
    private val offline: OfflineDownloads,
    private val offlineSync: OfflineSync,
    private val httpClient: dagger.Lazy<okhttp3.OkHttpClient>
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
        albumsRepository.loadCached()
    }

    /** Likes, charts, playlists and the My Wave queue. Concurrent calls join the requests already running. */
    fun syncAll() {
        scope.launch { refreshAccountStatus() }
        scope.launch { libraryRepository.refreshCharts() }
        syncLibraryNow()
        playbackController.primeWave()
    }

    private var libraryJob: Job? = null
    private var librarySyncedAt: Long? = null

    /**
     * Likes, playlists (own and saved) and albums from the server, all at once; kept playlists follow.
     * A sync already running is joined, never doubled. Each list is light: likes and albums ask for ids
     * first and fetch more only when something changed, a playlist's tracks only for a new revision.
     */
    fun syncLibraryNow(): Job {
        libraryJob?.takeIf { it.isActive }?.let { return it }
        librarySyncedAt = clock()
        return scope.launch {
            coroutineScope {
                launch { syncLikes() }
                launch { albumsRepository.loadCached(); albumsRepository.refresh() }
                launch {
                    playlistsRepository.loadCached()
                    playlistsRepository.refresh()
                    // Kept playlists follow what changed on the server
                    offlineSync.refreshAll()
                }
            }
        }.also { libraryJob = it }
    }

    /**
     * The same as [syncLibraryNow] when the last sync is older than [maxAgeMs] — the app coming to the screen,
     * the library opened, the timer while the app is shown. Skipped without a sign-in or a network, and while
     * the music stream is struggling: the music comes first.
     */
    fun syncLibraryIfStale(maxAgeMs: Long) {
        if (authRepository.getToken().isNullOrEmpty()) return
        if (!LibrarySyncPolicy.due(librarySyncedAt, clock(), maxAgeMs)) return
        if (!networkMonitor.isCurrentlyConnected() || playbackController.linkState.value != LinkState.GOOD) return
        syncLibraryNow()
    }

    private val clock: () -> Long = SystemClock::elapsedRealtime

    /** Likes only; new likes are downloaded when auto-download is on. */
    suspend fun syncLikes() {
        libraryRepository.loadCached()
        val changed = libraryRepository.syncLikes()
        if (changed && settingsRepository.autoDownloadLikedTracks.first()) downloadScheduler.downloadAllLiked()
    }

    /** Refreshes account subscription status (hasPlus) and profile info from the server. */
    suspend fun refreshAccountStatus() = withContext(Dispatchers.IO) {
        if (authRepository.getToken().isNullOrEmpty()) return@withContext
        val active = authRepository.getActiveAccount() ?: return@withContext
        try {
            val status = api.getAccountStatus().result ?: return@withContext
            val avatar = status.avatarUrl ?: runCatching { api.getYandexLoginInfo().avatarUrl }.getOrNull()
            authRepository.saveOrUpdateAccount(
                active.copy(
                    // The server has just said who this is: a provisional id gives way to the real one
                    uid = status.uidString.takeIf { !active.hasRealUid && it.isNotEmpty() } ?: active.uid,
                    displayName = status.displayUserName.ifBlank { active.displayName },
                    login = status.account?.login?.ifBlank { active.login } ?: active.login,
                    avatarUrl = avatar ?: active.avatarUrl,
                    hasPlus = status.hasPlus
                )
            )
            Log.i(TAG, "Account status refreshed: hasPlus=${status.hasPlus}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Account status refresh: ${e.message}")
        }
    }

    /** Fills in the name and avatar of an account saved before they were known. */
    suspend fun refreshProfileIfIncomplete() = withContext(Dispatchers.IO) {
        refreshAccountStatus()
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

    /**
     * Drops the active user's cached lists and downloaded tracks (kept playlists included) no other saved
     * account has liked, then reloads.
     */
    suspend fun clearMusicCache() {
        withContext(Dispatchers.IO) {
            val activeUid = authRepository.getActiveAccount()?.uid
            cacheManager.clearUserCache(activeFolder)
            val protectedTrackIds = authRepository.getAllAccounts()
                .filter { it.uid != activeUid }
                .flatMapTo(mutableSetOf()) { other -> cacheManager.loadLikedTracks(other.folderName).map { it.idString } }
            val kept = offline.keptTrackIds()
            offline.forgetAll()
            (libraryRepository.likedTracks.value.map { it.idString } + kept)
                .distinct()
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
        // Answers kept by the network cache belong to the account that asked: another one never gets them
        scope.launch(Dispatchers.IO) {
            runCatching { httpClient.get().cache?.evictAll() }.onFailure { Log.w(TAG, "Network cache not cleared", it) }
        }
        libraryRepository.resetForAccountChange()
        // Also closes an opened playlist: it is no longer in the list
        playlistsRepository.resetForAccountChange()
        albumsRepository.resetForAccountChange()
        // A sync of the account just left must not stand in for the new one's
        libraryJob?.cancel()
        libraryJob = null
        librarySyncedAt = null
    }

    private val activeFolder: String
        get() = authRepository.getActiveAccount()?.folderName ?: "default"

    private companion object {
        const val TAG = "AccountSession"
    }
}

/** When the library is asked from the server again. Pure; unit-tested. */
object LibrarySyncPolicy {
    /** Again after the app comes back to the screen or the library opens, if older than this. */
    const val ON_SHOW_MS = 30_000L

    /** While the app is on the screen (a head unit never closes it): this often. */
    const val WHILE_SHOWN_MS = 3 * 60_000L

    fun due(lastSyncAt: Long?, now: Long, maxAgeMs: Long): Boolean = lastSyncAt == null || now - lastSyncAt >= maxAgeMs
}

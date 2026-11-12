package com.korenskoy.yandexmusic.presentation.main

import kotlinx.coroutines.flow.update
import com.korenskoy.yandexmusic.data.network.NetworkMonitor
import com.korenskoy.yandexmusic.data.download.OfflineDownloads
import com.korenskoy.yandexmusic.data.player.PlayRequests
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.flow.Flow
import com.korenskoy.yandexmusic.data.download.OfflineState
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository.AddTrackResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's playlists: the opened one, and edits (create, rename, delete, add and remove tracks).
 * The repository updates the list and the cache, so the library, the opened playlist and Android Auto
 * follow by themselves; edits only report their outcome to the user.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UserPlaylistsRepository,
    private val playbackController: PlaybackController,
    private val playRequests: PlayRequests,
    private val offline: OfflineDownloads,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val playlists: StateFlow<List<UserPlaylistDto>> = repository.playlists

    /** Other people's playlists saved to the collection. */
    val saved: StateFlow<List<UserPlaylistDto>> = repository.saved

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // region Opened playlist

    private val _opened = MutableStateFlow<OpenedPlaylist?>(null)
    val opened: StateFlow<OpenedPlaylist?> = _opened.asStateFlow()
    private var openJob: Job? = null

    init {
        // What was added to playlists without a connection goes out as soon as there is one
        networkMonitor.isConnectedFlow
            .onEach { online -> if (online) repository.flushPendingAdds() }
            .launchIn(viewModelScope)
        // Edits (here or in Android Auto's list), server refreshes and account changes
        combine(repository.playlists, repository.saved) { own, saved -> own + saved }.onEach { list ->
            val opened = _opened.value ?: return@onEach
            // The same owner first: an own playlist of an old cache (owner unknown) may share the number
            val now = list.firstOrNull { it.ownerUid == opened.playlist.ownerUid && it.kindString == opened.playlist.kindString }
                ?: list.firstOrNull { UserPlaylistsRepository.sameAs(it, opened.playlist) }
            when {
                now == null -> close() // deleted, or another account
                now.revision != opened.playlist.revision -> open(now, keepShownTracks = true)
                now != opened.playlist -> _opened.value = opened.copy(playlist = now)
            }
        }.launchIn(viewModelScope)
    }

    /** @param keepShownTracks reload in place (after an edit) instead of showing the spinner. */
    fun open(playlist: UserPlaylistDto, keepShownTracks: Boolean = false) {
        openJob?.cancel()
        val shown = _opened.value?.takeIf { keepShownTracks && UserPlaylistsRepository.sameAs(it.playlist, playlist) }?.tracks.orEmpty()
        val editable = repository.isOwn(playlist)
        _opened.value = OpenedPlaylist(playlist, shown, isLoading = shown.isEmpty(), editable = editable)
        openJob = viewModelScope.launch {
            val loaded = try {
                OpenedPlaylist(playlist, repository.tracks(playlist), isLoading = false, editable = editable)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playlist load failed", e)
                OpenedPlaylist(playlist, isLoading = false, failed = true, failReason = failureReason(e), editable = editable)
            }
            if (_opened.value?.playlist?.let { UserPlaylistsRepository.sameAs(it, playlist) } == true) _opened.value = loaded
            if (!loaded.failed) offline.sync(OfflineCollection.keyOf(playlist), playlist.title.orEmpty(), playlist.coverUri, loaded.tracks)
        }
    }

    // region On the device

    /** The opened playlist's download button. */
    fun offlineState(playlist: UserPlaylistDto): Flow<OfflineState> = offline.state(OfflineCollection.keyOf(playlist))

    /** Keeps the opened playlist on the device: its tracks are downloaded, and new ones follow. */
    fun downloadOpened() {
        val opened = _opened.value?.takeIf { !it.isLoading && !it.failed && it.tracks.isNotEmpty() } ?: return
        viewModelScope.launch {
            offline.download(OfflineCollection(OfflineCollection.keyOf(opened.playlist), opened.playlist.title.orEmpty(), opened.playlist.coverUri, opened.tracks))
        }
    }

    /** Stops the download, or takes the playlist off the device (it stays in the library). */
    fun removeOpenedFromDevice() {
        val playlist = _opened.value?.playlist ?: return
        viewModelScope.launch { offline.remove(OfflineCollection.keyOf(playlist)) }
    }

    // endregion

    fun close() {
        openJob?.cancel()
        _opened.value = null
    }

    fun play(shuffle: Boolean) {
        val tracks = _opened.value?.tracks.orEmpty().let { if (shuffle) it.shuffled() else it }
        tracks.firstOrNull()?.let {
            playRequests.userStarted()
            playbackController.playTrack(it.idString, tracks, PlaylistSource.LIBRARY)
        }
    }

    fun playTrack(trackId: String) {
        val tracks = _opened.value?.tracks ?: return
        playRequests.userStarted()
        playbackController.playTrack(trackId, tracks, PlaylistSource.LIBRARY)
    }

    // endregion

    // region Editing

    /** Creates a playlist; with [thenAdd] the track goes straight into it. */
    fun create(title: String, thenAdd: TrackDetailsDto? = null) = edit {
        val created = repository.create(title)
        if (thenAdd != null) {
            repository.addTrack(created, thenAdd)
            say(R.string.playlist_msg_added, created.title ?: title)
        } else {
            say(R.string.playlist_msg_created, created.title ?: title)
        }
    }

    fun rename(playlist: UserPlaylistDto, title: String) = edit {
        repository.rename(playlist, title)
        say(R.string.playlist_msg_renamed, title.trim())
    }

    fun delete(playlist: UserPlaylistDto) = edit {
        repository.delete(playlist)
        say(R.string.playlist_msg_deleted, playlist.title.orEmpty())
    }

    fun addTrack(playlist: UserPlaylistDto, track: TrackDetailsDto) = edit {
        val result = repository.addTrack(playlist, track)
        say(if (result == AddTrackResult.ALREADY_THERE) R.string.playlist_msg_already else R.string.playlist_msg_added, playlist.title.orEmpty())
    }

    // region «В плейлист» in the car: the panel stays open, each row says what became of the track

    private val _quickAdd = MutableStateFlow(QuickAdd())
    val quickAdd: StateFlow<QuickAdd> = _quickAdd.asStateFlow()

    /** The panel opens for [track]: the playlists that already have it are marked at once. */
    fun quickAddOpened(track: TrackDetailsDto) {
        val id = track.idString
        if (_quickAdd.value.trackId != id) _quickAdd.value = QuickAdd(trackId = id)
        viewModelScope.launch {
            val waiting = repository.pendingAdds.value.filter { it.trackId == id }.map { it.kind }.toSet()
            val known = repository.playlistsWith(id).associateWith { if (it in waiting) QuickMark.WAITING else QuickMark.ALREADY }
            _quickAdd.update { if (it.trackId != id) it else it.copy(marks = known + it.marks) }
        }
    }

    /** Adds [track] to [playlist]; without a connection it is queued and goes out later. Never twice. */
    fun quickAdd(playlist: UserPlaylistDto, track: TrackDetailsDto) {
        val id = track.idString
        val kind = playlist.kindString
        val title = playlist.title.orEmpty()
        if (_quickAdd.value.trackId != id) _quickAdd.value = QuickAdd(trackId = id)
        if (_quickAdd.value.marks[kind] != null) return
        mark(id, kind, QuickMark.ADDING)
        viewModelScope.launch {
            val outcome = try {
                if (repository.addTrack(playlist, track) == AddTrackResult.ALREADY_THERE) QuickMark.ALREADY else QuickMark.ADDED
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "No connection: «$title» gets the track later", e)
                repository.queueAdd(playlist, track)
                QuickMark.WAITING
            } catch (e: Exception) {
                Log.e(TAG, "Quick add failed", e)
                null
            }
            mark(id, kind, outcome)
            note(
                when (outcome) {
                    QuickMark.ADDED -> R.string.playlist_msg_added
                    QuickMark.ALREADY -> R.string.playlist_msg_already
                    QuickMark.WAITING -> R.string.car_playlist_note_waiting
                    else -> R.string.car_playlist_note_failed
                },
                title
            )
        }
    }

    /** A new playlist with [track] in it; that needs the connection. */
    fun quickCreate(title: String, track: TrackDetailsDto) {
        val id = track.idString
        viewModelScope.launch {
            try {
                val created = repository.create(title)
                mark(id, created.kindString, QuickMark.ADDING)
                repository.addTrack(created, track)
                mark(id, created.kindString, QuickMark.ADDED)
                note(R.string.playlist_msg_added, created.title ?: title.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "No connection for a new playlist", e)
                note(R.string.car_playlist_note_create_offline, title.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Quick create failed", e)
                note(R.string.car_playlist_note_failed, title.trim())
            }
        }
    }

    private fun mark(trackId: String, kind: String, mark: QuickMark?) {
        _quickAdd.update {
            if (it.trackId != trackId) it
            else it.copy(marks = if (mark == null) it.marks - kind else it.marks + (kind to mark))
        }
    }

    private fun note(resId: Int, title: String) {
        _quickAdd.update { it.copy(note = QuickNote(context.getString(resId, title), it.note?.id?.plus(1) ?: 0L)) }
    }

    // endregion

    fun removeTrack(playlist: UserPlaylistDto, trackId: String) = edit {
        repository.removeTrack(playlist, trackId)
        say(R.string.playlist_msg_removed, playlist.title.orEmpty())
    }

    /** Saves a new track order; when the playlist changed elsewhere meanwhile, reloads it instead. */
    fun reorder(playlist: UserPlaylistDto, shownOrder: List<String>, newOrder: List<String>) {
        if (shownOrder == newOrder) return
        viewModelScope.launch {
            try {
                repository.reorder(playlist, shownOrder, newOrder)
                say(R.string.playlist_msg_reordered, playlist.title.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserPlaylistsRepository.PlaylistChangedException) {
                _messages.tryEmit(context.getString(R.string.playlist_msg_changed_elsewhere))
                repository.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Reorder failed", e)
                _messages.tryEmit(context.getString(R.string.playlist_msg_failed))
            }
        }
    }

    fun setPublic(playlist: UserPlaylistDto, public: Boolean) = edit {
        repository.setPublic(playlist, public)
        say(if (public) R.string.playlist_msg_public else R.string.playlist_msg_private, playlist.title.orEmpty())
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playlist edit failed", e)
                _messages.tryEmit(context.getString(R.string.playlist_msg_failed))
            }
        }
    }

    private fun say(resId: Int, title: String) {
        _messages.tryEmit(context.getString(resId, title))
    }

    // endregion

    private companion object {
        const val TAG = "Playlists"
    }
}

/** What became of the track in a playlist through «В плейлист». */
enum class QuickMark { ADDING, ADDED, ALREADY, WAITING }

/** A short line at the bottom of «В плейлист»; [id] makes the same words show again. */
data class QuickNote(val text: String, val id: Long)

/** «В плейлист» for the track [trackId]: each playlist's mark by its kind, and the last note. */
data class QuickAdd(val trackId: String? = null, val marks: Map<String, QuickMark> = emptyMap(), val note: QuickNote? = null)

/** A playlist opened from the library, with its tracks once loaded. */
data class OpenedPlaylist(
    val playlist: UserPlaylistDto,
    val tracks: List<TrackDetailsDto> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    /** Short and technical: a tester's screenshot should say what to fix. */
    val failReason: String? = null,
    /** The user's own playlist; a saved one of someone else's is only played and downloaded. */
    val editable: Boolean = true
)

/** What went wrong with a request, in the fewest words: the HTTP status and the server's own error name, or the kind of failure. */
internal fun failureReason(e: Throwable): String = when (e) {
    is retrofit2.HttpException -> {
        val name = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            ?.let { Regex(""""name"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
        "HTTP ${e.code()}" + name?.let { " $it" }.orEmpty()
    }
    is SocketTimeoutException -> "TIMEOUT"
    is UnknownHostException -> "NO_DNS"
    is IOException -> "NETWORK"
    is SerializationException -> "BAD_RESPONSE"
    else -> e::class.java.simpleName
}

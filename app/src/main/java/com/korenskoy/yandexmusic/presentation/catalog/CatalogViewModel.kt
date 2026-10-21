package com.korenskoy.yandexmusic.presentation.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistBriefDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An artist or album page; [title] is shown while the page loads. */
sealed interface CatalogPage {
    val id: String
    val title: String

    data class Artist(override val id: String, override val title: String) : CatalogPage
    data class Album(override val id: String, override val title: String) : CatalogPage
}

data class CatalogPageState(
    val page: CatalogPage,
    val artist: ArtistBriefDto? = null,
    val album: AlbumDto? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false
)

/**
 * Artist and album pages, opened from search and the track menu. They stack: artist → album → artist…,
 * and Back returns through them.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _stack = MutableStateFlow<List<CatalogPageState>>(emptyList())
    val stack: StateFlow<List<CatalogPageState>> = _stack.asStateFlow()

    fun openArtist(id: String, name: String) = open(CatalogPage.Artist(id, name))

    fun openAlbum(id: String, title: String) = open(CatalogPage.Album(id, title))

    private fun open(page: CatalogPage) {
        if (_stack.value.lastOrNull()?.page == page) return
        _stack.update { it + CatalogPageState(page) }
        load(page)
    }

    fun retry() {
        val top = _stack.value.lastOrNull() ?: return
        replace(top.page) { it.copy(isLoading = true, failed = false) }
        load(top.page)
    }

    fun back() = _stack.update { it.dropLast(1) }

    fun closeAll() {
        _stack.value = emptyList()
    }

    /** Plays [tracks] from [trackId] (or the first one, shuffled if asked). */
    fun play(tracks: List<TrackDetailsDto>, trackId: String? = null, shuffle: Boolean = false) {
        val queue = if (shuffle) tracks.shuffled() else tracks
        val start = trackId ?: queue.firstOrNull()?.idString ?: return
        playbackController.playTrack(start, queue, PlaylistSource.SEARCH)
    }

    private fun load(page: CatalogPage) {
        viewModelScope.launch {
            try {
                when (page) {
                    is CatalogPage.Artist -> {
                        val artist = repository.artist(page.id)
                        replace(page) { it.copy(artist = artist, isLoading = false) }
                    }
                    is CatalogPage.Album -> {
                        val album = repository.album(page.id)
                        replace(page) { it.copy(album = album, isLoading = false) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CatalogViewModel", "Loading $page failed", e)
                replace(page) { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    private fun replace(page: CatalogPage, change: (CatalogPageState) -> CatalogPageState) =
        _stack.update { list -> list.map { if (it.page == page) change(it) else it } }
}

package com.korenskoy.yandexmusic.data.repository

import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import javax.inject.Inject
import javax.inject.Singleton

/** Track search, shared by the search screen and voice search in Android Auto. */
@Singleton
class SearchRepository @Inject constructor(
    private val api: YandexMusicApi
) {
    /** Throws on a network error. Lazy lists key items by id, so duplicates from the API are dropped. */
    suspend fun searchTracks(text: String): List<TrackDetailsDto> = search(text).tracks

    /** Tracks, artists and albums for a query. Throws on a network error. */
    suspend fun search(text: String): SearchResults {
        val result = api.search(text).result
        return SearchResults(
            tracks = result?.tracks?.results.orEmpty().distinctBy { it.idString },
            artists = result?.artists?.results.orEmpty().filter { it.idString != null }.distinctBy { it.idString }.take(MAX_ROW),
            albums = result?.albums?.results.orEmpty().filter { it.idString != null }.distinctBy { it.idString }.take(MAX_ROW)
        )
    }

    data class SearchResults(
        val tracks: List<TrackDetailsDto> = emptyList(),
        val artists: List<ArtistDto> = emptyList(),
        val albums: List<AlbumDto> = emptyList()
    )

    private companion object {
        const val MAX_ROW = 10
    }
}

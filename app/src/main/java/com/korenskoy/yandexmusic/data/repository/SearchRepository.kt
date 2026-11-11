package com.korenskoy.yandexmusic.data.repository

import kotlinx.serialization.json.Json
import com.korenskoy.yandexmusic.data.dto.SearchBestDto
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import javax.inject.Inject
import javax.inject.Singleton

/** Track search, shared by the search screen and voice search in Android Auto. */
@Singleton
class SearchRepository @Inject constructor(
    private val api: YandexMusicApi,
    private val json: Json
) {
    /** Throws on a network error. Lazy lists key items by id, so duplicates from the API are dropped. */
    suspend fun searchTracks(text: String): List<TrackDetailsDto> = search(text).tracks

    /** Tracks, artists and albums for a query. Throws on a network error. */
    suspend fun search(text: String): SearchResults {
        val result = api.search(text).result
        return SearchResults(
            best = result?.best?.let(::bestOf),
            tracks = result?.tracks?.results.orEmpty().distinctBy { it.idString },
            artists = result?.artists?.results.orEmpty().filter { it.idString != null }.distinctBy { it.idString }.take(MAX_ROW),
            albums = result?.albums?.results.orEmpty().filter { it.idString != null }.distinctBy { it.idString }.take(MAX_ROW)
        )
    }

    /** The best result as the app plays it; anything else (a playlist, a podcast) is not one. */
    private fun bestOf(best: SearchBestDto): Best? {
        val result = best.result ?: return null
        return runCatching {
            when (best.type) {
                "track" -> json.decodeFromJsonElement(TrackDetailsDto.serializer(), result).takeIf { it.idString.isNotEmpty() }?.let(Best::Track)
                "artist" -> json.decodeFromJsonElement(ArtistDto.serializer(), result).takeIf { it.idString != null }?.let(Best::Artist)
                "album" -> json.decodeFromJsonElement(AlbumDto.serializer(), result).takeIf { it.idString != null }?.let(Best::Album)
                else -> null
            }
        }.getOrNull()
    }

    sealed interface Best {
        data class Track(val track: TrackDetailsDto) : Best
        data class Artist(val artist: ArtistDto) : Best
        data class Album(val album: AlbumDto) : Best
    }

    data class SearchResults(
        val best: Best? = null,
        val tracks: List<TrackDetailsDto> = emptyList(),
        val artists: List<ArtistDto> = emptyList(),
        val albums: List<AlbumDto> = emptyList()
    )

    private companion object {
        const val MAX_ROW = 10
    }
}

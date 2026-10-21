package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistBriefDto
import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import com.korenskoy.yandexmusic.data.dto.SearchResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Response shapes of `artists/{id}/brief-info`, `albums/{id}/with-tracks` and `search`. */
class CatalogDtoTest {

    // Same settings as NetworkModule.provideJson
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun artistBriefInfo() {
        val body = """{"result":{
            "artist":{"id":41052,"name":"Кино","cover":{"type":"from-artist-photos","uri":"avatars.yandex.net/get-music-content/a/%%"},"ogImage":"x"},
            "popularTracks":[{"id":"1","title":"Группа крови","albums":[{"id":2,"title":"Группа крови"}]},{"id":"1","title":"Группа крови"}],
            "albums":[{"id":2,"title":"Группа крови","year":1988,"trackCount":11,"coverUri":"avatars.yandex.net/get-music-content/b/%%"}]
        }}"""
        val brief = json.decodeFromString<ResponseWrapper<ArtistBriefDto>>(body).result!!
        assertEquals("41052", brief.artist?.idString)
        assertEquals("avatars.yandex.net/get-music-content/a/%%", brief.artist?.coverUri)
        assertEquals(2, brief.popularTracks.size)
        assertEquals("Группа крови", brief.popularTracks[0].albums[0].title)
        assertEquals(1988, brief.albums.single().year)
    }

    @Test
    fun albumWithTracksFlattensDiscs() {
        val body = """{"result":{"id":2,"title":"Двойной","year":2001,"trackCount":3,
            "artists":[{"id":7,"name":"Исполнитель"}],
            "volumes":[[{"id":"a","title":"1"},{"id":"b","title":"2"}],[{"id":"c","title":"3"}]]}}"""
        val album = json.decodeFromString<ResponseWrapper<AlbumDto>>(body).result!!
        assertEquals("2", album.idString)
        assertEquals(listOf("a", "b", "c"), album.tracks.map { it.idString })
        assertEquals("7", album.artists.single().idString)
    }

    @Test
    fun searchCarriesArtistsAndAlbums() {
        val body = """{"result":{
            "tracks":{"results":[{"id":"1","title":"t"}]},
            "artists":{"results":[{"id":5,"name":"A"},{"id":0,"name":"Various"}]},
            "albums":{"results":[{"id":9,"title":"B","year":2020}]}
        }}"""
        val result = json.decodeFromString<ResponseWrapper<SearchResponseDto>>(body).result!!
        assertEquals("5", result.artists?.results?.get(0)?.idString)
        assertNull("id 0 is not a real artist", result.artists?.results?.get(1)?.idString)
        assertEquals("9", result.albums?.results?.single()?.idString)
    }
}

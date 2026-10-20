package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Response shapes of `users/{uid}/playlists/list` and `users/{uid}/playlists/{kind}`. */
class PlaylistDtoTest {

    // Same settings as NetworkModule.provideJson
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun playlistListParsesKindTitleCountAndCover() {
        val body = """
            {"invocationInfo":{"req-id":"x"},"result":[
              {"owner":{"uid":1,"login":"u"},"kind":1003,"title":"В дорогу","trackCount":42,"revision":7,
               "cover":{"type":"pic","uri":"avatars.yandex.net/get-music-user-playlist/1/abc/%%","custom":true},
               "visibility":"public","playlistUuid":"lk.123"},
              {"kind":1004,"title":"Микс","trackCount":3,"revision":2,
               "cover":{"type":"mosaic","itemsUri":["avatars.yandex.net/get-music-content/2/a/%%","avatars.yandex.net/get-music-content/2/b/%%"]}},
              {"kind":1005,"title":"Без обложки","trackCount":0,"revision":1}
            ]}
        """.trimIndent()

        val list = json.decodeFromString<ResponseWrapper<List<UserPlaylistDto>>>(body).result!!

        assertEquals(listOf("1003", "1004", "1005"), list.map { it.kindString })
        assertEquals("В дорогу", list[0].title)
        assertEquals(42, list[0].trackCount)
        assertEquals(7, list[0].revision)
        assertEquals("avatars.yandex.net/get-music-user-playlist/1/abc/%%", list[0].coverUri)
        assertEquals("avatars.yandex.net/get-music-content/2/a/%%", list[1].coverUri) // first mosaic tile
        assertNull(list[2].coverUri)
    }

    @Test
    fun playlistWithTracksKeepsOrderAndEmbeddedTracks() {
        val body = """
            {"result":{"kind":1003,"title":"В дорогу","trackCount":2,"revision":8,"tracks":[
              {"id":111,"timestamp":"2026-01-01T00:00:00+00:00","track":{"id":"111","title":"Первый","artists":[{"id":5,"name":"A"}]}},
              {"id":222,"timestamp":"2026-01-02T00:00:00+00:00"}
            ]}}
        """.trimIndent()

        val playlist = json.decodeFromString<ResponseWrapper<UserPlaylistDto>>(body).result!!

        assertEquals(8, playlist.revision)
        assertEquals(2, playlist.tracks.size)
        assertEquals("111", playlist.tracks[0].track?.idString)
        assertEquals("Первый", playlist.tracks[0].track?.title)
        assertNull("metadata is fetched separately", playlist.tracks[1].track)
        assertEquals("222", (playlist.tracks[1].id as kotlinx.serialization.json.JsonPrimitive).content)
    }
}

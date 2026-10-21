package com.korenskoy.yandexmusic.data.repository

import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.dto.ArtistBriefDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import javax.inject.Inject
import javax.inject.Singleton

/** Artist and album pages. Kept in memory for the session, so going back and forth costs no requests. */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: YandexMusicApi
) {
    private val artists = lruMap<String, ArtistBriefDto>()
    private val albums = lruMap<String, AlbumDto>()

    /** Throws on a network error. */
    suspend fun artist(id: String): ArtistBriefDto =
        synchronized(artists) { artists[id] } ?: (api.getArtistBriefInfo(id).result ?: error("Empty artist " + id))
            .let { brief -> brief.copy(popularTracks = brief.popularTracks.distinctBy { it.idString }) }
            .also { synchronized(artists) { artists[id] = it } }

    /** Throws on a network error. */
    suspend fun album(id: String): AlbumDto =
        synchronized(albums) { albums[id] } ?: (api.getAlbumWithTracks(id).result ?: error("Empty album " + id))
            .also { synchronized(albums) { albums[id] = it } }

    private fun <K, V> lruMap(): MutableMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > CACHE_SIZE
    }

    private companion object {
        const val CACHE_SIZE = 20
    }
}

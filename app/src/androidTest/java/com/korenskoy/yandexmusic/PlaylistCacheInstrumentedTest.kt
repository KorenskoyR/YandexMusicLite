package com.korenskoy.yandexmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.cache.CacheDatabase
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The Room cache behind [PlaylistCacheManager], and the one-time import of the old JSON files. */
@RunWith(AndroidJUnit4::class)
class PlaylistCacheInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val owners = listOf("cache_test_a", "cache_test_b", "cache_test_legacy")
    private lateinit var cache: PlaylistCacheManager

    @Before
    fun setUp() {
        cache = PlaylistCacheManager(context, json)
        wipe()
    }

    @After
    fun wipe() = runBlocking {
        owners.forEach { cache.clearUserCache(it) }
    }

    private fun track(id: String, title: String = "Трек $id") =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = "Исполнитель")))

    private fun storedTrackCount(vararg ids: String): Int {
        val db = CacheDatabase.get(context).openHelper.readableDatabase
        db.query("SELECT COUNT(*) FROM tracks WHERE id IN (${ids.joinToString { "'$it'" }})").use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }

    @Test
    fun oldJsonFilesAreImportedOnceAndRemoved() = runBlocking {
        val root = File(context.cacheDir, "playlists")
        val dir = File(root, "cache_test_legacy").apply { mkdirs() }
        File(dir, "liked_tracks.json").writeText(json.encodeToString(listOf(track("ct_l1"), track("ct_l2"))))
        File(dir, "likes_revision.txt").writeText("42")
        File(dir, "user_playlists.json").writeText("""[{"kind":1003,"title":"Старый","trackCount":1,"revision":7}]""")
        File(dir, "playlist_1003_7.json").writeText(json.encodeToString(listOf(track("ct_p1"))))
        File(dir, "wave_queue.json").writeText(json.encodeToString(listOf(track("ct_w1"))))
        File(dir, "recent_track_ids.json").writeText("""["ct_r2","ct_r1"]""")

        val fresh = PlaylistCacheManager(context, json) // a new instance imports what it finds

        assertEquals(listOf("ct_l1", "ct_l2"), fresh.loadLikedTracks("cache_test_legacy").map { it.idString })
        assertEquals(42, fresh.loadLikesRevision("cache_test_legacy"))
        assertEquals("Старый", fresh.loadUserPlaylists("cache_test_legacy").single().title)
        assertEquals(listOf("ct_p1"), fresh.loadPlaylistTracks("cache_test_legacy", "1003", 7).map { it.idString })
        assertEquals(listOf("ct_w1"), fresh.loadWaveQueue("cache_test_legacy").map { it.idString })
        assertEquals(listOf("ct_r2", "ct_r1"), fresh.loadRecentTrackIds("cache_test_legacy"))
        assertFalse("old files are removed", dir.exists())
    }

    @Test
    fun trackMetadataIsSharedAndOrphansAreRemoved() = runBlocking {
        cache.saveLikedTracks("cache_test_a", listOf(track("ct_1"), track("ct_2")))
        cache.saveWaveQueue("cache_test_a", listOf(track("ct_2", "Новое название"), track("ct_3")))

        assertEquals("one copy of a track's metadata", "Новое название", cache.loadLikedTracks("cache_test_a")[1].title)

        cache.saveWaveQueue("cache_test_a", listOf(track("ct_2")))
        assertEquals("ct_3 left every list", 2, storedTrackCount("ct_1", "ct_2", "ct_3"))

        cache.clearUserCache("cache_test_a")
        assertEquals(0, storedTrackCount("ct_1", "ct_2", "ct_3"))
    }

    @Test
    fun accountsAreIsolated() = runBlocking {
        cache.saveLikedTracks("cache_test_a", listOf(track("ct_a")))
        cache.saveLikedTracks("cache_test_b", listOf(track("ct_b"), track("ct_a")))
        cache.saveLikesRevision("cache_test_b", 9)

        cache.clearUserCache("cache_test_a")

        assertTrue(cache.loadLikedTracks("cache_test_a").isEmpty())
        assertEquals("the other account keeps its list and the shared metadata",
            listOf("ct_b", "ct_a"), cache.loadLikedTracks("cache_test_b").map { it.idString })
        assertEquals(9, cache.loadLikesRevision("cache_test_b"))
    }

    @Test
    fun playlistTracksBelongToOneRevision() = runBlocking {
        cache.savePlaylistTracks("cache_test_a", "1003", 5, listOf(track("ct_1"), track("ct_2")))
        assertTrue("other revision", cache.loadPlaylistTracks("cache_test_a", "1003", 6).isEmpty())

        cache.savePlaylistTracks("cache_test_a", "1003", 6, listOf(track("ct_2")))
        assertTrue("old revision replaced", cache.loadPlaylistTracks("cache_test_a", "1003", 5).isEmpty())
        assertEquals(listOf("ct_2"), cache.loadPlaylistTracks("cache_test_a", "1003", 6).map { it.idString })
        assertTrue("another playlist is separate", cache.loadPlaylistTracks("cache_test_a", "10030", 6).isEmpty())

        cache.deletePlaylistTracks("cache_test_a", "1003")
        assertTrue(cache.loadPlaylistTracks("cache_test_a", "1003", 6).isEmpty())
    }

    @Test
    fun listsKeepOrderAndDuplicates() = runBlocking {
        cache.saveRecentTrackIds("cache_test_a", listOf("ct_3", "ct_1", "ct_3"))
        assertEquals(listOf("ct_3", "ct_1", "ct_3"), cache.loadRecentTrackIds("cache_test_a"))

        cache.saveLikedTracks("cache_test_a", listOf(track("ct_2"), track("ct_1"), track("ct_2")))
        assertEquals(listOf("ct_2", "ct_1", "ct_2"), cache.loadLikedTracks("cache_test_a").map { it.idString })
    }
}

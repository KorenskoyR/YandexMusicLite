package com.korenskoy.yandexmusic.data.download

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What stays on the device when playlists are kept, changed and taken off. */
class OfflineRulesTest {

    @get:Rule
    val dir = TemporaryFolder()

    private fun t(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "Трек $id")
    private fun ids(list: List<TrackDetailsDto>) = list.map { it.idString }
    private fun kept(key: String, vararg trackIds: String) = OfflineCollection(key, key, null, trackIds.map(::t))

    @Test
    fun aTrackStaysWhileLikedOrInAnyKeptPlaylist() {
        val road = kept("playlist:me:1", "a", "b", "c")
        val dacha = kept("playlist:me:2", "c", "d")
        val stillKept = OfflineRules.kept(listOf(dacha), liked = setOf("a"))
        // "В дорогу" taken off: "a" is liked, "c" is in "Дача" — only "b" goes
        assertEquals(listOf("b"), OfflineRules.toDelete(road.trackIds, stillKept))
        // A like taken back: the track of a kept playlist stays
        assertTrue("d" in OfflineRules.kept(listOf(road, dacha), liked = emptySet()))
    }

    @Test
    fun missingIsWhatIsNotOnTheDeviceYetEachOnce() {
        val road = kept("playlist:me:1", "a", "b", "b", "c")
        assertEquals(listOf("b", "c"), ids(OfflineRules.missing(road, downloaded = setOf("a"))))
        assertTrue(OfflineRules.missing(road, setOf("a", "b", "c")).isEmpty())
    }

    @Test
    fun downloadedListsLikedFirstThenThePlaylistsOnlyWhatIsOnTheDevice() {
        val liked = listOf(t("a"), t("b"))
        val road = kept("playlist:me:1", "b", "c", "x")
        assertEquals(listOf("a", "b", "c"), ids(OfflineRules.downloadedTracks(liked, listOf(road), downloaded = setOf("a", "b", "c"))))
    }

    @Test
    fun theButtonsState() {
        val road = kept("playlist:me:1", "a", "b", "c")
        assertEquals(OfflineState.None, OfflineRules.stateOf(null, running = false, currentTrackId = null, downloaded = emptySet(), bytes = 0))
        assertEquals(OfflineState.Downloading(1, 3, "b"), OfflineRules.stateOf(road, running = true, currentTrackId = "b", downloaded = setOf("a"), bytes = 10))
        val done = OfflineRules.stateOf(road, running = false, currentTrackId = null, downloaded = setOf("a", "b", "c"), bytes = 30) as OfflineState.Done
        assertTrue(done.complete)
        // Some can't be fetched now: done, but says how many are on the device
        val partly = OfflineRules.stateOf(road, running = false, currentTrackId = null, downloaded = setOf("a"), bytes = 10) as OfflineState.Done
        assertEquals(1, partly.done)
        assertTrue(!partly.complete)
        // A finished worker still marked running shows done, not "3 из 3"
        assertTrue(OfflineRules.stateOf(road, running = true, currentTrackId = null, downloaded = setOf("a", "b", "c"), bytes = 30) is OfflineState.Done)
    }

    @Test
    fun keptPlaylistsSurviveARestart() = runTest {
        val file = dir.newFile("kept.json").apply { delete() }
        val store = OfflineCollections(file)
        store.save(kept("playlist:me:1", "a", "b"))
        store.save(kept("album:7", "c"))
        store.save(kept("playlist:me:1", "a", "b", "d")) // updated in place
        val again = OfflineCollections(file)
        assertEquals(listOf("playlist:me:1", "album:7"), again.all.value.map { it.key })
        assertEquals(listOf("a", "b", "d"), again.get("playlist:me:1")?.trackIds)
        assertEquals(setOf("a", "b", "d", "c"), again.keptTrackIds())
        again.remove("album:7")
        assertNull(OfflineCollections(file).get("album:7"))
        assertTrue(OfflineCollections(file).get("album:7") == null && OfflineCollections(file).get("playlist:me:1") != null)
    }

    @Test
    fun keysOfPlaylistsAndAlbums() {
        val album = OfflineCollection(OfflineCollection.albumKey("123"), "Альбом")
        assertTrue(album.isAlbum)
        assertEquals("123", album.itemId)
        val playlist = kept("playlist:42:1003")
        assertTrue(!playlist.isAlbum)
        assertEquals("1003", playlist.itemId)
    }
}

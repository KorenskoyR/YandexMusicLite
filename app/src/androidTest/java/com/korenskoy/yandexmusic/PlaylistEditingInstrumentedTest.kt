package com.korenskoy.yandexmusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.PlaylistTrackDto
import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import com.korenskoy.yandexmusic.data.dto.TrackAlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository.AddTrackResult
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/**
 * Playlist edits against an in-memory "server" that applies the diffs it receives, with the real
 * on-disk cache: positions, revisions, album ids, and the cache after success and failure.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistEditingInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = UserAccount(uid = "4242", login = "playlist_edit_test", token = "t")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private lateinit var server: FakeServer
    private lateinit var cache: PlaylistCacheManager
    private lateinit var repository: UserPlaylistsRepository

    @Before
    fun setUp() {
        server = FakeServer()
        cache = PlaylistCacheManager(context, json)
        runBlocking { cache.clearUserCache(account.folderName) }
        val auth = SingleAccountAuth(account)
        repository = UserPlaylistsRepository(server, auth, LibraryRepository(server, auth, cache), cache)
        runBlocking { assertTrue(repository.refresh()) }
    }

    @After
    fun tearDown() {
        runBlocking { cache.clearUserCache(account.folderName) }
    }

    private val playlist get() = repository.playlists.value.single { it.kindString == "1003" }

    @Test
    fun addedTrackGoesOnTopWithItsAlbumAndOpensWithoutNetwork() = runBlocking {
        assertEquals(listOf("1", "2"), repository.tracks(playlist).map { it.idString })

        val result = repository.addTrack(playlist, track("3"))

        assertEquals(AddTrackResult.ADDED, result)
        assertEquals("""[{"op":"insert","at":0,"tracks":[{"id":"3","albumId":"300"}]}]""", server.diffs.single().first)
        assertEquals(listOf("3", "1", "2"), server.trackIds())
        assertEquals(server.revision, playlist.revision)
        assertEquals(3, playlist.trackCount)

        val callsBefore = server.playlistCalls
        assertEquals(listOf("3", "1", "2"), repository.tracks(playlist).map { it.idString })
        assertEquals("the new revision is cached", callsBefore, server.playlistCalls)
    }

    @Test
    fun trackWithoutAlbumGetsItFromMetadata() = runBlocking {
        repository.addTrack(playlist, track("3").copy(albums = emptyList())) // e.g. from an older cache
        assertTrue(server.diffs.single().first.contains(""""albumId":"300""""))
    }

    @Test
    fun addingATrackAlreadyThereChangesNothing() = runBlocking {
        assertEquals(AddTrackResult.ALREADY_THERE, repository.addTrack(playlist, track("2")))
        assertTrue(server.diffs.isEmpty())
    }

    @Test
    fun removeDeletesEveryCopyFromTheEnd() = runBlocking {
        server.tracks.add("1" to "100") // [1, 2, 1]
        repository.removeTrack(playlist, "1")

        assertEquals(
            """[{"op":"delete","from":2,"to":3},{"op":"delete","from":0,"to":1}]""",
            server.diffs.single().first
        )
        assertEquals(listOf("2"), server.trackIds())
        assertEquals(listOf("2"), repository.tracks(playlist).map { it.idString })
    }

    @Test
    fun editUsesTheServersCurrentRevision() = runBlocking {
        server.revision = 15 // changed on another device since the list was loaded
        repository.addTrack(playlist, track("3"))
        assertEquals(15, server.diffs.single().second)
    }

    @Test
    fun failedEditKeepsListAndCache() = runBlocking {
        val before = repository.tracks(playlist)
        val listBefore = repository.playlists.value
        server.failChanges = true
        try {
            repository.addTrack(playlist, track("3"))
            fail("the server error must reach the caller")
        } catch (_: IllegalStateException) {
        }
        assertEquals(listBefore, repository.playlists.value)
        assertEquals(before, cache.loadPlaylistTracks(account.folderName, "1003", playlist.revision))
    }

    @Test
    fun reorderSavesTheNewOrderAsOneChange() = runBlocking {
        server.tracks.add("3" to "300") // [1, 2, 3]
        repository.reorder(playlist, listOf("1", "2", "3"), listOf("3", "1", "2"))

        assertEquals(listOf("3", "1", "2"), server.trackIds())
        assertEquals("one request", 1, server.diffs.size)
        assertEquals(listOf("3", "1", "2"), repository.tracks(playlist).map { it.idString })
    }

    @Test
    fun reorderOfAPlaylistChangedElsewhereSavesNothing() = runBlocking {
        server.tracks.add("3" to "300") // changed on another device after the user saw [1, 2]
        try {
            repository.reorder(playlist, listOf("1", "2"), listOf("2", "1"))
            fail("must report the change")
        } catch (_: UserPlaylistsRepository.PlaylistChangedException) {
        }
        assertTrue(server.diffs.isEmpty())
        assertEquals(listOf("1", "2", "3"), server.trackIds())
    }

    @Test
    fun visibilityIsSaved() = runBlocking {
        repository.setPublic(playlist, true)
        assertEquals("public", server.visibility)
        assertTrue(playlist.isPublic)
        repository.setPublic(playlist, false)
        assertEquals("private", server.visibility)
        assertFalse(playlist.isPublic)
    }

    @Test
    fun createRenameDelete() = runBlocking {
        val created = repository.create("  Новый  ")
        assertEquals("Новый", server.createdTitle)
        assertEquals(listOf(created.kindString, "1003"), repository.playlists.value.map { it.kindString })

        repository.tracks(playlist) // cached under the old revision
        repository.rename(playlist, "В дорогу")
        assertEquals("В дорогу", playlist.title)
        val callsBefore = server.playlistCalls
        assertEquals("renamed playlist still opens from the cache", listOf("1", "2"), repository.tracks(playlist).map { it.idString })
        assertEquals(callsBefore, server.playlistCalls)

        repository.delete(created)
        assertEquals(listOf("1003"), repository.playlists.value.map { it.kindString })
        assertEquals("list is cached", listOf("1003"), cache.loadUserPlaylists(account.folderName).map { it.kindString })
    }

    private fun track(id: String) = TrackDetailsDto(
        id = JsonPrimitive(id),
        title = "Трек $id",
        artists = listOf(ArtistDto(name = "Исполнитель")),
        albums = listOf(TrackAlbumDto(JsonPrimitive("${id}00")))
    )

    /** One user playlist (kind 1003) that applies `change-relative` diffs like the real API. */
    private inner class FakeServer : YandexMusicApi by unimplemented() {
        var revision = 10
        var title = "Мой плейлист"
        val tracks = mutableListOf("1" to "100", "2" to "200")
        val diffs = mutableListOf<Pair<String, Int>>()
        var failChanges = false
        var playlistCalls = 0
        var createdTitle: String? = null
        var visibility = "private"

        fun trackIds() = tracks.map { it.first }

        private fun dto(withTracks: Boolean) = UserPlaylistDto(
            kind = JsonPrimitive(1003), title = title, trackCount = tracks.size, revision = revision,
            tracks = if (withTracks) tracks.map { (id, album) ->
                PlaylistTrackDto(id = JsonPrimitive(id.toInt()), albumId = JsonPrimitive(album.toInt()), track = track(id))
            } else emptyList()
        )

        override suspend fun getUserPlaylists(userId: String) = ResponseWrapper(result = listOf(dto(false)))

        override suspend fun getUserPlaylist(userId: String, kind: String): ResponseWrapper<UserPlaylistDto> {
            playlistCalls++
            return ResponseWrapper(result = dto(true))
        }

        override suspend fun getTracksMetadata(trackIds: List<String>) = ResponseWrapper(result = trackIds.map { track(it) })

        override suspend fun changePlaylist(userId: String, kind: String, diff: String, revision: Int): ResponseWrapper<UserPlaylistDto> {
            if (failChanges) throw IllegalStateException("HTTP 500")
            check(revision == this.revision) { "wrong revision" }
            diffs += diff to revision
            for (op in json.parseToJsonElement(diff).jsonArray.map { it.jsonObject }) {
                when (op["op"]!!.jsonPrimitive.content) {
                    "insert" -> op["tracks"]!!.jsonArray.reversed().forEach { t ->
                        val o = t.jsonObject
                        tracks.add(op["at"]!!.jsonPrimitive.int, o["id"]!!.jsonPrimitive.content to o["albumId"]!!.jsonPrimitive.content)
                    }
                    "delete" -> repeat(op["to"]!!.jsonPrimitive.int - op["from"]!!.jsonPrimitive.int) {
                        tracks.removeAt(op["from"]!!.jsonPrimitive.int)
                    }
                }
            }
            this.revision++
            return ResponseWrapper(result = dto(false))
        }

        override suspend fun createPlaylist(userId: String, title: String, visibility: String): ResponseWrapper<UserPlaylistDto> {
            createdTitle = title
            return ResponseWrapper(result = UserPlaylistDto(kind = JsonPrimitive(1010), title = title, revision = 1))
        }

        override suspend fun renamePlaylist(userId: String, kind: String, title: String): ResponseWrapper<UserPlaylistDto> {
            this.title = title
            revision++
            return ResponseWrapper(result = dto(false))
        }

        override suspend fun setPlaylistVisibility(userId: String, kind: String, value: String): ResponseWrapper<UserPlaylistDto> {
            visibility = value
            revision++
            return ResponseWrapper(result = dto(false).copy(visibility = value))
        }

        override suspend fun deletePlaylist(userId: String, kind: String): ResponseWrapper<JsonElement> =
            ResponseWrapper(result = JsonPrimitive("ok"))
    }
}

/** Only the active account is read by the playlist code. */
private class SingleAccountAuth(account: UserAccount) : AuthRepository {
    override val activeAccount = MutableStateFlow<UserAccount?>(account)
    override val allAccounts = MutableStateFlow(listOf(account))
    override val defaultAccountUid = MutableStateFlow<String?>(null)
    override fun getActiveAccount() = activeAccount.value
    override fun getAllAccounts() = allAccounts.value
    override fun getToken() = activeAccount.value?.token
    override fun isAuthorized() = flowOf(true)
    override fun getDefaultAccountUid(): String? = null
    override fun saveToken(token: String) = unsupported()
    override fun clearToken() = unsupported()
    override fun saveOrUpdateAccount(account: UserAccount) = unsupported()
    override fun switchAccount(uid: String): Boolean = unsupported()
    override fun removeAccount(uid: String): UserAccount? = unsupported()
    override fun clearAllAccounts() = unsupported()
    override fun setDefaultAccountUid(uid: String?) = unsupported()
    private fun unsupported(): Nothing = throw UnsupportedOperationException()
}

/** An implementation whose every call fails; delegate to it and override only what a test needs. */
private inline fun <reified T : Any> unimplemented(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException("Not faked: ${method.name}")
    } as T

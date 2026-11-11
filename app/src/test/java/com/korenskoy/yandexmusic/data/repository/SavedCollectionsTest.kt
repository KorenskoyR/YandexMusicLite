package com.korenskoy.yandexmusic.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.account.LibrarySyncPolicy
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.AlbumDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Albums and other people's playlists saved in the official app show up here, and a heart on an album page
 * goes to the server. The fake server answers like the real one does for `likes/albums` and `likes/playlists`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SavedCollectionsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val requested = mutableListOf<String>()
    private val posted = mutableListOf<String>()
    private var serverDown = false
    private var albumIds = listOf(5, 6)
    private var likeRefused = false

    private val server = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val rich = request.url.queryParameter("rich")
        requested += path + (rich?.let { "?rich=$it" } ?: "")
        request.body?.let { body -> posted += Buffer().also(body::writeTo).readUtf8() }
        when {
            serverDown -> respond(chain, 503, """{"error":{"name":"unavailable","message":""}}""")
            path == "/users/$ME/playlists/list" -> respond(chain, 200, """{"result":[{"uid":$ME,"kind":1003,"title":"Мой","revision":4}]}""")
            path == "/users/$ME/likes/playlists" -> respond(chain, 200, """{"result":[{"timestamp":"2026-11-01","playlist":{"uid":$OTHER,"kind":1003,"title":"Чужой","revision":2,"trackCount":1}}]}""")
            path == "/users/$ME/playlists/1003" -> respond(chain, 200, playlist(ME, 4, "111"))
            path == "/users/$OTHER/playlists/1003" -> respond(chain, 200, playlist(OTHER, 2, "222"))
            path == "/users/$ME/likes/albums" && rich == "false" ->
                respond(chain, 200, """{"result":[${albumIds.joinToString(",") { """{"id":$it,"timestamp":"2026-11-0$it"}""" }}]}""")
            path == "/users/$ME/likes/albums" ->
                respond(chain, 200, """{"result":[${albumIds.joinToString(",") { """{"id":$it,"album":{"id":$it,"title":"Альбом $it","year":2019,"artists":[{"id":1,"name":"Исполнитель"}]}}""" }}]}""")
            path.startsWith("/users/$ME/likes/albums/") ->
                if (likeRefused) respond(chain, 500, """{"error":{"name":"internal","message":""}}""") else respond(chain, 200, """{"result":"ok"}""")
            else -> respond(chain, 404, """{"error":{"name":"not-found","message":""}}""")
        }
    }

    private fun playlist(owner: String, revision: Int, track: String) =
        """{"result":{"uid":$owner,"kind":1003,"revision":$revision,"tracks":[{"id":$track,"track":{"id":"$track","title":"Трек $track"}}]}}"""

    private fun respond(chain: Interceptor.Chain, code: Int, body: String): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.music.yandex.net/")
        .client(OkHttpClient.Builder().addInterceptor(server).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YandexMusicApi::class.java)

    private val cache = PlaylistCacheManager(ApplicationProvider.getApplicationContext(), json)
    private val auth = FakeAuth(UserAccount(uid = ME, login = "saved_" + System.nanoTime(), token = "token"))
    private val library = LibraryRepository(api, auth, cache)

    @Test
    fun savedPlaylistsArriveAndKeepTheirTracksApartFromTheUsersOwn() = runBlocking {
        val playlists = UserPlaylistsRepository(api, auth, library, cache)
        playlists.refresh()

        val own = playlists.playlists.value.single()
        val saved = playlists.saved.value.single()
        assertEquals(OTHER, saved.ownerUid)
        assertTrue(playlists.isOwn(own))
        assertFalse("someone else's playlist is not edited here", playlists.isOwn(saved))

        assertEquals(listOf("111"), playlists.tracks(own).map { it.idString })
        assertEquals(listOf("222"), playlists.tracks(saved).map { it.idString })
        // Both have number 1003: offline each opens its own copy, not the other's
        serverDown = true
        assertEquals(listOf("111"), playlists.tracks(own).map { it.idString })
        assertEquals(listOf("222"), playlists.tracks(saved).map { it.idString })
    }

    @Test
    fun savedPlaylistsSurviveARestart() = runBlocking {
        UserPlaylistsRepository(api, auth, library, cache).refresh()
        serverDown = true

        val restarted = UserPlaylistsRepository(api, auth, library, cache)
        restarted.loadCached()

        assertEquals(listOf("Чужой"), restarted.saved.value.map { it.title })
    }

    @Test
    fun albumsAreFetchedWholeOnlyWhenTheListChanged() = runBlocking {
        val albums = LikedAlbumsRepository(api, auth, library, cache)

        assertTrue(albums.refresh())
        assertEquals(listOf("5", "6"), albums.albums.value.map { it.idString })
        assertEquals("Альбом 5", albums.albums.value.first().title)

        assertFalse("nothing changed: only the ids were asked for", albums.refresh())
        assertEquals(1, requested.count { it.endsWith("likes/albums?rich=true") })

        albumIds = listOf(7, 5, 6) // added in the official app
        assertTrue(albums.refresh())
        assertEquals(listOf("7", "5", "6"), albums.albums.value.map { it.idString })
        assertEquals(2, requested.count { it.endsWith("likes/albums?rich=true") })
    }

    @Test
    fun theHeartGoesToTheServerAndARefusalPutsItBack() = runBlocking {
        val albums = LikedAlbumsRepository(api, auth, library, cache)
        albums.refresh()
        val newOne = AlbumDto(id = JsonPrimitive(9), title = "Новый")

        albums.setLiked(newOne, liked = true)
        assertEquals(listOf("9", "5", "6"), albums.albums.value.map { it.idString })
        assertTrue(requested.contains("/users/$ME/likes/albums/add-multiple"))
        assertTrue(posted.any { it == "album-ids=9" })

        likeRefused = true
        try {
            albums.setLiked(albums.albums.value.first { it.idString == "5" }, liked = false)
            fail("the refusal must come through, so the screen can say so")
        } catch (_: Exception) {
        }
        assertEquals("the album is back", listOf("9", "5", "6"), albums.albums.value.map { it.idString })

        likeRefused = false
        albums.setLiked(albums.albums.value.first { it.idString == "5" }, liked = false)
        assertEquals(listOf("9", "6"), albums.albums.value.map { it.idString })
        assertTrue(requested.contains("/users/$ME/likes/albums/remove"))
    }

    @Test
    fun anUnconfirmedHeartIsShownOverTheServersList() {
        fun album(id: Int) = AlbumDto(id = JsonPrimitive(id))
        val server = listOf(album(1), album(2))
        val shown = LikedAlbumsRepository.overlay(
            server,
            listOf(LikedAlbumsRepository.Pending(album(3), liked = true), LikedAlbumsRepository.Pending(album(2), liked = false))
        )
        assertEquals(listOf("3", "1"), shown.map { it.idString })
        assertFalse(LikedAlbumsRepository.changed(listOf("1", "2"), server))
        assertTrue(LikedAlbumsRepository.changed(listOf("2", "1"), server))
    }

    @Test
    fun theLibraryIsAskedAgainOnlyWhenItIsDue() {
        assertTrue("never synced", LibrarySyncPolicy.due(null, 1_000, LibrarySyncPolicy.ON_SHOW_MS))
        assertFalse("just synced", LibrarySyncPolicy.due(1_000, 20_000, LibrarySyncPolicy.ON_SHOW_MS))
        assertTrue(LibrarySyncPolicy.due(1_000, 31_000, LibrarySyncPolicy.ON_SHOW_MS))
        assertFalse(LibrarySyncPolicy.due(0, 2 * 60_000, LibrarySyncPolicy.WHILE_SHOWN_MS))
        assertTrue(LibrarySyncPolicy.due(0, 3 * 60_000, LibrarySyncPolicy.WHILE_SHOWN_MS))
    }

    private class FakeAuth(account: UserAccount) : AuthRepository {
        override val activeAccount = MutableStateFlow<UserAccount?>(account)
        override val allAccounts = MutableStateFlow(listOf(account))
        override val defaultAccountUid = MutableStateFlow<String?>(null)
        override fun getActiveAccount() = activeAccount.value
        override fun getAllAccounts() = allAccounts.value
        override fun getToken() = activeAccount.value?.token
        override fun isAuthorized() = flowOf(true)
        override fun getDefaultAccountUid(): String? = null
        override fun saveToken(token: String) = Unit
        override fun clearToken() = Unit
        override fun saveOrUpdateAccount(account: UserAccount) {
            activeAccount.value = account
        }
        override fun switchAccount(uid: String) = false
        override fun removeAccount(uid: String): UserAccount? = null
        override fun clearAllAccounts() = Unit
        override fun setDefaultAccountUid(uid: String?) = Unit
    }

    private companion object {
        const val ME = "414787002"
        const val OTHER = "555000111"
    }
}

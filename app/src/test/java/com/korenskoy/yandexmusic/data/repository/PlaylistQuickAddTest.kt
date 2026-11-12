package com.korenskoy.yandexmusic.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackAlbumDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * «В плейлист» in the car: a track added without a connection waits on the device and goes out once there is
 * one; a playlist that has the track is known without asking; what the server refuses is not sent forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaylistQuickAddTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val changes = mutableListOf<String>()
    private var offline = false
    private var refuse = false

    private val server = Interceptor { chain ->
        if (offline) throw IOException("no network")
        val request = chain.request()
        val path = request.url.encodedPath
        when {
            path == "/users/$UID/playlists/list" -> respond(chain, 200, """{"result":[$ROCK]}""")
            path == "/users/$UID/playlists/1003" -> respond(chain, 200, """{"result":$ROCK_FULL}""")
            path == "/users/$UID/playlists/1003/change-relative" -> {
                changes += okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
                if (refuse) respond(chain, 400, """{"error":{"name":"wrong-revision","message":""}}""")
                else respond(chain, 200, """{"result":{"uid":$UID,"kind":1003,"title":"Рок","trackCount":2,"revision":9}}""")
            }
            else -> respond(chain, 404, """{"error":{"name":"not-found","message":""}}""")
        }
    }

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
    private val auth = FakeAuth(UserAccount(uid = UID, login = "driver", token = "token"))

    private fun repository() = UserPlaylistsRepository(api, auth, LibraryRepository(api, auth, cache), cache)

    private val newTrack = TrackDetailsDto(id = JsonPrimitive("222"), title = "Новая", albums = listOf(TrackAlbumDto(id = JsonPrimitive(5))))

    @Test
    fun aTrackAddedWithoutAConnectionGoesOutWhenThereIsOne() = runBlocking {
        val playlists = repository()
        playlists.refresh()
        val rock = playlists.playlists.value.single()
        offline = true

        try {
            playlists.addTrack(rock, newTrack)
            error("offline, so the add must fail")
        } catch (e: IOException) {
            playlists.queueAdd(rock, newTrack)
        }
        assertEquals(listOf("222"), playlists.pendingAdds.value.map { it.trackId })
        assertEquals("a queued track counts as there", setOf("1003"), playlists.playlistsWith("222"))

        // Still offline: nothing is lost
        playlists.flushPendingAdds()
        assertEquals(1, playlists.pendingAdds.value.size)

        offline = false
        // The queue lives on the device, a new start of the app finds it
        val restarted = repository()
        restarted.loadCached()
        restarted.flushPendingAdds()

        assertTrue("the track went to the server: $changes", changes.single().contains("222"))
        assertTrue(restarted.pendingAdds.value.isEmpty())
    }

    @Test
    fun aTrackTheServerRefusesIsNotSentAgain() = runBlocking {
        val playlists = repository()
        playlists.refresh()
        playlists.queueAdd(playlists.playlists.value.single(), newTrack)
        refuse = true

        playlists.flushPendingAdds()
        playlists.flushPendingAdds()

        assertEquals("sent once, then dropped", 1, changes.size)
        assertTrue(playlists.pendingAdds.value.isEmpty())
    }

    @Test
    fun withoutTheListOfPlaylistsTheQueueWaits() = runBlocking {
        // An account of its own: nothing of it is cached by the other tests
        val other = FakeAuth(UserAccount(uid = "555", login = "no_list", token = "token"))
        val playlists = UserPlaylistsRepository(api, other, LibraryRepository(api, other, cache), cache)
        playlists.queueAdd(rockOnly(), newTrack)

        // No list yet (not loaded, nothing cached): a queued track must not be taken for one of a deleted playlist
        playlists.flushPendingAdds()

        assertTrue(changes.isEmpty())
        assertEquals(1, playlists.pendingAdds.value.size)
    }

    @Test
    fun aPlaylistThatHasTheTrackIsKnownWithoutAsking() = runBlocking {
        val playlists = repository()
        playlists.refresh()
        playlists.tracks(playlists.playlists.value.single())

        assertEquals(setOf("1003"), playlists.playlistsWith("111"))
        assertTrue(playlists.playlistsWith("999").isEmpty())
    }

    @Test
    fun theSameTrackForTheSamePlaylistIsQueuedOnce() {
        val add = PendingPlaylistAdd("1003", newTrack)
        val queue = PendingPlaylistAdds.add(PendingPlaylistAdds.add(emptyList(), add), add.copy())
        assertEquals(1, queue.size)
        assertEquals(2, PendingPlaylistAdds.add(queue, PendingPlaylistAdd("1004", newTrack)).size)
    }

    @Test
    fun aQueuedTrackKeepsItsTitleAndAlbum() {
        val saved = PendingPlaylistAdds.save(PendingPlaylistAdd("1003", newTrack))
        val back = PendingPlaylistAdds.parse(saved)!!
        assertEquals("1003", back.kind)
        assertEquals("Новая", back.track.title)
        assertEquals("5", back.track.albumId)
        assertNull(PendingPlaylistAdds.parse("garbage"))
        assertNull(PendingPlaylistAdds.parse("1003\n{not json"))
        assertNull(PendingPlaylistAdds.parse("\n{}"))
    }

    private fun rockOnly() = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.UserPlaylistDto>(ROCK)

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
        const val UID = "414787002"
        const val ROCK = """{"uid":414787002,"kind":1003,"title":"Рок","trackCount":1,"revision":8}"""
        const val ROCK_FULL = """{"uid":414787002,"kind":1003,"title":"Рок","trackCount":1,"revision":8,"tracks":[{"id":111,"albumId":7,"track":{"id":"111","title":"Первая","albums":[{"id":7}]}}]}"""
    }
}

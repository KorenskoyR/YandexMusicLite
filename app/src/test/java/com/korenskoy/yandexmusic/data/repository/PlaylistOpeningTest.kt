package com.korenskoy.yandexmusic.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import com.korenskoy.yandexmusic.presentation.main.failureReason
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
 * Playlists that would not open. The server in these tests answers the way the real one did when asked
 * without a token: a provisional id in a playlist's address is refused with 400.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaylistOpeningTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val requested = mutableListOf<String>()
    private var serverDown = false

    private val server = Interceptor { chain ->
        val path = chain.request().url.encodedPath
        requested += path
        when {
            serverDown -> respond(chain, 503, """{"error":{"name":"unavailable","message":""}}""")
            path == "/account/status" -> respond(chain, 200, """{"result":{"account":{"uid":$REAL_UID,"login":"driver"},"plus":{"hasPlus":true}}}""")
            path == "/users/$REAL_UID/playlists/1003" -> respond(chain, 200, PLAYLIST)
            path.startsWith("/users/temp_") && path.contains("/playlists/1") ->
                respond(chain, 400, """{"error":{"name":"playlistIdBindingError","message":"No uid for playlistId found"}}""")
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

    private fun repositoryFor(auth: AuthRepository) =
        UserPlaylistsRepository(api, auth, LibraryRepository(api, auth, cache), cache)

    private val cachedPlaylist = UserPlaylistDto(kind = JsonPrimitive(1003), title = "Избранное", trackCount = 1, revision = 8)

    @Test
    fun anAccountWithAProvisionalIdOpensItsPlaylistsAndLearnsItsRealId() = runBlocking {
        val auth = FakeAuth(UserAccount(uid = "temp_1793921349367", login = "provisional", token = "token"))

        val tracks = repositoryFor(auth).tracks(cachedPlaylist)

        assertEquals(listOf("111"), tracks.map { it.idString })
        assertEquals(REAL_UID, auth.getActiveAccount()?.uid)
        assertTrue("the provisional id is what the server refuses", requested.none { it.startsWith("/users/temp_") })
    }

    @Test
    fun aRealIdIsNotAskedFor() = runBlocking {
        val auth = FakeAuth(UserAccount(uid = REAL_UID, login = "real", token = "token"))

        repositoryFor(auth).tracks(cachedPlaylist)

        assertFalse(requested.contains("/account/status"))
        assertEquals(listOf("/users/$REAL_UID/playlists/1003"), requested)
    }

    @Test
    fun theOwnerFromTheListGoesFirst() = runBlocking {
        val auth = FakeAuth(UserAccount(uid = "temp_1", login = "owner_first", token = "token"))

        repositoryFor(auth).tracks(cachedPlaylist.copy(uid = JsonPrimitive(REAL_UID.toLong())))

        assertEquals(listOf("/users/$REAL_UID/playlists/1003"), requested)
    }

    @Test
    fun theOwnerSurvivesACachedList() {
        val list = json.decodeFromString<List<UserPlaylistDto>>(json.encodeToString(listOf(cachedPlaylist.copy(uid = JsonPrimitive(REAL_UID.toLong())))))

        assertEquals(REAL_UID, list.single().ownerUid)
    }

    @Test
    fun aFailedServerShowsTheCopySavedLast() = runBlocking {
        val auth = FakeAuth(UserAccount(uid = REAL_UID, login = "saved_copy", token = "token"))
        val saved = listOf(TrackDetailsDto(id = JsonPrimitive("777"), title = "Старая версия"))
        cache.savePlaylistTracks(auth.getActiveAccount()!!.folderName, "1003", 5, saved)
        serverDown = true

        val tracks = repositoryFor(auth).tracks(cachedPlaylist)

        assertEquals(listOf("777"), tracks.map { it.idString })
    }

    @Test
    fun withNothingSavedTheFailureCarriesItsCode() = runBlocking {
        val auth = FakeAuth(UserAccount(uid = "other", login = "nothing_saved", token = "token"))
        serverDown = true

        try {
            repositoryFor(auth).tracks(cachedPlaylist)
            fail("nothing saved, so the failure must come through")
        } catch (e: Exception) {
            assertEquals("HTTP 503 unavailable", failureReason(e))
        }
    }

    @Test
    fun theReasonOfOtherFailuresIsShort() {
        assertEquals("TIMEOUT", failureReason(java.net.SocketTimeoutException()))
        assertEquals("NO_DNS", failureReason(java.net.UnknownHostException()))
        assertEquals("NETWORK", failureReason(java.io.IOException()))
        assertEquals("BAD_RESPONSE", failureReason(kotlinx.serialization.SerializationException()))
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
        const val REAL_UID = "414787002"
        const val PLAYLIST = """{"result":{"uid":414787002,"kind":1003,"title":"Избранное","trackCount":1,"revision":8,"tracks":[{"id":111,"track":{"id":"111","title":"Первая"}}]}}"""
    }
}

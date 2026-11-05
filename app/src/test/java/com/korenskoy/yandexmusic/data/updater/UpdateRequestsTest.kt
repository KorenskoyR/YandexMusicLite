package com.korenskoy.yandexmusic.data.updater

import android.app.Application
import android.content.Context
import com.korenskoy.yandexmusic.data.network.AuthInterceptor
import com.korenskoy.yandexmusic.di.NetworkModule
import com.korenskoy.yandexmusic.di.UpdateClient
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The token is for Yandex: the updater talks to GitHub and must not send it there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateRequestsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val shared = NetworkModule.provideOkHttpClient(AuthInterceptor(SignedIn(TOKEN)), context)
    private val update = NetworkModule.provideUpdateClient(shared)

    private val sent = mutableListOf<Request>()

    /** Answers instead of the network, after every interceptor of the client has run. */
    private fun OkHttpClient.answering(body: String) = newBuilder()
        .addInterceptor(Interceptor { chain ->
            sent += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        })
        .build()

    @Test
    fun yandexRequestsStillCarryTheToken() {
        val request = Request.Builder().url("https://api.music.yandex.net/account/status").build()

        shared.answering("{}").newCall(request).execute().close()

        assertEquals("OAuth $TOKEN", sent.single().header("Authorization"))
    }

    @Test
    fun checkingForUpdatesSendsNoToken() = runBlocking {
        val manager = GitHubUpdateManager(context, update.answering("""{"tag_name":"v0.0.1"}"""))

        val result = manager.checkForUpdates()

        assertTrue("the answer is still read: $result", result is UpdateCheckResult.UpToDate)
        val request = sent.single()
        assertEquals("api.github.com", request.url.host)
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-Yandex-Music-Client"))
        assertEquals("YandexMusicLite-Android", request.header("User-Agent"))
        assertEquals("application/vnd.github.v3+json", request.header("Accept"))
    }

    @Test
    fun downloadingTheApkSendsNoToken() = runBlocking {
        val manager = GitHubUpdateManager(context, update.answering("apk"))

        val result = manager.downloadApk("https://github.com/KorenskoyR/YandexMusicLite/releases/download/v1/a.apk", APK_SHA256) {}

        assertEquals("the file still arrives and passes the checksum", "apk", result.getOrThrow().readText())
        val request = sent.single()
        assertEquals("github.com", request.url.host)
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-Yandex-Music-Client"))
    }

    @Test
    fun onlyTheAuthIsTakenFromTheUpdateClient() {
        assertSame(shared.cache, update.cache)
        assertSame(shared.connectionPool, update.connectionPool)
        assertEquals(shared.connectTimeoutMillis, update.connectTimeoutMillis)
        assertEquals(shared.readTimeoutMillis, update.readTimeoutMillis)
        assertEquals(shared.interceptors.size - 1, update.interceptors.size)
    }

    @Test
    fun theManagerAsksForTheUpdateClient() {
        val parameters = GitHubUpdateManager::class.java.constructors.single().parameters
        val client = parameters.single { it.type == OkHttpClient::class.java }

        assertTrue(client.isAnnotationPresent(UpdateClient::class.java))
    }

    private class SignedIn(private val token: String) : AuthRepository {
        override val activeAccount = MutableStateFlow<UserAccount?>(null)
        override val allAccounts = MutableStateFlow(emptyList<UserAccount>())
        override val defaultAccountUid = MutableStateFlow<String?>(null)
        override fun getToken() = token
        override fun getActiveAccount(): UserAccount? = null
        override fun getAllAccounts() = emptyList<UserAccount>()
        override fun isAuthorized() = flowOf(true)
        override fun getDefaultAccountUid(): String? = null
        override fun saveToken(token: String) = Unit
        override fun clearToken() = Unit
        override fun saveOrUpdateAccount(account: UserAccount) = Unit
        override fun switchAccount(uid: String) = false
        override fun removeAccount(uid: String): UserAccount? = null
        override fun clearAllAccounts() = Unit
        override fun setDefaultAccountUid(uid: String?) = Unit
    }

    private companion object {
        const val TOKEN = "secret-token"
        const val APK_SHA256 = "dd37c2d7274f7ea982cb83390c36918fee9ce8889073c44b68cdc00bdb8c3e04"
    }
}

package com.korenskoy.yandexmusic.data.player

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.domain.model.UserAccount
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

/** Compatible sound: only the ordinary request goes out, and a saved FLAC file does not count as a copy to play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SoundModeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val requested = mutableListOf<String>()

    private val server = Interceptor { chain ->
        val url = chain.request().url
        requested += url.encodedPath
        val body = when {
            url.encodedPath.endsWith("/download-info") -> """{"result":[
                {"codec":"mp3","bitrateInKbps":192,"downloadInfoUrl":"https://storage.example/info"}
            ]}"""
            url.host == "storage.example" -> "<download-info><host>h.example</host><path>/p</path><ts>1</ts><s>s</s></download-info>"
            else -> """{"error":{"name":"unexpected","message":""}}"""
        }
        val ok = url.encodedPath.endsWith("/download-info") || url.host == "storage.example"
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(if (ok) 200 else 404)
            .message("")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private val client = OkHttpClient.Builder().addInterceptor(server).build()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.music.yandex.net/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YandexMusicApi::class.java)

    private fun mode(choice: Boolean?, decoder: Boolean = true) = SoundMode(flowOf(choice), {}, { decoder })

    private fun manager(mode: SoundMode, downloads: TrackDownloadManager = TrackDownloadManager(context, client)) =
        TrackPreloadManager(api, downloads, client, NoAuth, mode)

    /** Wait for the mode to load: [SoundMode.compatible] is the main thread's quick answer. */
    private suspend fun SoundMode.loaded(value: Boolean) = apply { withTimeout(2_000) { compatible.first { it == value } } }

    private fun saved(trackId: String, quality: String?): TrackDownloadManager {
        val dir = File(context.filesDir, "tracks").apply { mkdirs() }
        File(dir, "$trackId.mp3").writeBytes(ByteArray(16))
        File(dir, "$trackId.quality").apply { if (quality != null) writeText(quality) else delete() }
        return TrackDownloadManager(context, client) // indexes the folder as it starts
    }

    @Test
    fun aChoiceWinsOverTheDevice() = runBlocking {
        assertTrue(mode(true, decoder = true).isCompatible())
        assertFalse(mode(false, decoder = false).isCompatible())
    }

    @Test
    fun withNoChoiceOnlyADeviceWithoutADecoderIsCompatible() = runBlocking {
        assertFalse(mode(null, decoder = true).isCompatible())
        assertTrue(mode(null, decoder = false).isCompatible())
    }

    @Test
    fun aDeviceChecksItsDecodersOnce() = runBlocking {
        var checks = 0
        val mode = SoundMode(flowOf(null), {}) { checks++; true }
        repeat(3) { mode.isCompatible() }
        assertEquals(1, checks)
    }

    @Test
    fun choosingWaitsUntilTheQuickAnswerAgrees() = runBlocking {
        val saved = MutableStateFlow<Boolean?>(false)
        val mode = SoundMode(saved, { saved.value = it }, { true })
        mode.loaded(false)

        withTimeout(2_000) { mode.choose(true) }

        assertTrue(mode.compatible.value)
    }

    @Test
    fun compatibleDownloadsTakeOnlyTheOrdinaryStream() = runBlocking {
        val stream = manager(mode(true)).resolveForDownload("1")

        assertNotNull(stream)
        assertEquals("mp3", stream!!.quality.codec)
        assertEquals(192, stream.quality.bitrateKbps)
        assertEquals(listOf("/tracks/1/download-info", "/info"), requested)
    }

    @Test
    fun compatiblePlaybackTakesOnlyTheOrdinaryStream() = runBlocking {
        val manager = manager(mode(true))

        assertNotNull(manager.getOrFetchUrl("2"))

        assertEquals(192, manager.qualityOf("2")?.bitrateKbps)
        assertEquals(listOf("/tracks/2/download-info", "/info"), requested)
    }

    @Test
    fun inCompatibleSoundASavedFlacIsNotACopyToPlay() = runBlocking {
        val downloads = saved("10", "flac:1411")
        val manager = manager(mode(true).loaded(true), downloads)

        assertFalse(manager.hasPlayableCopy("10"))
        assertNull(manager.playableCopy("10"))
        assertNotNull("so the stream is fetched instead", manager.getOrFetchUrl("10"))
    }

    @Test
    fun aSavedMp3PlaysInCompatibleSound() = runBlocking {
        val downloads = saved("11", "mp3:192")
        val manager = manager(mode(true).loaded(true), downloads)

        assertTrue(manager.hasPlayableCopy("11"))
        assertNotNull(manager.playableCopy("11"))
    }

    @Test
    fun aFileFromBeforeQualitiesWereSavedPlaysInCompatibleSound() = runBlocking {
        val downloads = saved("12", null)
        val manager = manager(mode(true).loaded(true), downloads)

        assertTrue(manager.hasPlayableCopy("12"))
    }

    @Test
    fun outsideCompatibleSoundASavedFlacStaysACopy() = runBlocking {
        val downloads = saved("13", "flac:1411")
        val manager = manager(mode(false).loaded(false), downloads)

        assertTrue(manager.hasPlayableCopy("13"))
        assertNotNull(manager.playableCopy("13"))
    }

    private object NoAuth : AuthRepository {
        override val activeAccount = MutableStateFlow<UserAccount?>(null)
        override val allAccounts = MutableStateFlow(emptyList<UserAccount>())
        override val defaultAccountUid = MutableStateFlow<String?>(null)
        override fun getActiveAccount(): UserAccount? = null
        override fun getAllAccounts(): List<UserAccount> = emptyList()
        override fun getToken(): String? = null
        override fun isAuthorized() = flowOf(false)
        override fun getDefaultAccountUid(): String? = null
        override fun saveToken(token: String) = Unit
        override fun clearToken() = Unit
        override fun saveOrUpdateAccount(account: UserAccount) = Unit
        override fun switchAccount(uid: String) = false
        override fun removeAccount(uid: String): UserAccount? = null
        override fun clearAllAccounts() = Unit
        override fun setDefaultAccountUid(uid: String?) = Unit
    }
}

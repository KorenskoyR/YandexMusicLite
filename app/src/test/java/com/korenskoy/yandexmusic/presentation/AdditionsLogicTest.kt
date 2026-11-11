package com.korenskoy.yandexmusic.presentation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.download.OfflineCollection
import com.korenskoy.yandexmusic.data.download.OfflineRules
import com.korenskoy.yandexmusic.data.download.RemoveOutcome
import com.korenskoy.yandexmusic.data.download.TrackOfflineState
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.data.player.VoicePhrase
import com.korenskoy.yandexmusic.data.player.VoiceSearch
import com.korenskoy.yandexmusic.data.repository.CatalogRepository
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import com.korenskoy.yandexmusic.data.repository.SearchRepository
import com.korenskoy.yandexmusic.presentation.car.CarWide
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

/** The rules behind 1.14.1's additions: the car on a flat head unit, voice search, one track on the device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AdditionsLogicTest {

    // --- The car on a flat head unit ---

    @Test
    fun aFlatHeadUnitGetsThreeColumnsAndAUsualOneDoesNot() {
        // 1920×720: the row is 1832 × ~604 units
        val flat = CarWide.layout(width = 1832f, height = 604f)!!
        assertEquals("the cover as tall as the row allows", 600f, flat.cover)
        assertTrue("the player bigger, not too much: ${flat.driverScale}", flat.driverScale > 1f && flat.driverScale <= CarWide.MAX_GROWTH)
        assertTrue("the next tracks readable: ${flat.middle}", flat.middle >= CarWide.MIN_MIDDLE - 0.01f)

        assertNull("16:9 keeps its layout", CarWide.layout(width = 1192f, height = 604f))
        assertNull("2560×1080 (2.37:1) is not wide enough for three columns", CarWide.layout(width = 1618f, height = 604f))
        assertNull("a phone on its side neither", CarWide.layout(width = 1512f, height = 550f))
    }

    @Test
    fun aStatusBarLeavesASmallerCoverAndMoreForTheList() {
        val withBar = CarWide.layout(width = 1832f, height = 556f)!!
        assertEquals(556f, withBar.cover)
        assertTrue(withBar.middle > CarWide.layout(1832f, 604f)!!.middle)
    }

    // --- Voice ---

    @Test
    fun theWordsOfAskingAreDropped() {
        assertEquals("as said, without the asking", "Земфира Хочешь", VoicePhrase.query("Включи Земфира Хочешь"))
        assertEquals("кино группа крови", VoicePhrase.query("поставь пожалуйста песню кино группа крови"))
        assertEquals("сплин", VoicePhrase.query("найди мне сплин, пожалуйста"))
        assertEquals("ария", VoicePhrase.query("ария"))
        assertEquals("only the asking: kept as said", "включи", VoicePhrase.query("включи"))
        val radio = VoicePhrase.of("включи радио европа плюс")
        assertEquals("европа плюс", radio.query)
        assertTrue(radio.wantsRadio)
    }

    private val api = Retrofit.Builder()
        .baseUrl("https://api.music.yandex.net/")
        .client(OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url
            val body = when {
                url.encodedPath == "/search" -> when (url.queryParameter("text")) {
                    "земфира хочешь" -> """{"result":{"best":{"type":"track","result":{"id":"7","title":"Хочешь?"}},"tracks":{"results":[{"id":"8","title":"Искала"},{"id":"7","title":"Хочешь?"}]}}}"""
                    "кино" -> """{"result":{"best":{"type":"artist","result":{"id":41,"name":"Кино"}},"tracks":{"results":[{"id":"9","title":"Кукушка"}]}}}"""
                    "группа крови" -> """{"result":{"best":{"type":"album","result":{"id":3,"title":"Группа крови"}},"tracks":{"results":[]}}}"""
                    else -> """{"result":{"tracks":{"results":[]}}}"""
                }
                url.encodedPath == "/artists/41/brief-info" -> """{"result":{"artist":{"id":41,"name":"Кино"},"popularTracks":[{"id":"1","title":"Звезда по имени Солнце"},{"id":"2","title":"Кукушка"}]}}"""
                url.encodedPath == "/albums/3/with-tracks" -> """{"result":{"id":3,"title":"Группа крови","volumes":[[{"id":"11","title":"Группа крови"},{"id":"12","title":"Закрой за мной дверь"}]]}}"""
                else -> """{"result":null}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build())
        .addConverterFactory(Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YandexMusicApi::class.java)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val voice = VoiceSearch(SearchRepository(api, json), CatalogRepository(api), RadioRepository(ApplicationProvider.getApplicationContext()))

    private fun ids(pick: VoiceSearch.Pick) = (pick as VoiceSearch.Pick.Tracks).tracks.map { it.idString }

    @Test
    fun aTrackPlaysFirstWithTheOtherFoundTracksAfter() = runBlocking {
        assertEquals(listOf("7", "8"), ids(voice.pick("включи земфира хочешь")))
    }

    @Test
    fun anArtistPlaysTheirPopularTracks() = runBlocking {
        assertEquals(listOf("1", "2"), ids(voice.pick("поставь кино")))
    }

    @Test
    fun anAlbumPlaysTheAlbum() = runBlocking {
        assertEquals(listOf("11", "12"), ids(voice.pick("включи альбом группа крови")))
    }

    @Test
    fun aNamedStationPlaysAndALookalikeWordDoesNot() = runBlocking {
        val station = voice.pick("включи радио европа плюс")
        assertEquals("Европа Плюс", (station as VoiceSearch.Pick.Station).station.name)
        assertEquals("the whole name is enough without the word", "Европа Плюс", (voice.pick("европа плюс") as VoiceSearch.Pick.Station).station.name)
        assertTrue("«новое» is a search, not «Новое Радио»", voice.pick("включи новое") is VoiceSearch.Pick.Nothing)
        assertTrue("just the radio", voice.pick("включи радио") is VoiceSearch.Pick.Station)
    }

    @Test
    fun nothingFoundSaysSo() = runBlocking {
        assertEquals(VoiceSearch.Pick.Nothing("абвгд"), voice.pick("найди абвгд"))
    }

    // --- One track on the device ---

    private fun track(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = "Трек $id")

    @Test
    fun aTrackDownloadedByItselfIsKeptAndShown() {
        val singles = OfflineCollection(OfflineCollection.SINGLES_KEY, "Отдельные треки", tracks = listOf(track("5")))
        assertTrue(singles.isSingles)
        assertEquals(TrackOfflineState.DOWNLOADING, OfflineRules.trackState("5", onDevice = emptySet(), singles = singles))
        assertEquals(TrackOfflineState.DONE, OfflineRules.trackState("5", onDevice = setOf("5"), singles = singles))
        assertEquals(TrackOfflineState.NONE, OfflineRules.trackState("6", onDevice = emptySet(), singles = singles))
        assertEquals("downloaded with the liked ones: on the device too", TrackOfflineState.DONE, OfflineRules.trackState("6", setOf("6"), null))
        // Unliked, it stays: the set keeps it, as a kept playlist would
        assertTrue("5" in OfflineRules.kept(listOf(singles), liked = emptySet()))
        // And "Скачанные" lists it
        assertEquals(listOf("5"), OfflineRules.downloadedTracks(emptyList(), listOf(singles), setOf("5")).map { it.idString })
    }

    @Test
    fun takingATrackOffTheDeviceSaysWhyItStays() {
        assertEquals(RemoveOutcome.REMOVED, OfflineRules.removeOutcome(inKeptCollection = false, liked = true, autoDownloadLiked = false))
        assertEquals(RemoveOutcome.KEPT_BY_COLLECTION, OfflineRules.removeOutcome(inKeptCollection = true, liked = false, autoDownloadLiked = false))
        assertEquals("it would only come back", RemoveOutcome.KEPT_BY_AUTO_DOWNLOAD, OfflineRules.removeOutcome(inKeptCollection = false, liked = true, autoDownloadLiked = true))
        assertEquals(RemoveOutcome.REMOVED, OfflineRules.removeOutcome(inKeptCollection = false, liked = false, autoDownloadLiked = true))
    }
}

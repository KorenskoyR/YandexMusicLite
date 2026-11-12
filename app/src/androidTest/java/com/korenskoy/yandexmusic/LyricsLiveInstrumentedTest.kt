package com.korenskoy.yandexmusic

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.korenskoy.yandexmusic.data.lyrics.LyricsAnswer
import com.korenskoy.yandexmusic.data.lyrics.LyricsRules
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The words of songs on the real server (no sign-in needed): "lyricsAvailable" says whether the open
 * "supplement" method gives them, and only that flag decides whether to ask. The newer lyricsInfo says "yes"
 * for most of the chart while those words are behind a signed method: trusting it made the "Текст" button
 * show and then vanish (1.14.3).
 */
@RunWith(AndroidJUnit4::class)
class LyricsLiveInstrumentedTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MainViewModel
    private lateinit var api: YandexMusicApi

    /** The app's own API client, found among the view model's dependencies. */
    private fun findApi(root: Any, depth: Int = 0, seen: MutableSet<Int> = mutableSetOf()): YandexMusicApi? {
        if (depth > 3 || !seen.add(System.identityHashCode(root))) return null
        var c: Class<*>? = root.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (f.type.isPrimitive) continue
                val v = runCatching { f.isAccessible = true; f.get(root) }.getOrNull() ?: continue
                if (v is YandexMusicApi) return v
                if (v.javaClass.name.startsWith("com.korenskoy")) findApi(v, depth + 1, seen)?.let { return it }
            }
            c = c.superclass
        }
        return null
    }

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        api = checkNotNull(findApi(vm)) { "no API client" }
    }

    @After
    fun tearDown() = scenario.close()

    @Test
    fun aSongWithWordsGetsThem() = runBlocking {
        // Imagine Dragons, «Believer»
        val track = api.getTracksMetadataQuery("33311009").result.orEmpty().single()
        assertEquals(true, track.lyricsAvailable)
        assertTrue(LyricsRules.canHave(track))
        val words = vm.lyricsUi.load(track.idString)
        assertTrue("words: $words", words is LyricsAnswer.Text && words.text.lines().size > 10)
    }

    @Test
    fun theFlagTheAppTrustsMatchesTheWordsOnTheChart() = runBlocking {
        val ids = api.getUserPlaylist("414787002", "1076").result?.tracks.orEmpty().mapNotNull { it.trackId }.take(40)
        assertTrue("the chart is there: ${ids.size}", ids.size >= 20)
        val tracks = api.getTracksMetadataQuery(ids.joinToString(",")).result.orEmpty()
        val wrong = tracks.filter { t ->
            val hasWords = vm.lyricsUi.load(t.idString) is LyricsAnswer.Text
            hasWords != (t.lyricsAvailable == true)
        }
        assertTrue("lyricsAvailable and the words disagree: ${wrong.map { "${it.idString} ${it.title} ${it.lyricsAvailable}" }}", wrong.isEmpty())
        // And the newer flag really does say "yes" where no words come: the reason not to trust it
        val misleading = tracks.count { it.lyricsInfo?.hasAvailableTextLyrics == true && it.lyricsAvailable == false }
        println("chart: ${tracks.size} tracks, lyricsInfo says yes without words for $misleading")
    }
}

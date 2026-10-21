package com.korenskoy.yandexmusic

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.korenskoy.yandexmusic.presentation.auth.AuthViewModel
import com.korenskoy.yandexmusic.presentation.account.AccountViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.korenskoy.yandexmusic.data.cache.PlaylistCacheManager
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Library → playlist → back, with the playlists served from the on-disk cache (the fake test token
 * cannot reach the API, so the server refresh fails and the cache stays).
 * Note: wipes the app's auth storage — run on a test device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class UserPlaylistsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    /** Cache folder of the provisional account a saved token creates ("Яндекс Аккаунт"). */
    private val accountFolder = "Яндекс_Аккаунт"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val cache = PlaylistCacheManager(context, json)

    /** Seeds the account's cached playlists: the list, and tracks of playlist 1003 at revision 5. */
    private fun seed(playlists: String, tracks: String) = runBlocking {
        cache.saveUserPlaylists(accountFolder, json.decodeFromString<List<UserPlaylistDto>>(playlists))
        cache.savePlaylistTracks(accountFolder, "1003", 5, json.decodeFromString<List<TrackDetailsDto>>(tracks))
    }

    @Before
    @After
    fun wipe() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
        runBlocking { cache.clearUserCache(accountFolder) }
    }

    private fun hasText(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.text(text)) != null) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun isGone(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.text(text)) == null) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun waitUntilTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun hasDesc(desc: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.desc(desc)) != null) return true
            Thread.sleep(200)
        }
        return false
    }

    @Test
    fun playlistOpensFromLibraryAndBackReturns() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // Seed first: the repository is a process singleton that reads the cache once the token is saved
        seed(
            """[{"kind":1003,"title":"Тестовый плейлист","trackCount":2,"revision":5}]""",
            """[{"id":"9001","title":"Первая песня","artists":[{"name":"Исполнитель"}]},
               {"id":"9002","title":"Вторая песня","artists":[{"name":"Исполнитель"}]}]"""
        )

        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var vm: AccountViewModel? = null
        try {
            scenario.onActivity { vm = ViewModelProvider(it)[AccountViewModel::class.java] }
            // Earlier tests in this process may have loaded the same account already: re-enter it so the
            // seeded cache is read (the same path as switching accounts in the app)
            val model = vm!!
            instrumentation.runOnMainSync { model.switchAccount(model.activeAccount.value!!.uid) }
            assertTrue("main screen", hasDesc("Моя Музыка", 15_000))
            device.findObject(By.desc("Моя Музыка")).click()

            assertTrue("playlists row", hasText("Плейлисты", 10_000))
            assertTrue("playlist card", hasText("Тестовый плейлист", 2_000))
            device.executeShellCommand("screencap -p /data/local/tmp/library_playlists.png")
            device.findObject(By.text("Тестовый плейлист")).click()

            assertTrue("playlist tracks", hasText("Первая песня", 10_000) && hasText("Вторая песня", 2_000))
            device.executeShellCommand("screencap -p /data/local/tmp/playlist_opened.png")

            device.findObject(By.desc("Назад")).click()
            assertTrue("back to the library", hasText("Плейлисты", 5_000) && !hasText("Первая песня", 1_000))
        } finally {
            vm?.let { model -> instrumentation.runOnMainSync { model.logoutAllAccounts() } }
            scenario.close()
        }
    }

    /**
     * Track menu → playlist picker, and the playlist's own menu. The fake token cannot reach the API, so
     * the rename fails: the user gets the error and the playlist keeps its name.
     */
    @Test
    fun trackMenuAndPlaylistMenu() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        seed(
            """[{"kind":1003,"title":"Тестовый плейлист","trackCount":2,"revision":5},
               {"kind":1004,"title":"Второй плейлист","trackCount":0,"revision":1}]""",
            """[{"id":"9001","title":"Первая песня","artists":[{"name":"Исполнитель"}]},
               {"id":"9002","title":"Вторая песня","artists":[{"name":"Исполнитель"}]}]"""
        )

        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var vm: AccountViewModel? = null
        try {
            scenario.onActivity { vm = ViewModelProvider(it)[AccountViewModel::class.java] }
            val model = vm!!
            instrumentation.runOnMainSync { model.switchAccount(model.activeAccount.value!!.uid) }
            assertTrue("main screen", hasDesc("Моя Музыка", 15_000))
            device.findObject(By.desc("Моя Музыка")).click()

            assertTrue("new playlist card", hasText("Новый плейлист", 10_000))
            device.executeShellCommand("screencap -p /data/local/tmp/library_new_playlist.png")
            device.findObject(By.text("Тестовый плейлист")).click()
            assertTrue("playlist tracks", hasText("Первая песня", 10_000))

            // Track menu
            device.findObjects(By.desc("Ещё")).first().click()
            assertTrue("play next", hasText("Играть следующим", 5_000))
            assertTrue("remove from playlist", hasText("Убрать из плейлиста", 1_000))
            assertTrue("like", hasText("Добавить в «Мне нравится»", 1_000))
            device.executeShellCommand("screencap -p /data/local/tmp/track_menu.png")

            // Picker: other playlists and "new", not the playlist the track is in
            device.findObject(By.text("Добавить в плейлист")).click()
            assertTrue("picker: other playlist", hasText("Второй плейлист", 5_000))
            assertTrue("picker: new playlist", hasText("Новый плейлист", 1_000))
            device.executeShellCommand("screencap -p /data/local/tmp/playlist_picker.png")
            // Tap the scrim: a back press racing the sheet's closing could also close the playlist
            device.click(device.displayWidth / 2, device.displayHeight / 6)
            assertTrue("sheet closed", isGone("Второй плейлист", 3_000))

            // Playlist menu → rename (prefilled) → fails without API access
            // The sheet's window outlives its content for a moment and hides the screen below
            assertTrue("playlist menu button", hasDesc("Действия с плейлистом", 5_000))
            device.findObject(By.desc("Действия с плейлистом")).click()
            assertTrue("rename item", hasText("Переименовать", 5_000))
            assertTrue("delete item", hasText("Удалить плейлист", 1_000))
            assertTrue("reorder item", hasText("Изменить порядок", 1_000))
            assertTrue("visibility item", hasText("Сделать публичным", 1_000))
            // Reorder: drag the first track below the second, then cancel with Back (nothing is sent)
            device.findObject(By.text("Изменить порядок")).click()
            assertTrue("reorder mode", hasText("Готово", 5_000))
            val handles = device.findObjects(By.desc("Перетащить")).sortedBy { it.visibleBounds.top }
            assertTrue("drag handles", handles.size == 2)
            val second = handles[1].visibleBounds
            handles[0].drag(android.graphics.Point(second.centerX(), second.bottom + 10), 300)
            val titles = { device.findObjects(By.textStartsWith("Перв").clazz("android.widget.TextView")).firstOrNull()?.visibleBounds?.top ?: 0 }
            assertTrue("dragged below", waitUntilTrue(3_000) {
                titles() > (device.findObject(By.text("Вторая песня"))?.visibleBounds?.top ?: Int.MAX_VALUE)
            })
            device.executeShellCommand("screencap -p /data/local/tmp/playlist_reorder.png")
            device.pressBack()
            assertTrue("reorder cancelled", isGone("Готово", 3_000))
            assertTrue("playlist menu button again", hasDesc("Действия с плейлистом", 5_000))
            device.findObject(By.desc("Действия с плейлистом")).click()
            assertTrue("rename item again", hasText("Переименовать", 5_000))
            device.findObject(By.text("Переименовать")).click()
            assertTrue("rename dialog", hasText("Сохранить", 5_000))
            device.executeShellCommand("screencap -p /data/local/tmp/playlist_rename.png")
            device.findObject(By.text("Сохранить")).click()
            assertTrue("error shown", hasText("Не удалось изменить плейлист. Проверьте подключение", 30_000))
            assertTrue("name kept", hasText("Тестовый плейлист", 1_000))

            // Player: "add to playlist" opens the picker directly. The track cannot stream with the fake
            // token, but it stays in the mini-player, which opens the player.
            // (the queue may move on to another track that fails too, so the mini-player is found by its button)
            device.findObject(By.text("Вторая песня")).click()
            assertTrue("mini-player", hasDesc("Воспроизведение или пауза", 10_000))
            device.findObjects(By.desc("Обложка")).maxBy { it.visibleBounds.top }.click() // the mini-player's cover
            assertTrue("player button", hasDesc("Добавить в плейлист", 10_000))
            device.executeShellCommand("screencap -p /data/local/tmp/player_track.png")
            device.findObject(By.desc("Добавить в плейлист")).click()
            assertTrue("player picker", hasText("Второй плейлист", 5_000) && hasText("Новый плейлист", 1_000))
            assertTrue("straight to the picker", !hasText("Играть следующим", 500))
        } finally {
            vm?.let { model -> instrumentation.runOnMainSync { model.logoutAllAccounts() } }
            scenario.close()
        }
    }
}

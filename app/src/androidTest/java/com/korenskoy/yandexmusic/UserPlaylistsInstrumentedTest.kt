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
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
    private val accountCache = File(context.cacheDir, "playlists/Яндекс_Аккаунт")

    @Before
    @After
    fun wipe() {
        for (name in listOf("auth_secure", "auth_prefs", "auth_prefs_fallback")) context.deleteSharedPreferences(name)
        accountCache.deleteRecursively()
    }

    private fun hasText(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (device.findObject(By.text(text)) != null) return true
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
        accountCache.mkdirs()
        File(accountCache, "user_playlists.json").writeText(
            """[{"kind":1003,"title":"Тестовый плейлист","trackCount":2,"revision":5}]"""
        )
        File(accountCache, "playlist_1003_5.json").writeText(
            """[{"id":"9001","title":"Первая песня","artists":[{"name":"Исполнитель"}]},
               {"id":"9002","title":"Вторая песня","artists":[{"name":"Исполнитель"}]}]"""
        )

        ActivityScenario.launch(MainActivity::class.java).use { first ->
            first.onActivity { ViewModelProvider(it)[AuthViewModel::class.java].saveToken("y0_instrumentation_fake_token") }
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var vm: MainViewModel? = null
        try {
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
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
            vm?.let { model -> instrumentation.runOnMainSync { model.closePlaylist(); model.logoutAllAccounts() } }
            scenario.close()
        }
    }
}

package com.korenskoy.yandexmusic

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.player.MusicService
import com.korenskoy.yandexmusic.widget.mediaButtonIntent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A head unit sends Play at power-on, with the app closed (Teyes CC3, 1.14.0: "Context.startForegroundService() did
 * not then call Service.startForeground()"). The service is started for the foreground; with nothing to resume the
 * player's notification never came and Android ended the app. Now it outlives the system's deadline: either the
 * music plays in the foreground, or the service has left quietly.
 */
@RunWith(AndroidJUnit4::class)
class HeadUnitPlayInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Suppress("DEPRECATION") // getRunningServices still reports the app's own services
    private fun ours(): ActivityManager.RunningServiceInfo? =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Int.MAX_VALUE)
            .firstOrNull { it.service.className == MusicService::class.java.name }

    @Test
    fun aPlayAtPowerOnNeverEndsTheApp() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        context.stopService(Intent(context, MusicService::class.java))
        val until = System.currentTimeMillis() + 10_000
        while (ours() != null && System.currentTimeMillis() < until) Thread.sleep(200)

        context.sendBroadcast(mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY))
        // Android ends an app 5 s (12+) to 10 s (8–11) after such a start without the foreground: well past that.
        // Ended, this test's process (the app's) would be gone with it
        Thread.sleep(15_000)
        val service = ours()
        assertTrue("the service either plays in the foreground or has left", service == null || service.foreground)
    }
}

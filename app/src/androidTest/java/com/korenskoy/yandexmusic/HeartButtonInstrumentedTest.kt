package com.korenskoy.yandexmusic

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.player.SessionButtons
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.14.8 on a real Android: what the system's music controls are told about the heart. The shade, the lock screen
 * and the music widgets of head units read the session's custom actions; after a like the heart must come as a
 * different action (its own id, picture and name), or some of them keep drawing the empty heart.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class HeartButtonInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (onMain(condition)) return true
            Thread.sleep(50)
        }
        return onMain(condition)
    }

    @Test
    fun aLikeReachesTheSystemsMusicControlsAsADifferentHeart() {
        val track = TrackDetailsDto(id = JsonPrimitive("42"), title = "Хочешь?")
        val player = onMain { ExoPlayer.Builder(context).build() }
        // The commands as the app's service gives them (to the system and the notification)
        val callback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionButtons.LIKE)
                    .add(SessionButtons.LIKED)
                    .add(SessionButtons.DISLIKE)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
            }
        }
        val session = onMain { MediaSession.Builder(context, player).setId("heart-test").setCallback(callback).build() }
        // The notification's own controller, as the app's service connects it: the system's controls are told the
        // buttons it may use (media3 marks it with this hint)
        val hints = android.os.Bundle().apply { putBoolean(NOTIFICATION_MANAGER_HINT, true) }
        val notification = androidx.media3.session.MediaController.Builder(context, session.token)
            .setConnectionHints(hints)
            .buildAsync()
            .get(10, java.util.concurrent.TimeUnit.SECONDS)
        try {
            val controller = onMain { MediaController(context, session.platformToken) }
            fun heart(): PlaybackState.CustomAction? =
                controller.playbackState?.customActions?.firstOrNull { SessionButtons.isHeart(it.action) }

            onMain { session.setMediaButtonPreferences(SessionButtons.buttonsFor(context, track, liked = false)) }
            Thread.sleep(1_000)
            Log.i(TAG, "state: " + onMain { controller.playbackState?.toString() } + " connected: " + onMain { session.connectedControllers.map { it.packageName + "/" + it.controllerVersion } })
            assertTrue("the empty heart reaches the system", waitFor(5_000) { heart() != null })
            val before = onMain { heart()!! }

            onMain { session.setMediaButtonPreferences(SessionButtons.buttonsFor(context, track, liked = true)) }
            assertTrue("the liked heart reaches the system", waitFor(5_000) { heart()?.action != before.action })
            val after = onMain { heart()!! }
            Log.i(TAG, "before ${before.action} ${before.name} ${before.icon}; after ${after.action} ${after.name} ${after.icon}")

            assertEquals(SessionButtons.LIKE_ACTION, before.action)
            assertEquals(SessionButtons.LIKED_ACTION, after.action)
            assertNotEquals("its own picture", before.icon, after.icon)
            assertNotEquals("its own name", before.name.toString(), after.name.toString())
            val dislikes = onMain { controller.playbackState?.customActions?.count { it.action == SessionButtons.DISLIKE.customAction } }
            assertEquals("«Не нравится» stays as it was", 1, dislikes)
        } finally {
            onMain {
                notification.release()
                session.release()
                player.release()
            }
        }
    }

    private companion object {
        const val TAG = "HeartButton"
        const val NOTIFICATION_MANAGER_HINT = "androidx.media3.session.MediaNotificationManager"
    }
}

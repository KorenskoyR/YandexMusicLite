package com.korenskoy.yandexmusic.data

import android.app.Application
import com.korenskoy.yandexmusic.data.player.PlayRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Разворачивать плеер» works every time: a start of the music is kept until the screen reads it (a fired event was
 * lost when the music on launch started before the screen listened), and «Музыка при запуске» plays for every
 * opening of the app, not once per process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExpandPlayerTest {

    @Test
    fun aStartBeforeAnyoneListensIsKept() {
        val requests = PlayRequests()
        requests.userStarted()
        // The screen comes later and still finds it
        val start = requests.starts.value
        assertNotNull(start)
        assertEquals(1L, start!!.id)
        requests.userStarted()
        assertEquals(2L, requests.starts.value!!.id)
    }

    @Test
    fun onlyAFreshStartOpensThePlayer() {
        val start = PlayRequests.Start(id = 1, atMs = 100_000)
        assertTrue(PlayRequests.isFresh(start, 100_000))
        assertTrue(PlayRequests.isFresh(start, 100_000 + PlayRequests.FRESH_MS))
        // A previous opening's start: the app opened again a minute later does not open the player for it
        assertFalse(PlayRequests.isFresh(start, 160_000))
        // A clock from before the start (a reboot): no
        assertFalse(PlayRequests.isFresh(start, 50_000))
    }

    @Test
    fun theMusicOnLaunchPlaysForEveryOpening() {
        val requests = PlayRequests()
        assertTrue(requests.claimLaunchStart(launch = 1))
        // The same opening (the screen came back, the activity recreated): not again
        assertFalse(requests.claimLaunchStart(launch = 1))
        // Opened again after Back with the process alive (a head unit after its sleep): again
        assertTrue(requests.claimLaunchStart(launch = 2))
        assertFalse(requests.claimLaunchStart(launch = 2))
    }
}

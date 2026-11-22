package com.korenskoy.yandexmusic.data

import com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager
import com.korenskoy.yandexmusic.data.updater.UpdateSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update is looked for again while the app stays open, and found past GitHub's API limit. */
class UpdateCheckTest {

    @Test
    fun theTagComesFromWhereTheLatestReleaseSends() {
        assertEquals("v1.15.0", GitHubUpdateManager.tagFromLocation("https://github.com/KorenskoyR/YandexMusicLite/releases/tag/v1.15.0"))
        assertEquals("v1.16.0", GitHubUpdateManager.tagFromLocation("https://github.com/KorenskoyR/YandexMusicLite/releases/tag/v1.16.0?ref=x"))
        // No release yet, a sign-in page, nothing at all
        assertNull(GitHubUpdateManager.tagFromLocation("https://github.com/KorenskoyR/YandexMusicLite/releases"))
        assertNull(GitHubUpdateManager.tagFromLocation("https://github.com/login"))
        assertNull(GitHubUpdateManager.tagFromLocation(null))
    }

    @Test
    fun anOpenAppLooksAgainEverySixHoursAndSoonAfterAFailure() {
        val hour = 60 * 60_000L
        // Never checked
        assertTrue(UpdateSchedule.due(now = 10 * hour, lastAt = 0L, lastOk = false))
        // A good check five hours ago: not yet; six: yes
        assertFalse(UpdateSchedule.due(now = 10 * hour, lastAt = 5 * hour, lastOk = true))
        assertTrue(UpdateSchedule.due(now = 11 * hour, lastAt = 5 * hour, lastOk = true))
        // A failed check: a quarter of an hour later
        assertFalse(UpdateSchedule.due(now = 10 * hour + 10 * 60_000L, lastAt = 10 * hour, lastOk = false))
        assertTrue(UpdateSchedule.due(now = 10 * hour + 15 * 60_000L, lastAt = 10 * hour, lastOk = false))
        // The clock went back (a head unit without its time): checked
        assertTrue(UpdateSchedule.due(now = 1 * hour, lastAt = 5 * hour, lastOk = true))
    }
}

package com.korenskoy.yandexmusic.data

import android.app.Application
import com.korenskoy.yandexmusic.data.download.DownloadOutcome
import com.korenskoy.yandexmusic.data.player.BackgroundWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.15: downloads that cannot be had are not retried forever; the maker's own background switch is found. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Logic115Test {

    @Test
    fun tracksThatCannotBeHadOnAConnectionAreNotRetried() {
        // 608 liked, 599 downloaded, the last 9 failed with the network there: done, not «Ожидание сети»
        assertFalse(DownloadOutcome.shouldRetry(failed = 9, online = true))
    }

    @Test
    fun aDroppedNetworkIsRetried() {
        assertTrue(DownloadOutcome.shouldRetry(failed = 3, online = false))
        assertFalse(DownloadOutcome.shouldRetry(failed = 0, online = false))
    }

    @Test
    fun xiaomiHasItsAutostartScreen() {
        val screens = BackgroundWork.makerScreens("Xiaomi")
        assertEquals(1, screens.size)
        assertEquals("com.miui.securitycenter", screens.single().component?.packageName)
        assertTrue(BackgroundWork.makerScreens("POCO").isNotEmpty())
    }

    @Test
    fun eachKnownMakerHasAScreenAndOthersNone() {
        listOf("HUAWEI", "HONOR", "samsung", "OPPO", "realme", "OnePlus", "vivo").forEach {
            assertTrue(it, BackgroundWork.makerScreens(it).isNotEmpty())
        }
        assertTrue(BackgroundWork.makerScreens("Google").isEmpty())
        assertTrue(BackgroundWork.makerScreens("QUALCOMM").isEmpty())
    }
}

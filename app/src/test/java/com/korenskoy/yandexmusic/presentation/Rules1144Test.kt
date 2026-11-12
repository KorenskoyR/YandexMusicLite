package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.data.download.Drive
import com.korenskoy.yandexmusic.data.download.RemovableDrives
import com.korenskoy.yandexmusic.domain.model.CarLayout
import com.korenskoy.yandexmusic.presentation.auth.signInPageKey
import com.korenskoy.yandexmusic.presentation.auth.signInStartedOver
import com.korenskoy.yandexmusic.presentation.car.CarWide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules behind 1.14.4's additions: the car's layout lying down, a sign-in page that starts over, a drive by hand. */
class Rules1144Test {

    // --- «В авто» lying on a wide head unit ---

    /** 1920×720 at a large interface scale: the room the car screen gets, a little under the full three columns. */
    private val nearEdge = 1657f to 536f

    @Test
    fun autoHoldsWhatIsOnScreenNearTheEdge() {
        val (w, h) = nearEdge
        assertNull("from two columns, three need the full room", CarWide.layout(w, h, CarLayout.AUTO, wasWide = false))
        assertNull("at first too", CarWide.layout(w, h, CarLayout.AUTO, wasWide = null))
        val held = CarWide.layout(w, h, CarLayout.AUTO, wasWide = true)
        assertNotNull("three columns on screen stay within the margin", held)
        assertTrue("the player gives a little, not more than the margin: ${held!!.driverScale}", held.driverScale >= 1f - CarWide.HOLD)
    }

    @Test
    fun autoDoesNotFlipOnSmallChanges() {
        // A bar of the head unit that shows for a moment (≈40 dp) and the interface scale a step either way
        val (w, h) = 1832f to 604f
        val shown = CarWide.layout(w, h, CarLayout.AUTO, wasWide = null)
        assertNotNull(shown)
        for ((dw, dh) in listOf(0f to -40f, -120f to -40f, -0.1f * w to -0.1f * h)) {
            assertNotNull("held at ${w + dw}×${h + dh}", CarWide.layout(w + dw, h + dh, CarLayout.AUTO, wasWide = true))
        }
        // And two columns hold as well: a moment of more room does not switch to three
        val narrow = 1500f to 520f
        assertNull(CarWide.layout(narrow.first, narrow.second, CarLayout.AUTO, wasWide = false))
        assertNull(CarWide.layout(narrow.first + 40f, narrow.second + 20f, CarLayout.AUTO, wasWide = false))
    }

    @Test
    fun theSettingFixesTheLayout() {
        val (w, h) = nearEdge
        val three = CarWide.layout(w, h, CarLayout.THREE, wasWide = false)
        assertNotNull("«Три колонки» squeezes a little to stay", three)
        assertTrue(three!!.driverScale >= 1f - CarWide.SQUEEZE)
        assertTrue("the next tracks stay readable: ${three.middle}", three.middle >= CarWide.MIN_MIDDLE - 0.01f)
        assertTrue("the cover never under its least: ${three.cover}", three.cover >= CarWide.MIN_COVER * (1f - CarWide.SQUEEZE) - 0.01f)
        assertNull("«Две колонки» always", CarWide.layout(1832f, 604f, CarLayout.TWO, wasWide = true))
        assertNull("three columns where they cannot fit at all fall back to two", CarWide.layout(1192f, 604f, CarLayout.THREE))
        assertNull("a phone on its side neither", CarWide.layout(1512f, 550f, CarLayout.THREE))
    }

    @Test
    fun theColumnsAlwaysAddUpToTheRow() {
        for (mode in CarLayout.entries) for (w in 1500..2600 step 50) for (h in 420..720 step 20) {
            val layout = CarWide.layout(w.toFloat(), h.toFloat(), mode, wasWide = true) ?: continue
            val used = CarWide.DRIVER_WIDTH * layout.driverScale + layout.middle + layout.cover + 2 * CarWide.GAP
            assertTrue("$mode $w×$h uses $used", used <= w + 0.5f)
            assertTrue("$mode $w×$h cover ${layout.cover} over the row", layout.cover <= h + 0.01f)
            assertTrue("$mode $w×$h middle ${layout.middle}", layout.middle >= CarWide.MIN_MIDDLE - 0.01f)
        }
    }

    @Test
    fun theSettingIsOfferedOnlyWhereThreeColumnsCanBe() {
        assertTrue(CarWide.possible(1832f, 604f))
        assertFalse("16:9", CarWide.possible(1192f, 604f))
        assertFalse("a tablet", CarWide.possible(1192f, 682f))
    }

    // --- Sign-in page that starts over ---

    @Test
    fun aSignInPageIsKnownByItsHostAndPath() {
        assertEquals("passport.yandex.ru/auth", signInPageKey("https://passport.yandex.ru/auth/?retpath=x&origin=y"))
        assertEquals("passport.yandex.ru/auth/welcome", signInPageKey("https://passport.yandex.ru/auth/welcome?x=1#z"))
        assertNull("the OAuth address only passes on", signInPageKey("https://oauth.yandex.ru/authorize?response_type=token"))
        assertNull(signInPageKey("about:blank"))
        assertNull(signInPageKey("data:text/html,x"))
        assertNull(signInPageKey(null))
    }

    @Test
    fun startingOverCountsOnlyAfterTheUserDidSomething() {
        val start = "passport.yandex.ru/auth"
        assertFalse("the first load", signInStartedOver(start, start, touched = false))
        assertTrue(signInStartedOver(start, start, touched = true))
        assertFalse("a step further is no reset", signInStartedOver(start, "passport.yandex.ru/auth/welcome", touched = true))
        assertFalse(signInStartedOver(null, start, touched = true))
    }

    // --- A flash drive without the system's folder window ---

    private val drives = listOf(
        Drive("/storage/1A2B-3C4D/Android/data/app/files", "USB-накопитель", usb = true, freeBytes = 1L),
        Drive("/storage/6266-080C/Android/data/app/files", "SD-карта", usb = false, freeBytes = 2L)
    )

    @Test
    fun aTypedPathOnAKnownDriveUsesTheAppsOwnFolderThere() {
        assertEquals(drives[0], RemovableDrives.driveFor("/storage/1A2B-3C4D", drives))
        assertEquals(drives[0], RemovableDrives.driveFor("/storage/1A2B-3C4D/Music", drives))
        assertEquals(drives[1], RemovableDrives.driveFor("/storage/6266-080C/Android", drives))
        assertNull("only the same drive, not one whose name starts alike", RemovableDrives.driveFor("/storage/1A2B-3C4DX", drives))
        assertNull(RemovableDrives.driveFor("/mnt/usb_storage/USB_DISK0", drives))
    }

    @Test
    fun aTypedPathIsCleanedAndChecked() {
        assertEquals("/mnt/usb_storage/USB_DISK0", RemovableDrives.normalize("  /mnt/usb_storage/USB_DISK0/ "))
        assertNull("relative", RemovableDrives.normalize("mnt/usb"))
        assertNull("the root itself", RemovableDrives.normalize("/"))
        assertNull("no climbing out", RemovableDrives.normalize("/storage/../data"))
        assertNull(RemovableDrives.normalize(""))
    }

    @Test
    fun aFlashDriveIsToldFromACard() {
        assertTrue(RemovableDrives.isUsb("USB-накопитель SanDisk", "/storage/1A2B-3C4D"))
        assertTrue(RemovableDrives.isUsb(null, "/mnt/usb_storage/USB_DISK0"))
        assertFalse(RemovableDrives.isUsb("SD-карта", "/storage/6266-080C"))
        assertEquals("/storage/1A2B-3C4D", RemovableDrives.volumeRoot(drives[0].path))
        assertTrue(RemovableDrives.isAppFolder(drives[0].path))
        assertFalse(RemovableDrives.isAppFolder("/mnt/usb_storage/USB_DISK0/YandexMusicLite"))
    }
}

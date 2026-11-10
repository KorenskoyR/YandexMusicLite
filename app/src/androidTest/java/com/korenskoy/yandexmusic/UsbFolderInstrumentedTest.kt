package com.korenskoy.yandexmusic

import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.korenskoy.yandexmusic.data.download.CardStorage
import com.korenskoy.yandexmusic.data.download.StorageAccess
import com.korenskoy.yandexmusic.data.download.UsbStorage
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager.Place
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * A flash drive through the real system window: "Флешка" in the settings opens the picker on the drive,
 * the folder is chosen and allowed, and a downloaded track moves there encrypted and back whole.
 * Needs a removable public volume with a "YandexMusicLite" folder (on an emulator:
 * `sm set-virtual-disk true`, `sm partition <disk> public`, then mkdir); skipped without one.
 */
@RunWith(AndroidJUnit4::class)
class UsbFolderInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deps = EntryPointAccessors.fromApplication(context.applicationContext, StorageAccess::class.java)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun aFolderOnTheDriveIsPickedAndTracksMoveThereAndBack() {
        val drive = context.getSystemService(StorageManager::class.java).storageVolumes
            .firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        assumeTrue("No removable drive on this device", drive != null)

        // A downloaded track in the phone's memory
        val music = Random(5).nextBytes(90_001)
        val id = "usbcheck1"
        File(context.filesDir, "tracks").mkdirs()
        File(context.filesDir, "tracks/$id.mp3").writeBytes(music)
        val storage = deps.storage()
        storage.moveTo(Place.INTERNAL)
        storage.refresh()
        waitFor { deps.downloads().isKnownDownloaded(id) && storage.state.value.usbPickerStart != null }

        compose.setContent {
            YandexMusicTheme {
                val status by storage.state.collectAsState()
                SettingsSheet(
                    state = SettingsUiState(storage = status),
                    actions = SettingsActions(onStoragePlaceChange = storage::moveTo, onUsbFolderChosen = storage::useUsbFolder),
                    onDismiss = {},
                    initialPage = SettingsPage.PLAYBACK
                )
            }
        }
        compose.onNodeWithText("Флешка").performScrollTo().performClick()

        // The system window, opened on the drive: the folder, "use this folder", "allow". A folder kept from an
        // earlier run is used as it is, without the window: the app asks only once
        if (!storage.state.value.usbChosen || !storage.state.value.usbAvailable) {
            assertNotNull("The folder picker did not open on the drive", device.wait(Until.findObject(By.text("YandexMusicLite")), 15_000))
            device.findObject(By.text("YandexMusicLite")).click()
            device.wait(Until.findObject(By.textContains("USE THIS FOLDER").clickable(true)), 10_000)?.click()
                ?: device.wait(Until.findObject(By.textContains("Use this folder")), 5_000)!!.click()
            (device.wait(Until.findObject(By.text("ALLOW")), 10_000) ?: device.wait(Until.findObject(By.text("Allow")), 5_000))!!.click()
        }

        // Kept, moved, encrypted
        waitFor(30_000) { storage.state.value.onUsb && storage.state.value.moving == null && !File(context.filesDir, "tracks/$id.mp3").exists() }
        assertTrue(deps.usb().present)
        val onDrive = deps.usb().files()["$id.enc"]
        assertNotNull("The track is not on the drive", onDrive)
        assertEquals((music.size + 16).toLong(), onDrive!!.size)
        val uri = Uri.parse(deps.downloads().localTrackUri(id))
        assertEquals("content", uri.scheme)
        val plain = CardStorage.decryptingStream(deps.card().key()!!, deps.card().openSeekable(uri)).use { it.readBytes() }
        assertArrayEquals(music, plain)
        compose.onNodeWithText("Другая папка", substring = true).assertExists()

        // And back into the phone's memory, whole
        storage.moveTo(Place.INTERNAL)
        waitFor(30_000) { !storage.state.value.onUsb && storage.state.value.moving == null && File(context.filesDir, "tracks/$id.mp3").exists() }
        assertArrayEquals(music, File(context.filesDir, "tracks/$id.mp3").readBytes())
        assertFalse(deps.usb().files().containsKey("$id.enc"))
        File(context.filesDir, "tracks/$id.mp3").delete()
        deps.downloads().refresh()
    }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue("Timed out", condition())
    }
}

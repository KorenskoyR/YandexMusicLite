package com.korenskoy.yandexmusic

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.download.CardStorage
import com.korenskoy.yandexmusic.data.download.PathCheck
import com.korenskoy.yandexmusic.data.download.RemovableDrives
import com.korenskoy.yandexmusic.data.download.StorageAccess
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager.Place
import com.korenskoy.yandexmusic.presentation.main.components.SettingsActions
import com.korenskoy.yandexmusic.presentation.main.components.SettingsPage
import com.korenskoy.yandexmusic.presentation.main.components.SettingsSheet
import com.korenskoy.yandexmusic.presentation.main.components.SettingsUiState
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * A flash drive on a head unit without the system's folder window (the Monjaro): the drive the app may write to by
 * itself is found, a path typed by hand is checked for real, and a downloaded track moves there encrypted and back
 * whole. Needs a removable drive the system shows the app (on an emulator: `sm set-virtual-disk true`, then
 * `sm partition <disk> public`); skipped without one.
 */
@RunWith(AndroidJUnit4::class)
class DriveWithoutPickerInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deps = EntryPointAccessors.fromApplication(context.applicationContext, StorageAccess::class.java)
    private val settings = deps.settings()

    @After
    fun tearDown() {
        runBlocking { settings.setCardRoot(null) }
    }

    @Test
    fun aDriveTypedByHandTakesTheDownloadsAndGivesThemBack() {
        val card = deps.card()
        val drive = card.drives().firstOrNull()
        assumeTrue("No removable drive the app may write to", drive != null)
        drive!!

        // What the check says about places that are not a drive
        assertEquals(PathCheck.NotFound, card.check("/no/such/folder"))
        assertEquals(PathCheck.NotWritable, card.check("/proc"))
        assertEquals("the drive's own root leads to the app's folder there", drive.path, (card.check(RemovableDrives.volumeRoot(drive.path)) as PathCheck.Ok).path)

        // A downloaded track in the phone's memory
        val music = Random(7).nextBytes(70_001)
        val id = "drivecheck1"
        File(context.filesDir, "tracks").mkdirs()
        File(context.filesDir, "tracks/$id.mp3").writeBytes(music)
        val storage = deps.storage()
        storage.moveTo(Place.INTERNAL)
        storage.refresh()
        waitFor { deps.downloads().isKnownDownloaded(id) && storage.state.value.moving == null && storage.state.value.drives.isNotEmpty() }

        // As on a unit without the folder window: the path typed by hand in the settings
        compose.setContent {
            YandexMusicTheme {
                val status by storage.state.collectAsState()
                SettingsSheet(
                    state = SettingsUiState(storage = status.copy(pickerAvailable = false)),
                    actions = SettingsActions(onStoragePlaceChange = storage::moveTo, onDriveChosen = storage::useDrive, onCheckPath = storage::checkPath),
                    onDismiss = {},
                    initialPage = SettingsPage.PLAYBACK
                )
            }
        }
        compose.onNodeWithText("Указать путь").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput(RemovableDrives.volumeRoot(drive.path))
        compose.onNodeWithText("Проверить").performScrollTo().performClick()

        // Taken, moved, encrypted
        waitFor(30_000) { storage.state.value.onCard && storage.state.value.moving == null && !File(context.filesDir, "tracks/$id.mp3").exists() }
        compose.onNodeWithText("Сохраняем сюда", substring = true).assertExists()
        val onDrive = File(drive.path, "tracks/$id.${CardStorage.EXTENSION}")
        assertTrue("The track is not on the drive: $onDrive", onDrive.exists())
        assertEquals((music.size + 16).toLong(), onDrive.length())
        val uri = Uri.parse(deps.downloads().localTrackUri(id))
        val plain = CardStorage.decryptingStream(card.key()!!, card.openSeekable(uri)).use { it.readBytes() }
        assertArrayEquals(music, plain)

        // And back into the phone's memory, whole
        storage.moveTo(Place.INTERNAL)
        waitFor(30_000) { !storage.state.value.onCard && storage.state.value.moving == null && File(context.filesDir, "tracks/$id.mp3").exists() }
        assertArrayEquals(music, File(context.filesDir, "tracks/$id.mp3").readBytes())
        assertTrue(!onDrive.exists())
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

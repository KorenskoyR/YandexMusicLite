package com.korenskoy.yandexmusic.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.player.SessionButtons
import com.korenskoy.yandexmusic.data.player.VoicePhrase
import com.korenskoy.yandexmusic.presentation.car.CarSource
import com.korenskoy.yandexmusic.presentation.car.carSourceOf
import com.korenskoy.yandexmusic.presentation.local.durationText
import com.korenskoy.yandexmusic.presentation.player.playerSourceLabelRes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Музыка с устройства": what counts as music, how files are named, grouped and sorted, and how it plays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalMusicLogicTest {

    private fun track(
        id: Int,
        title: String,
        artist: String? = null,
        folder: String = "Music/",
        source: LocalSource = LocalSource.PHONE,
        addedAt: Long = id.toLong(),
        album: String? = null,
        number: Int? = null
    ) = LocalTrack(
        id = "local_m$id", uri = "content://media/external/audio/media/$id", title = title, artist = artist, album = album,
        durationMs = 200_000, source = source, folder = folder, addedAt = addedAt, trackNumber = number
    )

    // --- What is music ---

    @Test
    fun soundsAndRecordingsAreNotMusic() {
        assertTrue(LocalRules.isMusic(200_000, "Music/Rock/"))
        assertFalse("a notification sound", LocalRules.isMusic(4_000, "Music/"))
        assertTrue("29.9 s is short, 30 s is a song", LocalRules.isMusic(30_000, "Download/"))
        assertFalse("a call recording", LocalRules.isMusic(600_000, "Recordings/Call/"))
        assertFalse(LocalRules.isMusic(600_000, "MIUI/sound_recorder/"))
        assertFalse(LocalRules.isMusic(60_000, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202544/"))
        assertTrue("a folder the user picked: its length is not known yet", LocalRules.isMusic(null, "Rock/"))
        assertTrue("WhatsApp's received audio is music", LocalRules.isMusic(240_000, "WhatsApp/Media/WhatsApp Audio/"))
    }

    // --- Names ---

    @Test
    fun aFileWithoutTagsIsNamedByItsFile() {
        assertEquals("Кукушка" to "Кино", LocalRules.nameOf(null, null, "03 - Кино - Кукушка.mp3"))
        assertEquals("Android writes the file name in the title: it is no title", "Кукушка" to "Кино", LocalRules.nameOf("03 - Кино - Кукушка", "<unknown>", "03 - Кино - Кукушка.mp3"))
        assertEquals("Track 05" to null, LocalRules.nameOf(null, null, "Track 05.mp3"))
        assertEquals("Ночной город" to null, LocalRules.nameOf(null, null, "01. Ночной_город.flac"))
        assertEquals("tags win", "Хочешь?" to "Земфира", LocalRules.nameOf("Хочешь?", "Земфира", "01 - track.mp3"))
        assertEquals("a known artist keeps the whole name", "A - B" to "Сплин", LocalRules.nameOf(null, "Сплин", "A - B.mp3"))
    }

    @Test
    fun oldRussianTagsAreReadInTheirCodePage() {
        assertEquals("Группа крови", LocalRules.fixEncoding("Ãðóïïà êðîâè"))
        assertEquals("Кино", LocalRules.fixEncoding("Êèíî"))
        assertEquals("real French stays French", "Café de Flore", LocalRules.fixEncoding("Café de Flore"))
        assertEquals("Für Elise", LocalRules.fixEncoding("Für Elise"))
        assertEquals("already Russian", "Звезда", LocalRules.fixEncoding("Звезда"))
        assertEquals("1999", LocalRules.fixEncoding("1999"))
        assertEquals("the artist goes through it too", "Ария" to "Кино", LocalRules.nameOf("Àðèÿ", "Êèíî", "x.mp3"))
    }

    @Test
    fun unknownArtistsAndFolderAlbumsAreNone() {
        assertNull(LocalRules.artistOf("<unknown>"))
        assertNull(LocalRules.artistOf("  "))
        assertEquals("ДДТ", LocalRules.artistOf("ДДТ"))
        assertNull("Android names the album after the folder", LocalRules.albumOf("Rock", "Rock"))
        assertEquals("Группа крови", LocalRules.albumOf("Группа крови", "Rock"))
    }

    // --- Where it lies ---

    @Test
    fun aStorageIsKnownByItsName() {
        assertEquals(LocalSource.PHONE, LocalRules.sourceOf(true, "Внутренний общий накопитель"))
        assertEquals(LocalSource.SD, LocalRules.sourceOf(false, "SD-карта SanDisk"))
        assertEquals(LocalSource.FLASH, LocalRules.sourceOf(false, "USB-накопитель Kingston"))
        assertEquals(LocalSource.FLASH, LocalRules.sourceOf(false, "Флешка"))
        assertEquals("a name that says nothing, a phone with a card slot", LocalSource.SD, LocalRules.sourceOf(false, "Kingston DT", phoneHasCardSlot = true))
        assertEquals("a head unit without one", LocalSource.FLASH, LocalRules.sourceOf(false, "Kingston DT", phoneHasCardSlot = false))
    }

    @Test
    fun formatsAndFolderPictures() {
        assertEquals("MP3", LocalRules.formatOf("audio/mpeg", "a.mp3"))
        assertEquals("FLAC", LocalRules.formatOf(null, "a.flac"))
        assertEquals("AAC", LocalRules.formatOf("audio/mp4", "a.m4a"))
        assertTrue(LocalRules.isAudioFile(null, "Song.OPUS"))
        assertFalse(LocalRules.isAudioFile("image/jpeg", "cover.jpg"))
        assertEquals("cover.jpg", LocalRules.folderPicture(listOf("01.mp3", "back.jpg", "cover.jpg", "Folder.jpg")))
        assertEquals("Folder.jpg", LocalRules.folderPicture(listOf("01.mp3", "back.jpg", "Folder.jpg")))
        assertEquals("the only picture", "scan.png", LocalRules.folderPicture(listOf("01.mp3", "scan.png")))
        assertNull("two unknown pictures: neither", LocalRules.folderPicture(listOf("a.jpg", "b.jpg")))
    }

    @Test
    fun aFolderAddedByHandDoesNotRepeatWhatTheCatalogueLists() {
        val listed = setOf(LocalRules.placeKey("external_primary", "Music/Rock/", "a.mp3"), LocalRules.placeKey("1234-abcd", "Music/", "b.mp3"))
        val (vol, path) = LocalRules.placeOfDocument("1234-ABCD:Music")
        assertEquals("1234-ABCD" to "Music", vol to path)
        val fromFolders = listOf(
            LocalRules.placeKey("primary", "Music/Rock/", "a.mp3") to track(1, "a"),
            LocalRules.placeKey("1234-ABCD", "Music/", "b.mp3") to track(2, "b"),
            LocalRules.placeKey("1234-ABCD", "Music/", "c.mp3") to track(3, "c")
        )
        assertEquals(listOf("c"), LocalRules.notListed(fromFolders, listed).map { it.title })
    }

    // --- Lists ---

    @Test
    fun sortsGoAsPeopleExpect() {
        val tracks = listOf(
            track(1, "Ёлка", "Би-2", addedAt = 10),
            track(2, "Атлантида", "Сплин", addedAt = 30),
            track(3, "Звезда", null, addedAt = 20),
            track(4, "Батарейка", "Жуки", addedAt = 30)
        )
        assertEquals("newest first, then by name", listOf("Атлантида", "Батарейка", "Звезда", "Ёлка"), LocalRules.sorted(tracks, LocalSort.RECENT).map { it.title })
        assertEquals("ё among е, as in a dictionary", listOf("Атлантида", "Батарейка", "Ёлка", "Звезда"), LocalRules.sorted(tracks, LocalSort.TITLE).map { it.title })
        assertEquals("no artist last", listOf("Ёлка", "Батарейка", "Атлантида", "Звезда"), LocalRules.sorted(tracks, LocalSort.ARTIST).map { it.title })
    }

    @Test
    fun foldersAreAsOnTheDriveAndPlayInTheirOrder() {
        val tracks = listOf(
            track(1, "Б", folder = "Rock/", source = LocalSource.FLASH, number = 2),
            track(2, "А", folder = "Rock/", source = LocalSource.FLASH, number = 1),
            track(3, "Корень", folder = "", source = LocalSource.FLASH),
            track(4, "Дорога", folder = "Дорожное/", source = LocalSource.FLASH),
            track(5, "Телефон", folder = "Music/", source = LocalSource.PHONE),
            track(6, "Тоже Rock", folder = "Rock/", source = LocalSource.SD)
        )
        val folders = LocalRules.folders(tracks)
        assertEquals("the drive's root first", null, folders.first().name)
        assertEquals("the same folder on two drives is two folders", 2, folders.count { it.name == "Rock" })
        assertEquals("by track number", listOf("А", "Б"), folders.first { it.key == "FLASH:Rock/" }.tracks.map { it.title })
        assertEquals("the flash drive's folders first in the car", LocalSource.FLASH, LocalRules.carFolders(tracks, 3).map { it.source }.distinct().single())
        val artists = LocalRules.artists(listOf(track(1, "x", "Кино"), track(2, "y", null), track(3, "z", "кино")))
        assertEquals("one artist whatever the case, no artist last", listOf("Кино", null), artists.map { it.name })
    }

    // --- Playing ---

    @Test
    fun theDevicesMusicIsNotTheServices() {
        val repo = repository(listOf(track(7, "Кукушка", "Кино")))
        val dto = repo.toDto(track(7, "Кукушка", "Кино"))
        assertTrue(dto.isLocal)
        assertEquals("content://media/external/audio/media/7", dto.localUri)
        assertNull("no artist page to open", dto.artists.single().idString)
        assertEquals("no like or dislike in the notification", emptyList<SessionButtons.Kind>(), SessionButtons.kindsFor(dto, liked = false))
        assertEquals("the car lights «Скачанные»", CarSource.DOWNLOADED, carSourceOf(PlaylistSource.DEVICE, isRadio = false))
        assertEquals(com.korenskoy.yandexmusic.R.string.local_title, playerSourceLabelRes(PlaylistSource.DEVICE, isRadio = false))
        // A playlist of the device's music loops like any other
        assertEquals(com.korenskoy.yandexmusic.data.player.NextAction.PlayIndex(0), PlaybackQueue.nextAction(3, 2, PlaylistSource.DEVICE))
        assertEquals("4:12", durationText(252_000))
        assertEquals("1:02:05", durationText(3_725_000))
    }

    @Test
    fun theListIsReadWithAccessOnlyAndFollowsTheDevice() = runBlocking {
        var allowed = false
        var files = listOf(track(1, "Один"), track(2, "Два"))
        val repo = LocalMusicRepository(
            ApplicationProvider.getApplicationContext(),
            catalogue = { files.map { LocalCatalogue.Found(it, "primary:music/${it.title}") } },
            folderScan = null,
            access = { allowed },
            observe = false
        )
        val first = withTimeout(5_000) { repo.current() }
        assertFalse(first.hasAccess)
        assertTrue("nothing is read without the access", first.tracks.isEmpty())

        allowed = true
        repo.onAccessMaybeChanged()
        val granted = withTimeout(5_000) { repo.library.first { it.hasAccess } }
        assertEquals(listOf("Два", "Один"), granted.tracks.map { it.title })
        assertEquals("Один", repo.track("local_m1")?.title)

        // The flash drive taken out: its tracks go
        files = files.take(1)
        repo.refresh()
        val after = withTimeout(5_000) { repo.library.first { it.tracks.size == 1 } }
        assertEquals("Один", after.tracks.single().title)
        assertNull(repo.track("local_m2"))
    }

    // --- Voice ---

    @Test
    fun theDevicesMusicIsAskedForByWhereItLies() {
        assertEquals(VoicePhrase.Offline.FLASH, VoicePhrase.offline("Включи музыку с флешки"))
        assertEquals(VoicePhrase.Offline.FLASH, VoicePhrase.offline("поставь песни с флешки, пожалуйста"))
        assertEquals(VoicePhrase.Offline.SD, VoicePhrase.offline("включи с SD-карты"))
        assertEquals(VoicePhrase.Offline.PHONE, VoicePhrase.offline("включи музыку с телефона"))
        assertEquals(VoicePhrase.Offline.DEVICE, VoicePhrase.offline("включи музыку с устройства"))
        assertEquals(VoicePhrase.Offline.DOWNLOADED, VoicePhrase.offline("включи скачанное"))
        assertNull("a song called so is a search", VoicePhrase.offline("включи песню флешка"))
        assertNull(VoicePhrase.offline("включи кино"))
    }

    private fun repository(tracks: List<LocalTrack>) = LocalMusicRepository(
        ApplicationProvider.getApplicationContext(),
        catalogue = { tracks.map { LocalCatalogue.Found(it, it.id) } },
        folderScan = null,
        access = { true },
        observe = false
    )
}

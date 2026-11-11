package com.korenskoy.yandexmusic.data.local

import java.nio.charset.Charset
import java.text.Collator
import java.util.Locale

/** Where a file of the user's own music lies. */
enum class LocalSource { PHONE, SD, FLASH }

/**
 * One file of music already on the device: on the phone, an SD card or a flash drive. Found by Android's own
 * music catalogue (MediaStore) or in a folder the user added (head units that do not list their flash drive).
 */
data class LocalTrack(
    /** Stable between scans: "local_m<MediaStore id>" or "local_d<hash of the document>". */
    val id: String,
    /** content:// — what the player opens. */
    val uri: String,
    val title: String,
    /** Null when the file does not say. */
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val source: LocalSource,
    /** The folder, relative to the root of its storage ("Music/Rock/"); "" for the root itself. */
    val folder: String,
    /** When the file appeared, seconds since 1970. */
    val addedAt: Long,
    /** "MP3", "FLAC", "M4A"…; null when unknown. */
    val format: String? = null,
    val bitrateKbps: Int? = null,
    /** The cover: content:// of [LocalArtProvider]. */
    val art: String? = null,
    /** Where the folder's tracks go in their own order (the number of the track on its album). */
    val trackNumber: Int? = null
) {
    /** The folder's key: the same folder on two drives is two folders. */
    val folderKey: String get() = "${source.name}:$folder"

    /** The last part of the folder ("Rock"), null for the root of a storage. */
    val folderName: String? get() = folder.trimEnd('/').substringAfterLast('/').ifEmpty { null }
}

/** A folder of the user's music, as the files lie on the drive. */
data class LocalFolder(val key: String, val name: String?, val source: LocalSource, val tracks: List<LocalTrack>)

/** An artist of the user's music; [name] null — the files that do not say whose they are. */
data class LocalArtist(val name: String?, val tracks: List<LocalTrack>)

enum class LocalSort { RECENT, TITLE, ARTIST }

/** The rules of the user's own music: what counts as music, how it is named, grouped and sorted. Pure; unit-tested. */
object LocalRules {
    const val ID_PREFIX = "local_"

    /** Shorter than this is a sound (a notification, a message), not a song. */
    const val MIN_DURATION_MS = 30_000L

    /** Folders of recordings, calls and system sounds: never music, whatever the file says. */
    private val notMusicFolders = setOf(
        "recordings", "recording", "recorder", "voice recorder", "voicerecorder", "sound_recorder", "soundrecorder",
        "call", "calls", "callrecord", "callrecords", "call recordings", "call_rec", "callrecordings",
        "ringtones", "notifications", "alarms", "ui", "podcasts", "audiobooks",
        "whatsapp voice notes", "telegram voice", "voice notes", "voice messages", "диктофон", "записи", "звонки"
    )

    fun isLocalId(id: String): Boolean = id.startsWith(ID_PREFIX)

    /**
     * A file that is music: long enough (an unknown length passes — a folder the user picked is trusted)
     * and not in a folder of recordings or system sounds.
     */
    fun isMusic(durationMs: Long?, folder: String): Boolean {
        if (durationMs != null && durationMs in 0 until MIN_DURATION_MS) return false
        return folder.split('/').none { it.trim().lowercase(Locale.ROOT) in notMusicFolders }
    }

    private val unknownArtists = setOf("<unknown>", "unknown", "unknown artist", "various artists", "неизвестен", "неизвестный исполнитель", "без названия")

    /** The artist a file names, null for "<unknown>" and the like. */
    fun artistOf(tag: String?): String? = tag?.let(::fixEncoding)?.trim()?.takeUnless { it.isEmpty() || it.lowercase(Locale.ROOT) in unknownArtists }

    /** Album likewise; the folder's name Android writes when there is no album tag does not count. */
    fun albumOf(tag: String?, folderName: String?): String? =
        tag?.let(::fixEncoding)?.trim()?.takeUnless { it.isEmpty() || it.equals(folderName, ignoreCase = true) || it.lowercase(Locale.ROOT) in unknownArtists }

    private val trackNumberPrefix = Regex("^\\(?\\d{1,3}\\)?(?:\\s*[-._)]\\s*|\\s+)")

    /**
     * Title and artist. A file without tags is named by its file: "03 - Кино - Кукушка.mp3" is "Кукушка" by
     * "Кино"; Android puts the file name in the title then, so a title equal to it counts as none.
     */
    fun nameOf(tagTitle: String?, tagArtist: String?, fileName: String): Pair<String, String?> {
        val base = fileName.substringBeforeLast('.').ifBlank { fileName }
        val artist = artistOf(tagArtist)
        val title = tagTitle?.let(::fixEncoding)?.trim()?.takeUnless { it.isEmpty() || it == base || it == fileName }
        if (title != null) return title to artist
        val cleaned = trackNumberPrefix.replace(base.replace('_', ' ').trim(), "").trim().ifEmpty { base }
        val parts = cleaned.split(" - ", " – ", " — ", limit = 2)
        val artistAndTitle = parts.size == 2 && parts.all { it.isNotBlank() }
        return if (artist == null && artistAndTitle) {
            parts[1].trim() to parts[0].trim()
        } else {
            cleaned to artist
        }
    }

    private val windows1251: Charset? = runCatching { Charset.forName("windows-1251") }.getOrNull()

    /**
     * Old Russian MP3s keep their tags in the Windows code page, and come out as "Ãðóïïà êðîâè". Such text (only
     * Latin-1 letters, mostly the accented ones from À to ÿ) is read again as Windows-1251; anything else is left
     * as it is, so real French or German titles stay untouched.
     */
    fun fixEncoding(text: String): String {
        val charset = windows1251 ?: return text
        if (text.any { it.code > 0xFF }) return text
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return text
        val high = letters.count { it.code in 0xC0..0xFF }
        // Real Latin text has a few accents among plain letters; a misread Russian one is nearly all accents
        if (high * 10 < letters.length * 7) return text
        val fixed = String(text.toByteArray(Charsets.ISO_8859_1), charset)
        val cyrillic = fixed.count { it in 'А'..'я' || it == 'Ё' || it == 'ё' }
        return if (cyrillic * 10 >= fixed.count { it.isLetter() } * 7) fixed else text
    }

    /**
     * What kind of storage a volume is, from the name the system gives it ("SD-карта SanDisk", "USB-накопитель").
     * Android tells only "removable", not which kind, so the name decides; an unclear name is a flash drive on a
     * head unit (they rarely have an SD slot) — [phoneHasCardSlot] says otherwise.
     */
    fun sourceOf(isPrimary: Boolean, description: String?, phoneHasCardSlot: Boolean = true): LocalSource {
        if (isPrimary) return LocalSource.PHONE
        val name = description.orEmpty().lowercase(Locale.ROOT)
        return when {
            "usb" in name || "флеш" in name || "флэш" in name || "flash" in name || "накопитель" in name || "otg" in name -> LocalSource.FLASH
            "sd" in name || "карт" in name || "card" in name -> LocalSource.SD
            phoneHasCardSlot -> LocalSource.SD
            else -> LocalSource.FLASH
        }
    }

    /** "MP3" from "audio/mpeg" or the file's extension. */
    fun formatOf(mime: String?, fileName: String): String? {
        val byMime = when (mime?.lowercase(Locale.ROOT)) {
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/mp4a-latm" -> "AAC"
            "audio/ogg", "application/ogg", "audio/vorbis" -> "OGG"
            "audio/opus" -> "OPUS"
            "audio/x-wav", "audio/wav", "audio/wave" -> "WAV"
            "audio/x-ms-wma" -> "WMA"
            else -> null
        }
        if (byMime != null) return byMime
        val ext = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
        return when (ext) {
            "M4A", "MP4", "AAC" -> "AAC"
            "" -> null
            else -> ext.takeIf { it.length in 2..4 }
        }
    }

    /** Audio a folder walk takes: by its type, or by the extension when the type is not told. */
    fun isAudioFile(mime: String?, fileName: String): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        if (mime == "application/ogg") return true
        return fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions
    }

    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mka", "amr")

    private val imageNames = listOf("cover", "folder", "front", "albumart", "album", "obloshka", "обложка")

    /** The picture of a folder among its file names: "cover.jpg" first, then the other usual names. */
    fun folderPicture(names: List<String>): String? {
        val images = names.filter { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp") }
        for (wanted in imageNames) {
            images.firstOrNull { it.substringBeforeLast('.').lowercase(Locale.ROOT) == wanted }?.let { return it }
        }
        images.firstOrNull { it.lowercase(Locale.ROOT).startsWith("albumart") }?.let { return it }
        return images.singleOrNull()
    }

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.PRIMARY }

    fun sorted(tracks: List<LocalTrack>, sort: LocalSort): List<LocalTrack> = when (sort) {
        LocalSort.RECENT -> tracks.sortedWith(compareByDescending<LocalTrack> { it.addedAt }.thenComparator { a, b -> collator.compare(a.title, b.title) })
        LocalSort.TITLE -> tracks.sortedWith { a, b -> collator.compare(a.title, b.title) }
        LocalSort.ARTIST -> tracks.sortedWith(
            Comparator<LocalTrack> { a, b -> compareNullsLast(a.artist, b.artist) }
                .thenComparator { a, b -> compareNullsLast(a.album, b.album) }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenComparator { a, b -> collator.compare(a.title, b.title) }
        )
    }

    private fun compareNullsLast(a: String?, b: String?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> collator.compare(a, b)
    }

    /** The order inside a folder: as the album goes (track numbers), then by name, as a file manager shows it. */
    fun inFolderOrder(tracks: List<LocalTrack>): List<LocalTrack> = tracks.sortedWith(
        compareBy<LocalTrack> { it.trackNumber ?: Int.MAX_VALUE }.thenComparator { a, b -> collator.compare(a.title, b.title) }
    )

    /** Folders by name; the root of a drive comes first. */
    fun folders(tracks: List<LocalTrack>): List<LocalFolder> = tracks.groupBy { it.folderKey }
        .map { (key, inFolder) -> LocalFolder(key, inFolder.first().folderName, inFolder.first().source, inFolderOrder(inFolder)) }
        .sortedWith(compareBy<LocalFolder> { it.name != null }.thenComparator { a, b -> compareNullsLast(a.name, b.name) })

    /** Artists by name; the files that do not say whose they are come last. */
    fun artists(tracks: List<LocalTrack>): List<LocalArtist> = tracks.groupBy { it.artist?.lowercase(Locale.ROOT) }
        .map { (_, theirs) -> LocalArtist(theirs.first().artist, sorted(theirs, LocalSort.ARTIST)) }
        .sortedWith { a, b -> compareNullsLast(a.name, b.name) }

    /** The folders the car offers: those on a flash drive first, then the SD card's, then the phone's. */
    fun carFolders(tracks: List<LocalTrack>, limit: Int): List<LocalFolder> =
        folders(tracks).sortedBy { if (it.source == LocalSource.FLASH) 0 else if (it.source == LocalSource.SD) 1 else 2 }.take(limit)

    /** Where a document of an added folder lies: "<volume>:<relative path>" from the document's id. */
    fun placeOfDocument(documentId: String): Pair<String, String> {
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', documentId)
        return volume to path
    }

    /**
     * The tracks of the added folders that Android's catalogue does not already list: a folder added on a phone
     * whose music is listed anyway must not show every song twice. [listed] holds "<volume>:<relative path>"
     * keys of the catalogue's files.
     */
    fun notListed(fromFolders: List<Pair<String, LocalTrack>>, listed: Set<String>): List<LocalTrack> =
        fromFolders.filter { (place, _) -> place.lowercase(Locale.ROOT) !in listed }.map { it.second }

    /** The catalogue key of a file: volume "external_primary" is the document volume "primary". */
    fun placeKey(volume: String, relativePath: String, fileName: String): String {
        val v = if (volume == "external_primary" || volume == "external") "primary" else volume
        return "$v:$relativePath$fileName".lowercase(Locale.ROOT)
    }
}

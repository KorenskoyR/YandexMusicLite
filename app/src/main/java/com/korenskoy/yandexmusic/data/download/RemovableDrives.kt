package com.korenskoy.yandexmusic.data.download

/**
 * A removable drive (a flash drive or an SD card) the app may write to by itself, without the system's folder
 * window: [path] is the app's own folder on it.
 */
data class Drive(val path: String, val label: String, val usb: Boolean, val freeBytes: Long)

/** What a path typed by the user turned out to be. */
sealed interface PathCheck {
    /** The downloads can go to [path] (the app's folder on that drive, or a folder made there). */
    data class Ok(val path: String, val freeBytes: Long) : PathCheck

    /** No such folder. */
    data object NotFound : PathCheck

    /** The folder is there, but the system does not let the app write to it. */
    data object NotWritable : PathCheck
}

/** The pure part of choosing a drive by hand. Unit-tested. */
object RemovableDrives {
    /** The folder made on a drive given by its path (not on a drive's own app folder). */
    const val FOLDER = "YandexMusicLite"

    private const val APP_DIR = "/Android/data/"

    /** "/storage/1A2B-3C4D/Android/data/<app>/files" → "/storage/1A2B-3C4D": where the drive itself starts. */
    fun volumeRoot(appDir: String): String = appDir.substringBefore(APP_DIR)

    /** The app's own folder on a drive goes away with the app: the settings say so. */
    fun isAppFolder(path: String): Boolean = path.contains(APP_DIR)

    /** A flash drive, by the system's own name for it or by its mount point ("/mnt/usb_storage", "/storage/usbotg"). */
    fun isUsb(description: String?, path: String): Boolean =
        description?.contains("USB", ignoreCase = true) == true || path.contains("usb", ignoreCase = true)

    /** The typed path, cleaned of spaces and a trailing slash; null unless it is an absolute path. */
    fun normalize(input: String): String? {
        val path = input.trim().trimEnd('/')
        return path.takeIf { it.startsWith("/") && it.length > 1 && !it.contains("/../") && !it.endsWith("/..") }
    }

    /** The drive a typed [path] is on: then the app's own folder there serves it (no permission needed). */
    fun driveFor(path: String, drives: List<Drive>): Drive? = drives.firstOrNull { drive ->
        val root = volumeRoot(drive.path)
        path == root || path.startsWith("$root/")
    }
}

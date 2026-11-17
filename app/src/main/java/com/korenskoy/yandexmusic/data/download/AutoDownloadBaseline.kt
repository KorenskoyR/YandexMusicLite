package com.korenskoy.yandexmusic.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Скачивать избранное" takes the likes made after it was switched on; what was liked before goes with «Скачать всю
 * коллекцию». Switching it on downloaded the whole collection at once, so the two did the same (owner, 1.14.10).
 * The tracks liked when it was switched on are kept here; null — not taken yet (it was on before this rule: the
 * first sync of the likes takes them).
 */
@Singleton
class AutoDownloadBaseline @Inject constructor(@ApplicationContext private val context: Context) {
    // Not at construction: it is made on the main thread while the app starts
    private val file by lazy { File(context.filesDir, FILE) }

    @Volatile
    private var cached: Set<String>? = null

    @Synchronized
    fun get(): Set<String>? {
        cached?.let { return it }
        if (!file.exists()) return null
        return runCatching { file.readLines().filter { it.isNotBlank() }.toSet() }.getOrNull()?.also { cached = it }
    }

    @Synchronized
    fun set(trackIds: Collection<String>) {
        val ids = trackIds.toSet()
        runCatching { file.writeText(ids.joinToString("\n")) }
        cached = ids
    }

    @Synchronized
    fun clear() {
        file.delete()
        cached = null
    }

    private companion object {
        const val FILE = "auto_download_baseline.txt"
    }
}

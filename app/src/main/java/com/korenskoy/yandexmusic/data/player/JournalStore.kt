package com.korenskoy.yandexmusic.data.player

import java.io.IOException
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Where the [PlaybackJournal] is kept between runs of the app. */
interface JournalStore {
    fun load(): String?

    /** Writes what [text] gives, off the caller's thread; [urgent] at once, otherwise a little later with what came meanwhile. */
    fun requestSave(urgent: Boolean, text: () -> String)
}

/** The journal in a file of the app, written by one background thread. */
class FileJournalStore(private val file: File) : JournalStore {
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "journal-writer").apply { isDaemon = true } }
    private val scheduled = AtomicBoolean(false)

    override fun load(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    override fun requestSave(urgent: Boolean, text: () -> String) {
        if (urgent) {
            writer.execute { write(text()) }
        } else if (scheduled.compareAndSet(false, true)) {
            writer.schedule({
                scheduled.set(false)
                write(text())
            }, DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun write(content: String) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(content)
            if (!temp.renameTo(file)) {
                file.writeText(content)
                temp.delete()
            }
        } catch (e: IOException) {
            Log.w("JournalStore", "Journal not saved", e)
        }
    }

    private companion object {
        const val DELAY_MS = 3_000L
    }
}

/** How a run of the app ended, as Android tells it: [reason] is one of [ProcessExits.reasonName]. */
data class ProcessExit(val atMs: Long, val reason: String)

/** Android's own word on why the app's process ended (Android 11 and later; nothing before). */
class ProcessExits(private val context: Context) {

    fun lastSince(sinceMs: Long): ProcessExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val exit = am.getHistoricalProcessExitReasons(context.packageName, 0, LOOK_BACK)
            .filter { it.processName == context.packageName }
            .maxByOrNull { it.timestamp }
            ?.takeIf { it.timestamp >= sinceMs } ?: return null
        return ProcessExit(exit.timestamp, reasonName(exit.reason))
    }

    companion object {
        private const val LOOK_BACK = 5

        /** Android's reason, as a stable name the journal keeps and the screen puts into words. */
        fun reasonName(reason: Int): String = when (reason) {
            REASON_LOW_MEMORY -> LOW_MEMORY
            REASON_CRASH, REASON_CRASH_NATIVE -> CRASH
            REASON_ANR -> ANR
            REASON_USER_REQUESTED, REASON_USER_STOPPED -> USER
            REASON_PACKAGE_UPDATED, REASON_PACKAGE_STATE_CHANGE -> UPDATED
            REASON_EXCESSIVE_RESOURCE_USAGE, REASON_FREEZER -> BATTERY
            REASON_SIGNALED, REASON_OTHER -> SYSTEM
            else -> UNKNOWN
        }

        // ApplicationExitInfo's reasons; some came after Android 11, so they are written out
        private const val REASON_SIGNALED = 2
        private const val REASON_LOW_MEMORY = 3
        private const val REASON_CRASH = 4
        private const val REASON_CRASH_NATIVE = 5
        private const val REASON_ANR = 6
        private const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
        private const val REASON_USER_REQUESTED = 10
        private const val REASON_USER_STOPPED = 11
        private const val REASON_OTHER = 13
        private const val REASON_FREEZER = 14
        private const val REASON_PACKAGE_STATE_CHANGE = 15
        private const val REASON_PACKAGE_UPDATED = 16

        const val LOW_MEMORY = "low_memory"
        const val CRASH = "crash"
        const val ANR = "anr"
        const val USER = "user"
        const val UPDATED = "updated"
        const val BATTERY = "battery"
        const val SYSTEM = "system"
        const val UNKNOWN = "unknown"

        /**
         * Closed by the phone itself, not by the listener, an update or a crash of the app: what letting the app
         * work in the background helps against. Unknown counts too (Android before 11 does not say).
         */
        fun closedByThePhone(reason: String?): Boolean = reason == null || reason in setOf(LOW_MEMORY, BATTERY, SYSTEM, UNKNOWN)
    }
}

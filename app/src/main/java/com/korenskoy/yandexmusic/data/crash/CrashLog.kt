package com.korenskoy.yandexmusic.data.crash

import android.content.Context
import android.os.Build
import com.korenskoy.yandexmusic.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local crash journal: an uncaught exception is written to a file before the app closes, so the user
 * can send it to the developer from the settings. Nothing leaves the device by itself; no third-party
 * service is involved. Sign-in tokens are blanked out of every report.
 */
object CrashLog {

    private const val DIR = "crashes"
    private const val MAX_REPORTS = 10

    data class Report(val file: File, val time: Date, val summary: String)

    /** The last events of the playback journal, set by the journal itself: what the player did just before. */
    @Volatile
    var recentPlayback: () -> String? = { null }

    /** Call first thing in Application.onCreate; keeps the system handler (the "app stopped" dialog). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: Thread, error: Throwable, now: Date = Date()) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("Время: " + SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(now))
            appendLine("Версия: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            appendLine("Устройство: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            appendLine("Поток: " + thread.name)
            runCatching { memoryLine(context) }.getOrNull()?.let { appendLine(it) }
            runCatching { webViewLine() }.getOrNull()?.let { appendLine(it) }
            runCatching { "Вывод звука: " + com.korenskoy.yandexmusic.data.player.AudioOutputs.current(context) }.getOrNull()?.let { appendLine(it) }
            appendLine()
            append(stack)
            runCatching { recentPlayback() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Последние события воспроизведения:")
                append(it)
            }
        }
        File(dir, "crash_" + now.time + ".txt").writeText(redact(text))
        // The oldest reports go first
        dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_REPORTS)?.forEach { it.delete() }
    }

    /** "Память: свободно 812 МБ из 3 712, мало: нет; приложение 143 из 512 МБ". */
    private fun memoryLine(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        return "Память: свободно ${info.availMem / mb} МБ из ${info.totalMem / mb}, мало: ${if (info.lowMemory) "да" else "нет"}; " +
            "приложение ${(rt.totalMemory() - rt.freeMemory()) / mb} из ${rt.maxMemory() / mb} МБ"
    }

    /** The WebView of the device (the sign-in page): an old one crashes on some. */
    private fun webViewLine(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val p = android.webkit.WebView.getCurrentWebViewPackage() ?: return "WebView: нет"
        return "WebView: " + p.packageName + " " + p.versionName
    }

    /** Newest first. */
    fun reports(context: Context): List<Report> =
        File(context.filesDir, DIR).listFiles { f -> f.name.startsWith("crash_") }.orEmpty()
            .sortedByDescending { it.name }
            .map { file ->
                val time = file.name.removePrefix("crash_").removeSuffix(".txt").toLongOrNull()?.let { Date(it) } ?: Date(file.lastModified())
                val summary = file.useLines { lines -> lines.dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() } }.orEmpty()
                Report(file, time, summary)
            }

    /** All reports as one text to send. */
    fun fullText(context: Context): String = reports(context).joinToString("\n\n———\n\n") { it.file.readText() }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    /** OAuth tokens (y0_…, "OAuth …", access_token=…) never get into a report. */
    internal fun redact(text: String): String = text
        .replace(Regex("""\by0_[A-Za-z0-9_\-]+"""), "y0_***")
        .replace(Regex("""(?i)(OAuth\s+)[A-Za-z0-9_\-.]+"""), "$1***")
        .replace(Regex("""(?i)(access_token=)[^&\s]+"""), "$1***")
}

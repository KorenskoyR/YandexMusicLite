package com.korenskoy.yandexmusic.data.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What the system said about the update being installed. */
sealed interface InstallState {
    /** Handed to the system; the user may still be asked to confirm. */
    data object Started : InstallState

    /** The system asked the user to confirm (its own window is on the screen). */
    data object Confirming : InstallState

    /** Refused or failed, with the system's own words when it gave any. */
    data class Failed(val reason: String?) : InstallState

    /** Confirmed, but no answer came in time: the system's installer hangs (seen on a head unit). */
    data object NoAnswer : InstallState
}

/**
 * Installs the downloaded update through a session of Android's installer: the same system installer as opening the
 * file, but the app hears back — "confirm", "installed" (the app restarts) or "failed, because…". On a head unit that
 * hung on «Обновление…» for ten minutes the user now learns it did not work and gets the file to install by hand.
 */
@Singleton
class SessionInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow<InstallState?>(null)
    val state: StateFlow<InstallState?> = _state.asStateFlow()

    /** Writes [apk] into a new session and commits it. A failure here (some units refuse sessions) is returned. */
    suspend fun install(apk: File): Result<Unit> = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val sender = PendingIntent.getBroadcast(context, sessionId, callback, flags).intentSender
                _state.value = InstallState.Started
                session.commit(sender)
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.w(TAG, "Install session failed", e)
            abandon(installer, sessionId)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Install session refused", e)
            abandon(installer, sessionId)
            Result.failure(e)
        }
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        if (sessionId < 0) return
        try {
            installer.abandonSession(sessionId)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not abandon session $sessionId", e)
        }
    }

    /** Confirmed but nothing came back in time. */
    fun noAnswer() {
        if (_state.value == InstallState.Started || _state.value == InstallState.Confirming) _state.value = InstallState.NoAnswer
    }

    fun reset() {
        _state.value = null
    }

    internal fun onStatus(intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system's own "install?" window: shown on top of everything
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    _state.value = InstallState.Failed(null)
                    return
                }
                _state.value = InstallState.Confirming
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.w(TAG, "No confirmation window", e)
                    _state.value = InstallState.Failed(null)
                }
            }
            // On success the app is replaced and restarts: this is rarely seen
            PackageInstaller.STATUS_SUCCESS -> _state.value = null
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Install failed: $status $message")
                _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) null else InstallState.Failed(message)
            }
        }
    }

    /**
     * Keeps a copy of the update in «Загрузки» for installing by hand (a file manager). Android 10+ only: older
     * systems need a permission for it, there the share window is used instead.
     */
    suspend fun saveToDownloads(apk: File, versionName: String): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileNameFor(versionName))
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } } ?: error("No stream for $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the update", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    companion object {
        private const val TAG = "SessionInstaller"
        private const val APK_NAME = "base.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
        const val ACTION_STATUS = "com.korenskoy.yandexmusic.UPDATE_INSTALL_STATUS"

        /** "YandexMusicLite-1.14.5.apk": what the user finds in «Загрузки». */
        fun fileNameFor(versionName: String): String = "YandexMusicLite-${versionName.removePrefix("v")}.apk"
    }
}

/** The installer's answers about our session. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionInstaller.ACTION_STATUS) return
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, UpdateAccess::class.java).installer().onStatus(intent)
    }
}

/** The installer singleton for the receiver (created by the system, outside the graph). */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface UpdateAccess {
    fun installer(): SessionInstaller
}

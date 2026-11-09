package com.korenskoy.yandexmusic.data.updater

import com.korenskoy.yandexmusic.R
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.dto.GitHubReleaseDto
import com.korenskoy.yandexmusic.di.UpdateClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApkInstallPreCheck {
    object Compatible : ApkInstallPreCheck()
    data class SignatureMismatch(
        val currentSignatureDigest: String,
        val newSignatureDigest: String
    ) : ApkInstallPreCheck()
    data class CorruptedApk(val reason: String) : ApkInstallPreCheck()
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val versionName: String,
        val title: String,
        val changelog: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long,
        /** Expected SHA-256 (hex) of the APK from the GitHub asset digest; null for old releases. */
        val apkSha256: String? = null
    ) : UpdateCheckResult()

    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

@Singleton
class GitHubUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:UpdateClient private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // GitHub repository endpoint
    private val repoOwner = "KorenskoyR"
    private val repoName = "YandexMusicLite"
    private val releasesApiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

    /**
     * Checks if a newer release exists on GitHub Releases.
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "YandexMusicLite-Android")
                .build()

            val release = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return@withContext UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
                    }
                    return@withContext UpdateCheckResult.Error("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body.string().ifEmpty { return@withContext UpdateCheckResult.Error(context.getString(R.string.update_err_empty_response)) }
                json.decodeFromString<GitHubReleaseDto>(body)
            }

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val localVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            if (isVersionNewer(remoteVersion, localVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext UpdateCheckResult.Error(context.getString(R.string.update_err_no_apk, remoteVersion))

                UpdateCheckResult.UpdateAvailable(
                    versionName = remoteVersion,
                    title = release.name ?: "Yandex Music Lite v$remoteVersion",
                    changelog = cleanChangelog(release.body ?: "", context.getString(R.string.update_notes_default)),
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size,
                    apkSha256 = apkAsset.digest?.removePrefix("sha256:")?.lowercase()
                )
            } else {
                UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error(e.localizedMessage ?: context.getString(R.string.update_err_network))
        }
    }

    /**
     * Cleans up all files in the updates cache folder.
     */
    fun cleanupUpdateFiles() {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning update cache", e)
        }
    }

    /**
     * Checks if the app is allowed to request package installations.
     */
    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Opens Android system settings for allowing installation of unknown apps for this package.
     */
    fun openInstallPermissionSettings() {
        try {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch unknown sources settings", e)
        }
    }

    /**
     * Downloads the APK file to the app's cache directory with streaming progress.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        expectedSha256: String?,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isTrustedDownloadUrl(downloadUrl)) {
            return@withContext Result.failure(SecurityException(context.getString(R.string.update_err_not_github)))
        }
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val tempFile = File(updatesDir, "YandexMusicLite_download.tmp")
        val targetFile = File(updatesDir, "YandexMusicLite.apk")

        try {
            // Clean up old files before downloading
            cleanupUpdateFiles()
            updatesDir.mkdirs()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "YandexMusicLite-Android")
                .build()

            val downloadResult = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(context.getString(R.string.update_err_http, response.code)))
                }

                val responseBody = response.body
                val contentLength = responseBody.contentLength()

                val sha256 = MessageDigest.getInstance("SHA-256")
                responseBody.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            sha256.update(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }

                if (contentLength > 0 && tempFile.length() != contentLength) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception(context.getString(R.string.update_err_incomplete, tempFile.length(), contentLength)))
                }
                val actualSha256 = sha256.digest().joinToString("") { "%02x".format(it) }
                if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(SecurityException(context.getString(R.string.update_err_checksum)))
                }
                true
            }

            if (!downloadResult) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception(context.getString(R.string.update_err_save)))
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(Exception(context.getString(R.string.update_err_apk_empty)))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Pre-checks the downloaded APK for corruption and signature compatibility with the installed application.
     */
    fun checkApkCompatibility(apkFile: File): ApkInstallPreCheck {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return ApkInstallPreCheck.CorruptedApk(context.getString(R.string.update_err_apk_missing))
        }

        return try {
            @Suppress("DEPRECATION")
            val baseFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val packageArchiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, baseFlags)
                ?: return ApkInstallPreCheck.CorruptedApk(context.getString(R.string.update_err_apk_corrupt))

            if (packageArchiveInfo.packageName != context.packageName) {
                return ApkInstallPreCheck.CorruptedApk(context.getString(R.string.update_err_apk_foreign))
            }

            val currentPackageInfo = context.packageManager.getPackageInfo(context.packageName, baseFlags)
            if (versionCodeOf(packageArchiveInfo) <= versionCodeOf(currentPackageInfo)) {
                return ApkInstallPreCheck.CorruptedApk(context.getString(R.string.update_err_apk_older))
            }

            val currentDigests = installedSignatures()
            val newDigests = archiveSignatures(apkFile)
            when {
                // getPackageArchiveInfo often cannot read an archive's signature on some devices even when the
                // APK is correctly signed. Rather than block a legitimate update, defer to the system installer,
                // which verifies the signature cryptographically (and the download was SHA-256-checked already).
                newDigests.isEmpty() || currentDigests.isEmpty() -> {
                    Log.w(TAG, "APK signatures unreadable for pre-check; deferring verification to the system installer")
                    ApkInstallPreCheck.Compatible
                }
                currentDigests.none { it in newDigests } -> ApkInstallPreCheck.SignatureMismatch(
                    currentSignatureDigest = currentDigests.firstOrNull().orEmpty(),
                    newSignatureDigest = newDigests.first()
                )
                else -> ApkInstallPreCheck.Compatible
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking APK compatibility", e)
            // Fail closed: never install what could not be verified
            ApkInstallPreCheck.CorruptedApk(context.getString(R.string.update_err_apk_check))
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    /** Signatures of the installed app, trying the modern API first and falling back to the legacy one. */
    private fun installedSignatures(): List<String> {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val modern = extractSignatures(
                runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            )
            if (modern.isNotEmpty()) return modern
        }
        @Suppress("DEPRECATION")
        return extractSignatures(
            runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES) }.getOrNull()
        )
    }

    /**
     * Signatures of a downloaded APK. `getPackageArchiveInfo` with GET_SIGNING_CERTIFICATES returns a null
     * signingInfo on many devices, so fall back to the (reliable for archives) legacy GET_SIGNATURES.
     */
    private fun archiveSignatures(apkFile: File): List<String> {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val modern = extractSignatures(
                runCatching { pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            )
            if (modern.isNotEmpty()) return modern
        }
        @Suppress("DEPRECATION")
        return extractSignatures(
            runCatching { pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES) }.getOrNull()
        )
    }

    private fun extractSignatures(packageInfo: PackageInfo?): List<String> {
        if (packageInfo == null) return emptyList()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageInfo.signingInfo != null) {
            packageInfo.signingInfo?.apkContentsSigners
                ?: packageInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        return signatures?.mapNotNull { sig ->
            try {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(sig.toByteArray())
                digest.joinToString(":") { "%02X".format(it) }
            } catch (e: java.security.NoSuchAlgorithmException) {
                Log.w(TAG, "SHA-256 unavailable", e)
                null
            }
        } ?: emptyList()
    }

    /**
     * Shares or saves the downloaded APK using Android system chooser.
     */
    fun shareOrSaveApk(apkFile: File) {
        try {
            val authority = "${context.packageName}.update_provider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.update_share_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing APK file", e)
        }
    }

    /**
     * Installs the downloaded APK using Android PackageInstaller.
     */
    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception(context.getString(R.string.update_err_apk_missing)))
            }

            // On Android 8.0+ check if installing from unknown sources is permitted
            if (!canRequestPackageInstalls()) {
                openInstallPermissionSettings()
                return Result.failure(SecurityException(context.getString(R.string.update_err_allow_install)))
            }

            val authority = "${context.packageName}.update_provider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }

            val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            }

            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GitHubUpdateManager"
        /** Used without a Context (tests); the app passes the localized string. */
        private const val DEFAULT_NOTES = "•"

        /**
         * Cleans technical noise and links from changelog text for user display.
         */
        fun cleanChangelog(rawBody: String, fallback: String = DEFAULT_NOTES): String {
            if (rawBody.isBlank()) return fallback

            val lines = rawBody.lines()
                .filterNot { it.contains("Full Changelog", ignoreCase = true) }
                .filterNot { it.contains("compare/v", ignoreCase = true) }
                .filterNot { it.contains("github.com", ignoreCase = true) && it.contains("/compare/") }
                .filterNot { it.startsWith("## What's Changed", ignoreCase = true) }
                .map { line ->
                    line.replace(Regex("by @[a-zA-Z0-9_-]+ in https://\\S+"), "")
                        .replace(Regex("\\[(.*?)\\]\\(https://\\S+\\)"), "$1")
                        .replace(Regex("https://github\\.com/\\S+"), "")
                        .replace(Regex("^[*-]\\s*"), "• ")
                        .trim()
                }
                .filter { it.isNotBlank() }

            return if (lines.isEmpty()) {
                fallback
            } else {
                lines.joinToString("\n")
            }
        }

        /**
         * Compares two semantic version strings (e.g. "1.2.0" vs "1.1.1").
         */
        /** Release assets are served from github.com and redirected to GitHub's object storage. */
        fun isTrustedDownloadUrl(url: String): Boolean {
            val uri = try { java.net.URI(url) } catch (e: java.net.URISyntaxException) { return false }
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && (host == "github.com" || host.endsWith(".githubusercontent.com"))
        }

        fun isVersionNewer(remoteVersion: String, localVersion: String): Boolean {
            val rParts = remoteVersion.removePrefix("v").split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val lParts = localVersion.removePrefix("v").split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(rParts.size, lParts.size)

            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }
    }
}

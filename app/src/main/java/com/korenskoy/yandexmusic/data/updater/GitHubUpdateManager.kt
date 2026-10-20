package com.korenskoy.yandexmusic.data.updater

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.dto.GitHubReleaseDto
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
    private val okHttpClient: OkHttpClient
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

                val body = response.body.string().ifEmpty { return@withContext UpdateCheckResult.Error("Пустой ответ от GitHub API") }
                json.decodeFromString<GitHubReleaseDto>(body)
            }

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val localVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            if (isVersionNewer(remoteVersion, localVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext UpdateCheckResult.Error("Релиз $remoteVersion найден, но APK-файл в ассетах отсутствует")

                UpdateCheckResult.UpdateAvailable(
                    versionName = remoteVersion,
                    title = release.name ?: "Yandex Music Lite v$remoteVersion",
                    changelog = cleanChangelog(release.body ?: ""),
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size,
                    apkSha256 = apkAsset.digest?.removePrefix("sha256:")?.lowercase()
                )
            } else {
                UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error(e.localizedMessage ?: "Сетевая ошибка при проверке обновлений")
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
    fun canRequestPackageInstalls(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Opens Android system settings for allowing installation of unknown apps for this package.
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            return@withContext Result.failure(SecurityException("Обновление можно скачать только с GitHub по HTTPS"))
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
                    return@withContext Result.failure(Exception("HTTP ${response.code} при скачивании обновления"))
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
                    return@withContext Result.failure(Exception("Загрузка прервана: получено ${tempFile.length()} из $contentLength байт"))
                }
                val actualSha256 = sha256.digest().joinToString("") { "%02x".format(it) }
                if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(SecurityException("Контрольная сумма обновления не совпадает с опубликованной на GitHub"))
                }
                true
            }

            if (!downloadResult) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext Result.failure(Exception("Ошибка сохранения обновления"))
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(Exception("Файл APK пуст или не был сохранен"))
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
            return ApkInstallPreCheck.CorruptedApk("Файл обновления пуст или не найден")
        }

        return try {
            val packageArchiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
            }

            if (packageArchiveInfo == null) {
                return ApkInstallPreCheck.CorruptedApk("Файл поврежден или не является корректным пакетом APK")
            }
            if (packageArchiveInfo.packageName != context.packageName) {
                return ApkInstallPreCheck.CorruptedApk("Файл не является обновлением Yandex Music Lite")
            }

            val currentPackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            if (versionCodeOf(packageArchiveInfo) <= versionCodeOf(currentPackageInfo)) {
                return ApkInstallPreCheck.CorruptedApk("Скачанная версия не новее установленной")
            }

            val currentDigests = extractSignatures(currentPackageInfo)
            val newDigests = extractSignatures(packageArchiveInfo)
            if (newDigests.isEmpty()) {
                return ApkInstallPreCheck.CorruptedApk("Файл обновления не подписан")
            }
            if (currentDigests.none { it in newDigests }) {
                return ApkInstallPreCheck.SignatureMismatch(
                    currentSignatureDigest = currentDigests.firstOrNull().orEmpty(),
                    newSignatureDigest = newDigests.first()
                )
            }

            ApkInstallPreCheck.Compatible
        } catch (e: Exception) {
            Log.e(TAG, "Error checking APK compatibility", e)
            // Fail closed: never install what could not be verified
            ApkInstallPreCheck.CorruptedApk("Не удалось проверить файл обновления")
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun extractSignatures(packageInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
            } catch (e: Exception) {
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
            val chooser = Intent.createChooser(shareIntent, "Сохранить или отправить файл обновления").apply {
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
                return Result.failure(Exception("Файл APK не найден или пуст"))
            }

            // On Android 8.0+ check if installing from unknown sources is permitted
            if (!canRequestPackageInstalls()) {
                openInstallPermissionSettings()
                return Result.failure(SecurityException("Пожалуйста, разрешите установку приложений для Yandex Music Lite в открывшихся настройках"))
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

        /**
         * Cleans technical noise and links from changelog text for user display.
         */
        fun cleanChangelog(rawBody: String): String {
            if (rawBody.isBlank()) return "• Улучшения стабильности и воспроизведения аудио"

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
                "• Улучшения стабильности и воспроизведения аудио"
            } else {
                lines.joinToString("\n")
            }
        }

        /**
         * Compares two semantic version strings (e.g. "1.2.0" vs "1.1.1").
         */
        /** Release assets are served from github.com and redirected to GitHub's object storage. */
        fun isTrustedDownloadUrl(url: String): Boolean {
            val uri = try { java.net.URI(url) } catch (e: Exception) { return false }
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

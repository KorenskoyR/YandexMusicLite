package com.korenskoy.yandexmusic.data.updater

import android.content.Context
import android.content.Intent
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
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val versionName: String,
        val title: String,
        val changelog: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long
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

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
                }
                return@withContext UpdateCheckResult.Error("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Пустой ответ от GitHub API")
            val release = json.decodeFromString<GitHubReleaseDto>(body)

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val localVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            if (isVersionNewer(remoteVersion, localVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext UpdateCheckResult.Error("Релиз $remoteVersion найден, но APK-файл в ассетах отсутствует")

                UpdateCheckResult.UpdateAvailable(
                    versionName = remoteVersion,
                    title = release.name ?: "Yandex Music Lite v$remoteVersion",
                    changelog = release.body ?: "Улучшения стабильности и производительности",
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size
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
     * Downloads the APK file to the app's cache directory with streaming progress.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val tempFile = File(updatesDir, "YandexMusicLite_download.tmp")
            val targetFile = File(updatesDir, "YandexMusicLite.apk")

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "YandexMusicLite-Android")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code} при скачивании обновления"))
            }

            val responseBody = response.body ?: return@withContext Result.failure(Exception("Пустое тело ответа"))
            val contentLength = responseBody.contentLength()

            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }

    /**
     * Installs the downloaded APK using Android PackageInstaller.
     */
    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("Файл APK не найден"))
            }

            // On Android 8.0+ check if installing from unknown sources is permitted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return Result.failure(SecurityException("Пожалуйста, разрешите установку приложений для Yandex Music Lite в открывшихся настройках, затем повторите"))
            }

            val authority = "${context.packageName}.update_provider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
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
         * Compares two semantic version strings (e.g. "1.2.0" vs "1.1.1").
         */
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

package com.korenskoy.yandexmusic.data

import com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlTest {
    @Test
    fun onlyGithubHttpsDownloadsAreTrusted() {
        assertTrue(GitHubUpdateManager.isTrustedDownloadUrl("https://github.com/KorenskoyR/YandexMusicLite/releases/download/v1.4.1/YandexMusicLite.apk"))
        assertTrue(GitHubUpdateManager.isTrustedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/1"))
        assertFalse(GitHubUpdateManager.isTrustedDownloadUrl("http://github.com/x.apk"))
        assertFalse(GitHubUpdateManager.isTrustedDownloadUrl("https://github.com.evil.com/x.apk"))
        assertFalse(GitHubUpdateManager.isTrustedDownloadUrl("https://evil.com/github.com/x.apk"))
        assertFalse(GitHubUpdateManager.isTrustedDownloadUrl("not a url"))
    }
}

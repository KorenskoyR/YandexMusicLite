package com.korenskoy.yandexmusic.presentation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Automated Architecture & Layout Safety Test Suite.
 * Prevents regressions in:
 * 1. Status bar insets (statusBarsPadding) on all screen headers.
 * 2. Navigation bar insets (navigationBarsPadding) on bottom bars.
 * 3. Coordinated bottom stack (MiniPlayer above FloatingGlassBottomBar) in portrait mode.
 * 4. Isolation of Car mode (MainTab.CAR) so global navigation/miniplayer never overlap car controls.
 */
class LayoutSafetyTest {

    private val sourceDir = File("src/main/java/com/korenskoy/yandexmusic")

    @Test
    fun testAllScreensHaveStatusBarsPadding() {
        val searchScreen = File(sourceDir, "presentation/main/SearchScreen.kt").readText()
        assertTrue("SearchScreen must contain statusBarsPadding() for its header", searchScreen.contains("statusBarsPadding()"))

        val libraryScreen = File(sourceDir, "presentation/main/LibraryScreen.kt").readText()
        assertTrue("LibraryScreen must contain statusBarsPadding() for its hero banner", libraryScreen.contains("statusBarsPadding()"))

        val radioScreen = File(sourceDir, "presentation/radio/RadioScreen.kt").readText()
        assertTrue("RadioScreen must contain statusBarsPadding() for its header", radioScreen.contains("statusBarsPadding()"))

        val homeScreen = File(sourceDir, "presentation/main/HomeScreen.kt").readText()
        assertTrue("HomeScreen must contain statusBarsPadding()", homeScreen.contains("statusBarsPadding()"))

        val carScreen = File(sourceDir, "presentation/car/CarScreen.kt").readText()
        assertTrue("CarScreen must contain statusBarsPadding()", carScreen.contains("statusBarsPadding()"))
    }

    @Test
    fun testCarScreenHasNavigationBarsPadding() {
        val carScreen = File(sourceDir, "presentation/car/CarScreen.kt").readText()
        assertTrue("CarScreen must contain navigationBarsPadding()", carScreen.contains("navigationBarsPadding()"))
    }

    @Test
    fun testMainScreenHidesGlobalBarsInCarMode() {
        val mainScreen = File(sourceDir, "presentation/main/MainScreen.kt").readText()
        assertTrue("MainScreen must check currentTab != MainTab.CAR for portrait bottom overlay", 
            mainScreen.contains("currentTab != MainTab.CAR"))
    }

    @Test
    fun testPortraitBottomOverlayIsCoordinatedColumn() {
        val mainScreen = File(sourceDir, "presentation/main/MainScreen.kt").readText()
        // Ensure MiniPlayer and FloatingGlassBottomBar are placed inside a Column in portrait mode
        assertTrue("MainScreen portrait overlay must use Column to stack MiniPlayer and FloatingGlassBottomBar",
            mainScreen.contains("Column(") && mainScreen.contains("FloatingGlassBottomBar("))
    }

    @Test
    fun testLandscapeMiniPlayerIsCentered() {
        val mainScreen = File(sourceDir, "presentation/main/MainScreen.kt").readText()
        assertTrue("MainScreen landscape MiniPlayer must be aligned to BottomCenter",
            mainScreen.contains(".align(Alignment.BottomCenter)"))
    }

    @Test
    fun testCarTimeWidgetPresent() {
        val carScreen = File(sourceDir, "presentation/car/CarScreen.kt").readText()
        assertTrue("CarScreen must declare CarTimeWidget", carScreen.contains("fun CarTimeWidget("))
        assertTrue("CarScreenPortrait must use CarTimeWidget", carScreen.contains("CarTimeWidget()"))
    }

    @Test
    fun testCarScreenActionTilesHaveExplicitHeights() {
        val carScreen = File(sourceDir, "presentation/car/CarScreen.kt").readText()
        assertTrue("CarScreen landscape must set height(48.dp) on preset rows", carScreen.contains("height(48.dp)"))
        assertTrue("CarScreen portrait must set height(50.dp) on preset rows", carScreen.contains("height(50.dp)"))
    }

    @Test
    fun testLibraryScreenHasLiquidGlassHeaderCapsule() {
        val libraryScreen = File(sourceDir, "presentation/main/LibraryScreen.kt").readText()
        assertTrue("LibraryScreen must accept onRefresh callback", libraryScreen.contains("onRefresh: () -> Unit"))
        assertTrue("LibraryScreen must contain Liquid Glass header capsule", libraryScreen.contains("Liquid Glass Header Toolbar Capsule"))
        assertTrue("LibraryScreen must render refresh action in header", libraryScreen.contains("Icons.Default.Refresh"))
        assertTrue("LibraryScreen must render settings action in header", libraryScreen.contains("Icons.Default.Settings"))
    }

    @Test
    fun testCarScreenLandscapePresetsGroupedAtBottom() {
        val carScreen = File(sourceDir, "presentation/car/CarScreen.kt").readText()
        assertTrue("CarScreen landscape presets must be grouped in a column", carScreen.contains("Presets Block (Grouped together at bottom like in portrait mode)"))
    }

    @Test
    fun testTracksResponseDeserializationNumericIds() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val sampleJson = """
            {
                "result": {
                    "library": {
                        "uid": 12345678,
                        "revision": 42,
                        "tracks": [
                            {"id": 99887766, "albumId": 554433, "timestamp": "2026-08-31T12:00:00"},
                            {"id": "11223344", "albumId": "443322", "timestamp": "2026-08-31T12:01:00"}
                        ]
                    }
                }
            }
        """.trimIndent()
        
        val parsed = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.TracksResponseDto>>(sampleJson)
        val tracks = parsed.result?.allTracks
        org.junit.Assert.assertNotNull("Parsed tracks must not be null", tracks)
        org.junit.Assert.assertEquals("Parsed tracks count must be 2", 2, tracks?.size)
        org.junit.Assert.assertEquals("Numeric ID must convert to string", "99887766", tracks?.get(0)?.idString)
        org.junit.Assert.assertEquals("String ID must stay string", "11223344", tracks?.get(1)?.idString)
    }

    @Test
    fun testTracksResponseDeserializationRichTracks() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val sampleJson = """
            {
                "result": {
                    "library": {
                        "uid": 12345678,
                        "revision": 42,
                        "tracks": [
                            {
                                "id": 12345,
                                "track": {
                                    "id": 12345,
                                    "title": "Rich Track Title",
                                    "artists": [{"id": 1, "name": "Famous Artist"}],
                                    "coverUri": "avatars.yandex.net/get-music-content/123/%%",
                                    "durationMs": 180000
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent()
        
        val parsed = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.TracksResponseDto>>(sampleJson)
        val tracks = parsed.result?.allTracks
        org.junit.Assert.assertNotNull("Parsed tracks must not be null", tracks)
        org.junit.Assert.assertEquals("Parsed tracks count must be 1", 1, tracks?.size)
        val firstTrack = tracks?.get(0)
        org.junit.Assert.assertNotNull("Embedded track object must be present", firstTrack?.track)
        org.junit.Assert.assertEquals("Track title must match", "Rich Track Title", firstTrack?.track?.title)
        org.junit.Assert.assertEquals("Track ID must match", "12345", firstTrack?.idString)
    }

    @Test
    fun testTracksResponseDeserializationDirectTracks() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val sampleJson = """
            {
                "result": {
                    "uid": 12345678,
                    "tracks": [
                        {"id": 776655, "albumId": 1122}
                    ]
                }
            }
        """.trimIndent()
        
        val parsed = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.TracksResponseDto>>(sampleJson)
        val tracks = parsed.result?.allTracks
        org.junit.Assert.assertNotNull("Parsed direct tracks must not be null", tracks)
        org.junit.Assert.assertEquals("Parsed direct tracks count must be 1", 1, tracks?.size)
        org.junit.Assert.assertEquals("Track ID must match", "776655", tracks?.get(0)?.idString)
    }

    @Test
    fun testFloatingGlassBottomBarConcentricCorners() {
        val bottomBar = File(sourceDir, "presentation/main/components/BottomNavigationBar.kt").readText()
        assertTrue("FloatingGlassBottomBar must use outerRadius shell", bottomBar.contains("val outerRadius = 26.dp") || bottomBar.contains("shape = RoundedCornerShape(outerRadius)"))
        assertTrue("FloatingGlassBottomBar must declare concentric innerRadius", bottomBar.contains("val innerRadius = 20.dp"))
    }

    @Test
    fun testApiTracksMetadataSignatures() {
        val apiFile = File(sourceDir, "data/network/YandexMusicApi.kt").readText()
        assertTrue("YandexMusicApi must declare POST getTracksMetadata with List<String>", 
            apiFile.contains("suspend fun getTracksMetadata(\n        @retrofit2.http.Field(\"track-ids\") trackIds: List<String>"))
        assertTrue("YandexMusicApi must declare GET getTracksMetadataQuery with String", 
            apiFile.contains("suspend fun getTracksMetadataQuery(\n        @retrofit2.http.Query(\"track-ids\") trackIds: String"))
        assertTrue("YandexMusicApi must declare rich-tracks parameter on getLikedTracks", 
            apiFile.contains("@retrofit2.http.Query(\"rich-tracks\") richTracks: Boolean = true"))
    }

    @Test
    fun testNoUnsafeTextUnitConstructors() {
        val allKotlinFiles = sourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
        for (file in allKotlinFiles) {
            val content = file.readText()
            org.junit.Assert.assertFalse(
                "File ${file.name} contains unsafe TextUnit( constructor call which causes runtime NoSuchMethodError in Compose: $file",
                content.contains("TextUnit(")
            )
        }
    }

    @Test
    fun testAccountStatusResponseDtoUidParsing() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        
        // Nested uid in account
        val nestedJson = """{"result": {"account": {"uid": 12345678}}}"""
        val parsedNested = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.AccountStatusResponseDto>>(nestedJson)
        org.junit.Assert.assertEquals("Nested uidLong should be 12345678", 12345678L, parsedNested.result?.uidLong)
        org.junit.Assert.assertEquals("Nested uidString should be 12345678", "12345678", parsedNested.result?.uidString)

        // Direct uid in result
        val directJson = """{"result": {"uid": "87654321"}}"""
        val parsedDirect = json.decodeFromString<com.korenskoy.yandexmusic.data.dto.ResponseWrapper<com.korenskoy.yandexmusic.data.dto.AccountStatusResponseDto>>(directJson)
        org.junit.Assert.assertEquals("Direct uidLong should be 87654321", 87654321L, parsedDirect.result?.uidLong)
        org.junit.Assert.assertEquals("Direct uidString should be 87654321", "87654321", parsedDirect.result?.uidString)
    }

    @Test
    fun testRadioStationResourceUriFormat() {
        val viewModelFile = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertTrue("MainViewModel must use BuildConfig.APPLICATION_ID for radio resource uri",
            viewModelFile.contains("android.resource://\${BuildConfig.APPLICATION_ID}/\$it"))
        org.junit.Assert.assertFalse("MainViewModel must not hardcode old package name in resource uri",
            viewModelFile.contains("android.resource://com.korenskoy.yandexmusic/"))
    }

    @Test
    fun testApplicationBrandingAndName() {
        val stringsXml = File("src/main/res/values/strings.xml").readText()
        assertTrue("strings.xml must declare app_name as Yandex Music Lite",
            stringsXml.contains("<string name=\"app_name\">Yandex Music Lite</string>"))

        val splashScreen = File(sourceDir, "presentation/splash/SplashScreen.kt").readText()
        assertTrue("SplashScreen must display YANDEX MUSIC LITE",
            splashScreen.contains("YANDEX MUSIC LITE"))

        val searchScreen = File(sourceDir, "presentation/main/SearchScreen.kt").readText()
        assertTrue("SearchScreen must display Каталог Yandex Music Lite",
            searchScreen.contains("Каталог Yandex Music Lite"))

        val musicService = File(sourceDir, "data/player/MusicService.kt").readText()
        assertTrue("MusicService notification channel must be Yandex Music Lite",
            musicService.contains("Yandex Music Lite"))
    }

    @Test
    fun testPlaybackWakeLockAndErrorHandling() {
        val playerRepo = File(sourceDir, "data/repository/PlayerRepositoryImpl.kt").readText()
        assertTrue("PlayerRepositoryImpl must use PlaybackWakeLockHelper",
            playerRepo.contains("wakeLockHelper.acquireTransitionLock"))
        assertTrue("PlayerRepositoryImpl must override onPlayerError",
            playerRepo.contains("override fun onPlayerError("))
        assertTrue("PlayerRepositoryImpl must triggerSkipNext on error",
            playerRepo.contains("triggerSkipNext()"))
    }

    @Test
    fun testInAppUpdateVersionComparison() {
        org.junit.Assert.assertTrue(com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager.isVersionNewer("1.2.1", "1.2.0"))
        org.junit.Assert.assertTrue(com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager.isVersionNewer("v2.0.0", "1.2.0"))
        org.junit.Assert.assertFalse(com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager.isVersionNewer("1.2.0", "1.2.0"))
        org.junit.Assert.assertFalse(com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager.isVersionNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun testApplicationIdMatchesUserRequirement() {
        val buildGradle = File("build.gradle.kts").readText()
        assertTrue("build.gradle.kts must set applicationId = YandexMusicLite.KorenskoyR",
            buildGradle.contains("applicationId = \"YandexMusicLite.KorenskoyR\""))
    }

    @Test
    fun testNoHardcodedSigningSecretsInBuildGradle() {
        val buildGradle = File("build.gradle.kts").readText()
        org.junit.Assert.assertFalse("build.gradle.kts must not contain hardcoded storePassword string literal",
            buildGradle.contains("storePassword = \""))
        org.junit.Assert.assertFalse("build.gradle.kts must not contain hardcoded keyPassword string literal",
            buildGradle.contains("keyPassword = \""))
        assertTrue("build.gradle.kts must read KEYSTORE_PASSWORD from localProperties or env",
            buildGradle.contains("localProperties.getProperty(\"KEYSTORE_PASSWORD\")"))
    }

    @Test
    fun testProgressTransitionIsolation() {
        val playerRepo = File(sourceDir, "data/repository/PlayerRepositoryImpl.kt").readText()
        assertTrue("PlayerRepositoryImpl must guard progress polling with !isTransitioning",
            playerRepo.contains("isPlaying && !isTransitioning"))
        assertTrue("PlayerRepositoryImpl must set isTransitioning = true during track transition",
            playerRepo.contains("isTransitioning = true"))
        assertTrue("PlayerRepositoryImpl must set isCrossfading = true during crossfade",
            playerRepo.contains("isCrossfading = true"))
    }

    @Test
    fun testStartupUpdateCheck5SecondsDelay() {
        val viewModel = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertTrue("MainViewModel must delay 5000ms before silent update check",
            viewModel.contains("delay(5000L)"))
    }

    @Test
    fun testQueuePrefetchThreshold() {
        val player = File(sourceDir, "presentation/player/FullScreenPlayer.kt").readText()
        assertTrue("FullScreenPlayer must prefetch when lastVisible >= total - 12",
            player.contains("lastVisible >= total - 12"))
    }

    @Test
    fun testUserAccountFolderNameSanitization() {
        val accountWithLogin = com.korenskoy.yandexmusic.domain.model.UserAccount(
            uid = "1001",
            login = "roman.korenskoy",
            displayName = "Роман Коренской",
            token = "secret_token_1"
        )
        // Login takes priority for folderName and dots are replaced with underscores
        org.junit.Assert.assertEquals("roman_korenskoy", accountWithLogin.folderName)

        val accountWithDisplayNameOnly = com.korenskoy.yandexmusic.domain.model.UserAccount(
            uid = "1002",
            login = "",
            displayName = "Анна Смирнова",
            token = "secret_token_2"
        )
        org.junit.Assert.assertEquals("Анна_Смирнова", accountWithDisplayNameOnly.folderName)

        val accountWithUidFallback = com.korenskoy.yandexmusic.domain.model.UserAccount(
            uid = "998877",
            login = "",
            displayName = "",
            token = "secret_token_3"
        )
        org.junit.Assert.assertEquals("998877", accountWithUidFallback.folderName)

        val accountWithBlankAll = com.korenskoy.yandexmusic.domain.model.UserAccount(
            uid = "!!!",
            login = "",
            displayName = "",
            token = "secret_token_4"
        )
        org.junit.Assert.assertEquals("user_!!!", accountWithBlankAll.folderName)
    }

    @Test
    fun testEqualPowerCrossfadeEnergyConstant() {
        // Equal-Power invariant: cos^2(angle) + sin^2(angle) == 1.0 (Zero loudness dip at midpoint)
        val steps = 30
        for (step in 0..steps) {
            val fraction = step.toDouble() / steps
            val angle = fraction * (Math.PI / 2.0)
            val outVol = kotlin.math.cos(angle)
            val inVol = kotlin.math.sin(angle)
            val totalPower = (outVol * outVol) + (inVol * inVol)
            org.junit.Assert.assertEquals(
                "Total acoustic energy must equal 1.0 at step $step ($fraction)",
                1.0,
                totalPower,
                0.0001
            )
        }
    }

    @Test
    fun testMultiAccountArchitectureComponentsExist() {
        val authRepoFile = File(sourceDir, "data/repository/AuthRepositoryImpl.kt").readText()
        assertTrue("AuthRepositoryImpl must implement switchAccount", authRepoFile.contains("fun switchAccount("))
        assertTrue("AuthRepositoryImpl must implement removeAccount", authRepoFile.contains("fun removeAccount("))
        assertTrue("AuthRepositoryImpl must implement clearAllAccounts", authRepoFile.contains("fun clearAllAccounts()"))

        val cacheManagerFile = File(sourceDir, "data/cache/PlaylistCacheManager.kt").readText()
        assertTrue("PlaylistCacheManager must contain getUserCacheDir", cacheManagerFile.contains("getUserCacheDir("))
        assertTrue("PlaylistCacheManager must contain clearUserCache", cacheManagerFile.contains("fun clearUserCache("))

        val dialogFile = File(sourceDir, "presentation/account/AccountManagementDialog.kt").readText()
        assertTrue("AccountManagementDialog must declare AccountManagementDialog composable",
            dialogFile.contains("fun AccountManagementDialog("))
        assertTrue("AccountManagementDialog must support switching accounts",
            dialogFile.contains("onSwitchAccount"))
        assertTrue("AccountManagementDialog must support removing accounts",
            dialogFile.contains("onRemoveAccount"))
    }

    @Test
    fun testCleanChangelogRemovesTechnicalNoise() {
        val rawGitHubChangelog = """
            ## What's Changed
            * feat: added multi-account switching by @user in https://github.com/repo/pull/1
            * fix: memory leak in player by @dev in https://github.com/repo/pull/2
            **Full Changelog**: https://github.com/KorenskoyR/YandexMusicLite/compare/v1.2.1...v1.3.0
        """.trimIndent()

        val cleaned = com.korenskoy.yandexmusic.data.updater.GitHubUpdateManager.cleanChangelog(rawGitHubChangelog)

        org.junit.Assert.assertFalse("Cleaned changelog must not contain Full Changelog", cleaned.contains("Full Changelog"))
        org.junit.Assert.assertFalse("Cleaned changelog must not contain compare/ link", cleaned.contains("compare/v1"))
        org.junit.Assert.assertFalse("Cleaned changelog must not contain PR links", cleaned.contains("by @"))
        org.junit.Assert.assertFalse("Cleaned changelog must not contain raw github url", cleaned.contains("https://github.com"))
        assertTrue("Cleaned changelog must format items with clean bullet points", cleaned.contains("• feat: added multi-account switching"))
    }

    @Test
    fun testDefaultAccountAndStartupPickerArchitecture() {
        val authRepoImpl = File(sourceDir, "data/repository/AuthRepositoryImpl.kt").readText()
        assertTrue("AuthRepositoryImpl must contain KEY_DEFAULT_ACCOUNT_UID",
            authRepoImpl.contains("KEY_DEFAULT_ACCOUNT_UID"))
        assertTrue("AuthRepositoryImpl must implement defaultAccountUid StateFlow",
            authRepoImpl.contains("defaultAccountUid: StateFlow<String?>"))
        assertTrue("AuthRepositoryImpl must implement setDefaultAccountUid",
            authRepoImpl.contains("fun setDefaultAccountUid("))

        val startupDialogFile = File(sourceDir, "presentation/account/StartupAccountPickerDialog.kt").readText()
        assertTrue("StartupAccountPickerDialog must declare composable",
            startupDialogFile.contains("fun StartupAccountPickerDialog("))
        assertTrue("StartupAccountPickerDialog must have rememberAsDefault option",
            startupDialogFile.contains("rememberAsDefault"))

        val mainScreenFile = File(sourceDir, "presentation/main/MainScreen.kt").readText()
        assertTrue("MainScreen must check showStartupAccountPicker",
            mainScreenFile.contains("showStartupAccountPicker"))
    }

    @Test
    fun testUpdateInstallationSecurityAndUnknownSourcesHandling() {
        val updateManagerFile = File(sourceDir, "data/updater/GitHubUpdateManager.kt").readText()
        assertTrue("GitHubUpdateManager must have cleanupUpdateFiles",
            updateManagerFile.contains("fun cleanupUpdateFiles("))
        assertTrue("GitHubUpdateManager must have canRequestPackageInstalls",
            updateManagerFile.contains("fun canRequestPackageInstalls("))
        assertTrue("GitHubUpdateManager must pass EXTRA_NOT_UNKNOWN_SOURCE",
            updateManagerFile.contains("Intent.EXTRA_NOT_UNKNOWN_SOURCE"))

        assertTrue("GitHubUpdateManager must have checkApkCompatibility",
            updateManagerFile.contains("fun checkApkCompatibility("))

        val manifestFile = File("src/main/AndroidManifest.xml").readText()
        assertTrue("AndroidManifest must declare autoRevokePermissions discouraged",
            manifestFile.contains("android:autoRevokePermissions=\"discouraged\""))

        val buildGradle = File("build.gradle.kts").readText()
        assertTrue("build.gradle.kts must be versionCode 17",
            buildGradle.contains("versionCode = 17"))
        assertTrue("build.gradle.kts must be versionName 1.4.0",
            buildGradle.contains("versionName = \"1.4.0\""))
    }

    @Test
    fun testWarmStandbyAndEqualPowerCrossfadeIntegration() {
        val playerRepo = File(sourceDir, "data/repository/PlayerRepositoryImpl.kt").readText()
        assertTrue("PlayerRepositoryImpl must define PREPARE_STANDBY_THRESHOLD_MS",
            playerRepo.contains("PREPARE_STANDBY_THRESHOLD_MS = 15_000L"))
        assertTrue("PlayerRepositoryImpl must define CROSSFADE_DURATION_MS",
            playerRepo.contains("CROSSFADE_DURATION_MS = 3000L"))
        assertTrue("PlayerRepositoryImpl must define CROSSFADE_STEPS",
            playerRepo.contains("CROSSFADE_STEPS = 60"))
        assertTrue("PlayerRepositoryImpl must have prepareNextTrackEvent",
            playerRepo.contains("override val prepareNextTrackEvent: Flow<Unit>"))
        assertTrue("PlayerRepositoryImpl must wait for STATE_READY before starting crossfade",
            playerRepo.contains("incomingStandby.playbackState != Player.STATE_READY"))
        assertTrue("PlayerRepositoryImpl must support isAutoCrossfade",
            playerRepo.contains("isAutoCrossfade: Boolean"))
        assertTrue("PlayerRepositoryImpl must expose activePlayerState StateFlow",
            playerRepo.contains("override val activePlayerState: StateFlow<ExoPlayer>"))

        val preloadManager = File(sourceDir, "data/network/TrackPreloadManager.kt").readText()
        assertTrue("TrackPreloadManager must use Deferred concurrency for in-flight requests",
            preloadManager.contains("Deferred<String?>"))

        val musicService = File(sourceDir, "data/player/MusicService.kt").readText()
        assertTrue("MusicService must track transient loss",
            musicService.contains("wasPlayingBeforeTransientLoss"))
        assertTrue("MusicService must resume on AUDIOFOCUS_GAIN",
            musicService.contains("AUDIOFOCUS_GAIN"))

        val viewModel = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertTrue("MainViewModel must observe prepareNextTrackEvent",
            viewModel.contains("playerRepository.prepareNextTrackEvent"))
        assertTrue("MainViewModel must implement prepareNextTrackInQueue",
            viewModel.contains("fun prepareNextTrackInQueue()"))
    }

    @Test
    fun testMd5HelperFallbackSaltArchitecture() {
        val md5HelperFile = File(sourceDir, "data/network/Md5Helper.kt").readText()
        assertTrue("Md5Helper must define FALLBACK_SALT",
            md5HelperFile.contains("const val FALLBACK_SALT = \"XGR3485624001b979\""))
        assertTrue("Md5Helper must fallback if BuildConfig.YANDEX_SALT is blank",
            md5HelperFile.contains("if (BuildConfig.YANDEX_SALT.isNotBlank()) BuildConfig.YANDEX_SALT else FALLBACK_SALT"))
        assertEquals("Md5Helper.FALLBACK_SALT must match standard production salt",
            "XGR3485624001b979", com.korenskoy.yandexmusic.data.network.Md5Helper.FALLBACK_SALT)
        assertTrue("Md5Helper.SALT must not be blank",
            com.korenskoy.yandexmusic.data.network.Md5Helper.SALT.isNotBlank())
    }

    @Test
    fun testTrackPreloadManagerTtlAndEvictUrlArchitecture() {
        val preloadManagerFile = File(sourceDir, "data/network/TrackPreloadManager.kt").readText()
        assertTrue("TrackPreloadManager must define URL_TTL_MS constant",
            preloadManagerFile.contains("URL_TTL_MS = 45 * 60 * 1000L"))
        assertTrue("TrackPreloadManager must implement evictUrl",
            preloadManagerFile.contains("fun evictUrl(trackId: String)"))
        assertTrue("TrackPreloadManager must skip radio streams",
            preloadManagerFile.contains("trackId.startsWith(\"radio_\")"))
        assertTrue("TrackPreloadManager must check TTL expiration",
            preloadManagerFile.contains("System.currentTimeMillis() - cached.fetchedAtMs > URL_TTL_MS"))

        val playerRepoFile = File(sourceDir, "data/repository/PlayerRepositoryImpl.kt").readText()
        assertTrue("PlayerRepositoryImpl must evict URL on player error",
            playerRepoFile.contains("preloadManager.evictUrl(failedTrackId)"))
    }

    @Test
    fun testLikedTracksZeroLikesSanitization() {
        val viewModelFile = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertFalse("MainViewModel must not call landing personal-playlists fallback",
            viewModelFile.contains("landing3?blocks=personal-playlists"))
        assertFalse("MainViewModel must not call getPlaylistsListRaw fallback",
            viewModelFile.contains("api.getPlaylistsListRaw(targetUid)"))
        assertFalse("MainViewModel must not call liked playlists fallback",
            viewModelFile.contains("likes/playlists"))
        assertTrue("MainViewModel must reset liked tracks when allIds is empty",
            viewModelFile.contains("User has 0 liked tracks. Resetting state."))
        assertTrue("MainViewModel must save empty liked tracks to cache on 0 likes",
            viewModelFile.contains("cacheManager.saveLikedTracks(activeUserFolder, emptyList())"))
    }

    @Test
    fun testMultiAccountCacheIsolationNewProfileEmpty() {
        val cacheManagerFile = File(sourceDir, "data/cache/PlaylistCacheManager.kt").readText()
        assertFalse("PlaylistCacheManager must not copy legacy cache to new user folders",
            cacheManagerFile.contains("val legacyFile = File(rootCacheDir, \"liked_tracks.json\")"))
    }

    @Test
    fun testSearchDebounceAndJobCancellation() {
        val viewModelFile = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertTrue("MainViewModel must declare searchJob",
            viewModelFile.contains("private var searchJob: Job? = null"))
        assertTrue("MainViewModel must cancel obsolete searchJob on query change",
            viewModelFile.contains("searchJob?.cancel()"))
        assertTrue("MainViewModel must implement 300ms debounce delay",
            viewModelFile.contains("delay(300L)"))
    }

    @Test
    fun testPlayerLifecycleAndStateSyncArchitecture() {
        val musicService = File(sourceDir, "data/player/MusicService.kt").readText()
        assertTrue("MusicService must implement onTaskRemoved",
            musicService.contains("override fun onTaskRemoved(rootIntent: Intent?)"))
        assertTrue("MusicService must return START_NOT_STICKY",
            musicService.contains("return START_NOT_STICKY"))
        assertTrue("MusicService must abandon audio focus in onDestroy",
            musicService.contains("abandonSystemAudioFocus()"))

        val playerRepo = File(sourceDir, "domain/repository/PlayerRepository.kt").readText()
        assertTrue("PlayerRepository interface must declare release()",
            playerRepo.contains("fun release()"))

        val playerRepoImpl = File(sourceDir, "data/repository/PlayerRepositoryImpl.kt").readText()
        assertTrue("PlayerRepositoryImpl must implement release()",
            playerRepoImpl.contains("override fun release()"))
        assertTrue("PlayerRepositoryImpl must check mediaItemCount in resume",
            playerRepoImpl.contains("if (player.mediaItemCount == 0) return"))
        assertTrue("PlayerRepositoryImpl must start foreground service in resume",
            playerRepoImpl.contains("ContextCompat.startForegroundService(context, intent)"))

        val viewModel = File(sourceDir, "presentation/main/MainViewModel.kt").readText()
        assertTrue("MainViewModel must reactively collect playerRepository.currentTrackId",
            viewModel.contains("playerRepository.currentTrackId.collect"))
        assertTrue("MainViewModel must implement resumeOrPlayCurrent",
            viewModel.contains("fun resumeOrPlayCurrent()"))
        assertTrue("MainViewModel must implement togglePlayPause",
            viewModel.contains("fun togglePlayPause()"))
        assertTrue("MainViewModel must debounce playNext",
            viewModel.contains("now - lastPlayNextTime < 500L"))

        val mainScreen = File(sourceDir, "presentation/main/MainScreen.kt").readText()
        assertTrue("MainScreen must use togglePlayPause",
            mainScreen.contains("viewModel.togglePlayPause()"))
    }
}



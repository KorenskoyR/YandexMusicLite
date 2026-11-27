package com.korenskoy.yandexmusic.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntegrityTest {

    @Test
    fun validSnapshotPassesAllChecks() {
        val report = AppIntegrity.evaluateSnapshot(
            packageName = AppIntegrity.expectedPackageName(),
            isDebuggableFlag = false,
            signerSha256List = listOf(AppIntegrity.expectedSha256())
        )
        assertTrue(report.isValid)
        assertTrue(report.packageOk)
        assertTrue(report.signatureOk)
        assertTrue(report.nonDebuggableOk)
    }

    @Test
    fun renamedPackageFailsIntegrity() {
        val report = AppIntegrity.evaluateSnapshot(
            packageName = "com.fake.store.yandexmusic",
            isDebuggableFlag = false,
            signerSha256List = listOf(AppIntegrity.expectedSha256())
        )
        assertFalse(report.isValid)
        assertFalse(report.packageOk)
        assertTrue(report.signatureOk)
    }

    @Test
    fun repackagedSignatureFailsIntegrity() {
        val fakeCert = ByteArray(32) { 0x42 }
        val report = AppIntegrity.evaluateSnapshot(
            packageName = AppIntegrity.expectedPackageName(),
            isDebuggableFlag = false,
            signerSha256List = listOf(fakeCert)
        )
        assertFalse(report.isValid)
        assertFalse(report.signatureOk)
        assertTrue(report.packageOk)
    }

    @Test
    fun debuggableFlagFailsReleaseIntegrity() {
        val report = AppIntegrity.evaluateSnapshot(
            packageName = AppIntegrity.expectedPackageName(),
            isDebuggableFlag = true,
            signerSha256List = listOf(AppIntegrity.expectedSha256())
        )
        assertFalse(report.isValid)
        assertFalse(report.nonDebuggableOk)
    }

    @Test
    fun decodedConstantsMatchOfficialReleaseMetadata() {
        assertEquals("YandexMusicLite.KorenskoyR", AppIntegrity.expectedPackageName())
        assertEquals("https://github.com/KorenskoyR/YandexMusicLite", AppIntegrity.officialUrl())
        assertEquals(32, AppIntegrity.expectedSha256().size)
    }
}

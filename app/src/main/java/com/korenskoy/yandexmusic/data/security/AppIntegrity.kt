package com.korenskoy.yandexmusic.data.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.korenskoy.yandexmusic.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Multi-point release integrity check against unauthorized APK repackaging and store rebranding.
 * Disabled in debug builds so development and Robolectric/Compose tests never trip falsely.
 */
object AppIntegrity {

    private val _tampered = MutableStateFlow(false)
    val tampered: StateFlow<Boolean> = _tampered.asStateFlow()

    internal data class IntegrityReport(
        val isValid: Boolean,
        val packageOk: Boolean,
        val signatureOk: Boolean,
        val nonDebuggableOk: Boolean
    )

    private val shaXor = intArrayOf(
        5, 225, 186, 146, 228, 227, 184, 147,
        208, 33, 90, 30, 58, 6, 144, 12,
        239, 225, 242, 126, 224, 22, 246, 104,
        10, 132, 111, 238, 166, 245, 239, 221
    )

    private val pkgXor = intArrayOf(
        100, 7, 225, 220, 132, 114, 126, 41,
        246, 199, 180, 76, 64, 38, 30, 138,
        134, 153, 109, 45, 31, 233, 168, 131,
        108, 108
    )

    private val urlXor = intArrayOf(
        3, 210, 149, 108, 36, 168, 226, 39,
        36, 23, 205, 156, 90, 8, 139, 131,
        116, 59, 190, 135, 104, 48, 24, 214,
        128, 69, 6, 221, 141, 53, 12, 241,
        165, 98, 36, 4, 250, 135, 94, 1,
        192, 146, 112, 32, 234
    )

    internal fun expectedSha256(): ByteArray = ByteArray(shaXor.size) { index ->
        (shaXor[index] xor ((index * 73 + 0xA7) and 0xFF)).toByte()
    }

    internal fun expectedPackageName(): String = String(
        ByteArray(pkgXor.size) { index ->
            (pkgXor[index] xor ((index * 41 + 0x3D) and 0xFF)).toByte()
        },
        Charsets.UTF_8
    )

    fun officialUrl(): String = String(
        ByteArray(urlXor.size) { index ->
            (urlXor[index] xor ((index * 59 + 0x6B) and 0xFF)).toByte()
        },
        Charsets.UTF_8
    )

    internal fun evaluateSnapshot(
        packageName: String,
        isDebuggableFlag: Boolean,
        signerSha256List: List<ByteArray>
    ): IntegrityReport {
        val packageOk = packageName == expectedPackageName()
        val nonDebuggableOk = !isDebuggableFlag
        val expected = expectedSha256()
        val signatureOk = signerSha256List.any { MessageDigest.isEqual(it, expected) }
        return IntegrityReport(
            isValid = packageOk && nonDebuggableOk && signatureOk,
            packageOk = packageOk,
            signatureOk = signatureOk,
            nonDebuggableOk = nonDebuggableOk
        )
    }

    fun probeBoot(context: Context) {
        if (BuildConfig.DEBUG) return
        val report = runCatching { inspectContext(context) }.getOrNull()
        if (report == null || !report.isValid) _tampered.value = true
    }

    fun probePlayback(context: Context) {
        if (BuildConfig.DEBUG) return
        val expected = expectedSha256()
        val signers = runCatching { readSignerSha256(context) }.getOrNull().orEmpty()
        val pkgOk = context.packageName == expectedPackageName()
        if (!pkgOk || signers.none { MessageDigest.isEqual(it, expected) }) {
            _tampered.value = true
        }
    }

    fun probeUi(context: Context) {
        if (BuildConfig.DEBUG) return
        val report = runCatching { inspectContext(context) }.getOrNull()
        if (report == null || !report.isValid) {
            _tampered.value = true
        }
    }

    private fun inspectContext(context: Context): IntegrityReport {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val signers = readSignerSha256(context)
        return evaluateSnapshot(
            packageName = context.packageName,
            isDebuggableFlag = debuggable,
            signerSha256List = signers
        )
    }

    @Suppress("DEPRECATION")
    private fun readSignerSha256(context: Context): List<ByteArray> {
        val pm = context.packageManager
        val pkg = context.packageName
        val md = MessageDigest.getInstance("SHA-256")
        val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val certs = signingInfo?.apkContentsSigners ?: signingInfo?.signingCertificateHistory
            certs?.toList().orEmpty()
        } else {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            info.signatures?.toList().orEmpty()
        }
        return rawSignatures.map { sig -> md.digest(sig.toByteArray()) }
    }
}

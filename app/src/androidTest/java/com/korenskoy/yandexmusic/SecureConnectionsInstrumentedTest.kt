package com.korenskoy.yandexmusic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.korenskoy.yandexmusic.data.repository.RadioRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Every place the app reaches over HTTPS is trusted on this Android, the oldest supported (7.1) included:
 * radio streams and station logos, Yandex, GitHub's updates. Only a certificate refusal fails the test; a station
 * that is down or slow does not. And Let's Encrypt's roots bundled in res/raw verify Let's Encrypt sites on
 * their own: Android 7.1.0 lacks ISRG Root X1, and it is what saves radio there. A server whose own certificate has
 * run out is its owner's lapse, not a root missing here: it is logged, not failed.
 */
@RunWith(AndroidJUnit4::class)
class SecureConnectionsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isTrustFailure(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is SSLHandshakeException || cause is SSLPeerUnverifiedException || cause is CertificateException) return true
            if (cause.javaClass.name.contains("CertPathValidator")) return true
            cause = cause.cause
        }
        return false
    }

    /** Only reads what [url]'s server presents (nothing is sent): is its own certificate out of date? */
    private fun expiredOnServer(url: String): Boolean {
        val look = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val factory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(look), null) }.socketFactory
        val host = URL(url).host
        return try {
            (factory.createSocket(host, 443) as SSLSocket).use { socket ->
                socket.soTimeout = 8_000
                socket.startHandshake()
                val leaf = socket.session.peerCertificates.first() as X509Certificate
                runCatching { leaf.checkValidity() }.isFailure
            }
        } catch (e: IOException) {
            false
        }
    }

    /** Opens [url] and reads the answer's status: null when trusted (or merely unreachable), the error when refused. */
    private fun refusal(url: String, factory: SSLSocketFactory? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (factory != null) (connection as HttpsURLConnection).sslSocketFactory = factory
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Range", "bytes=0-0")
        return try {
            connection.responseCode
            null
        } catch (e: IOException) {
            when {
                !isTrustFailure(e) -> null
                factory == null && expiredOnServer(url) -> {
                    Log.w("SecureConnections", "$url: the server's own certificate has expired")
                    null
                }
                else -> "${e.javaClass.simpleName}: ${e.message}"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun checkAll(urls: List<String>, factory: SSLSocketFactory? = null): List<String> {
        val pool = Executors.newFixedThreadPool(8)
        val results = urls.distinct().map { url -> pool.submit<String?> { refusal(url, factory)?.let { "$url — $it" } } }
        pool.shutdown()
        pool.awaitTermination(3, TimeUnit.MINUTES)
        return results.mapNotNull { it.get() }
    }

    @Test
    fun everyHttpsPlaceOfTheAppIsTrusted() {
        val stations = RadioRepository(context).getBuiltinStations()
        val urls = (stations.map { it.streamUrl } + stations.map { it.logoUrl })
            .filter { it.startsWith("https://") } +
            listOf(
                "https://api.music.yandex.net/",
                "https://oauth.yandex.ru/",
                "https://avatars.yandex.net/",
                "https://api.github.com/repos/KorenskoyR/YandexMusicLite/releases/latest",
                "https://github.com/KorenskoyR/YandexMusicLite/releases",
                "https://release-assets.githubusercontent.com/",
                "https://objects.githubusercontent.com/"
            )
        val refused = checkAll(urls)
        assertTrue("refused on Android ${android.os.Build.VERSION.RELEASE}:\n${refused.joinToString("\n")}", refused.isEmpty())
        // Not passed for want of a network: Yandex and GitHub really answered over a trusted connection
        for (must in listOf("https://api.music.yandex.net/", "https://api.github.com/")) {
            val connection = URL(must).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val status = try { connection.responseCode } finally { connection.disconnect() }
            assertTrue("$must answered $status", status in 200..499)
        }
    }

    @Test
    fun theBundledLetsEncryptRootsAloneVerifyLetsEncryptSites() {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val certificates = CertificateFactory.getInstance("X.509")
        for ((alias, id) in listOf("x1" to R.raw.isrg_root_x1, "x2" to R.raw.isrg_root_x2)) {
            context.resources.openRawResource(id).use { keyStore.setCertificateEntry(alias, certificates.generateCertificate(it)) }
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        val factory = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
        // Sites on Let's Encrypt (its own pages and its test site with a valid certificate)
        val refused = checkAll(listOf("https://letsencrypt.org/", "https://valid-isrgrootx1.letsencrypt.org/"), factory)
        assertTrue("refused with only the bundled roots:\n${refused.joinToString("\n")}", refused.isEmpty())
        // And a site on another authority is NOT trusted by them alone: the check really narrows the trust
        assertTrue("a non-Let's Encrypt site must be refused by these roots alone", checkAll(listOf("https://api.github.com/"), factory).isNotEmpty())
    }
}

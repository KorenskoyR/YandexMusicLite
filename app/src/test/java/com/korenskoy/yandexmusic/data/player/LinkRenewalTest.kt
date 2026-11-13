package com.korenskoy.yandexmusic.data.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** 1.14.7: a link that ran out mid-track is renewed and the download goes on from the same byte, unheard. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LinkRenewalTest {

    private val old = Uri.parse("https://s3.example/old.mp3")
    private val fresh = Uri.parse("https://s3.example/fresh.mp3")

    /** A server: [sizes] of the file behind each link; a link in [expired] answers [code]. */
    private class Server(val sizes: Map<Uri, Long>, val expired: MutableSet<Uri> = mutableSetOf(), val code: Int = 403) : DataSource.Factory {
        val opened = mutableListOf<DataSpec>()
        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                if (dataSpec.uri in expired) {
                    throw HttpDataSource.InvalidResponseCodeException(code, "no", null, emptyMap(), dataSpec, ByteArray(0))
                }
                opened += dataSpec
                return sizes.getValue(dataSpec.uri) - dataSpec.position
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = opened.lastOrNull()?.uri
            override fun close() = Unit
        }
    }

    private fun spec(uri: Uri, position: Long = 0L, key: String? = "42") = DataSpec.Builder().setUri(uri).setPosition(position).setKey(key).build()

    @Test
    fun theDownloadGoesOnFromTheSameByteWithAFreshLinkOfTheSameFile() {
        val server = Server(mapOf(old to 1_000L, fresh to 1_000L))
        var renewed = 0
        var asked = 0
        val renewal = LinkRenewal(server, renew = { asked++; fresh }, onRenewed = { renewed++ })
        assertEquals(1_000L, renewal.createDataSource().open(spec(old)))
        // The connection broke at byte 600; by the time it is picked up again, the link has run out
        server.expired += old
        val left = renewal.createDataSource().open(spec(old, 600))
        assertEquals(400L, left)
        assertEquals(fresh, server.opened.last().uri)
        assertEquals(600L, server.opened.last().position)
        assertEquals(1, asked)
        assertEquals(1, renewed)
    }

    @Test
    fun aFreshLinkToAnotherFileIsNotTakenTheUsualRecoveryFollows() {
        val server = Server(mapOf(old to 1_000L, fresh to 1_400L))
        val renewal = LinkRenewal(server, renew = { fresh })
        renewal.createDataSource().open(spec(old))
        server.expired += old
        val error = refused { renewal.createDataSource().open(spec(old, 600)) }
        assertEquals(403, error.responseCode)
    }

    @Test
    fun aRunOutLinkAtTheVeryStartIsRenewedToo() {
        val server = Server(mapOf(fresh to 900L), expired = mutableSetOf(old))
        val renewal = LinkRenewal(server, renew = { fresh })
        assertEquals(900L, renewal.createDataSource().open(spec(old)))
    }

    @Test
    fun whatIsNotARunOutLinkIsLeftToTheUsualRecovery() {
        var asked = 0
        // A server error is retried by the player as before, not renewed
        val failing = Server(mapOf(old to 1_000L), expired = mutableSetOf(old), code = 503)
        refused { LinkRenewal(failing, renew = { asked++; fresh }).createDataSource().open(spec(old)) }
        // No track to renew for
        val expired = Server(mapOf(fresh to 1_000L), expired = mutableSetOf(old))
        refused { LinkRenewal(expired, renew = { asked++; fresh }).createDataSource().open(spec(old, key = null)) }
        assertEquals(0, asked)
        // Nothing fresh to be had (a station, a FLAC stream, no network)
        refused { LinkRenewal(expired, renew = { null }).createDataSource().open(spec(old)) }
    }

    @Test
    fun onlyAPlainLinkRefusedAsNoLongerValidIsRenewable() {
        fun refusal(code: Int) = HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), spec(old), ByteArray(0))
        assertTrue(LinkRenewal.renewable(old, refusal(403)))
        assertTrue(LinkRenewal.renewable(old, IOException(refusal(410))))
        assertFalse(LinkRenewal.renewable(old, refusal(500)))
        assertFalse(LinkRenewal.renewable(old, IOException("reset")))
        assertFalse("an encrypted stream keeps its own way", LinkRenewal.renewable(Uri.parse("https://s3.example/a#encraw_key=00"), refusal(403)))
        assertFalse(LinkRenewal.renewable(Uri.parse("file:///sdcard/a.mp3"), refusal(403)))
    }

    @Test
    fun aBusyServerIsTriedAgainNotTakenForANo() {
        fun answer(code: Int) = HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), spec(old), ByteArray(0))
        assertTrue(PlaybackBuffering.refusedByServer(answer(403)))
        assertTrue(PlaybackBuffering.refusedByServer(answer(404)))
        assertFalse("busy, later: a station's relay hands over to another", PlaybackBuffering.refusedByServer(answer(429)))
        assertFalse(PlaybackBuffering.refusedByServer(answer(408)))
        assertFalse(PlaybackBuffering.refusedByServer(answer(503)))
    }

    private fun refused(block: () -> Unit): HttpDataSource.InvalidResponseCodeException {
        try {
            block()
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            return e
        }
        fail("the refusal should stay")
        throw AssertionError()
    }

    @Test
    fun theSameRefusalIsGivenBackSoThePlayerTreatsItAsBefore() {
        val server = Server(mapOf(old to 1_000L), expired = mutableSetOf(old))
        var thrown: Throwable? = null
        try {
            LinkRenewal(server, renew = { null }).createDataSource().open(spec(old))
        } catch (e: IOException) {
            thrown = e
        }
        assertTrue(thrown is HttpDataSource.InvalidResponseCodeException)
        assertSame(old, (thrown as HttpDataSource.InvalidResponseCodeException).dataSpec.uri)
    }
}

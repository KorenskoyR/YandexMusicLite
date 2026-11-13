package com.korenskoy.yandexmusic.data.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * A track's link to its stream lives for a while. When the connection breaks mid-track, the player picks the
 * download up again from where it stopped, with the same link; if by then the link has run out, the server says
 * no and the music would stop. Here the stop does not happen: a fresh link is asked for, and the download goes on
 * from the same byte while the music plays from what is ahead.
 *
 * Only the very same file is accepted: its size must be what the old link served, otherwise the error stays and
 * the usual recovery (a new start from the same second) takes over. A track is known by the media item's key,
 * which is its id; [renew] says null for what is not to be renewed here.
 */
@OptIn(UnstableApi::class)
class LinkRenewal(
    private val upstream: DataSource.Factory,
    /** A fresh link for the track, or null. Called on the loading thread: it may wait for the network. */
    private val renew: (trackId: String) -> Uri?,
    private val onRenewed: (trackId: String) -> Unit = {}
) : DataSource.Factory {

    /** The size of each track's file as served, by track id: a fresh link must serve the same. */
    private val sizes = ConcurrentHashMap<String, Long>()

    override fun createDataSource(): DataSource = Renewing(upstream.createDataSource())

    private inner class Renewing(private val source: DataSource) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = source.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            val trackId = dataSpec.key
            val opened = try {
                source.open(dataSpec)
            } catch (e: IOException) {
                if (trackId == null || !renewable(dataSpec.uri, e)) throw e
                return reopen(dataSpec, trackId, e)
            }
            if (trackId != null) remember(trackId, dataSpec, opened)
            return opened
        }

        /** The same bytes from a fresh link; otherwise [refused] stays, for the usual recovery. */
        private fun reopen(dataSpec: DataSpec, trackId: String, refused: IOException): Long =
            openFresh(dataSpec, trackId, refused) ?: throw refused

        private fun openFresh(dataSpec: DataSpec, trackId: String, refused: IOException): Long? {
            val fresh = runCatching { renew(trackId) }.getOrNull() ?: return null
            runCatching { source.close() }
            val opened = try {
                source.open(dataSpec.buildUpon().setUri(fresh).build())
            } catch (e: IOException) {
                refused.addSuppressed(e)
                return null
            }
            val known = sizes[trackId]
            val size = sizeOf(dataSpec, opened)
            // A different file (another quality): its bytes do not go on from these
            if (dataSpec.position > 0 && (known == null || size != known)) {
                runCatching { source.close() }
                return null
            }
            if (size != null) sizes[trackId] = size
            onRenewed(trackId)
            return opened
        }

        private fun remember(trackId: String, dataSpec: DataSpec, opened: Long) {
            sizeOf(dataSpec, opened)?.let { sizes[trackId] = it }
            if (sizes.size > MAX_REMEMBERED) sizes.keys.firstOrNull()?.let(sizes::remove)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length)
        override fun getUri(): Uri? = source.uri
        override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders
        override fun close() = source.close()
    }

    companion object {
        private const val MAX_REMEMBERED = 64

        /** The whole file's size, when this answer tells it (an open to the end). */
        internal fun sizeOf(dataSpec: DataSpec, opened: Long): Long? =
            if (opened == C.LENGTH_UNSET.toLong() || dataSpec.length != C.LENGTH_UNSET.toLong()) null else dataSpec.position + opened

        /**
         * A plain link of the network the server refused as no longer valid. A link with a key in its fragment
         * (an encrypted stream) is left as it is.
         */
        internal fun renewable(uri: Uri, error: Throwable): Boolean {
            if (uri.scheme != "http" && uri.scheme != "https") return false
            if (uri.fragment != null) return false
            val response = generateSequence(error) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            return response != null && response.responseCode in REFUSED
        }

        /** What a server answers for a link that has run out. */
        private val REFUSED = setOf(401, 403, 404, 410)
    }
}

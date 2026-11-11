package com.korenskoy.yandexmusic.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.korenskoy.yandexmusic.BuildConfig
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * The covers of the user's own music: the picture inside the file, or the folder's "cover.jpg". A file's cover
 * is drawn out once and kept, small, in the cache; a file without one is remembered as such, so a long list does
 * not open every file again. Covers are read in this app only (the list, the player, the notification): the
 * provider is not exported.
 */
class LocalArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("No context")
        val key = uri.lastPathSegment?.takeIf { it.matches(KEY) } ?: throw FileNotFoundException("Bad key")
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val kept = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        val alt = uri.getQueryParameter(ALT)?.let(Uri::parse)
        if (kept.length() > 0) return ParcelFileDescriptor.open(kept, ParcelFileDescriptor.MODE_READ_ONLY)
        if (!none.exists()) {
            val source = uri.getQueryParameter(SRC)?.let(Uri::parse) ?: throw FileNotFoundException("No source")
            // A file Android has just listed may refuse to open for a moment: once more before giving up
            val read = embeddedPicture(context, source) ?: run {
                Thread.sleep(RETRY_MS)
                embeddedPicture(context, source)
            }
            val picture = read?.getOrNull()
            if (picture != null && keep(picture, kept)) return ParcelFileDescriptor.open(kept, ParcelFileDescriptor.MODE_READ_ONLY)
            // Only a file that opened and has no picture is remembered as such; one that did not open is asked again
            if (read != null) runCatching { none.createNewFile() }
        }
        if (alt != null) {
            return context.contentResolver.openFileDescriptor(alt, "r") ?: throw FileNotFoundException("No folder picture")
        }
        throw FileNotFoundException("No cover")
    }

    /**
     * The picture inside the file: a result with the picture, or with null when the file has none; null itself when
     * the file would not open (gone with its drive, or not ready yet).
     */
    private fun embeddedPicture(context: Context, source: Uri): Result<ByteArray?>? {
        val retriever = MediaMetadataRetriever()
        return try {
            val opened = runCatching { retriever.setDataSource(context, source) }
                .onFailure { Log.d(TAG, "Cannot open $source: ${it.message}") }
            if (opened.isFailure) null else Result.success(runCatching { retriever.embeddedPicture }.getOrNull())
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Scaled to at most [MAX_SIDE] and kept as JPEG; a picture that does not decode is no cover. */
    private fun keep(picture: ByteArray, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val part = File(target.parentFile, target.name + ".part")
        return try {
            part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            part.renameTo(target)
        } catch (e: IOException) {
            Log.w(TAG, "Cannot keep a cover", e)
            part.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "LocalArtProvider"
        private const val DIR = "local_art"
        private const val SRC = "src"
        private const val ALT = "alt"
        private const val MAX_SIDE = 600
        private const val JPEG_QUALITY = 88
        private const val RETRY_MS = 300L

        /** A cover of the user's own music: this app's only, never to be handed to other apps by its address. */
        fun isOurs(uri: String?): Boolean = uri != null && uri.startsWith("content://$AUTHORITY/")
        private val KEY = Regex("[0-9a-f]{16,40}")

        val AUTHORITY: String get() = "${BuildConfig.APPLICATION_ID}.localart"

        /**
         * The cover of [audio] as a content:// address. [version] (the file's change time) is part of the key, so
         * a file whose tags were changed gets its new cover; [folderPicture] shows when the file has none.
         */
        fun uriFor(audio: String, version: Long, folderPicture: String? = null): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$audio|$version".toByteArray())
            val key = digest.joinToString("") { "%02x".format(it) }.take(24)
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("art").appendPath(key)
                .appendQueryParameter(SRC, audio)
                .apply { if (folderPicture != null) appendQueryParameter(ALT, folderPicture) }
                .build().toString()
        }

        /** Covers kept longer than needed: dropped when the cache is cleared with the rest. */
        fun clear(context: Context) {
            File(context.cacheDir, DIR).deleteRecursively()
        }
    }
}

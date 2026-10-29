package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.dto.DownloadInfoResponseDto
import com.korenskoy.yandexmusic.data.dto.ResponseWrapper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choosing the stream to play from what `tracks/{id}/download-info` offers. */
class StreamVariantsTest {

    private fun v(codec: String, kbps: Int, preview: Boolean = false) =
        DownloadInfoResponseDto(codec = codec, bitrateInKbps = kbps, preview = preview, downloadInfoUrl = "u/$codec/$kbps")

    private fun ranked(vararg offered: DownloadInfoResponseDto) =
        StreamVariants.ranked(offered.toList()).map { "${it.codec}${it.bitrateInKbps}" }

    @Test
    fun highestBitrateWinsWhateverTheCodec() {
        // The old code took the best MP3 only, so an AAC 256 offer played as MP3 192
        assertEquals(listOf("aac256", "mp3192", "mp3128"), ranked(v("mp3", 192), v("aac", 256), v("mp3", 128)))
    }

    @Test
    fun aacGoesFirstAtAnEqualBitrate() {
        assertEquals(listOf("aac192", "mp3192"), ranked(v("mp3", 192), v("aac", 192)))
    }

    @Test
    fun mp3_320IsChosenWhenOffered() {
        assertEquals("mp3320", ranked(v("mp3", 320), v("aac", 192), v("mp3", 192)).first())
    }

    @Test
    fun previewsAndUnknownCodecsAreSkipped() {
        assertEquals(listOf("mp3192"), ranked(v("mp3", 320, preview = true), v("flac", 999), v("mp3", 192)))
    }

    @Test
    fun onlyAPreviewIsStillPlayable() {
        assertEquals(listOf("mp3128"), ranked(v("mp3", 128, preview = true)))
    }

    @Test
    fun betterDecidesWhetherADownloadIsUpgraded() {
        val q = TrackPreloadManager::StreamQuality
        // Lossless beats any lossy stream
        assertTrue(StreamVariants.isBetter(q("flac", 1411), q("mp3", 320)))
        assertTrue(StreamVariants.isBetter(q("flac-mp4", 0), q("mp3", 320)))
        assertTrue(StreamVariants.isBetter(q("flac", 1411), q("aac", 256)))
        assertFalse(StreamVariants.isBetter(q("mp3", 320), q("flac", 1411)))
        assertFalse(StreamVariants.isBetter(q("mp3", 320), q("flac-mp4", 0)))

        // HQ 320 beats 192
        assertTrue(StreamVariants.isBetter(q("mp3", 320), q("mp3", 192)))
        assertTrue(StreamVariants.isBetter(q("mp3", 320), q("aac", 192)))

        // AAC vs MP3
        assertTrue(StreamVariants.isBetter(q("aac", 256), q("mp3", 192)))
        assertTrue(StreamVariants.isBetter(q("aac", 192), q("mp3", 192)))
        assertFalse(StreamVariants.isBetter(q("mp3", 192), q("mp3", 192)))
        assertFalse(StreamVariants.isBetter(q("mp3", 128), q("mp3", 192)))
    }

    @Test
    fun describeListsTheOfferBestFirst() {
        assertEquals("AAC 192 · MP3 192 · MP3 128 (фрагмент)", StreamVariants.describe(listOf(v("mp3", 128, true), v("mp3", 192), v("aac", 192))))
    }

    @Test
    fun parsesTheServerResponse() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val body = """{"result":[
            {"codec":"mp3","gain":false,"preview":false,"downloadInfoUrl":"https://x/1","direct":false,"bitrateInKbps":192},
            {"codec":"aac","preview":false,"downloadInfoUrl":"https://x/2","bitrateInKbps":64}]}"""
        val offered = json.decodeFromString<ResponseWrapper<List<DownloadInfoResponseDto>>>(body).result!!
        assertEquals(listOf("mp3192", "aac64"), StreamVariants.ranked(offered).map { "${it.codec}${it.bitrateInKbps}" })
    }

    @Test
    fun linkPathNamesTheCodec() {
        assertTrue(Md5Helper.generateStreamUrl("h.example", "/p/1", "ts", "s", "aac").startsWith("https://h.example/get-aac/"))
        assertTrue(Md5Helper.generateMp3Url("h.example", "/p/1", "ts", "s").startsWith("https://h.example/get-mp3/"))
    }
}

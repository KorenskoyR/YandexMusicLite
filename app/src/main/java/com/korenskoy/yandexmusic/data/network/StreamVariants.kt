package com.korenskoy.yandexmusic.data.network

import com.korenskoy.yandexmusic.data.dto.DownloadInfoResponseDto

/**
 * Which of the streams `download-info` offers to play: the best first. A higher bitrate wins; at the
 * same bitrate AAC goes first (it sounds better than MP3 at an equal rate). Previews (30-second
 * fragments) are never chosen while a full track is offered.
 */
object StreamVariants {

    /** Codecs ExoPlayer plays from these links, by preference at an equal bitrate. */
    private val SUPPORTED = listOf("aac", "mp3")

    fun ranked(offered: List<DownloadInfoResponseDto>, maxBitrateKbps: Int? = null): List<DownloadInfoResponseDto> {
        val full = offered.filter { !it.preview && it.codec.lowercase() in SUPPORTED }
        // A cap (economy on mobile data) never leaves the user without sound
        val allowed = maxBitrateKbps?.let { cap -> full.filter { it.bitrateInKbps <= cap }.ifEmpty { listOfNotNull(full.minByOrNull { it.bitrateInKbps }) } } ?: full
        val ranked = allowed.sortedWith(
            compareByDescending<DownloadInfoResponseDto> { it.bitrateInKbps }.thenBy { SUPPORTED.indexOf(it.codec.lowercase()) }
        )
        return ranked.ifEmpty { offered.filter { !it.preview }.ifEmpty { offered }.take(1) }
    }

    /** True when [candidate] is a better stream than [current] (what a downloaded file has). */
    fun isBetter(candidate: TrackPreloadManager.StreamQuality, current: TrackPreloadManager.StreamQuality): Boolean =
        candidate.bitrateKbps > current.bitrateKbps ||
            (candidate.bitrateKbps == current.bitrateKbps && rank(candidate.codec) < rank(current.codec))

    /** "AAC 192 · MP3 192 · MP3 128" — what the server offered, best first. */
    fun describe(offered: List<DownloadInfoResponseDto>): String =
        offered.sortedWith(compareByDescending<DownloadInfoResponseDto> { it.bitrateInKbps }.thenBy { rank(it.codec) })
            .joinToString(" · ") { "${it.codec.uppercase()} ${it.bitrateInKbps}" + if (it.preview) " (фрагмент)" else "" }

    private fun rank(codec: String) = SUPPORTED.indexOf(codec.lowercase()).let { if (it < 0) SUPPORTED.size else it }
}

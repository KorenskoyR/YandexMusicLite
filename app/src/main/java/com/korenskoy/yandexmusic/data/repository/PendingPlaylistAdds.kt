package com.korenskoy.yandexmusic.data.repository

import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.serialization.json.Json

/** A track for a playlist, added without a connection: it goes to the server once the network is back. */
data class PendingPlaylistAdd(val kind: String, val track: TrackDetailsDto) {
    val trackId: String get() = track.idString
}

/**
 * The queue of tracks added to playlists without a connection, kept with the track itself (title, album) so it
 * goes out without asking the server about it first. Pure; unit-tested.
 */
object PendingPlaylistAdds {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** The same track for the same playlist is queued once. */
    fun add(queue: List<PendingPlaylistAdd>, new: PendingPlaylistAdd): List<PendingPlaylistAdd> =
        if (queue.any { it.kind == new.kind && it.trackId == new.trackId }) queue else queue + new

    fun save(add: PendingPlaylistAdd): String = add.kind + SEPARATOR + json.encodeToString(TrackDetailsDto.serializer(), add.track)

    fun parse(saved: String): PendingPlaylistAdd? {
        val kind = saved.substringBefore(SEPARATOR, "").takeIf { it.isNotEmpty() } ?: return null
        // A broken entry (an older form, a cut write) is dropped, not sent
        val track = runCatching { json.decodeFromString(TrackDetailsDto.serializer(), saved.substringAfter(SEPARATOR)) }.getOrNull() ?: return null
        return PendingPlaylistAdd(kind, track).takeIf { it.trackId.isNotEmpty() }
    }

    private const val SEPARATOR = "\n"
}

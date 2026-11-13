package com.korenskoy.yandexmusic.data.player

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What the wave hears about its tracks: the station started, a track started, was heard to the end or skipped.
 * The rotor's feedback method reads a JSON body only: a form-encoded one (what the app sent up to 1.14.5) is answered
 * "condition is not met", exactly like an empty body, so the wave never learned what was heard and offered the same
 * tracks again. Pure; unit-tested.
 */
internal data class RotorFeedback(
    val station: String,
    val type: String,
    val atMs: Long,
    val trackId: String? = null,
    val albumId: String? = null,
    /** The answer of the station the track came in; it goes with the request, not in the body. */
    val batchId: String? = null,
    val playedSeconds: Double? = null
) {
    fun body(): JsonObject = buildJsonObject {
        put("type", type)
        put("timestamp", timestamp(atMs))
        put("from", WaveQueue.feedbackFrom(station))
        // A track is named with its album, as the service names it in its own lists
        trackId?.let { put("trackId", if (albumId.isNullOrEmpty()) it else "$it:$albumId") }
        playedSeconds?.let { put("totalPlayedSeconds", it) }
    }

    companion object {
        const val RADIO_STARTED = "radioStarted"
        const val TRACK_STARTED = "trackStarted"
        const val TRACK_FINISHED = "trackFinished"
        const val SKIP = "skip"

        /** "2026-11-13T09:43:15.000Z": the form the service asks for in its error answers. */
        fun timestamp(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
    }
}

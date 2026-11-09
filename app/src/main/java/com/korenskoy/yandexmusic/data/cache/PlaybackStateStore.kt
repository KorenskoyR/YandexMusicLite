package com.korenskoy.yandexmusic.data.cache

import android.content.Context
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Last playback session, persisted so the app can show and resume it after the process dies. */
@Serializable
data class PlaybackSnapshot(
    val userFolder: String,
    val source: String,
    val queue: List<TrackDetailsDto>,
    val currentTrackId: String,
    val positionMs: Long,
    /** The track a "wave by track" was started from; null for My Wave and everything else. */
    val waveSeed: TrackDetailsDto? = null
)

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val json: Json
) {
    private val file: File
        get() = File(context.filesDir, "playback_state.json")

    suspend fun save(snapshot: PlaybackSnapshot) = withContext(Dispatchers.IO) {
        try {
            // Write-then-rename so a crash mid-write never leaves a corrupt session file.
            val tmp = File(context.filesDir, "playback_state.json.tmp")
            tmp.writeText(json.encodeToString(PlaybackSnapshot.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackStateStore", "Could not save the session", e)
        }
    }

    suspend fun load(): PlaybackSnapshot? = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) json.decodeFromString(PlaybackSnapshot.serializer(), file.readText()) else null
        } catch (e: Exception) {
            android.util.Log.w("PlaybackStateStore", "Could not read the saved session", e)
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }
}

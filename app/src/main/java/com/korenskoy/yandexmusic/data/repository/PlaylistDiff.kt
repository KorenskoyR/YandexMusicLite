package com.korenskoy.yandexmusic.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The `diff` of `playlists/{kind}/change-relative`: a JSON list of operations applied in order to the
 * playlist at the given revision. Positions are indexes in the server's track list.
 */
internal object PlaylistDiff {

    fun insert(at: Int, trackId: String, albumId: String): String = JsonArray(
        listOf(
            JsonObject(
                mapOf(
                    "op" to JsonPrimitive("insert"),
                    "at" to JsonPrimitive(at),
                    "tracks" to JsonArray(
                        listOf(JsonObject(mapOf("id" to JsonPrimitive(trackId), "albumId" to JsonPrimitive(albumId))))
                    )
                )
            )
        )
    ).toString()

    /** Removes the tracks at [positions]; the last ones go first so earlier positions stay valid. */
    fun delete(positions: Collection<Int>): String = JsonArray(
        positions.distinct().sortedDescending().map { at ->
            JsonObject(mapOf("op" to JsonPrimitive("delete"), "from" to JsonPrimitive(at), "to" to JsonPrimitive(at + 1)))
        }
    ).toString()

    /**
     * Turns the order [from] into [to] (the same tracks) with moves — a delete followed by an insert, as
     * the API has no move operation. Empty when the order is unchanged.
     */
    fun reorder(from: List<String>, to: List<String>, albumOf: (String) -> String): String {
        require(from.sorted() == to.sorted()) { "Reordering must keep the same tracks" }
        val current = from.toMutableList()
        val ops = mutableListOf<JsonObject>()
        for (i in to.indices) {
            if (current[i] == to[i]) continue
            val j = (i + 1 until current.size).first { current[it] == to[i] }
            ops += JsonObject(mapOf("op" to JsonPrimitive("delete"), "from" to JsonPrimitive(j), "to" to JsonPrimitive(j + 1)))
            ops += JsonObject(
                mapOf(
                    "op" to JsonPrimitive("insert"),
                    "at" to JsonPrimitive(i),
                    "tracks" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive(to[i]), "albumId" to JsonPrimitive(albumOf(to[i]))))))
                )
            )
            current.add(i, current.removeAt(j))
        }
        return if (ops.isEmpty()) "" else JsonArray(ops).toString()
    }
}

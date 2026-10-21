package com.korenskoy.yandexmusic.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The `diff` field of `playlists/{kind}/change-relative`. */
class PlaylistDiffTest {

    @Test
    fun insertCarriesTrackAndAlbum() {
        assertEquals(
            """[{"op":"insert","at":0,"tracks":[{"id":"123","albumId":"456"}]}]""",
            PlaylistDiff.insert(0, "123", "456")
        )
    }

    @Test
    fun deletesGoFromTheEndSoPositionsStayValid() {
        assertEquals(
            """[{"op":"delete","from":7,"to":8},{"op":"delete","from":3,"to":4},{"op":"delete","from":0,"to":1}]""",
            PlaylistDiff.delete(listOf(3, 0, 7, 3))
        )
    }

    /** Applies a diff the way the server does, to check where the tracks end up. */
    private fun apply(order: List<String>, diff: String): List<String> {
        val list = order.toMutableList()
        for (op in Json.parseToJsonElement(diff).jsonArray.map { it.jsonObject }) {
            when (op["op"]!!.jsonPrimitive.content) {
                "delete" -> list.removeAt(op["from"]!!.jsonPrimitive.int)
                "insert" -> list.add(op["at"]!!.jsonPrimitive.int, op["tracks"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
            }
        }
        return list
    }

    @Test
    fun reorderReachesTheNewOrder() {
        val from = listOf("a", "b", "c", "d", "e")
        for (to in listOf(listOf("e", "d", "c", "b", "a"), listOf("b", "a", "c", "d", "e"), listOf("a", "c", "d", "e", "b"))) {
            assertEquals(to, apply(from, PlaylistDiff.reorder(from, to) { "alb_$it" }))
        }
    }

    @Test
    fun reorderKeepsCopiesOfATrack() {
        val from = listOf("a", "b", "a", "c")
        val to = listOf("c", "a", "a", "b")
        assertEquals(to, apply(from, PlaylistDiff.reorder(from, to) { "alb_$it" }))
    }

    @Test
    fun unchangedOrderNeedsNoRequest() {
        assertEquals("", PlaylistDiff.reorder(listOf("a", "b"), listOf("a", "b")) { "x" })
    }

    @Test
    fun movedTrackCarriesItsAlbum() {
        assertEquals(
            """[{"op":"delete","from":1,"to":2},{"op":"insert","at":0,"tracks":[{"id":"b","albumId":"alb_b"}]}]""",
            PlaylistDiff.reorder(listOf("a", "b"), listOf("b", "a")) { "alb_$it" }
        )
    }
}

package com.korenskoy.yandexmusic.data.repository

import com.korenskoy.yandexmusic.domain.model.RadioCategory
import com.korenskoy.yandexmusic.domain.model.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Радио» in the car and "next" / "previous" on a station: the favourites first, like a car radio's presets. */
class RadioRulesTest {

    private fun station(id: String) = RadioStation(id, id, "100.0 FM", "", "", RadioCategory.POPULAR)
    private val all = listOf("a", "b", "c", "d", "e").map(::station)

    @Test
    fun radioStartsWhereItWasLeft() {
        assertEquals("c", RadioRules.start(all, setOf("b", "d"), lastId = "c")?.id)
        assertEquals("no last one: the first favourite", "b", RadioRules.start(all, setOf("d", "b"), lastId = null)?.id)
        assertEquals("a last one that is gone", "b", RadioRules.start(all, setOf("b"), lastId = "zzz")?.id)
        assertEquals("nothing chosen yet: the first of all", "a", RadioRules.start(all, emptySet(), lastId = null)?.id)
        assertNull(RadioRules.start(emptyList(), emptySet(), null))
    }

    @Test
    fun aFavouriteStepsAmongTheFavourites() {
        val favs = setOf("b", "d", "e")
        assertEquals("d", RadioRules.step(all, favs, "b", 1)?.id)
        assertEquals("round the presets", "b", RadioRules.step(all, favs, "e", 1)?.id)
        assertEquals("e", RadioRules.step(all, favs, "b", -1)?.id)
    }

    @Test
    fun otherwiseAmongAll() {
        assertEquals("not a favourite itself: all the stations", "d", RadioRules.step(all, setOf("b", "e"), "c", 1)?.id)
        assertEquals("one favourite is no preset list", "c", RadioRules.step(all, setOf("b"), "b", 1)?.id)
        assertEquals("round all", "e", RadioRules.step(all, emptySet(), "a", -1)?.id)
        assertNull("unknown station", RadioRules.step(all, emptySet(), "x", 1))
    }
}

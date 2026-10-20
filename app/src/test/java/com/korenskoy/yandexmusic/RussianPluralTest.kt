package com.korenskoy.yandexmusic

import com.korenskoy.yandexmusic.utils.russianPluralIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class RussianPluralTest {
    @Test
    fun russianRules() {
        val expected = mapOf(
            0 to 2, 1 to 0, 2 to 1, 4 to 1, 5 to 2, 11 to 2, 12 to 2, 14 to 2, 21 to 0,
            22 to 1, 25 to 2, 37 to 2, 101 to 0, 111 to 2, 112 to 2, 122 to 1, 242 to 1
        )
        expected.forEach { (n, form) -> assertEquals("form for $n", form, russianPluralIndex(n)) }
    }
}

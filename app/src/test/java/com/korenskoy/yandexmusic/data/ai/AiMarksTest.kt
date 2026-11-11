package com.korenskoy.yandexmusic.data.ai

import com.korenskoy.yandexmusic.data.dto.CreditDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.presentation.main.upcomingOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Music made with AI: what counts as marked, and asking Yandex as little as possible. */
class AiMarksTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun credits(ai: String?) = listOfNotNull(
        CreditDto("Исполнитель", "Нейробег"),
        ai?.let { CreditDto("Использование ИИ", it) },
        CreditDto("Лейбл", "-")
    )

    // --- What counts ---

    @Test
    fun wholeAndPartAreMarksAGuessIsNot() {
        assertEquals("Трек полностью создан с использованием ИИ", AiRules.markOf(credits("Трек полностью создан с использованием ИИ")))
        assertEquals("Трек частично создан с использованием ИИ", AiRules.markOf(credits("Трек частично создан с использованием ИИ")))
        assertNull("«возможно» is a guess: no mark, never passed over", AiRules.markOf(credits("Возможно, трек создан с использованием ИИ")))
        assertNull("no line at all", AiRules.markOf(credits(null)))
        assertNull("an empty line", AiRules.markOf(credits("  ")))
    }

    @Test
    fun anEnglishAnswerIsUnderstoodToo() {
        assertEquals("Track created entirely with the help of AI", AiRules.markOf(listOf(CreditDto("AI use", "Track created entirely with the help of AI"))))
        assertNull(AiRules.markOf(listOf(CreditDto("AI use", "Possibly created with the help of AI"))))
    }

    @Test
    fun onlyTracksOfTheCatalogueAreAsked() {
        assertTrue(AiRules.isAskable("156022399"))
        assertFalse("the user's own file", AiRules.isAskable("local_m5"))
        assertFalse("a station", AiRules.isAskable("radio_europa"))
        assertFalse("an upload", AiRules.isAskable("a3f1c2d4-1b2c-4d5e-9f00-000000000000"))
        assertFalse(AiRules.isAskable(""))
    }

    // --- Asking ---

    private class Server {
        val asked = ConcurrentHashMap<String, AtomicInteger>()
        @Volatile var answers: Map<String, AiMarks.Answer> = emptyMap()
        val fetch: suspend (String) -> AiMarks.Answer = { id ->
            asked.getOrPut(id) { AtomicInteger() }.incrementAndGet()
            answers[id] ?: AiMarks.Answer.Credits(emptyList())
        }
        fun times(id: String) = asked[id]?.get() ?: 0
    }

    private fun marked(words: String) = AiMarks.Answer.Credits(listOf(CreditDto("Использование ИИ", words)))

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun aMarkIsAskedOnceAndRemembered() = runBlocking {
        val server = Server().apply { answers = mapOf("1" to marked("Трек полностью создан с использованием ИИ"), "3" to marked("Возможно, трек создан с использованием ИИ")) }
        val file = File(temp.root, "ai.json")
        var clock = 1_000_000L
        val marks = AiMarks(server.fetch, file, now = { clock })
        marks.want(listOf("1", "2", "3"))
        withTimeout(5_000) { marks.marks.first { it.containsKey("1") } }
        waitFor { server.times("2") == 1 && server.times("3") == 1 }
        assertTrue(marks.isAi("1"))
        assertFalse("checked, not marked", marks.isAi("2"))
        assertFalse("a guess", marks.isAi("3"))
        // Asked again within the month: not a single new request
        marks.want(listOf("1", "2", "3"))
        Thread.sleep(200)
        assertEquals(1, server.times("1"))
        assertEquals(1, server.times("2"))
        // Kept on the device: a new start knows without asking
        waitFor { file.exists() }
        val again = AiMarks(server.fetch, file, now = { clock })
        withTimeout(5_000) { again.marks.first { it.containsKey("1") } }
        again.want(listOf("1", "2"))
        Thread.sleep(200)
        assertEquals("remembered across starts", 1, server.times("1"))
        // A month later it is asked again
        clock += AiMarks.KEEP_MS + 1
        again.want("2")
        waitFor { server.times("2") == 2 }
    }

    @Test
    fun noAnswerIsNoMarkAndIsAskedAgainLater() = runBlocking {
        val server = Server().apply { answers = mapOf("7" to AiMarks.Answer.Failed) }
        val marks = AiMarks(server.fetch, null)
        marks.want("7")
        waitFor { server.times("7") == 1 }
        assertFalse("no connection: the track plays", marks.isAi("7"))
        Thread.sleep(100)
        marks.want("7")
        waitFor { server.times("7") == 2 }
        // An upload without credits is not asked again
        server.answers = mapOf("8" to AiMarks.Answer.None)
        marks.want("8")
        waitFor { server.times("8") == 1 }
        Thread.sleep(100)
        marks.want("8")
        Thread.sleep(200)
        assertEquals(1, server.times("8"))
    }

    @Test
    fun aRowScrolledAwayBeforeItsTurnCostsNoRequest() = runBlocking {
        val gate = java.util.concurrent.CountDownLatch(1)
        val asked = ConcurrentHashMap.newKeySet<String>()
        val marks = AiMarks({ id -> asked += id; gate.await(); AiMarks.Answer.Credits(emptyList()) }, null)
        // Four are taken at once (the newest first); the rest wait their turn
        marks.want((1..10).map { it.toString() })
        waitFor { asked.size == 4 }
        assertEquals("the newest wishes first", setOf("10", "9", "8", "7"), asked.toSet())
        (1..6).forEach { marks.unwant(it.toString()) }
        gate.countDown()
        Thread.sleep(300)
        assertEquals("the six that left the screen were never asked", setOf("10", "9", "8", "7"), asked.toSet())
    }

    // --- What shows as coming next ---

    @Test
    fun theNextShelfLeavesOutWhatWillBePassedOver() {
        fun t(id: String) = TrackDetailsDto(id = JsonPrimitive(id), title = id)
        val queue = listOf(t("1"), t("2"), t("3"), t("4"))
        assertEquals(listOf("2", "3", "4"), upcomingOf(queue, "1").map { it.idString })
        assertEquals(listOf("2", "4"), upcomingOf(queue, "1", passOver = { it.idString == "3" }).map { it.idString })
    }
}

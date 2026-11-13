package com.korenskoy.yandexmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/** The station list of 1.14.6: every stream says what it really is, and a plain-http stream is allowed to play. */
class RadioStreamsTest {

    private val repository = File("src/main/java/com/korenskoy/yandexmusic/data/repository/RadioRepository.kt").readText()
    private val security = File("src/main/res/xml/network_security_config.xml").readText()

    private val stations: List<Triple<String, String, String?>> =
        Regex("""RadioStation\("([^"]+)", "[^"]*", "[^"]*", "([^"]*)"[^\n]*?(?:format = "([^"]*)")?\)""")
            .findAll(repository)
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3].ifEmpty { null }) }
            .toList()

    @Test
    fun everyStationShowsTheFormatMeasuredFromItsStream() {
        assertTrue("stations parsed", stations.size >= 30)
        for ((id, _, format) in stations) {
            assertTrue("$id: format \"$format\"", format != null && Regex("""(MP3|AAC) \d{2,3}""").matches(format))
        }
    }

    @Test
    fun noTwoStationsPlayTheSameStream() {
        val shared = stations.groupBy { it.second }.filterValues { it.size > 1 }.keys
        assertTrue("one stream for several stations: $shared", shared.isEmpty())
    }

    @Test
    fun aPlainHttpStreamIsOnAHostAllowedPlainHttp() {
        val allowed = Regex("""<domain includeSubdomains="(true|false)">([^<]+)</domain>""").findAll(security)
            .map { it.groupValues[2] to (it.groupValues[1] == "true") }.toList()
        for ((id, url, _) in stations.filter { it.second.startsWith("http://") }) {
            val host = URI(url).host
            val ok = allowed.any { (domain, sub) -> host == domain || (sub && host.endsWith(".$domain")) }
            assertTrue("$id: $host is not allowed plain http", ok)
        }
    }

    @Test
    fun theStreamsThatGotBetterStayBetter() {
        val byId = stations.associate { it.first to it.third }
        assertEquals("MP3 256", byId["nashe_radio"])
        assertEquals("MP3 256", byId["rock_fm"])
        assertEquals("MP3 320", byId["dorojnoe"])
        assertTrue("Love Radio's stream is gone (404): not listed", "love_radio" !in byId)
    }
}

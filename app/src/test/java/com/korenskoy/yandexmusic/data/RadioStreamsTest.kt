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

    /**
     * The report: louder after switching from the wave to the radio. Every station has its loudness measured from its
     * stream; with "Выравнивание громкости" a loud one is brought down to the music's -14 LUFS as a track is.
     */
    @Test
    fun aLoudStationIsBroughtDownToTheMusicLikeALoudTrack() {
        val loudness = Regex("""RadioStation\("([^"]+)"[^\n]*?loudness = (-?\d+\.\d)""").findAll(repository)
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        assertTrue("measured: ${loudness.size}", loudness.size >= stations.size - 1)
        loudness.forEach { (id, lufs) -> assertTrue("$id: $lufs LUFS", lufs in -30.0..-5.0) }
        val record = com.korenskoy.yandexmusic.data.player.Loudness.gainFor(loudness.getValue("record"))
        assertEquals("Radio Record at -7.8 LUFS: 6.2 dB down", 0.49f, record, 0.01f)
        assertEquals("a quiet talk station is not raised", 1f, com.korenskoy.yandexmusic.data.player.Loudness.gainFor(loudness.getValue("vesti_fm")), 0.0001f)
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

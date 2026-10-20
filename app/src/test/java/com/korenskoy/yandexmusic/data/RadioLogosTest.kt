package com.korenskoy.yandexmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Guards against the 1.4.2 regression where 20 stations showed other stations' logos
 * (several stations shared one wrong image).
 */
class RadioLogosTest {

    private val repository = File("src/main/java/com/korenskoy/yandexmusic/data/repository/RadioRepository.kt").readText()
    private val drawables = File("src/main/res/drawable-nodpi")

    private fun stationIcons(): Map<String, String> =
        Regex("""RadioStation\("([^"]+)".*?iconRes = R\.drawable\.(\w+)""")
            .findAll(repository)
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun everyStationHasABundledLogoFile() {
        val stations = Regex("""RadioStation\("([^"]+)"""").findAll(repository).map { it.groupValues[1] }.toList()
        val icons = stationIcons()
        assertEquals("every station must reference a logo", stations.toSet(), icons.keys)
        for ((station, icon) in icons) {
            assertTrue("$station: missing $icon.png", File(drawables, "$icon.png").exists())
        }
    }

    @Test
    fun noTwoStationsShareTheSameLogoImage() {
        val md5 = MessageDigest.getInstance("MD5")
        val byHash = stationIcons().entries.groupBy { (_, icon) ->
            md5.digest(File(drawables, "$icon.png").readBytes()).joinToString("") { "%02x".format(it) }
        }
        val shared = byHash.values.filter { it.size > 1 }.map { group -> group.map { it.key } }
        assertTrue("stations sharing one logo image: $shared", shared.isEmpty())
    }
}

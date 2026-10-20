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

    /**
     * Logos are drawn on a rounded tile with a shadow; a transparent logo leaves only the shadow visible,
     * which looks like an empty square on the card. Every logo must be a solid tile.
     */
    @Test
    fun everyLogoIsAnOpaqueTile() {
        for ((station, icon) in stationIcons()) {
            val image = javax.imageio.ImageIO.read(File(drawables, "$icon.png"))
            if (!image.colorModel.hasAlpha()) continue
            val transparent = (0 until image.width step 4).sumOf { x ->
                (0 until image.height step 4).count { y -> (image.getRGB(x, y) ushr 24) < 250 }
            }
            assertEquals("$station: logo has transparent pixels", 0, transparent)
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

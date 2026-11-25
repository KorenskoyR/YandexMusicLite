package com.korenskoy.yandexmusic.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive geometry, symmetry and proportion invariants for [lineSpec], [playerSpec], [inviteSpec] and [coverSpec].
 */
class WidgetSpecTest {

    private val scales = listOf(0.85f, 1.0f, 1.15f, 1.3f)

    @Test
    fun lineSpecInvariantsAcrossAllSizesAndFontScales() {
        for (fs in scales) {
            for (w in 160..900 step 10) {
                for (h in 40..220 step 6) {
                    for (hasTrack in listOf(false, true)) {
                        for (canReact in listOf(false, true)) {
                            val sp = lineSpec(w.toFloat(), h.toFloat(), fs, hasTrack, canReact)
                            assertTrue("cardH <= 96 for $w x $h", sp.cardH <= 96f)
                            assertTrue("cardH <= h for $w x $h", sp.cardH <= h + 0.01f)
                            assertTrue("knob in 36..50 for $w x $h", sp.knob in 36f..50f)
                            if (sp.showHeart) assertTrue("heart only with canReact", canReact && hasTrack)
                            if (sp.showHeart) assertTrue("chip >= 32", sp.chip >= 32f)
                            if (sp.showPrev) assertTrue("prev chip >= 32", sp.chip >= 32f)
                            if (sp.showNext) assertTrue("next chip >= 32", sp.chip >= 32f)
                            if (hasTrack) {
                                val usedW = 2f * sp.padH +
                                    (if (sp.showCover) sp.cover + sp.coverGap else 0f) +
                                    6f +
                                    (if (sp.showHeart) sp.chip + sp.gap else 0f) +
                                    (if (sp.showPrev) sp.chip + sp.gap else 0f) +
                                    sp.knob + sp.gap + sp.chip + sp.textW
                                assertTrue("line sumWidth $usedW <= $w (h=$h fs=$fs)", usedW <= w + 0.1f)
                                if (w >= 220) {
                                    assertTrue("line textW=${sp.textW} >= 64 at w=$w h=$h", sp.textW >= 64f)
                                }
                            } else {
                                val usedW = 2f * sp.padH + sp.knob + (if (sp.showText) 8f + sp.textW else 0f)
                                assertTrue("empty line sumWidth $usedW <= $w", usedW <= w + 0.1f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun playerSpecInvariantsAcrossAllSizesAndFontScales() {
        for (fs in scales) {
            for (w in 180..900 step 20) {
                for (h in 100..420 step 10) {
                    for (canReact in listOf(false, true)) {
                        for (isRadio in listOf(false, true)) {
                            val sp = playerSpec(w.toFloat(), h.toFloat(), fs, canReact, isRadio)
                            assertEquals("player transport row is always symmetric", sp.showDislike, sp.showLike)
                            if (isRadio) assertFalse("radio never shows dislike", sp.showDislike)
                            if (!canReact) {
                                assertFalse(sp.showLike)
                                assertFalse(sp.showDislike)
                                assertFalse(sp.headerLike)
                            }
                            assertTrue("knob >= 44", sp.knob >= 44f)
                            assertTrue("chip >= 38", sp.chip >= 38f)
                            val count = 3 + (if (sp.showDislike) 1 else 0) + (if (sp.showLike) 1 else 0)
                            val rowW = 2f * sp.pad + sp.knob + 2f * sp.chip +
                                (if (sp.showDislike) sp.react else 0f) +
                                (if (sp.showLike) sp.react else 0f) + (count - 1) * sp.rowGap
                            assertTrue("player buttons $rowW <= $w (h=$h canReact=$canReact radio=$isRadio)", rowW <= w + 0.1f)
                            val inv = inviteSpec(w.toFloat(), h.toFloat(), fs)
                            assertTrue("invite knob >= 48", inv.knob >= 48f)
                            assertTrue("invite width <= $w", 2f * inv.pad + inv.knob + inv.gap + inv.textColW <= w + 0.1f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun coverSpecInvariantsAcrossAllSizesAndFontScales() {
        for (fs in scales) {
            for (w in 100..400 step 10) {
                for (h in 100..400 step 10) {
                    for (hasTrack in listOf(false, true)) {
                        val sp = coverSpec(w.toFloat(), h.toFloat(), fs, hasTrack)
                        if (!hasTrack) {
                            assertEquals(CoverMode.EMPTY, sp.mode)
                            assertTrue("empty cover knob >= 48", sp.knob >= 48f)
                            continue
                        }
                        assertEquals("cover controls row is always symmetric", sp.prev, sp.next, 0.01f)
                        assertTrue("knob >= 42", sp.knob >= 42f)
                        val count = (if (sp.prev > 0f) 1 else 0) + 1 + (if (sp.next > 0f) 1 else 0)
                        val rowW = sp.prev + sp.knob + sp.next + (count - 1) * sp.gap
                        when (sp.mode) {
                            CoverMode.SQUARE -> assertTrue("square rowW <= $w", 2f * sp.pad + rowW <= w + 0.1f)
                            CoverMode.TALL -> {
                                assertTrue("tall art fits innerW", sp.artSide <= w - 2f * sp.pad + 0.1f)
                                assertTrue("tall rowW <= $w", 2f * sp.pad + rowW <= w + 0.1f)
                                val textH = if (sp.showText) {
                                    widgetLineDp(sp.titleSp, fs) * sp.titleLines + widgetLineDp(sp.artistSp, fs) + sp.blockGap
                                } else {
                                    0f
                                }
                                val totalH = 2f * sp.pad + sp.artSide + sp.blockGap + textH + sp.knob
                                assertTrue("tall height $totalH <= $h", totalH <= h + 0.5f)
                            }
                            CoverMode.WIDE -> {
                                assertTrue("wide art fits height", sp.artSide <= h - 2f * sp.pad + 0.1f)
                                val totalW = 2f * sp.pad + sp.artSide + sp.artGap + rowW
                                assertTrue("wide totalW $totalW <= $w", totalW <= w + 0.1f)
                            }
                            CoverMode.EMPTY -> error("unexpected EMPTY when hasTrack=true")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun regressionProfilesForPhoneHeadUnitAndSmT725() {
        // Player 320x160 & 320x180: cover + all 5 symmetric buttons
        val p160 = playerSpec(320f, 160f, 1.0f, canReact = true, isRadio = false)
        assertTrue("320x160 shows cover", p160.showCover && p160.cover >= 64f)
        assertTrue("320x160 shows 5 symmetric buttons", p160.showDislike && p160.showLike && !p160.headerLike)

        // Player radio keeps 3 symmetric transport buttons + header heart
        val pRadio = playerSpec(472f, 353f, 1.0f, canReact = true, isRadio = true)
        assertTrue("radio keeps 3 centered buttons + headerLike", !pRadio.showDislike && !pRadio.showLike && pRadio.headerLike)

        // Player 250x110: symmetric 3 bottom buttons + header heart + artist visible
        val p250 = playerSpec(250f, 110f, 1.0f, canReact = true, isRadio = false)
        assertTrue("250x110 keeps symmetric buttons and artist", !p250.showDislike && !p250.showLike && p250.headerLike && p250.showArtist)

        // Line 250x64: text gets >= 64 dp; Line 472x157 shows heart + proportioned knob
        val l250 = lineSpec(250f, 64f, 1.0f, hasTrack = true, canReact = true)
        assertTrue("250x64 textW=${l250.textW} >= 64", l250.textW >= 64f)

        val tLine = lineSpec(472f, 157f, 1.0f, hasTrack = true, canReact = true)
        assertEquals(96f, tLine.cardH, 0.1f)
        assertTrue("T725 line keeps knob <= 50, cover, heart and wide title", tLine.knob <= 50f && tLine.showCover && tLine.showHeart && tLine.textW >= 140f)

        // Measured SM-T725 player 472x353: large cover >= 185dp, larger type & buttons
        val tPlayer = playerSpec(472f, 353f, 1.0f, canReact = true, isRadio = false)
        assertTrue("T725 player scales cover >= 185dp and shows all 5 buttons", tPlayer.cover >= 185f && tPlayer.knob >= 60f && tPlayer.showTimes && tPlayer.showDislike && tPlayer.showLike)

        // Cover 110x110: SQUARE solo centered play knob (prev == 0, next == 0)
        val c110 = coverSpec(110f, 110f, 1.0f, hasTrack = true)
        assertEquals(CoverMode.SQUARE, c110.mode)
        assertTrue("110x110 uses symmetric solo centered play", c110.prev == 0f && c110.next == 0f && c110.knob >= 44f)

        // Cover 160x160 shows title; 150x150 hides title cleanly
        val c160 = coverSpec(160f, 160f, 1.0f, hasTrack = true)
        assertTrue("160x160 shows title", c160.mode == CoverMode.SQUARE && c160.showText)
        val c150 = coverSpec(150f, 150f, 1.0f, hasTrack = true)
        assertFalse("150x150 hides title", c150.showText)

        // Measured SM-T725 cover 216x353: framed concentric square art >= 185dp, 2-line title, 3 centered buttons
        val tCover = coverSpec(216f, 353f, 1.0f, hasTrack = true)
        assertEquals(CoverMode.TALL, tCover.mode)
        assertTrue("T725 cover uses 188dp framed square art and 3 buttons", tCover.artSide >= 185f && tCover.prev > 0f && tCover.next == tCover.prev && tCover.showText)

        // Phone 20:9 tall 172x228: art >= 117dp (>=68% width) + title + 3 buttons
        val phoneTall = coverSpec(172f, 228f, 1.0f, hasTrack = true)
        assertEquals(CoverMode.TALL, phoneTall.mode)
        assertTrue("172x228 keeps >= 117dp square cover with text", phoneTall.artSide >= 117f && phoneTall.showText && phoneTall.prev > 0f)

        // Resize tall 180x320 & wide 340x170
        val tall320 = coverSpec(180f, 320f, 1.0f, hasTrack = true)
        assertTrue("180x320 keeps framed square cover >= 150dp", tall320.mode == CoverMode.TALL && tall320.artSide >= 150f && tall320.showText)

        val wide340 = coverSpec(340f, 170f, 1.0f, hasTrack = true)
        assertEquals(CoverMode.WIDE, wide340.mode)
        assertTrue("340x170 uses side-by-side square art + text + 3 buttons", wide340.artSide >= 130f && wide340.showText && wide340.prev > 0f)
    }
}

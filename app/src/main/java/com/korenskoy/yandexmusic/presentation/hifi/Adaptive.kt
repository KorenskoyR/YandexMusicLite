package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One rule for every screen size, instead of a number of its own on each page: the window's size decides how
 * many columns a grid has, how wide a standing page's column is and how big the main element grows. A phone looks
 * as it always did; a tablet or a big head unit gets more columns and bigger elements instead of the phone's sizes
 * with empty space around them. Pure; unit-tested.
 */
object Adaptive {
    /** A cover card is never smaller than this: its title and artist stay readable. */
    const val MIN_CELL_DP = 170
    const val GRID_GAP_DP = 14
    private const val MIN_COLUMNS = 2
    private const val MAX_COLUMNS = 8

    /**
     * Cover grids («Недавно добавленные», «В тренде», playlists, albums): as many columns of at least [MIN_CELL_DP]
     * as [contentWidthDp] holds. A phone 2, a tablet standing 4, 1280 lying 6, a wide head unit up to 8 — never
     * two huge cards across a tablet.
     */
    fun gridColumns(contentWidthDp: Int): Int =
        ((contentWidthDp + GRID_GAP_DP) / (MIN_CELL_DP + GRID_GAP_DP)).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * A standing page's main column (the player): all of a phone's width; on a tablet wider than a phone's, so the
     * cover grows with it, but never a strip across the whole screen.
     */
    fun standingColumn(windowWidthDp: Int): Dp = (windowWidthDp * STANDING_SHARE).dp.coerceIn(560.dp, 680.dp)

    /** The wave's knob: a share of the window's short side, from a small phone's size to a tablet's. */
    fun waveKnob(shortSideDp: Float, landscape: Boolean): Dp =
        (shortSideDp * if (landscape) 0.34f else 0.41f).dp.coerceIn(120.dp, 300.dp)

    /** The "next up" cards lying down: a phone on its side 84, a head unit 112, growing with a tablet's height to 150. */
    fun lyingShelfCard(windowHeightDp: Int): Dp =
        if (windowHeightDp < LOW_LYING_DP) 84.dp else (windowHeightDp * 0.15f).dp.coerceIn(112.dp, 150.dp)

    /** Under this height lying, a phone on its side: the compact layouts. */
    const val LOW_LYING_DP = 480

    private const val STANDING_SHARE = 0.8f
}

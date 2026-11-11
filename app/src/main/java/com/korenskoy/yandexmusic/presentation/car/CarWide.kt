package com.korenskoy.yandexmusic.presentation.car

/**
 * The car screen on a flat head unit (1920×720 and the like). The usual layout is fitted to the height, and on
 * a screen this wide a third of the width stays empty. Here that width goes to the music instead: the player
 * a little bigger, the next tracks always in sight with the sources under them, the cover as tall as the row.
 * All sizes are in the layout's own units (the 1280×720 design). Pure; unit-tested.
 */
internal object CarWide {
    /** The usual player column. */
    const val DRIVER_WIDTH = 652f

    /** How much bigger the player gets when the width allows. */
    const val MAX_GROWTH = 1.12f

    /** The cover never smaller than in the usual layout, never bigger than this. */
    const val MIN_COVER = 500f
    const val MAX_COVER = 600f

    /** Narrower than this, the next tracks and the five sources would not read: the usual layout stays. */
    const val MIN_MIDDLE = 460f

    /** Between the three columns. */
    const val GAP = 40f

    data class Layout(val driverScale: Float, val middle: Float, val cover: Float)

    /** The wide layout for a row of [width] × [height] units, or null when the usual one fits better. */
    fun layout(width: Float, height: Float): Layout? {
        val cover = height.coerceAtMost(MAX_COVER)
        if (cover < MIN_COVER) return null
        val forColumns = width - 2 * GAP
        val growth = ((forColumns - cover - MIN_MIDDLE) / DRIVER_WIDTH).coerceAtMost(MAX_GROWTH)
        if (growth < 1f) return null
        val middle = forColumns - cover - DRIVER_WIDTH * growth
        return Layout(driverScale = growth, middle = middle, cover = cover)
    }
}

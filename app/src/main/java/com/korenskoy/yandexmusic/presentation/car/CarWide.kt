package com.korenskoy.yandexmusic.presentation.car

import com.korenskoy.yandexmusic.domain.model.CarLayout

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

    /**
     * Once on screen, a layout stays while the room changes by less than this: a head unit's bar that shows for
     * a moment, or the interface scale a step either way, does not flip the screen between the two layouts.
     */
    const val HOLD = 0.08f

    /** "Три колонки" chosen in the settings: the player and the cover give up to this much to keep them. */
    const val SQUEEZE = 0.15f

    data class Layout(val driverScale: Float, val middle: Float, val cover: Float)

    /**
     * The wide layout for a row of [width] × [height] units, or null for the usual one. [mode] is the setting;
     * [wasWide] is what is on screen now (null at first), so that "Авто" holds it near the edge.
     */
    fun layout(width: Float, height: Float, mode: CarLayout = CarLayout.AUTO, wasWide: Boolean? = null): Layout? {
        val give = when (mode) {
            CarLayout.TWO -> return null
            CarLayout.THREE -> SQUEEZE
            CarLayout.AUTO -> if (wasWide == true) HOLD else 0f
        }
        val minScale = 1f - give
        val minCover = MIN_COVER * minScale
        val forColumns = width - 2 * GAP
        var cover = height.coerceAtMost(MAX_COVER)
        if (cover < minCover) return null
        // The cover gives way first (down to its least), then the player
        if (forColumns - cover - MIN_MIDDLE < DRIVER_WIDTH) {
            cover = cover.coerceAtMost((forColumns - MIN_MIDDLE - DRIVER_WIDTH).coerceAtLeast(minCover))
        }
        val growth = ((forColumns - cover - MIN_MIDDLE) / DRIVER_WIDTH).coerceAtMost(MAX_GROWTH)
        if (growth < minScale) return null
        val middle = forColumns - cover - DRIVER_WIDTH * growth
        return Layout(driverScale = growth, middle = middle, cover = cover)
    }

    /** Whether three columns can be on this screen at all (lying, as the settings show the choice only then). */
    fun possible(width: Float, height: Float): Boolean = layout(width, height, CarLayout.THREE) != null
}

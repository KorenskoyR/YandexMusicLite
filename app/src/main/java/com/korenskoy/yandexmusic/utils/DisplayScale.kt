package com.korenskoy.yandexmusic.utils

import kotlin.math.hypot
import kotlin.math.floor
import android.util.DisplayMetrics
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the app read from the screen and the UI scale it chose for an over/under-reported density. */
data class DisplayScaleInfo(
    val widthPx: Int,
    val heightPx: Int,
    val reportedDpi: Int,
    val physicalDpi: Int,
    val autoScale: Float,
    val reason: AutoScaleReason = AutoScaleReason.TRUSTED,
    /** The screen's diagonal by the reported physical density, inches (0 when unknown). */
    val impliedDiagonalIn: Float = 0f,
    /** The app's own window: the screen's whole, half of it beside a navigator, a launcher's strip, a free window. */
    val windowWidthPx: Int = widthPx,
    val windowHeightPx: Int = heightPx
) {
    /** True when the app occupies a sub-window (split screen, launcher widget, freeform) smaller than 85% of the screen. */
    val isSubWindow: Boolean
        get() = DisplayScale.isSubWindow(widthPx, heightPx, windowWidthPx, windowHeightPx)
}

/** Why "Авто" chose its scale: said in the settings beside it. */
enum class AutoScaleReason {
    /** The system's density matches the screen: 100%. */
    TRUSTED,
    /** The screen is denser (or looser) than the system says: scaled to match. */
    SCALED,
    /** The physical density the screen reports makes it smaller than any such device: not believed, 100%. */
    IMPLAUSIBLE,
    /** Scaled, but no further than the screens of the app still fit whole. */
    LIMITED
}

/**
 * Works out a UI-scale multiplier for screens whose reported density does not match their real pixel
 * density — common on car head units ("dense screen, low dpi"), where everything renders too small.
 * The app cannot change the system density, so it scales its own UI instead (see MainActivity).
 */
object DisplayScale {

    /** Below this relative mismatch the reported density is trusted and the scale stays at 1.0. */
    private const val MISMATCH_THRESHOLD = 0.15f

    fun analyze(metrics: DisplayMetrics): DisplayScaleInfo =
        decide(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, physicalDpi(metrics))

    /**
     * As [analyze], with the app in a window of [windowWidthPx]×[windowHeightPx] (0 — not known yet): the screen
     * decides whether its density is believed, the window how far the scale may go — a half of the screen or a
     * launcher's strip holds less than the whole, and the scale that fits the screen would not fit there.
     */
    fun analyze(metrics: DisplayMetrics, windowWidthPx: Int, windowHeightPx: Int): DisplayScaleInfo =
        inWindow(analyze(metrics), windowWidthPx, windowHeightPx)

    /** True when the window is noticeably smaller than the physical screen (split screen, launcher card, freeform). */
    internal fun isSubWindow(screenWidthPx: Int, screenHeightPx: Int, windowWidthPx: Int, windowHeightPx: Int): Boolean {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return false
        if (windowWidthPx <= 0 || windowHeightPx <= 0) return false
        return windowWidthPx < screenWidthPx * SUB_WINDOW_RATIO || windowHeightPx < screenHeightPx * SUB_WINDOW_RATIO
    }

    /** [info] for the app in a window of this size: "Авто" no larger than the window holds. Pure; unit-tested. */
    fun inWindow(info: DisplayScaleInfo, windowWidthPx: Int, windowHeightPx: Int): DisplayScaleInfo {
        if (windowWidthPx <= 0 || windowHeightPx <= 0) return info
        val subWindow = isSubWindow(info.widthPx, info.heightPx, windowWidthPx, windowHeightPx)
        val limit = largestFitting(windowWidthPx, windowHeightPx, info.reportedDpi, subWindow = subWindow)
        val auto = if (info.autoScale > limit) limit else info.autoScale
        val reason = if (auto < info.autoScale) AutoScaleReason.LIMITED else info.reason
        return info.copy(autoScale = auto, reason = reason, windowWidthPx = windowWidthPx, windowHeightPx = windowHeightPx)
    }

    /**
     * "Авто" for a screen of [widthPx]×[heightPx]: the ratio of the physical density to the system's, unless the
     * physical one cannot be true, and never so large that the app's screens stop fitting. Pure; unit-tested.
     *
     * The Geely Monjaro reports 321 dpi on a 12.3″ 1760×720 panel (really about 155 dpi): believed, it gave 200%
     * and left the app 880×360 dp, the car screen cut at the bottom.
     */
    fun decide(widthPx: Int, heightPx: Int, reportedDpi: Int, physicalDpi: Int): DisplayScaleInfo {
        val diagonal = if (physicalDpi > 0) hypot(widthPx / physicalDpi.toFloat(), heightPx / physicalDpi.toFloat()) else 0f
        fun info(scale: Float, reason: AutoScaleReason) = DisplayScaleInfo(widthPx, heightPx, reportedDpi, physicalDpi, scale, reason, diagonal)
        val raw = autoScale(reportedDpi, physicalDpi)
        if (raw == SettingsRepository.DEFAULT_UI_SCALE) return info(raw, AutoScaleReason.TRUSTED)
        if (!plausible(reportedDpi, diagonal)) return info(SettingsRepository.DEFAULT_UI_SCALE, AutoScaleReason.IMPLAUSIBLE)
        val limit = largestFitting(widthPx, heightPx, reportedDpi)
        return if (raw > limit) info(limit, AutoScaleReason.LIMITED) else info(raw, AutoScaleReason.SCALED)
    }

    /**
     * A system density of a large screen (head units, tablets: up to hdpi) with a physical density that makes the
     * screen smaller than [MIN_LARGE_DIAGONAL_IN] is the panel misreporting itself. Phones (higher system densities)
     * are not judged by size.
     */
    internal fun plausible(reportedDpi: Int, diagonalIn: Float): Boolean =
        reportedDpi > LARGE_SCREEN_MAX_DPI || diagonalIn >= MIN_LARGE_DIAGONAL_IN

    /**
     * The largest scale that still leaves the app [MIN_SHORT_SIDE_LANDSCAPE_DP] on the short side lying down
     * ([MIN_SHORT_SIDE_PORTRAIT_DP] standing): the car screen, the settings and the windows fit whole. Never
     * below 100% by this rule. Rounded down to 5%.
     */
    internal fun largestFitting(widthPx: Int, heightPx: Int, reportedDpi: Int, subWindow: Boolean = false): Float {
        val density = reportedDpi / BASE_DPI
        val minShort = when {
            subWindow -> MIN_SHORT_SIDE_WINDOW_DP
            widthPx >= heightPx -> MIN_SHORT_SIDE_LANDSCAPE_DP
            else -> MIN_SHORT_SIDE_PORTRAIT_DP
        }
        val fit = minOf(widthPx, heightPx) / (density * minShort)
        return (floor(fit * 20f) / 20f).coerceAtLeast(SettingsRepository.DEFAULT_UI_SCALE)
    }

    private const val BASE_DPI = 160f

    /** The short side of the smallest window the pages are made for when scaled manually. */
    private const val MIN_SHORT_SIDE_ANY_DP = 290f
    private const val LARGE_SCREEN_MAX_DPI = 240
    private const val MIN_LARGE_DIAGONAL_IN = 7f
    private const val MIN_SHORT_SIDE_LANDSCAPE_DP = 480f
    private const val MIN_SHORT_SIDE_PORTRAIT_DP = 360f
    private const val MIN_SHORT_SIDE_WINDOW_DP = 360f
    private const val SUB_WINDOW_RATIO = 0.85f

    /** Average of the panel's physical x/y dpi, or 0 when the device does not report a usable value. */
    private fun physicalDpi(metrics: DisplayMetrics): Int {
        val valid = listOf(metrics.xdpi, metrics.ydpi).filter { it.isFinite() && it in 60f..1200f }
        if (valid.isEmpty()) return 0
        return (valid.sum() / valid.size).roundToInt()
    }

    /**
     * Scale that makes the UI render at the screen's true density: physical / reported, but only when
     * both are plausible and differ by more than [MISMATCH_THRESHOLD]. Returns 1.0 otherwise, so screens
     * that report their density honestly (virtually every phone) are left untouched. Rounded to 5%.
     */
    fun autoScale(reportedDpi: Int, physicalDpi: Int): Float {
        if (reportedDpi !in 80..700 || physicalDpi !in 80..700) return SettingsRepository.DEFAULT_UI_SCALE
        val ratio = physicalDpi.toFloat() / reportedDpi.toFloat()
        if (abs(ratio - 1f) < MISMATCH_THRESHOLD) return SettingsRepository.DEFAULT_UI_SCALE
        val rounded = (ratio * 20f).roundToInt() / 20f // nearest 0.05
        return rounded.coerceIn(SettingsRepository.MIN_UI_SCALE, SettingsRepository.MAX_UI_SCALE)
    }

    /** The scale to apply now: the chosen automatic value, or the manual one. */
    fun effectiveScale(auto: Boolean, manualScale: Float, info: DisplayScaleInfo): Float =
        if (auto) info.autoScale
        else manualScale.coerceIn(SettingsRepository.MIN_UI_SCALE, manualCeiling(info.windowWidthPx, info.windowHeightPx, info.reportedDpi))

    /**
     * The most the interface may be scaled by hand on a screen of [widthPx]×[heightPx] at [reportedDpi]: the short
     * side never under [MIN_SHORT_SIDE_ANY_DP], the smallest screen every page is made for (a 320 dp phone). Beyond
     * it the wave's knob ran into its buttons and the dock (1760×720 at 320 dpi and 150 %). Never under 100 %, never
     * over [SettingsRepository.MAX_UI_SCALE]. Pure; unit-tested.
     */
    fun manualCeiling(widthPx: Int, heightPx: Int, reportedDpi: Int): Float {
        if (widthPx <= 0 || heightPx <= 0 || reportedDpi <= 0) return SettingsRepository.MAX_UI_SCALE
        val fit = minOf(widthPx, heightPx) / (reportedDpi / BASE_DPI * MIN_SHORT_SIDE_ANY_DP)
        return (floor(fit * 20f) / 20f).coerceIn(SettingsRepository.DEFAULT_UI_SCALE, SettingsRepository.MAX_UI_SCALE)
    }
}

package com.korenskoy.yandexmusic.utils

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
    val autoScale: Float
)

/**
 * Works out a UI-scale multiplier for screens whose reported density does not match their real pixel
 * density — common on car head units ("dense screen, low dpi"), where everything renders too small.
 * The app cannot change the system density, so it scales its own UI instead (see MainActivity).
 */
object DisplayScale {

    /** Below this relative mismatch the reported density is trusted and the scale stays at 1.0. */
    private const val MISMATCH_THRESHOLD = 0.15f

    fun analyze(metrics: DisplayMetrics): DisplayScaleInfo {
        val reportedDpi = metrics.densityDpi
        val physicalDpi = physicalDpi(metrics)
        return DisplayScaleInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            reportedDpi = reportedDpi,
            physicalDpi = physicalDpi,
            autoScale = autoScale(reportedDpi, physicalDpi)
        )
    }

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
        else manualScale.coerceIn(SettingsRepository.MIN_UI_SCALE, SettingsRepository.MAX_UI_SCALE)
}

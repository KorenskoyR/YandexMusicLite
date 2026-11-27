package com.korenskoy.yandexmusic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePolicyTest {

    @Test
    fun fixedModesIgnoreClockAndSystem() {
        assertTrue(
            ThemePolicy.isLightTheme(
                mode = AppThemeMode.LIGHT,
                scheduleType = ThemeScheduleType.SUNSET_TO_SUNRISE,
                lightMinutes = 540,
                darkMinutes = 960,
                nowMinutes = 120,
                systemDark = true
            )
        )
        assertFalse(
            ThemePolicy.isLightTheme(
                mode = AppThemeMode.DARK,
                scheduleType = ThemeScheduleType.SUNSET_TO_SUNRISE,
                lightMinutes = 540,
                darkMinutes = 960,
                nowMinutes = 720,
                systemDark = false
            )
        )
    }

    @Test
    fun autoFollowsSystemNightFlag() {
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.AUTO, ThemeScheduleType.CUSTOM, 540, 960, 720, systemDark = true))
        assertTrue(ThemePolicy.isLightTheme(AppThemeMode.AUTO, ThemeScheduleType.CUSTOM, 540, 960, 120, systemDark = false))
    }

    @Test
    fun sunsetToSunriseScheduleUsesSevenToNineteenDaytime() {
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.SUNSET_TO_SUNRISE, 0, 0, 6 * 60 + 59, systemDark = true))
        assertTrue(ThemePolicy.isLightTheme(AppThemePolicyMode, ThemeScheduleType.SUNSET_TO_SUNRISE, 0, 0, 7 * 60, systemDark = true))
        assertTrue(ThemePolicy.isLightTheme(AppThemePolicyMode, ThemeScheduleType.SUNSET_TO_SUNRISE, 0, 0, 18 * 60 + 59, systemDark = true))
        assertFalse(ThemePolicy.isLightTheme(AppThemePolicyMode, ThemeScheduleType.SUNSET_TO_SUNRISE, 0, 0, 19 * 60, systemDark = false))
    }

    @Test
    fun customScheduleHandlesSameDayAndOvernightIntervals() {
        // 09:00 .. 16:00 light
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 9 * 60, 16 * 60, 8 * 60 + 59, false))
        assertTrue(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 9 * 60, 16 * 60, 9 * 60, false))
        assertTrue(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 9 * 60, 16 * 60, 15 * 60 + 59, false))
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 9 * 60, 16 * 60, 16 * 60, false))

        // Wrap over midnight: light 22:00 .. 06:00
        assertTrue(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 22 * 60, 6 * 60, 23 * 60, true))
        assertTrue(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 22 * 60, 6 * 60, 2 * 60, true))
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 22 * 60, 6 * 60, 12 * 60, true))

        // Equal start/end means empty light window
        assertFalse(ThemePolicy.isLightTheme(AppThemeMode.SCHEDULE, ThemeScheduleType.CUSTOM, 9 * 60, 9 * 60, 9 * 60, false))
    }

    @Test
    fun formatMinutesFormats24HourClock() {
        assertEquals("00:00", ThemePolicy.formatMinutes(0))
        assertEquals("09:05", ThemePolicy.formatMinutes(9 * 60 + 5))
        assertEquals("23:59", ThemePolicy.formatMinutes(23 * 60 + 59))
    }

    private companion object {
        val AppThemePolicyMode = AppThemeMode.SCHEDULE
    }
}

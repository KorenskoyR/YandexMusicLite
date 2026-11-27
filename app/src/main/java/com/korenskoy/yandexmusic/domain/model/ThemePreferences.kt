package com.korenskoy.yandexmusic.domain.model

/** Theme mode chosen in Settings → Внешний вид. */
enum class AppThemeMode {
    AUTO,
    LIGHT,
    DARK,
    SCHEDULE
}

/** Schedule variant when [AppThemeMode.SCHEDULE] is active. */
enum class ThemeScheduleType {
    /** Fixed daytime interval: light 07:00–19:00, dark 19:00–07:00. */
    SUNSET_TO_SUNRISE,
    /** User-defined light and dark switch times. */
    CUSTOM
}

/** Pure resolver for active theme (daylight vs night) across UI and widgets. */
object ThemePolicy {
    const val DEFAULT_LIGHT_MINUTES = 9 * 60
    const val DEFAULT_DARK_MINUTES = 16 * 60
    private const val SUNRISE_MINUTES = 7 * 60
    private const val SUNSET_MINUTES = 19 * 60
    private const val MINUTES_PER_DAY = 24 * 60

    fun isLightTheme(
        mode: AppThemeMode,
        scheduleType: ThemeScheduleType,
        lightMinutes: Int,
        darkMinutes: Int,
        nowMinutes: Int,
        systemDark: Boolean
    ): Boolean = when (mode) {
        AppThemeMode.LIGHT -> true
        AppThemeMode.DARK -> false
        AppThemeMode.AUTO -> !systemDark
        AppThemeMode.SCHEDULE -> when (scheduleType) {
            ThemeScheduleType.SUNSET_TO_SUNRISE -> nowMinutes in SUNRISE_MINUTES until SUNSET_MINUTES
            ThemeScheduleType.CUSTOM -> isInHalfOpenInterval(
                nowMinutes = nowMinutes.mod(MINUTES_PER_DAY),
                startMinutes = lightMinutes.mod(MINUTES_PER_DAY),
                endMinutes = darkMinutes.mod(MINUTES_PER_DAY)
            )
        }
    }

    fun formatMinutes(minutes: Int): String {
        val m = minutes.mod(MINUTES_PER_DAY)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    private fun isInHalfOpenInterval(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean =
        when {
            startMinutes == endMinutes -> false
            startMinutes < endMinutes -> nowMinutes in startMinutes until endMinutes
            else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
}

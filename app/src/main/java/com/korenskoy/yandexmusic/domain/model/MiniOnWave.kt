package com.korenskoy.yandexmusic.domain.model

/**
 * Where the mini-player shows on "Моя волна" while the wave plays: always, standing only, or lying only (the
 * default: a flat head unit has room under the wave, a phone standing shows the wave's own controls).
 */
enum class MiniOnWave {
    ALWAYS, PORTRAIT, LANDSCAPE;

    fun shows(isLandscape: Boolean): Boolean = when (this) {
        ALWAYS -> true
        PORTRAIT -> !isLandscape
        LANDSCAPE -> isLandscape
    }
}

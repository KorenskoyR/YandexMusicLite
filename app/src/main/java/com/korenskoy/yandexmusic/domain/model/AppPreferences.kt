package com.korenskoy.yandexmusic.domain.model

/** What plays by itself when the app starts. */
enum class StartMusic { OFF, CONTINUE, WAVE }

/** Which page the app opens on. [LAST] is where it was closed, the car mode included. */
enum class StartPage { LAST, WAVE, CAR }

/** [AUTO] picks the light mode on weak devices, in battery saver and with animations off. */
enum class AnimationMode { AUTO, FULL, MINIMUM }

/** My Wave tuning, as the server names it (rotor settings2). */
enum class WaveDiversity(val wire: String) { FAVORITE("favorite"), DISCOVER("discover"), POPULAR("popular"), ANY("default") }
enum class WaveMood(val wire: String) { ACTIVE("active"), FUN("fun"), CALM("calm"), SAD("sad"), ANY("all") }
enum class WaveLanguage(val wire: String) { RUSSIAN("russian"), FOREIGN("not-russian"), NO_WORDS("without-words"), ANY("any") }

data class WaveTuning(
    val diversity: WaveDiversity = WaveDiversity.ANY,
    val mood: WaveMood = WaveMood.ANY,
    val language: WaveLanguage = WaveLanguage.ANY
) {
    val isDefault: Boolean
        get() = diversity == WaveDiversity.ANY && mood == WaveMood.ANY && language == WaveLanguage.ANY

    companion object {
        fun fromWire(diversity: String?, mood: String?, language: String?) = WaveTuning(
            WaveDiversity.entries.firstOrNull { it.wire == diversity } ?: WaveDiversity.ANY,
            WaveMood.entries.firstOrNull { it.wire == mood } ?: WaveMood.ANY,
            WaveLanguage.entries.firstOrNull { it.wire == language } ?: WaveLanguage.ANY
        )
    }
}

package com.korenskoy.yandexmusic.domain.model

import androidx.annotation.DrawableRes

data class RadioStation(
    val id: String,
    val name: String,
    val frequency: String,
    val streamUrl: String,
    val logoUrl: String,
    val category: RadioCategory,
    /** What the stream really is ("MP3 256", "AAC 96"), measured from the stream itself, not from its address. */
    val format: String? = null,
    val isFavorite: Boolean = false,
    @DrawableRes val iconRes: Int? = null,
    val brandColorStart: Long = 0xFF33333C,
    val brandColorEnd: Long = 0xFF1E1E24
)

enum class RadioCategory(val title: String) {
    ALL("Все"),
    FAVORITES("Избранное"),
    POPULAR("Популярные"),
    DANCE("Dance & Club"),
    ROCK("Рок"),
    RETRO("Ретро"),
    RELAX("Relax"),
    TALK("Разговорные")
}

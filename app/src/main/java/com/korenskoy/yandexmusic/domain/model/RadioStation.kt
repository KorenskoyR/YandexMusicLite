package com.korenskoy.yandexmusic.domain.model

data class RadioStation(
    val id: String,
    val name: String,
    val frequency: String,
    val streamUrl: String,
    val logoUrl: String,
    val category: RadioCategory,
    val bitRate: Int = 192,
    val isFavorite: Boolean = false,
    @androidx.annotation.DrawableRes val iconRes: Int? = null,
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

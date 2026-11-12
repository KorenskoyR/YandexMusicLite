package com.korenskoy.yandexmusic.domain.model

/**
 * «В авто» lying on a wide head unit: three columns (the player, the next tracks as a list, the cover) or two
 * (the player, the cover with the next ones under it). [AUTO] picks by the room and holds its choice near the
 * edge; the other two fix it (three columns fall back to two only where they cannot fit at all).
 */
enum class CarLayout { AUTO, THREE, TWO }

package com.korenskoy.yandexmusic.utils

import androidx.compose.runtime.compositionLocalOf

/** Real quality of the playing stream ("MP3 · 320 кбит/с"), provided by MainScreen for the player screens. */
val LocalStreamQuality = compositionLocalOf<String?> { null }

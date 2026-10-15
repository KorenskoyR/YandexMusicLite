package com.korenskoy.yandexmusic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserAccount(
    val uid: String,
    val login: String = "",
    val displayName: String = "",
    val token: String,
    val avatarUrl: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Sanitized user folder name suitable for directory paths in Android filesystem:
     * e.g. "roman_korenskoy" or "user123".
     */
    val folderName: String
        get() {
            val baseName = login.ifBlank { displayName }.ifBlank { uid }
            val sanitized = baseName.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").trim('_')
            return sanitized.ifBlank { "user_$uid" }.take(40)
        }
}

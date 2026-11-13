package com.korenskoy.yandexmusic.data.player

import android.media.AudioAttributes

/** What took the sound from the music, as the journal keeps it ([PlaybackJournal.Event.detail] of a pause). */
object SoundTaker {
    const val CALL = "call"
    const val VOICE_CHAT = "voice_chat"
    const val NAVIGATION = "navigation"
    const val ASSISTANT = "assistant"
    const val NOTIFICATION = "notification"
    const val ALARM = "alarm"
    const val GAME = "game"
    const val APP = "app"

    /** AudioAttributes.USAGE_ASSISTANT: Android 8 and later. */
    private const val USAGE_ASSISTANT = 16

    private val NOTIFICATIONS = setOf(
        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_NOTIFICATION_EVENT,
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
    )

    /** From the kinds of sound playing now (the music's own among them), the one that most likely took it. */
    fun byUsages(usages: Collection<Int>): String = when {
        AudioAttributes.USAGE_VOICE_COMMUNICATION in usages -> VOICE_CHAT
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE in usages -> CALL
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE in usages -> NAVIGATION
        USAGE_ASSISTANT in usages -> ASSISTANT
        AudioAttributes.USAGE_ALARM in usages -> ALARM
        usages.any { it in NOTIFICATIONS } -> NOTIFICATION
        AudioAttributes.USAGE_GAME in usages -> GAME
        else -> APP
    }
}

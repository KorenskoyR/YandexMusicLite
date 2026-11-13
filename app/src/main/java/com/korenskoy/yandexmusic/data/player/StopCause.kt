package com.korenskoy.yandexmusic.data.player

/**
 * Why the music paused. Every pause of the playing track is written to the [PlaybackJournal] with one of
 * these; a pause nobody announced is [UNKNOWN], and that is the one to look into.
 */
enum class StopCause {
    /** A pause in the app itself. */
    USER_APP,
    /** A pause from outside the app: the notification, the lock screen, a headset or car button ([PlaybackJournal.Event.detail] — who). */
    USER_DEVICE,
    /** Another app took the sound for good: a video, another player, a voice message ([PlaybackJournal.Event.detail] — what was heard). */
    FOCUS_LOST,
    /** Another app took the sound for a while: a call, an assistant; the music comes back after it. */
    FOCUS_TRANSIENT,
    /** The sound could not be had at all: a call is on. */
    FOCUS_DENIED,
    /** The headphones or the Bluetooth output went away. */
    OUTPUT_GONE,
    /** The sleep timer. */
    SLEEP_TIMER,
    /** The account has no Plus. */
    NO_PLUS,
    /** No connection and nothing on the device to play. */
    OFFLINE,
    /** Nothing announced it. */
    UNKNOWN;

    /** Asked for by the listener: it is not a stop the app has to answer for. */
    val byListener: Boolean get() = this == USER_APP || this == USER_DEVICE || this == SLEEP_TIMER
}

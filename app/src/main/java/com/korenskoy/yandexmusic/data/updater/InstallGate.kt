package com.korenskoy.yandexmusic.data.updater

/** The rules of one update, one session, one "install?" window. Pure; unit-tested. */
object InstallGate {
    /** Handed to the system, waiting for the confirmation, or installing: nothing new is started. */
    fun busy(state: InstallState?): Boolean = state == InstallState.Started || state == InstallState.Confirming

    /** The system's answer is about this update's session (an answer without an id is taken as this one's). */
    fun isCurrent(sessionId: Int, active: Int): Boolean = active >= 0 && (sessionId < 0 || sessionId == active)

    /** True the first time the system asks to confirm [sessionId]; a repeated ask opens no second window. */
    fun firstConfirmation(shown: MutableSet<Int>, sessionId: Int): Boolean = shown.add(sessionId)

    /**
     * Back in the app: the install goes on only on the return from the permission screen the app sent the user to,
     * with the file downloaded, the permission given and nothing under way. Any other return starts nothing.
     */
    fun installOnReturn(awaitingPermission: Boolean, downloaded: Boolean, permitted: Boolean, busy: Boolean): Boolean =
        awaitingPermission && downloaded && permitted && !busy
}

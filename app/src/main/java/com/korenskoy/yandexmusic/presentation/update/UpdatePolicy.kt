package com.korenskoy.yandexmusic.presentation.update

/** When the update window shows by itself, and what the status line says after a check. Pure; unit-tested. */
object UpdatePolicy {
    /**
     * A check the user asked for always shows the update. The check at launch shows it unless this very
     * version was skipped — a newer one is offered again.
     */
    fun offerOnLaunch(available: String, skipped: String?, silent: Boolean): Boolean = !silent || available != skipped

    /** What a finished check leaves in the status line. */
    sealed interface Status {
        /** Leave the line as it is: a quiet check at launch never writes there. */
        data object Keep : Status
        /** Nothing: the update window says it all, or there is nothing to say. */
        data object Clear : Status
        data object Latest : Status
        data class Skipped(val version: String) : Status
        data class Failed(val reason: String) : Status
    }

    /**
     * A finished check never leaves "Проверяем обновления…" behind: before, it stayed under the offer for
     * good and, when the check failed, in Settings too, which read as a hang.
     */
    fun statusAfterCheck(available: String?, upToDate: Boolean, error: String?, skipped: String?, silent: Boolean): Status = when {
        available != null && !offerOnLaunch(available, skipped, silent) -> Status.Skipped(available)
        silent -> Status.Keep
        available != null -> Status.Clear
        upToDate -> Status.Latest
        error != null -> Status.Failed(error)
        else -> Status.Clear
    }
}

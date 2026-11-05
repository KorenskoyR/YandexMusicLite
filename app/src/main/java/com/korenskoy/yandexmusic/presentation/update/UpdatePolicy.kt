package com.korenskoy.yandexmusic.presentation.update

/** When the update window shows by itself. Pure; unit-tested. */
object UpdatePolicy {
    /**
     * A check the user asked for always shows the update. The check at launch shows it unless this very
     * version was skipped — a newer one is offered again.
     */
    fun offerOnLaunch(available: String, skipped: String?, silent: Boolean): Boolean = !silent || available != skipped
}

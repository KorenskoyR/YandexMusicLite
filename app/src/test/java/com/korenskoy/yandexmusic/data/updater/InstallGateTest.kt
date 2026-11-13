package com.korenskoy.yandexmusic.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.14.8, the report: after the download several "Обновить?" windows came up (every return to the app started
 * another install session), and the system's installer hung on «Обновление…» with several installs at once.
 */
class InstallGateTest {

    @Test
    fun noReturnToTheAppStartsAnInstallButTheOneFromThePermissionScreen() {
        // Ten returns (a head unit's split screen: every touch on the app is one) with the update downloaded
        val started = (1..10).count { InstallGate.installOnReturn(awaitingPermission = false, downloaded = true, permitted = true, busy = false) }
        assertEquals("the old rule installed on each of them", 0, started)
        assertTrue("back from the permission screen the app sent the user to", InstallGate.installOnReturn(true, downloaded = true, permitted = true, busy = false))
        assertFalse("the permission still not given", InstallGate.installOnReturn(true, downloaded = true, permitted = false, busy = false))
        assertFalse("an install under way", InstallGate.installOnReturn(true, downloaded = true, permitted = true, busy = true))
        assertFalse("nothing downloaded", InstallGate.installOnReturn(true, downloaded = false, permitted = true, busy = false))
    }

    @Test
    fun oneUpdateOneSessionWhileItIsUnderWay() {
        assertTrue(InstallGate.busy(InstallState.Started))
        assertTrue(InstallGate.busy(InstallState.Confirming))
        assertFalse("nothing yet", InstallGate.busy(null))
        assertFalse("failed: «Ещё раз» may start a new one", InstallGate.busy(InstallState.Failed("x")))
        assertFalse("hung and given up: likewise", InstallGate.busy(InstallState.NoAnswer))
    }

    @Test
    fun theInstallWindowOpensOncePerSession() {
        val shown = mutableSetOf<Int>()
        assertTrue(InstallGate.firstConfirmation(shown, 7))
        assertFalse("the system's ask repeated: no second window", InstallGate.firstConfirmation(shown, 7))
        assertTrue("a new session after «Ещё раз»", InstallGate.firstConfirmation(shown, 8))
    }

    @Test
    fun answersAboutOtherSessionsAreNotThisUpdates() {
        assertTrue(InstallGate.isCurrent(7, active = 7))
        assertFalse("a session given up or left from before", InstallGate.isCurrent(6, active = 7))
        assertFalse("no session of ours under way", InstallGate.isCurrent(7, active = -1))
        assertTrue("an answer without an id", InstallGate.isCurrent(-1, active = 7))
    }
}

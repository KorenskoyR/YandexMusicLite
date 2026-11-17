package com.korenskoy.yandexmusic.data.download

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Скачивать избранное" counts from the likes there were when it was switched on, and keeps that across launches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AutoDownloadBaselineTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun notTakenYetIsNull() {
        assertNull(AutoDownloadBaseline(context).get())
    }

    @Test
    fun theLikesSoFarOutliveTheProcess() {
        AutoDownloadBaseline(context).set(listOf("1", "2:3", "1"))
        // A new instance, as after a restart: read back from the file
        assertEquals(setOf("1", "2:3"), AutoDownloadBaseline(context).get())
    }

    @Test
    fun anEmptyCollectionIsATakenBaseline() {
        AutoDownloadBaseline(context).set(emptyList())
        assertEquals(emptySet<String>(), AutoDownloadBaseline(context).get())
    }

    @Test
    fun switchedOffOrAnotherAccountForgetsIt() {
        val baseline = AutoDownloadBaseline(context)
        baseline.set(listOf("1"))
        baseline.clear()
        assertNull(baseline.get())
        assertNull(AutoDownloadBaseline(context).get())
    }
}

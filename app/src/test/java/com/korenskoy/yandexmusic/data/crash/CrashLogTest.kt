package com.korenskoy.yandexmusic.data.crash

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CrashLogTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var savedHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        savedHandler = Thread.getDefaultUncaughtExceptionHandler()
        CrashLog.clear(context)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(savedHandler)
        CrashLog.clear(context)
    }

    @Test
    fun crashIsRecordedAndStillReachesTheSystem() {
        var passedOn: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> passedOn = e }
        CrashLog.install(context)

        val error = IllegalStateException("Плеер сломался")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        assertSame("the system handler still runs (the 'app stopped' dialog)", error, passedOn)
        val report = CrashLog.reports(context).single()
        assertEquals("java.lang.IllegalStateException: Плеер сломался", report.summary)
        val text = CrashLog.fullText(context)
        assertTrue(text.contains("Версия: "))
        assertTrue(text.contains("CrashLogTest"))
    }

    @Test
    fun signInTokensNeverGetIntoAReport() {
        CrashLog.record(
            context, Thread.currentThread(),
            RuntimeException("HTTP 401 for Authorization: OAuth y0_FAKE_test_token and https://x/?access_token=abc123&a=1")
        )
        val text = CrashLog.fullText(context)
        assertFalse(text.contains("FAKE_test_token"))
        assertFalse(text.contains("abc123"))
        assertTrue(text.contains("OAuth ***"))
        assertTrue(text.contains("access_token=***&a=1"))
    }

    @Test
    fun onlyTheLastTenReportsAreKept() {
        repeat(13) { CrashLog.record(context, Thread.currentThread(), RuntimeException("сбой $it"), now = Date(1_000L + it)) }
        val reports = CrashLog.reports(context)
        assertEquals(10, reports.size)
        assertEquals("java.lang.RuntimeException: сбой 12", reports.first().summary)
    }
}

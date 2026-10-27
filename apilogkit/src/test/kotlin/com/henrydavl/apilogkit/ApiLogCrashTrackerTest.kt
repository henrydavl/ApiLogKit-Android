package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.crash.ApiLogCrashTracker
import com.henrydavl.apilogkit.model.CrashReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crash handler's two hard rules.
 *
 * Both fail *invisibly* if broken, and only in the field: a swallowed crash means
 * the host's Crashlytics quietly stops reporting, and a handler that throws
 * replaces the real stack trace with one from the debug logger. Neither shows up
 * in normal use of the library, so they are pinned here.
 *
 * [ApiLogCrashTracker.install] is process-global and deliberately has no
 * uninstall, so these tests exercise the handler body through
 * [ApiLogCrashTracker.reportFor] and a hand-built chain rather than installing
 * it — installing would leak a handler into every other test in the JVM.
 */
class ApiLogCrashTrackerTest {

    private class Recorder : Thread.UncaughtExceptionHandler {
        var thread: Thread? = null
        var error: Throwable? = null

        override fun uncaughtException(t: Thread, e: Throwable) {
            thread = t
            error = e
        }
    }

    /**
     * The handler body as [ApiLogCrashTracker.install] composes it, with the
     * previous handler injected so the chaining can be observed.
     */
    private fun handler(
        previous: Thread.UncaughtExceptionHandler?,
        onCrash: (CrashReport) -> Unit,
    ) = Thread.UncaughtExceptionHandler { thread, error ->
        runCatching { onCrash(ApiLogCrashTracker.reportFor(thread, error)) }
        previous?.uncaughtException(thread, error)
    }

    @Test
    fun `report captures thread, type, message and trace`() {
        val error = IllegalStateException("Expected a string but was NULL")

        val report = ApiLogCrashTracker.reportFor(Thread.currentThread(), error)

        assertEquals(Thread.currentThread().name, report.threadName)
        assertEquals("java.lang.IllegalStateException", report.exceptionClass)
        assertEquals("Expected a string but was NULL", report.message)
        assertTrue(report.stackTrace.contains("IllegalStateException"))
        assertTrue(report.stackTrace.contains("ApiLogCrashTrackerTest"))
    }

    /**
     * The cause names the offending field far more often than the top frame
     * does, so a trace that stops at the wrapper is close to useless.
     */
    @Test
    fun `report keeps the cause chain`() {
        val error = RuntimeException("mapping failed", NumberFormatException("For input string: \"\""))

        val report = ApiLogCrashTracker.reportFor(Thread.currentThread(), error)

        assertTrue(report.stackTrace.contains("Caused by"))
        assertTrue(report.stackTrace.contains("NumberFormatException"))
    }

    @Test
    fun `crash is always passed on to the previously installed handler`() {
        val previous = Recorder()
        val error = IllegalStateException("boom")
        val thread = Thread.currentThread()

        handler(previous) { /* records fine */ }.uncaughtException(thread, error)

        assertSame(error, previous.error)
        assertSame(thread, previous.thread)
    }

    /**
     * The important one: ApiLogKit failing to store a debug log must not cost the
     * host its crash report.
     */
    @Test
    fun `a failure while recording still passes the crash on`() {
        val previous = Recorder()
        val error = IllegalStateException("boom")

        handler(previous) { throw OutOfMemoryError("while writing the log") }
            .uncaughtException(Thread.currentThread(), error)

        assertSame(error, previous.error)
    }

    @Test
    fun `headline drops the package and keeps the message`() {
        val report = ApiLogCrashTracker.reportFor(
            Thread.currentThread(),
            IllegalStateException("Expected BEGIN_OBJECT but was STRING"),
        )

        assertEquals(
            "IllegalStateException: Expected BEGIN_OBJECT but was STRING",
            report.headline,
        )
    }

    @Test
    fun `headline is just the type when there is no message`() {
        val report = ApiLogCrashTracker.reportFor(Thread.currentThread(), NullPointerException())

        assertEquals("NullPointerException", report.headline)
    }
}

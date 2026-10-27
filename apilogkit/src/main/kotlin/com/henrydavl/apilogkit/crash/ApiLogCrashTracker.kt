package com.henrydavl.apilogkit.crash

import com.henrydavl.apilogkit.model.CrashReport
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Catches uncaught exceptions so the log that caused one is still there to look
 * at on the next launch.
 *
 * Without this, the most useful entry in the whole list is the one most likely to
 * be lost. Writes to [com.henrydavl.apilogkit.persistence.ApiLogStore] are queued
 * on a background thread, and a crash typically happens *milliseconds* after the
 * response that triggered it — while that insert is still sitting in the queue.
 * The process then dies and takes the queue with it. So the handler does two
 * things that only work from inside the dying process: it blocks until the
 * queued writes have actually reached disk, and it records which entries were
 * live at that moment.
 *
 * Two rules it must never break:
 *
 *  * **Chain, never swallow.** The previously installed handler is always
 *    invoked afterwards, so Crashlytics/Sentry/the platform's own
 *    `KillApplicationHandler` behave exactly as before. ApiLogKit is an
 *    observer here, not the owner of the crash.
 *  * **Never make the crash worse.** Everything is wrapped: a failure while
 *    recording a debug log must not replace the host's real stack trace with
 *    one from the logger.
 */
internal object ApiLogCrashTracker {

    @Volatile
    private var installed = false

    /** Whether the handler is in place. Exposed for the host-facing flag and tests. */
    val isInstalled: Boolean get() = installed

    /**
     * Installs the handler once per process, wrapping whatever is already there.
     *
     * Install order does not matter: a crash reporter that installs *after* this
     * wraps it and still calls through, and one that installed *before* becomes
     * the [previous] handler this delegates to.
     *
     * There is deliberately no uninstall. Handing the handler back would mean
     * overwriting whoever installed theirs in the meantime, which is a worse
     * outcome than an extra no-op callback — so turning persistence off instead
     * makes [onCrash] find nothing to write.
     */
    fun install(onCrash: (CrashReport) -> Unit) {
        synchronized(this) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // runCatching, not try/catch(Exception): an OutOfMemoryError
                // crash can perfectly well make *this* work fail too, and the
                // host's handler still has to run.
                runCatching { onCrash(reportFor(thread, error)) }

                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    // No host handler to defer to (effectively only in unit
                    // tests — Android's runtime always installs one). Print the
                    // trace so the crash is not silently discarded.
                    runCatching { error.printStackTrace() }
                }
            }
            installed = true
        }
    }

    /** Snapshots [error] into a storable report. Internal for the tests. */
    internal fun reportFor(thread: Thread, error: Throwable): CrashReport = CrashReport(
        date = Date(),
        threadName = thread.name,
        exceptionClass = error.javaClass.name,
        message = error.message,
        stackTrace = error.stackTraceText(),
    )

    /**
     * Full trace text including `Caused by:` chains — the cause is usually what
     * names the offending field, so the top frame alone is not enough.
     */
    private fun Throwable.stackTraceText(): String = runCatching {
        val writer = StringWriter()
        PrintWriter(writer).use { printStackTrace(it) }
        writer.toString()
    }.getOrElse { toString() }
}

package com.henrydavl.apilogkit.model

import java.util.Date

/**
 * An uncaught exception that killed the host process, kept on disk next to the
 * log entries it is blamed on.
 *
 * The point is the pairing, not the stack trace on its own: a crash report tells
 * you *where* the app died, the linked [ApiLog] tells you *what the server sent*
 * just before it did. A response that was still in flight, or the last one that
 * landed, is nearly always the input that caused a parse or mapping crash — and
 * the one thing a stack trace alone can never give you.
 *
 * Captured by [com.henrydavl.apilogkit.crash.ApiLogCrashTracker] and surfaced
 * through [ApiLog.crash]. Android-only: iOS has no equivalent, so (like
 * [ApiLog.fromPreviousSession]) it is additive and never changes the behaviour of
 * an entry that carries no crash.
 */
data class CrashReport(
    /** When the uncaught exception reached the handler. */
    val date: Date,
    /** Name of the thread that died, e.g. `main` or `OkHttp Dispatcher`. */
    val threadName: String,
    /** Fully-qualified throwable class, e.g. `java.lang.IllegalStateException`. */
    val exceptionClass: String,
    /** `Throwable.message`, which is frequently the most telling part. */
    val message: String?,
    /** Full `printStackTrace` text, including `Caused by:` chains. */
    val stackTrace: String,
) {

    /**
     * One-line summary for a badge or banner — `IllegalStateException: Expected a
     * string but was NULL`. The package is dropped because the simple name is
     * what identifies the failure at a glance, and the banner has one line.
     */
    val headline: String
        get() {
            val simpleName = exceptionClass.substringAfterLast('.').ifEmpty { exceptionClass }
            val detail = message?.trim()?.takeIf { it.isNotEmpty() } ?: return simpleName
            return "$simpleName: $detail"
        }
}

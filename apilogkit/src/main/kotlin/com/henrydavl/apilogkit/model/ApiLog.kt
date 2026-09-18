package com.henrydavl.apilogkit.model

import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/**
 * Lifecycle of a logged exchange. Mirrors the iOS `ApiLogState`.
 */
enum class ApiLogState {
    /** Request sent, no response yet. The response fields are placeholders. */
    PENDING,

    /** The exchange finished — successfully or not. [ApiLog.responseCode] tells which. */
    FINISHED,
}

/**
 * A single recorded log entry — an HTTP request/response or an analytics event.
 *
 * Mirrors the iOS `ApiLog` struct field-for-field so exports and behaviour stay
 * identical across platforms. Headers and request body are kept as ordered maps
 * of arbitrary values (`Any?`), matching the loosely-typed iOS `[String: Any]`.
 *
 * [fromPreviousSession] is the one Android-only addition; it is a display hint
 * and is never written to an export, so exported text remains byte-identical to
 * iOS.
 */
data class ApiLog(
    val responseCode: String,
    val method: String,
    val url: String,
    val responseTime: String,
    val size: String,
    val date: Date,
    val responseHeader: Map<String, Any?>,
    val responseBody: String,
    val requestHeader: Map<String, Any?>,
    val requestBody: Map<String, Any?>,
    /**
     * True when this entry was restored from disk on a later app launch rather
     * than captured in the current process — see
     * [com.henrydavl.apilogkit.ApiLogKitConfig.persistence]. Always false for
     * live captures, so it defaults appropriately and needs no call-site change.
     */
    val fromPreviousSession: Boolean = false,
    /**
     * Whether the exchange is still in flight.
     *
     * Defaults to [ApiLogState.FINISHED], so entries recorded through
     * [ApiLogger.addLog] — which is only called once a response exists — keep
     * behaving exactly as before. Only the [ApiLogger.beginLog]/
     * [ApiLogger.completeLog] pair and [com.henrydavl.apilogkit.interceptor.ApiLogInterceptor]
     * produce pending entries.
     */
    val state: ApiLogState = ApiLogState.FINISHED,
) {
    /**
     * Stable identity, assigned once when the entry is created — the counterpart
     * of iOS's `ApiLog.id`.
     *
     * The list rebuilds its rows on every logger emission, so `LazyColumn` needs
     * a key that neither derives from the log's contents nor changes on rebuild;
     * otherwise every arriving log re-keys the visible rows and jolts the scroll
     * position.
     *
     * Declared in the class body rather than the constructor on purpose: data
     * class `equals`/`hashCode`/`copy` ignore body properties, so two logs with
     * identical contents still compare equal, exactly as before. The flip side is
     * that `copy()` mints a *new* id, which is why completing a pending entry
     * goes through [completing] rather than a plain `copy`.
     *
     * Read-only from outside: the only writer is [completing].
     */
    var id: Long = nextId.incrementAndGet()
        private set

    /**
     * Returns this (finished) exchange carrying [pending]'s identity and start
     * time — the Kotlin counterpart of iOS's `restoreIdentity(id:date:)`, applied
     * after the same wholesale field replacement in [ApiLogger.completeLog].
     *
     * Both halves matter. The id keeps `LazyColumn` matching the completed row to
     * the pending one instead of tearing it down and re-keying it, and the start
     * time keeps the row in the chronological slot it was inserted at rather than
     * letting it jump to the top of the list as it resolves.
     */
    internal fun completing(pending: ApiLog): ApiLog {
        // `copy` assigns a fresh id — see the note on [id] — so it is reassigned
        // immediately afterwards.
        val completed = copy(date = pending.date, state = ApiLogState.FINISHED)
        completed.id = pending.id
        return completed
    }

    companion object {

        /**
         * Process-wide row-id counter. A plain counter rather than iOS's UUID:
         * uniqueness only has to hold within one process, which is all the list
         * needs, and this avoids a UUID allocation per captured request.
         */
        private val nextId = AtomicLong()

        /**
         * Analytics-style event entry (e.g. EventTracker) — no real HTTP fields.
         * Mirrors the iOS convenience initializer.
         */
        fun event(
            eventName: String,
            requestBody: Map<String, Any?>,
            responseBody: String,
        ): ApiLog = ApiLog(
            responseCode = "00",
            method = "POST",
            url = eventName,
            responseTime = "0",
            size = "0",
            date = Date(),
            responseHeader = emptyMap(),
            responseBody = responseBody,
            requestHeader = emptyMap(),
            requestBody = requestBody,
        )

        /**
         * In-flight entry: the request side is known, the response side isn't yet.
         *
         * Produced by [ApiLogger.beginLog]; the response fields are placeholders
         * until [ApiLogger.completeLog] fills them in. Mirrors the iOS pending
         * initializer.
         */
        fun pending(
            method: String,
            url: String,
            requestHeader: Map<String, Any?> = emptyMap(),
            requestBody: Map<String, Any?> = emptyMap(),
            date: Date = Date(),
        ): ApiLog = ApiLog(
            responseCode = "",
            method = method,
            url = url,
            responseTime = "0",
            size = "0",
            date = date,
            responseHeader = emptyMap(),
            responseBody = "",
            requestHeader = requestHeader,
            requestBody = requestBody,
            state = ApiLogState.PENDING,
        )
    }
}

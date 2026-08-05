package com.henrydavl.apilogkit.model

import java.util.Date
import java.util.concurrent.atomic.AtomicLong

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
     * identical contents still compare equal, exactly as before.
     */
    val id: Long = nextId.incrementAndGet()

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
    }
}

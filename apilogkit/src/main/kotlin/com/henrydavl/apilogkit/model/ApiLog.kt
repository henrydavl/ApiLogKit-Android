package com.henrydavl.apilogkit.model

import java.util.Date

/**
 * A single recorded log entry — an HTTP request/response or an analytics event.
 *
 * Mirrors the iOS `ApiLog` struct field-for-field so exports and behaviour stay
 * identical across platforms. Headers and request body are kept as ordered maps
 * of arbitrary values (`Any?`), matching the loosely-typed iOS `[String: Any]`.
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
) {
    companion object {
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

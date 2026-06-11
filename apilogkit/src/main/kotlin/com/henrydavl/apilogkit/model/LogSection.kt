package com.henrydavl.apilogkit.model

/** A flat key/value pair shown as a row in the detail screen. */
data class Log(val key: String, val value: String)

/**
 * The ordered sections rendered in the detail screen. Header sections are only
 * shown for API logs (events have no real HTTP headers) — matching iOS.
 */
enum class LogSection {
    REQUEST_URL,
    REQUEST_HEADER,
    REQUEST_BODY,
    RESPONSE_HEADER,
    RESPONSE_BODY;

    fun title(logType: LogEventType): String = when (this) {
        REQUEST_URL -> if (logType == LogEventType.EVENT_TRACKER) "Event Name" else "Request URL"
        REQUEST_HEADER -> "Request Header"
        REQUEST_BODY -> if (logType == LogEventType.EVENT_TRACKER) "Event Parameters" else "Request Body"
        RESPONSE_HEADER -> "Response Header"
        RESPONSE_BODY -> "Response Body"
    }

    fun isAvailable(logType: LogEventType): Boolean = when (this) {
        REQUEST_URL, REQUEST_BODY, RESPONSE_BODY -> true
        REQUEST_HEADER, RESPONSE_HEADER -> logType == LogEventType.API
    }
}

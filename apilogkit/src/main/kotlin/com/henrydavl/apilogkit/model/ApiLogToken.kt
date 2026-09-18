package com.henrydavl.apilogkit.model

/**
 * Handle to an in-flight entry, returned by [ApiLogger.beginLog] and handed back
 * to [ApiLogger.completeLog] once the response arrives. Compose port of the iOS
 * `ApiLogToken`.
 *
 * Opaque on purpose: it carries the row id rather than the entry itself, so a
 * caller holding a token can't pin a log in memory or mutate it behind the
 * logger's back.
 */
class ApiLogToken internal constructor(
    internal val id: Long,
    internal val bucket: LogEventType,
)

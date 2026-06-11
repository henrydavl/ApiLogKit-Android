package com.henrydavl.apilogkit.util

import com.henrydavl.apilogkit.ApiLogKitConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Vendored utilities — the Kotlin port of iOS `InternalHelpers.swift`. Kept
 * internal so they never collide with the host app's own extensions.
 */

private const val PRETTY_INDENT = 2

/** Pretty-prints the string if it contains JSON; otherwise returns itself. */
internal fun String.jsonize(): String {
    val trimmed = trim()
    return try {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(PRETTY_INDENT)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(PRETTY_INDENT)
            else -> this
        }
    } catch (_: Exception) {
        this
    }
}

/** Truncates the string to at most [maxLength] characters. */
internal fun String.maxCharacter(maxLength: Int): String =
    if (length > maxLength) substring(0, maxLength) else this

/**
 * Row-timestamp format used across the log screens
 * (e.g. "Wednesday, 10 June 2026, 13:56:02"), honouring [ApiLogKitConfig.dateLocale].
 */
internal fun Date.apiLogFormatted(): String {
    val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm:ss", ApiLogKitConfig.dateLocale)
    return formatter.format(this)
}

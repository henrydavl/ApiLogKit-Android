package com.henrydavl.apilogkit.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.henrydavl.apilogkit.export.ApiLogExporter
import com.henrydavl.apilogkit.json.JsonNode
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.Log
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.model.LogSection
import com.henrydavl.apilogkit.ui.component.JsonTreeModel
import com.henrydavl.apilogkit.util.jsonize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

/**
 * Prepares a single log for the detail screen. Compose port of the iOS
 * `ApiLogDetailViewModel` — same section ordering, JSON parsing, and chunking.
 *
 * The screen is **live**: an entry opened while its request is still in flight
 * shows the request side immediately and fills the response sections in on
 * screen when the response lands (see [awaitCompletion]). Everything derived
 * from the log is therefore held as Compose state rather than computed once in
 * `init`.
 */
class ApiLogDetailViewModel(log: ApiLog, val logType: LogEventType) {

    /** Republished so the screen fills in when an entry opened in flight completes. */
    var log by mutableStateOf(log)
        private set

    val sections: List<LogSection> = LogSection.entries.filter { it.isAvailable(logType) }

    private var derived by mutableStateOf(derive(log))

    /**
     * True while the request is still in flight — the response sections are empty
     * because there is nothing to show yet, not because anything is wrong.
     */
    val isPending: Boolean get() = log.state == ApiLogState.PENDING

    /** Parsed JSON for the body sections (null when the body isn't JSON). */
    val requestJson: JsonNode? get() = derived.requestJson
    val responseJson: JsonNode? get() = derived.responseJson

    /** Expansion-state models for the JSON tree views. */
    val requestTree: JsonTreeModel? get() = derived.requestTree
    val responseTree: JsonTreeModel? get() = derived.responseTree

    /**
     * Suspends until this entry's pending → finished transition, then re-derives
     * every display value. Returns immediately — without ever collecting — when
     * the screen was opened on an already-finished entry, so the common case
     * costs nothing while a busy logger emits on every request.
     *
     * An entry only ever completes once, so there is nothing to watch for
     * afterwards and the collection self-terminates. The Kotlin counterpart of
     * iOS's `observeCompletion()`.
     */
    suspend fun awaitCompletion() {
        if (!isPending) return
        val id = log.id
        val completed = ApiLogger.logsFlow(logType)
            .mapNotNull { logs ->
                logs.firstOrNull { it.id == id && it.state == ApiLogState.FINISHED }
            }
            .first()
        log = completed
        derived = derive(completed)
    }

    fun title(section: LogSection): String = section.title(logType)

    fun rows(section: LogSection): List<Log> = derived.rowsBySection[section] ?: emptyList()

    /** Parsed JSON tree for a section, if the body is JSON. */
    fun jsonNode(section: LogSection): JsonNode? = when (section) {
        LogSection.REQUEST_BODY -> requestJson
        LogSection.RESPONSE_BODY -> responseJson
        else -> null
    }

    /** Shared expansion-state model for a section's JSON tree. */
    fun treeModel(section: LogSection): JsonTreeModel? = when (section) {
        LogSection.REQUEST_BODY -> requestTree
        LogSection.RESPONSE_BODY -> responseTree
        else -> null
    }

    /** Whether any body section can be shown as a JSON tree. */
    val hasJsonBody: Boolean get() = requestJson != null || responseJson != null

    /** Full value copied when the section's "Copy" button is tapped. */
    fun copyValue(section: LogSection): String = when (section) {
        LogSection.REQUEST_URL -> log.url
        LogSection.REQUEST_HEADER -> log.requestHeader.toString()
        LogSection.REQUEST_BODY -> requestJson?.prettyPrinted() ?: log.requestBody.toString()
        LogSection.RESPONSE_HEADER -> log.responseHeader.toString()
        LogSection.RESPONSE_BODY -> responseJson?.prettyPrinted() ?: log.responseBody
    }

    fun exportRawLog(): String = ApiLogExporter.rawLog(log)
    fun exportCurl(): String = ApiLogExporter.curl(log)

    // MARK: - Helpers

    /** Everything re-derived from [log]; replaced wholesale when the entry completes. */
    private class Derived(
        val requestJson: JsonNode?,
        val responseJson: JsonNode?,
        val requestTree: JsonTreeModel?,
        val responseTree: JsonTreeModel?,
        val rowsBySection: Map<LogSection, List<Log>>,
    )

    private fun derive(log: ApiLog): Derived {
        val requestJson = JsonNode.fromMap(log.requestBody)
        val responseJson = JsonNode.parse(log.responseBody)

        val requestHeader = logsFrom(log.requestHeader)
        val responseHeader = logsFrom(log.responseHeader)

        // Body text mode: prefer pretty-printed JSON when the body parses,
        // otherwise fall back to the original payload.
        val requestBodyText = requestJson?.prettyPrinted() ?: keyValueDump(log.requestBody)
        val responseBodyText = responseJson?.prettyPrinted() ?: log.responseBody
        val requestBody = chunked(listOf(Log("", requestBodyText)))
        val responseBody = chunked(listOf(Log("", responseBodyText)))

        return Derived(
            requestJson = requestJson,
            responseJson = responseJson,
            requestTree = requestJson?.let { JsonTreeModel(it) },
            responseTree = responseJson?.let { JsonTreeModel(it) },
            rowsBySection = mapOf(
                LogSection.REQUEST_URL to listOf(Log("", log.url)),
                LogSection.REQUEST_HEADER to requestHeader,
                LogSection.REQUEST_BODY to requestBody,
                LogSection.RESPONSE_HEADER to responseHeader,
                LogSection.RESPONSE_BODY to responseBody,
            ),
        )
    }

    private fun logsFrom(map: Map<String, Any?>): List<Log> =
        map.map { (key, value) -> Log(key, value.toString()) }

    /** Readable `key: value` dump, used only when a body map isn't JSON. */
    private fun keyValueDump(map: Map<String, Any?>): String =
        map.keys.sorted().joinToString("\n") { "$it: ${map[it].toString().jsonize()}" }

    /**
     * Splits oversized values into [CHUNK_SIZE] pieces so very long strings
     * don't choke the UI (mirrors the iOS chunking).
     */
    private fun chunked(logs: List<Log>): List<Log> = logs.flatMap { log ->
        if (log.value.length <= CHUNK_SIZE) {
            listOf(log)
        } else {
            val result = ArrayList<Log>()
            var offset = 0
            var first = true
            while (offset < log.value.length) {
                val end = minOf(offset + CHUNK_SIZE, log.value.length)
                result.add(Log(if (first) log.key else "", log.value.substring(offset, end)))
                offset = end
                first = false
            }
            result
        }
    }

    private companion object {
        const val CHUNK_SIZE = 2_000
    }
}

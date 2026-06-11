package com.henrydavl.apilogkit.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.henrydavl.apilogkit.ApiLogKitConfig
import com.henrydavl.apilogkit.export.ApiLogExporter
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.util.maxCharacter

/**
 * State holder for the log list. Compose port of the iOS `ApiLogListViewModel`.
 *
 * [apiLogs] is the snapshot captured when the inspector opened and backs the
 * `API` tab; the `EVENT_TRACKER` tab reads live from [ApiLogger].
 */
class ApiLogListViewModel(private val apiLogs: List<ApiLog>) {

    var items by mutableStateOf<List<ApiLog>>(emptyList())
        private set

    var searchText by mutableStateOf("")
        private set

    var logType by mutableStateOf(LogEventType.API)
        private set

    val isEventTrackerLogEnabled: Boolean get() = ApiLogger.isEventTrackerLogEnabled
    val isDevOptionsEnabled: Boolean get() = ApiLogKitConfig.developerOptions != null

    init {
        reload()
    }

    fun onSearchChange(text: String) {
        searchText = text
        reload()
    }

    fun reload() {
        val source = when (logType) {
            LogEventType.API -> apiLogs
            LogEventType.EVENT_TRACKER -> ApiLogger.getEventTrackerLogs()
        }

        val query = searchText.maxCharacter(50).trim()
        val filtered = if (logType == LogEventType.API && query.length >= 3) {
            source.filter { it.url.contains(query, ignoreCase = true) }
        } else {
            source
        }

        // Newest first, matching iOS `logs.reversed()`.
        items = filtered.reversed()
    }

    fun switchTo(type: LogEventType) {
        if (type == LogEventType.EVENT_TRACKER && !isEventTrackerLogEnabled) {
            switchTo(LogEventType.API)
            return
        }
        if (type == logType) return
        logType = type
        reload()
    }

    fun clear() {
        ApiLogger.clearLogs()
        items = emptyList()
    }

    /** Raw textual dump of every visible log (same format as iOS export). */
    fun exportText(): String = ApiLogExporter.rawLogs(items)
}

package com.henrydavl.apilogkit.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henrydavl.apilogkit.ApiLogKitConfig
import com.henrydavl.apilogkit.export.ApiLogExporter
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.util.maxCharacter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State holder for the log list. Compose port of the iOS `ApiLogListViewModel`.
 *
 * The list is **live**: rather than rendering a snapshot taken when the
 * inspector opened, it collects [ApiLogger.logsFlow] and
 * [ApiLogger.eventTrackerLogsFlow], so requests captured while the screen is on
 * screen appear immediately. That is the Kotlin equivalent of iOS subscribing to
 * the logger's Combine publishers.
 *
 * A real [ViewModel] rather than a plain remembered object, so [viewModelScope]
 * can own the collectors and tear them down with the screen.
 */
class ApiLogListViewModel() : ViewModel() {

    /**
     * Retained for source compatibility; the snapshot is ignored because the
     * flows replay their current value on collection.
     */
    @Deprecated(
        "Logs now stream from ApiLogger; the snapshot argument is ignored.",
        ReplaceWith("ApiLogListViewModel()"),
    )
    constructor(apiLogs: List<ApiLog>) : this()

    var items by mutableStateOf<List<ApiLog>>(emptyList())
        private set

    var searchText by mutableStateOf("")
        private set

    var logType by mutableStateOf(LogEventType.API)
        private set

    /**
     * When paused, incoming logs are still collected but the list stops
     * refreshing, so reading a log isn't disturbed by live traffic.
     */
    var isPaused by mutableStateOf(false)
        private set

    /**
     * Number of logs in the current bucket that arrived since the list was last
     * refreshed. Only meaningful while [isPaused].
     */
    var pendingCount by mutableStateOf(0)
        private set

    // Live mirrors of the logger's buckets, kept current by the collectors below.
    private var apiLogs: List<ApiLog> = emptyList()
    private var eventTrackerLogs: List<ApiLog> = emptyList()

    // Size of the source bucket when [items] was last built, used to derive
    // [pendingCount] while paused.
    private var renderedSourceCount = 0

    private var searchJob: Job? = null

    val isEventTrackerLogEnabled: Boolean get() = ApiLogger.isEventTrackerLogEnabled
    val isDevOptionsEnabled: Boolean get() = ApiLogKitConfig.developerOptions != null

    init {
        // viewModelScope dispatches on the main thread, so these assignments and
        // the Compose state writes they trigger are already correctly confined.
        viewModelScope.launch {
            ApiLogger.logsFlow.collect { logs ->
                apiLogs = logs
                reloadIfLive()
            }
        }
        viewModelScope.launch {
            ApiLogger.eventTrackerLogsFlow.collect { logs ->
                eventTrackerLogs = logs
                reloadIfLive()
            }
        }
    }

    private val currentSource: List<ApiLog>
        get() = when (logType) {
            LogEventType.API -> apiLogs
            LogEventType.EVENT_TRACKER -> eventTrackerLogs
        }

    /**
     * Debounced so a burst of keystrokes filters once rather than per character;
     * the field itself still updates instantly. Mirrors the 250 ms Combine
     * debounce on iOS.
     */
    fun onSearchChange(text: String) {
        searchText = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    /** Applies a live logger update, unless paused — then only the badge moves. */
    private fun reloadIfLive() {
        if (isPaused) {
            pendingCount = (currentSource.size - renderedSourceCount).coerceAtLeast(0)
            return
        }
        reload()
    }

    fun reload() {
        val source = currentSource

        val query = searchText.maxCharacter(50).trim()
        val filtered = if (logType == LogEventType.API && query.length >= 3) {
            source.filter { it.url.contains(query, ignoreCase = true) }
        } else {
            source
        }

        // Newest first, matching iOS `logs.reversed()`.
        items = filtered.reversed()
        renderedSourceCount = source.size
        pendingCount = 0
    }

    /**
     * Toggles the live stream. Resuming immediately folds in whatever arrived
     * while paused.
     */
    fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            pendingCount = (currentSource.size - renderedSourceCount).coerceAtLeast(0)
        } else {
            reload()
        }
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
        renderedSourceCount = 0
        pendingCount = 0
    }

    /** Raw textual dump of every visible log (same format as iOS export). */
    fun exportText(): String = ApiLogExporter.rawLogs(items)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

package com.henrydavl.apilogkit.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrydavl.apilogkit.ApiLogKitConfig
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.ui.component.ApiLogRow
import com.henrydavl.apilogkit.ui.theme.ApiLogColors
import com.henrydavl.apilogkit.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiLogListScreen(
    viewModel: ApiLogListViewModel,
    listState: LazyListState,
    onOpenDetail: (ApiLog, LogEventType) -> Unit,
    onClose: () -> Unit,
) {
    // viewModel and listState are hoisted by the host (ApiLogActivity) so search
    // text and scroll position survive navigating into a detail and back.
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.logType == LogEventType.API) "API Logs" else "EventTracker") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePause) {
                        Icon(
                            if (viewModel.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (viewModel.isPaused) {
                                "Resume live logs"
                            } else {
                                "Pause live logs"
                            },
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    LogListMenu(
                        expanded = menuExpanded,
                        viewModel = viewModel,
                        onDismiss = { menuExpanded = false },
                        onExport = { ShareUtils.shareText(context, viewModel.exportText()) },
                        onDevOptions = { ApiLogKitConfig.developerOptions?.onSelected?.invoke(context) },
                        onClear = { showClearConfirm = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = viewModel.searchText,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search URL") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            // Shown while paused so the list is never silently stale.
            if (viewModel.isPaused) {
                PausedBanner(
                    pendingCount = viewModel.pendingCount,
                    onResume = viewModel::togglePause,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                // Keyed on the log's stable id: without it LazyColumn falls back to
                // the index, and because new logs land at the top every arrival
                // re-keys the visible rows and jolts the scroll position.
                items(viewModel.items, key = { it.id }) { log ->
                    ApiLogRow(
                        log = log,
                        logType = viewModel.logType,
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .clickable { onOpenDetail(log, viewModel.logType) },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear logs") },
            text = { Text("Are you sure you want to clear the logs?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** Banner surfacing that the live stream is held, and what is waiting behind it. */
@Composable
private fun PausedBanner(
    pendingCount: Int,
    onResume: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ApiLogColors.PausedBannerBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Pause,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (pendingCount > 0) "Paused — $pendingCount new" else "Paused",
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp).weight(1f),
        )
        TextButton(onClick = onResume, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LogListMenu(
    expanded: Boolean,
    viewModel: ApiLogListViewModel,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDevOptions: () -> Unit,
    onClear: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("API Logs") },
            onClick = { viewModel.switchTo(LogEventType.API); onDismiss() },
            leadingIcon = {
                Icon(
                    if (viewModel.logType == LogEventType.API) Icons.Filled.Check else Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                )
            },
        )

        if (viewModel.isEventTrackerLogEnabled) {
            DropdownMenuItem(
                text = { Text("EventTracker") },
                onClick = { viewModel.switchTo(LogEventType.EVENT_TRACKER); onDismiss() },
                leadingIcon = {
                    Icon(
                        if (viewModel.logType == LogEventType.EVENT_TRACKER) Icons.Filled.Check else Icons.Filled.ShowChart,
                        contentDescription = null,
                    )
                },
            )
        }

        DropdownMenuItem(
            text = { Text("Export") },
            onClick = { onExport(); onDismiss() },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
        )

        if (viewModel.isDevOptionsEnabled) {
            DropdownMenuItem(
                text = { Text(ApiLogKitConfig.developerOptions?.label ?: "Developer Options") },
                onClick = { onDevOptions(); onDismiss() },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            )
        }

        DropdownMenuItem(
            text = { Text("Clear") },
            onClick = { onClear(); onDismiss() },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        )
    }
}

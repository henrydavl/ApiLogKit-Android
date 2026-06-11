package com.henrydavl.apilogkit.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.Log
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.ui.component.JsonTreeControls
import com.henrydavl.apilogkit.ui.component.JsonTreeView
import com.henrydavl.apilogkit.util.ShareUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiLogDetailScreen(
    log: ApiLog,
    logType: LogEventType,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember(log) { ApiLogDetailViewModel(log, logType) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var rawMode by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    // Copy toast: bump the counter to (re)show, even with the same message.
    var toastTick by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val showCopied: (String) -> Unit = { text ->
        ShareUtils.copy(context, text)
        toastMessage = "Copied to clipboard"
        toastTick++
    }
    LaunchedEffect(toastTick) {
        if (toastTick > 0) {
            delay(1500)
            toastMessage = null
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.hasJsonBody) {
                        IconButton(onClick = { rawMode = !rawMode }) {
                            Icon(
                                if (rawMode) Icons.Filled.Code else Icons.Filled.Notes,
                                contentDescription = "Toggle raw / tree",
                            )
                        }
                    }
                    IconButton(onClick = { exportMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Raw Log") },
                            onClick = {
                                ShareUtils.shareText(context, viewModel.exportRawLog())
                                exportMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("cURL Command") },
                            onClick = {
                                ShareUtils.shareText(context, viewModel.exportCurl())
                                exportMenu = false
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                viewModel.sections.forEach { section ->
                    item(key = "header_$section") {
                        SectionHeader(
                            title = viewModel.title(section),
                            onCopy = { showCopied(viewModel.copyValue(section)) },
                        )
                    }

                    val tree = viewModel.treeModel(section)
                    if (tree != null && !rawMode) {
                        item(key = "controls_$section") {
                            JsonTreeControls(
                                model = tree,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item(key = "tree_$section") {
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                JsonTreeView(model = tree, onCopy = showCopied)
                            }
                        }
                    } else {
                        val monospaced = viewModel.jsonNode(section) != null
                        items(viewModel.rows(section)) { row ->
                            RowView(
                                row = row,
                                monospaced = monospaced,
                                onClick = { showCopied(row.value) },
                            )
                        }
                    }
                }
            }

            FloatingScrollButtons(
                showUp = listState.canScrollBackward,
                showDown = listState.canScrollForward,
                onUp = { scope.launch { listState.animateScrollToItem(0) } },
                onDown = { scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) } },
                modifier = Modifier.align(Alignment.BottomEnd),
            )

            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            ) {
                CopyToast(toastMessage.orEmpty())
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Button(onClick = onCopy, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
            Text("Copy", fontSize = 13.sp)
        }
    }
}

@Composable
private fun RowView(row: Log, monospaced: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (row.key.isNotEmpty()) {
            Text(
                text = row.key,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = row.value,
            fontSize = if (monospaced) 13.sp else 14.sp,
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = if (row.key.isNotEmpty()) 4.dp else 0.dp),
        )
    }
}

@Composable
private fun FloatingScrollButtons(
    showUp: Boolean,
    showDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showUp) {
            FilledIconButton(onClick = onUp, shape = CircleShape) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Scroll to top")
            }
        }
        if (showDown) {
            FilledIconButton(onClick = onDown, shape = CircleShape) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Scroll to bottom")
            }
        }
    }
}

@Composable
private fun CopyToast(message: String) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

package com.henrydavl.apilogkit.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.ui.detail.ApiLogDetailScreen
import com.henrydavl.apilogkit.ui.list.ApiLogListScreen
import com.henrydavl.apilogkit.ui.list.ApiLogListViewModel
import com.henrydavl.apilogkit.ui.theme.ApiLogTheme

/**
 * Standalone host for the inspector — the Android counterpart of iOS's
 * `ApiLogHostingController`. Launched on shake or manually
 * ([com.henrydavl.apilogkit.ApiLogInspector.launch]); because it is its own
 * Activity, XML-only host apps need no Compose of their own.
 */
class ApiLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The inspector is light-only: give it a light status bar (white background
        // with dark icons) so the icons stay visible on the light UI, regardless of
        // the host app's dark-mode setting. isAppearanceLightStatusBars = true means
        // "dark icons" and is supported on API 24+.
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // Snapshot the logs once when the inspector opens (matches iOS, which is
        // constructed with `getLogs()`).
        val logs = ApiLogger.getLogs()

        setContent {
            ApiLogTheme {
                // Hoisted above the list/detail switch so the list's search text and
                // scroll position survive opening a detail and pressing back (the
                // list composable leaves composition while the detail is shown).
                val listViewModel = remember { ApiLogListViewModel(logs) }
                val listState = rememberLazyListState()
                var selected by remember { mutableStateOf<Pair<ApiLog, LogEventType>?>(null) }

                if (selected == null) {
                    ApiLogListScreen(
                        viewModel = listViewModel,
                        listState = listState,
                        onOpenDetail = { log, type -> selected = log to type },
                        onClose = { finish() },
                    )
                } else {
                    BackHandler { selected = null }
                    val (log, type) = selected!!
                    ApiLogDetailScreen(
                        log = log,
                        logType = type,
                        onBack = { selected = null },
                    )
                }
            }
        }
    }
}

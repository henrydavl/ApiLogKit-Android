package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.henrydavl.apilogkit.ui.list.ApiLogListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Covers the live list behaviour ported from the iOS Combine implementation:
 * streaming updates, pause/resume with a pending count, and debounced search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiLogListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // The view models must go through a real store: ApiLogger is a process-wide
    // singleton, so a view model whose viewModelScope is never cancelled keeps
    // collecting its flows for the rest of the JVM run and corrupts later tests.
    // Clearing the store is what cancels those collectors, exactly as the
    // Activity does on destroy.
    private val viewModelStore = ViewModelStore()

    private fun createViewModel(): ApiLogListViewModel =
        ViewModelProvider(viewModelStore, ViewModelProvider.NewInstanceFactory())[
            ApiLogListViewModel::class.java,
        ]

    private fun log(url: String) = ApiLog(
        responseCode = "200", method = "GET", url = url, responseTime = "0", size = "0",
        date = Date(), responseHeader = emptyMap(), responseBody = "",
        requestHeader = emptyMap(), requestBody = emptyMap(),
    )

    @Before
    fun setUp() {
        // viewModelScope dispatches on Main; swap in a scheduler we control.
        Dispatchers.setMain(dispatcher)
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
    }

    @After
    fun tearDown() {
        // Cancel the collectors before Main goes away, or their cancellation has
        // nowhere to dispatch to.
        viewModelStore.clear()
        Dispatchers.resetMain()
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
    }

    @Test
    fun `items fill from logs recorded before the screen opened`() = runTest(dispatcher) {
        ApiLogger.addLog(log("a"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.items.size)
    }

    @Test
    fun `list updates live as logs arrive`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(0, viewModel.items.size)

        ApiLogger.addLog(log("a"))
        advanceUntilIdle()

        assertEquals(1, viewModel.items.size)
    }

    @Test
    fun `newest log is first`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        ApiLogger.addLog(log("older"))
        ApiLogger.addLog(log("newer"))
        advanceUntilIdle()

        assertEquals("newer", viewModel.items.first().url)
    }

    @Test
    fun `pausing holds the list and counts what arrives`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        ApiLogger.addLog(log("a"))
        advanceUntilIdle()

        viewModel.togglePause()
        ApiLogger.addLog(log("b"))
        ApiLogger.addLog(log("c"))
        advanceUntilIdle()

        assertEquals(1, viewModel.items.size)
        assertEquals(2, viewModel.pendingCount)
    }

    @Test
    fun `resuming folds in everything that arrived while paused`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.togglePause()
        ApiLogger.addLog(log("a"))
        ApiLogger.addLog(log("b"))
        advanceUntilIdle()

        viewModel.togglePause()
        advanceUntilIdle()

        assertEquals(2, viewModel.items.size)
        assertEquals(0, viewModel.pendingCount)
    }

    @Test
    fun `search applies only after the debounce elapses`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        ApiLogger.addLog(log("https://api.example.com/alpha"))
        ApiLogger.addLog(log("https://api.example.com/beta"))
        advanceUntilIdle()

        viewModel.onSearchChange("alpha")
        // The field updates immediately, the filter does not.
        assertEquals("alpha", viewModel.searchText)
        advanceTimeBy(100)
        assertEquals(2, viewModel.items.size)

        advanceUntilIdle()
        assertEquals(1, viewModel.items.size)
        assertEquals("https://api.example.com/alpha", viewModel.items.first().url)
    }

    @Test
    fun `queries shorter than three characters do not filter`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        ApiLogger.addLog(log("https://api.example.com/alpha"))
        ApiLogger.addLog(log("https://api.example.com/beta"))
        advanceUntilIdle()

        viewModel.onSearchChange("al")
        advanceUntilIdle()

        assertEquals(2, viewModel.items.size)
    }

    @Test
    fun `clear empties the list`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        ApiLogger.addLog(log("a"))
        advanceUntilIdle()

        viewModel.clear()
        advanceUntilIdle()

        assertEquals(0, viewModel.items.size)
        assertEquals(0, viewModel.pendingCount)
    }
}

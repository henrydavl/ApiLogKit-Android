package com.henrydavl.apilogkit

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.model.StatusClass
import com.henrydavl.apilogkit.ui.list.ApiLogListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * The filter bar as the list sees it: facet semantics applied to a live bucket,
 * the menus offering only reachable values, and the reset on bucket switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiLogListFilterTest {

    private val dispatcher = StandardTestDispatcher()

    // See ApiLogListViewModelTest: the store is what cancels the collectors, and
    // ApiLogger is a process-wide singleton that later tests share.
    private val viewModelStore = ViewModelStore()

    private fun createViewModel(): ApiLogListViewModel =
        ViewModelProvider(viewModelStore, ViewModelProvider.NewInstanceFactory())[
            ApiLogListViewModel::class.java,
        ]

    private fun log(url: String, code: String = "200", method: String = "GET") = ApiLog(
        responseCode = code, method = method, url = url, responseTime = "0", size = "0",
        date = Date(), responseHeader = emptyMap(), responseBody = "",
        requestHeader = emptyMap(), requestBody = emptyMap(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
        ApiLogger.enableEventTrackerLog(false)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
        ApiLogger.enableEventTrackerLog(false)
    }

    @Test
    fun `status facet narrows the list`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/ok", code = "200"))
        ApiLogger.addLog(log("https://api.example.com/missing", code = "404"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(2, viewModel.items.size)

        viewModel.toggleStatus(StatusClass.CLIENT_ERROR)

        assertEquals(1, viewModel.items.size)
        assertEquals("https://api.example.com/missing", viewModel.items.single().url)
    }

    /** `4xx + 5xx` × `POST` — failed writes only. */
    @Test
    fun `facets AND across and OR within`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/a", code = "404", method = "POST"))
        ApiLogger.addLog(log("https://api.example.com/b", code = "500", method = "POST"))
        ApiLogger.addLog(log("https://api.example.com/c", code = "404", method = "GET"))
        ApiLogger.addLog(log("https://api.example.com/d", code = "200", method = "POST"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleStatus(StatusClass.CLIENT_ERROR)
        viewModel.toggleStatus(StatusClass.SERVER_ERROR)
        viewModel.toggleMethod("POST")

        assertEquals(
            listOf("https://api.example.com/b", "https://api.example.com/a"),
            viewModel.items.map { it.url },
        )
    }

    @Test
    fun `an in-flight entry is reachable through the pending facet only`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/done"))
        ApiLogger.beginLog(method = "POST", url = "https://api.example.com/slow")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleStatus(StatusClass.PENDING)
        assertEquals(listOf("https://api.example.com/slow"), viewModel.items.map { it.url })

        viewModel.toggleStatus(StatusClass.PENDING)
        viewModel.toggleStatus(StatusClass.FAILED)
        assertTrue(
            "an empty response code must not fall through to the failure bucket",
            viewModel.items.isEmpty(),
        )
    }

    @Test
    fun `filters combine with the search query`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/users", code = "404"))
        ApiLogger.addLog(log("https://api.example.com/orders", code = "404"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleStatus(StatusClass.CLIENT_ERROR)
        viewModel.onSearchChange("orders")
        advanceUntilIdle()

        assertEquals(listOf("https://api.example.com/orders"), viewModel.items.map { it.url })
    }

    @Test
    fun `menus offer only values present in the bucket`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/a", method = "get"))
        ApiLogger.addLog(log("https://cdn.example.com/b.png", method = "POST"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("GET", "POST"), viewModel.availableMethods)
        assertEquals(listOf("api.example.com", "cdn.example.com"), viewModel.availableHosts)
    }

    /** EventTracker `url`s are event names, so neither status nor host applies. */
    @Test
    fun `status and host facets are hidden for EventTracker`() = runTest(dispatcher) {
        ApiLogger.enableEventTrackerLog(true)
        ApiLogger.addEventTrackerLog(ApiLog.event("purchase_completed", emptyMap(), ""))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.switchTo(LogEventType.EVENT_TRACKER)

        assertFalse(viewModel.showsStatusFilter)
        assertTrue(viewModel.availableHosts.isEmpty())
    }

    /** A host from another tab would filter everything out with no visible cause. */
    @Test
    fun `switching bucket resets the filter`() = runTest(dispatcher) {
        ApiLogger.enableEventTrackerLog(true)
        ApiLogger.addLog(log("https://api.example.com/a"))
        ApiLogger.addEventTrackerLog(ApiLog.event("purchase_completed", emptyMap(), ""))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleHost("api.example.com")
        assertTrue(viewModel.filter.isActive)

        viewModel.switchTo(LogEventType.EVENT_TRACKER)

        assertFalse(viewModel.filter.isActive)
        assertEquals(1, viewModel.items.size)
    }

    @Test
    fun `clearing filters restores the whole bucket`() = runTest(dispatcher) {
        ApiLogger.addLog(log("https://api.example.com/a", code = "200"))
        ApiLogger.addLog(log("https://api.example.com/b", code = "404"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleStatus(StatusClass.SERVER_ERROR)
        assertTrue("this is the over-filtered empty state", viewModel.items.isEmpty())

        viewModel.clearFilters()

        assertEquals(2, viewModel.items.size)
    }

    @Test
    fun `live arrivals respect the active filter`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleStatus(StatusClass.SERVER_ERROR)

        ApiLogger.addLog(log("https://api.example.com/ok", code = "200"))
        advanceUntilIdle()
        assertTrue(viewModel.items.isEmpty())

        ApiLogger.addLog(log("https://api.example.com/boom", code = "503"))
        advanceUntilIdle()
        assertEquals(listOf("https://api.example.com/boom"), viewModel.items.map { it.url })
    }

    @Test
    fun `a pending row becomes a normal row when it completes`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/slow")
        advanceUntilIdle()
        val pendingId = viewModel.items.single().id

        ApiLogger.completeLog(
            token,
            log("https://api.example.com/slow", code = "200"),
        )
        advanceUntilIdle()

        val row = viewModel.items.single()
        assertEquals("the row must not be re-keyed", pendingId, row.id)
        assertEquals("200", row.responseCode)
    }
}

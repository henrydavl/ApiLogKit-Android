package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.model.LogSection
import com.henrydavl.apilogkit.ui.detail.ApiLogDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * The live detail screen: opening a row that is still in flight shows the request
 * side immediately and fills the response sections in when the response lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiLogDetailViewModelTest {

    private fun finished(url: String) = ApiLog(
        responseCode = "200", method = "POST", url = url, responseTime = "0.10", size = "2",
        date = Date(), responseHeader = mapOf("Content-Type" to "application/json"),
        responseBody = """{"token":"abc"}""",
        requestHeader = emptyMap(), requestBody = emptyMap(),
    )

    @Before
    fun setUp() {
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
    }

    @After
    fun tearDown() {
        ApiLogger.clearLogs()
        ApiLogger.isEnabled = true
    }

    @Test
    fun `a pending entry shows its request side`() {
        val pending = ApiLog.pending(
            method = "POST",
            url = "https://api.example.com/login",
            requestHeader = mapOf("Authorization" to "Bearer xyz"),
            requestBody = mapOf("username" to "henry"),
        )
        val viewModel = ApiLogDetailViewModel(pending, LogEventType.API)

        assertTrue(viewModel.isPending)
        assertEquals(
            listOf("https://api.example.com/login"),
            viewModel.rows(LogSection.REQUEST_URL).map { it.value },
        )
        assertEquals(
            listOf("Authorization"),
            viewModel.rows(LogSection.REQUEST_HEADER).map { it.key },
        )
        assertTrue(
            "nothing has come back yet",
            viewModel.rows(LogSection.RESPONSE_HEADER).isEmpty(),
        )
    }

    @Test
    fun `the response sections fill in when the entry completes`() = runTest {
        val token = ApiLogger.beginLog(method = "POST", url = "https://api.example.com/login")
        val pending = ApiLogger.getLogs().single()
        val viewModel = ApiLogDetailViewModel(pending, LogEventType.API)

        val watcher = launch { viewModel.awaitCompletion() }
        advanceUntilIdle()

        ApiLogger.completeLog(token, finished("https://api.example.com/login"))
        withTimeout(1_000) { watcher.join() }

        assertFalse(viewModel.isPending)
        assertEquals("200", viewModel.log.responseCode)
        assertEquals(
            listOf("Content-Type"),
            viewModel.rows(LogSection.RESPONSE_HEADER).map { it.key },
        )
        assertTrue(
            "the JSON tree is rebuilt from the response that arrived",
            viewModel.responseJson != null,
        )
    }

    /**
     * The subscription is meant to cost nothing on the common path — a screen
     * opened on a finished entry must not scan the bucket on every emission.
     */
    @Test
    fun `awaiting a finished entry returns without collecting`() = runTest {
        val viewModel = ApiLogDetailViewModel(finished("https://api.example.com/login"), LogEventType.API)

        withTimeout(1_000) { viewModel.awaitCompletion() }

        assertFalse(viewModel.isPending)
    }

    @Test
    fun `completion keeps the entry's identity`() = runTest {
        val token = ApiLogger.beginLog(method = "POST", url = "https://api.example.com/login")
        val pending = ApiLogger.getLogs().single()
        val viewModel = ApiLogDetailViewModel(pending, LogEventType.API)

        val watcher = launch { viewModel.awaitCompletion() }
        advanceUntilIdle()
        ApiLogger.completeLog(token, finished("https://api.example.com/login"))
        withTimeout(1_000) { watcher.join() }

        assertEquals(pending.id, viewModel.log.id)
    }
}

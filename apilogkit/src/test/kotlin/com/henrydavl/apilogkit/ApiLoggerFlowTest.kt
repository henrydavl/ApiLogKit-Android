package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/** Covers the reactive store that backs the live-updating log list. */
class ApiLoggerFlowTest {

    private fun log(url: String) = ApiLog(
        responseCode = "200", method = "GET", url = url, responseTime = "0", size = "0",
        date = Date(), responseHeader = emptyMap(), responseBody = "",
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
    fun `logsFlow exposes the current value without collecting`() {
        ApiLogger.addLog(log("a"))
        ApiLogger.addLog(log("b"))

        // StateFlow replays its current value, so a newly opened inspector fills
        // immediately rather than waiting for the next request.
        assertEquals(2, ApiLogger.logsFlow.value.size)
        assertEquals(listOf("a", "b"), ApiLogger.logsFlow.value.map { it.url })
    }

    @Test
    fun `logsFlow emits a new list instance per add`() {
        ApiLogger.addLog(log("a"))
        val first = ApiLogger.logsFlow.value
        ApiLogger.addLog(log("b"))
        val second = ApiLogger.logsFlow.value

        // StateFlow suppresses equal values, so the emitted lists must not be the
        // same instance mutated in place — otherwise collectors never fire.
        assertNotEquals(first, second)
        assertEquals(1, first.size)
        assertEquals(2, second.size)
    }

    @Test
    fun `event tracker logs stream on their own flow`() {
        ApiLogger.addEventTrackerLog(ApiLog.event("evt", emptyMap(), ""))

        assertEquals(1, ApiLogger.eventTrackerLogsFlow.value.size)
        assertTrue(ApiLogger.logsFlow.value.isEmpty())
    }

    @Test
    fun `clear empties both flows`() {
        ApiLogger.addLog(log("a"))
        ApiLogger.addEventTrackerLog(ApiLog.event("evt", emptyMap(), ""))

        ApiLogger.clearLogs()

        assertTrue(ApiLogger.logsFlow.value.isEmpty())
        assertTrue(ApiLogger.eventTrackerLogsFlow.value.isEmpty())
    }

    @Test
    fun `disabled logger emits nothing`() {
        ApiLogger.isEnabled = false
        ApiLogger.addLog(log("a"))
        assertTrue(ApiLogger.logsFlow.value.isEmpty())
    }

    @Test
    fun `each log gets a distinct row id`() {
        ApiLogger.addLog(log("same"))
        ApiLogger.addLog(log("same"))

        val ids = ApiLogger.logsFlow.value.map { it.id }
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `row id is excluded from data class equality`() {
        // Two logs with identical contents must still compare equal, so adding
        // identity for the list did not change ApiLog's value semantics.
        assertEquals(log("a"), log("a"))
    }

    @Test
    fun `concurrent adds are thread-safe`() {
        val threads = (1..8).map { t ->
            Thread { repeat(100) { ApiLogger.addLog(log("$t-$it")) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(800, ApiLogger.logsFlow.value.size)
        assertEquals(800, ApiLogger.logsFlow.value.map { it.id }.toSet().size)
    }
}

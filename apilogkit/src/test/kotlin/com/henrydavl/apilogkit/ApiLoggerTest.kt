package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class ApiLoggerTest {

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
    fun `add and get logs`() {
        ApiLogger.addLog(log("a"))
        ApiLogger.addLog(log("b"))
        assertEquals(2, ApiLogger.getLogs().size)
    }

    @Test
    fun `disabled logger ignores adds`() {
        ApiLogger.isEnabled = false
        ApiLogger.addLog(log("a"))
        assertEquals(0, ApiLogger.getLogs().size)
    }

    @Test
    fun `clear removes everything`() {
        ApiLogger.addLog(log("a"))
        ApiLogger.addEventTrackerLog(ApiLog.event("evt", emptyMap(), ""))
        ApiLogger.clearLogs()
        assertEquals(0, ApiLogger.getLogs().size)
        assertEquals(0, ApiLogger.getEventTrackerLogs().size)
    }

    @Test
    fun `concurrent adds are thread-safe`() {
        val threads = (1..8).map { t ->
            Thread { repeat(100) { ApiLogger.addLog(log("$t-$it")) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(800, ApiLogger.getLogs().size)
    }
}

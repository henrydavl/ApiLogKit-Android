package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.model.LogEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * The in-flight entry lifecycle — the Android counterpart of iOS's
 * `testCompleteLogPreservesIdentityStartTimeAndPosition`.
 *
 * Identity and start time are what fail *silently*: a completed entry that
 * re-keys its row tears the row down (jolting the list and popping any detail
 * screen opened from it), and one that takes the response's timestamp jumps to
 * the top of the list as it resolves. Both look like UI flakiness rather than a
 * logging bug, so they are pinned here.
 */
class ApiLoggerPendingTest {

    private fun finished(url: String, code: String = "200") = ApiLog(
        responseCode = code, method = "GET", url = url, responseTime = "0.10", size = "2",
        date = Date(), responseHeader = emptyMap(), responseBody = "{}",
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
    fun `beginLog inserts a pending entry immediately`() {
        ApiLogger.beginLog(method = "POST", url = "https://api.example.com/login")

        val logs = ApiLogger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(ApiLogState.PENDING, logs[0].state)
        assertEquals("POST", logs[0].method)
        assertEquals("", logs[0].responseCode)
    }

    @Test
    fun `completeLog fills the response side in place`() {
        val token = ApiLogger.beginLog(method = "POST", url = "https://api.example.com/login")
        ApiLogger.completeLog(token, finished("https://api.example.com/login", code = "201"))

        val logs = ApiLogger.getLogs()
        assertEquals("the entry is completed, not appended to", 1, logs.size)
        assertEquals(ApiLogState.FINISHED, logs[0].state)
        assertEquals("201", logs[0].responseCode)
    }

    @Test
    fun `completeLog preserves the row id`() {
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/a")
        val pendingId = ApiLogger.getLogs().single().id

        ApiLogger.completeLog(token, finished("https://api.example.com/a"))

        assertEquals(
            "a re-keyed row is torn down and rebuilt by LazyColumn",
            pendingId,
            ApiLogger.getLogs().single().id,
        )
    }

    /**
     * Guards the `copy()` landmine: [ApiLog.id] lives in the class body, so a
     * plain copy mints a brand new id. If this ever stops failing for a plain
     * copy, `completing` can be simplified.
     */
    @Test
    fun `plain copy would assign a new id`() {
        val log = finished("https://api.example.com/a")
        assertNotEquals(log.id, log.copy(responseCode = "500").id)
    }

    @Test
    fun `completeLog preserves the request start time`() {
        val startedAt = Date(1_000_000L)
        val token = ApiLogger.begin(
            ApiLog.pending(method = "GET", url = "https://api.example.com/slow", date = startedAt),
            LogEventType.API,
        )
        val respondedAt = Date(9_000_000L)
        ApiLogger.completeLog(
            token,
            finished("https://api.example.com/slow").copy(date = respondedAt),
        )

        assertEquals(
            "keeping the start time is what holds the row in its chronological slot",
            startedAt,
            ApiLogger.getLogs().single().date,
        )
    }

    @Test
    fun `a completing entry keeps its position in the list`() {
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/slow")
        ApiLogger.addLog(finished("https://api.example.com/fast"))

        ApiLogger.completeLog(token, finished("https://api.example.com/slow"))

        val logs = ApiLogger.getLogs()
        assertEquals(2, logs.size)
        assertEquals(
            "the slow request stays where it was inserted rather than jumping to the end",
            "https://api.example.com/slow",
            logs[0].url,
        )
        assertEquals("https://api.example.com/fast", logs[1].url)
    }

    /** `fromPreviousSession` is a restore-path flag; completion must not set it. */
    @Test
    fun `a completed entry is not marked as coming from a previous session`() {
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/a")
        ApiLogger.completeLog(token, finished("https://api.example.com/a"))

        assertFalse(ApiLogger.getLogs().single().fromPreviousSession)
    }

    @Test
    fun `completing a cleared entry is a no-op`() {
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/a")
        ApiLogger.clearLogs()

        ApiLogger.completeLog(token, finished("https://api.example.com/a"))

        assertEquals(
            "a token outliving its entry must not resurrect it",
            0,
            ApiLogger.getLogs().size,
        )
    }

    @Test
    fun `addLog entries default to finished`() {
        ApiLogger.addLog(finished("https://api.example.com/a"))
        assertEquals(
            "existing call sites must keep behaving exactly as before",
            ApiLogState.FINISHED,
            ApiLogger.getLogs().single().state,
        )
    }

    @Test
    fun `disabled logger ignores begin and complete`() {
        ApiLogger.isEnabled = false
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/a")
        ApiLogger.completeLog(token, finished("https://api.example.com/a"))

        assertEquals(0, ApiLogger.getLogs().size)
    }

    @Test
    fun `completing does not disturb other entries`() {
        ApiLogger.addLog(finished("https://api.example.com/first"))
        val first = ApiLogger.getLogs().single()
        val token = ApiLogger.beginLog(method = "GET", url = "https://api.example.com/second")
        ApiLogger.completeLog(token, finished("https://api.example.com/second"))

        assertSame(first, ApiLogger.getLogs()[0])
    }
}

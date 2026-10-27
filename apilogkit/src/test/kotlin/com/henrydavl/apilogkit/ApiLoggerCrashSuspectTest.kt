package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Which entries a crash gets pinned to.
 *
 * This is the judgement call the whole feature rests on: get it wrong and the
 * inspector points confidently at the wrong response, which is worse than
 * pointing at nothing. The rules — everything in flight, plus the newest
 * finished entry, and never anything restored from an earlier run — are pinned
 * here because they are invisible until you are already debugging a crash.
 */
class ApiLoggerCrashSuspectTest {

    private fun finished(url: String, code: String = "200") = ApiLog(
        responseCode = code, method = "GET", url = url, responseTime = "0.10", size = "2",
        date = Date(), responseHeader = emptyMap(), responseBody = "{}",
        requestHeader = emptyMap(), requestBody = emptyMap(),
    )

    private fun restored(url: String) = ApiLog(
        responseCode = "200", method = "GET", url = url, responseTime = "0.10", size = "2",
        date = Date(), responseHeader = emptyMap(), responseBody = "{}",
        requestHeader = emptyMap(), requestBody = emptyMap(),
        fromPreviousSession = true,
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
    fun `no logs means nothing to blame`() {
        assertTrue(ApiLogger.crashSuspectIds().isEmpty())
    }

    /**
     * A crash that lands milliseconds after a response is almost always the app
     * choking on that payload, so the newest finished entry is a suspect even
     * though it completed cleanly.
     */
    @Test
    fun `the newest finished entry is blamed`() {
        ApiLogger.addLog(finished("https://api.example.com/first"))
        ApiLogger.addLog(finished("https://api.example.com/second"))
        val newest = ApiLogger.getLogs().last()

        assertEquals(listOf(newest.id), ApiLogger.crashSuspectIds())
    }

    /**
     * An in-flight request leaves no other trace at all — nothing came back, so
     * nothing else can explain the failure.
     */
    @Test
    fun `every in-flight entry is blamed, alongside the last finished one`() {
        ApiLogger.addLog(finished("https://api.example.com/done"))
        ApiLogger.beginLog(method = "POST", url = "https://api.example.com/slow")
        ApiLogger.beginLog(method = "POST", url = "https://api.example.com/slower")

        val logs = ApiLogger.getLogs()
        val suspects = ApiLogger.crashSuspectIds()

        assertEquals(3, suspects.size)
        assertTrue(logs.all { it.id in suspects })
    }

    /**
     * The one that would otherwise mislead: with persistence on, a quiet session
     * that crashes without issuing a single request would see yesterday's last
     * response blamed for today's crash.
     */
    @Test
    fun `entries restored from an earlier run are never blamed`() {
        ApiLogger.addLog(restored("https://api.example.com/yesterday"))

        assertTrue(ApiLogger.crashSuspectIds().isEmpty())
    }

    @Test
    fun `a restored entry does not displace this session's own suspect`() {
        ApiLogger.addLog(restored("https://api.example.com/yesterday"))
        ApiLogger.addLog(finished("https://api.example.com/today"))
        val today = ApiLogger.getLogs().last()

        assertEquals(listOf(today.id), ApiLogger.crashSuspectIds())
    }

    /**
     * EventTracker entries are synthesised locally and carry no server payload,
     * so there is nothing in one to be wrong.
     */
    @Test
    fun `event tracker entries are never blamed`() {
        ApiLogger.addEventTrackerLog(
            ApiLog.event("checkout_tapped", requestBody = emptyMap(), responseBody = ""),
        )

        assertTrue(ApiLogger.crashSuspectIds().isEmpty())
    }

    /** A single entry that is both newest and in flight must not be listed twice. */
    @Test
    fun `suspects are deduplicated`() {
        ApiLogger.beginLog(method = "POST", url = "https://api.example.com/only")

        assertEquals(1, ApiLogger.crashSuspectIds().size)
    }
}

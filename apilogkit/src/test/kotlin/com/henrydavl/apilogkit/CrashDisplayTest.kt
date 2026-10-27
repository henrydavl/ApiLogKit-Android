package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.export.ApiLogExporter
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.CrashReport
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.model.LogSection
import com.henrydavl.apilogkit.ui.detail.ApiLogDetailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * How a crash-linked entry presents itself: the extra detail section, the export
 * block, and the restored entry that can never complete.
 */
class CrashDisplayTest {

    private val crash = CrashReport(
        date = Date(0),
        threadName = "main",
        exceptionClass = "java.lang.IllegalStateException",
        message = "Expected BEGIN_OBJECT but was STRING",
        stackTrace = "java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING\n" +
            "\tat com.example.Mapper.map(Mapper.kt:42)\n",
    )

    private fun log(
        crash: CrashReport? = null,
        state: ApiLogState = ApiLogState.FINISHED,
        fromPreviousSession: Boolean = false,
    ) = ApiLog(
        responseCode = if (state == ApiLogState.PENDING) "" else "200",
        method = "GET",
        url = "https://api.example.com/profile",
        responseTime = "0.10",
        size = "2",
        date = Date(0),
        responseHeader = emptyMap(),
        responseBody = """{"name":"ada"}""",
        requestHeader = emptyMap(),
        requestBody = emptyMap(),
        fromPreviousSession = fromPreviousSession,
        state = state,
        crash = crash,
    )

    // MARK: - Detail sections

    @Test
    fun `an entry without a crash has no crash section`() {
        val viewModel = ApiLogDetailViewModel(log(), LogEventType.API)

        assertFalse(LogSection.CRASH in viewModel.sections)
    }

    /** First, because on that row the stack trace is what you opened it for. */
    @Test
    fun `the crash section comes first`() {
        val viewModel = ApiLogDetailViewModel(log(crash = crash), LogEventType.API)

        assertEquals(LogSection.CRASH, viewModel.sections.first())
    }

    @Test
    fun `the crash section carries the summary and the trace`() {
        val viewModel = ApiLogDetailViewModel(log(crash = crash), LogEventType.API)

        val rows = viewModel.rows(LogSection.CRASH)

        assertEquals("IllegalStateException: Expected BEGIN_OBJECT but was STRING", rows[0].value)
        assertEquals("Thread", rows[1].key)
        assertEquals("main", rows[1].value)
        assertEquals("Time", rows[2].key)
        assertEquals("Stack Trace", rows[3].key)
        assertTrue(rows[3].value.contains("Mapper.kt:42"))
    }

    /** A stack trace is as column-aligned as pretty-printed JSON. */
    @Test
    fun `the crash section renders monospaced`() {
        val viewModel = ApiLogDetailViewModel(log(crash = crash), LogEventType.API)

        assertTrue(viewModel.isMonospaced(LogSection.CRASH))
    }

    @Test
    fun `copying the crash section yields the full report`() {
        val viewModel = ApiLogDetailViewModel(log(crash = crash), LogEventType.API)

        val copied = viewModel.copyValue(LogSection.CRASH)

        assertTrue(copied.contains("IllegalStateException"))
        assertTrue(copied.contains("Thread: main"))
        assertTrue(copied.contains("Mapper.kt:42"))
    }

    // MARK: - Restored pending entries

    /**
     * The spinner would never stop: that request's process is gone, so no
     * response can arrive to clear it.
     */
    @Test
    fun `a restored in-flight entry reads as unfinished, not pending`() {
        val viewModel = ApiLogDetailViewModel(
            log(state = ApiLogState.PENDING, fromPreviousSession = true),
            LogEventType.API,
        )

        assertFalse(viewModel.isPending)
        assertTrue(viewModel.isUnfinished)
    }

    @Test
    fun `a live in-flight entry still reads as pending`() {
        val viewModel = ApiLogDetailViewModel(log(state = ApiLogState.PENDING), LogEventType.API)

        assertTrue(viewModel.isPending)
        assertFalse(viewModel.isUnfinished)
    }

    // MARK: - Export

    /**
     * One file with the payload and the stack trace in it is what gets pasted
     * into the bug report, so the crash leads the raw log.
     */
    @Test
    fun `a crash-linked raw log leads with the crash block`() {
        val text = ApiLogExporter.rawLog(log(crash = crash))

        assertTrue(text.startsWith("---------- Crash\n"))
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.contains("Mapper.kt:42"))
        // The log itself still follows, unchanged.
        assertTrue(text.contains("---------- Response Body"))
        assertTrue(text.contains("""{"name":"ada"}"""))
    }

    /** Parity: an entry with no crash exports exactly as it did before. */
    @Test
    fun `a raw log without a crash is unchanged`() {
        val text = ApiLogExporter.rawLog(log())

        assertTrue(text.startsWith("https://api.example.com/profile"))
        assertFalse(text.contains("Crash"))
    }
}

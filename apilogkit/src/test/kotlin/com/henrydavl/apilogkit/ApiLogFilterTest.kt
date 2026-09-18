package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogFilter
import com.henrydavl.apilogkit.model.StatusClass
import com.henrydavl.apilogkit.model.host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Mirrors the iOS `ApiLogFilterTests`. Weighted towards the classifications that
 * fail silently — a failed call bucketed as a success looks like working traffic.
 */
class ApiLogFilterTest {

    private fun log(
        url: String = "https://api.example.com/v1/users",
        code: String = "200",
        method: String = "GET",
    ) = ApiLog(
        responseCode = code, method = method, url = url, responseTime = "0.10", size = "12",
        date = Date(), responseHeader = emptyMap(), responseBody = "{}",
        requestHeader = emptyMap(), requestBody = emptyMap(),
    )

    // MARK: - Status classification

    @Test
    fun `status codes map to their class`() {
        assertEquals(StatusClass.SUCCESS, StatusClass.of("204"))
        assertEquals(StatusClass.REDIRECT, StatusClass.of("301"))
        assertEquals(StatusClass.CLIENT_ERROR, StatusClass.of("404"))
        assertEquals(StatusClass.SERVER_ERROR, StatusClass.of("503"))
    }

    /**
     * ApiLogInterceptor stores "ERR" for calls that never reached a server, and
     * EventTracker entries carry the "00" sentinel; none of them may read as 2xx.
     */
    @Test
    fun `unreachable calls and sentinels classify as failed`() {
        assertEquals("OkHttp failure", StatusClass.FAILED, StatusClass.of("ERR"))
        assertEquals("EventTracker sentinel", StatusClass.FAILED, StatusClass.of("00"))
        assertEquals(StatusClass.FAILED, StatusClass.of("-1"))
        assertEquals(StatusClass.FAILED, StatusClass.of("0"))
        assertEquals(StatusClass.FAILED, StatusClass.of(""))
        assertEquals(StatusClass.FAILED, StatusClass.of("not a number"))
        assertEquals("above the HTTP range", StatusClass.FAILED, StatusClass.of("600"))
    }

    @Test
    fun `pending entry classifies as pending not failed`() {
        val pending = ApiLog.pending(method = "POST", url = "https://api.example.com/login")
        assertEquals(
            "an in-flight entry has an empty code but must not be bucketed as a failure",
            StatusClass.PENDING,
            StatusClass.of(pending),
        )
    }

    // MARK: - Matching

    @Test
    fun `empty filter matches everything`() {
        val filter = ApiLogFilter()
        assertFalse(filter.isActive)
        assertTrue(filter.matches(log()))
        assertTrue(filter.matches(log(code = "500")))
    }

    @Test
    fun `status facet matches any selected`() {
        val filter = ApiLogFilter(statuses = setOf(StatusClass.CLIENT_ERROR, StatusClass.SERVER_ERROR))

        assertTrue(filter.matches(log(code = "404")))
        assertTrue(filter.matches(log(code = "500")))
        assertFalse(filter.matches(log(code = "200")))
    }

    @Test
    fun `method facet is case insensitive`() {
        val filter = ApiLogFilter().toggleMethod("post")

        assertTrue(filter.matches(log(method = "POST")))
        assertTrue(filter.matches(log(method = "post")))
        assertFalse(filter.matches(log(method = "GET")))
    }

    @Test
    fun `host facet matches exact host`() {
        val filter = ApiLogFilter(hosts = setOf("api.example.com"))

        assertTrue(filter.matches(log(url = "https://api.example.com/v1/users")))
        assertFalse(filter.matches(log(url = "https://cdn.example.com/img.png")))
    }

    /** EventTracker entries hold an event name in `url`, so they have no host. */
    @Test
    fun `host facet excludes entries without a host`() {
        val filter = ApiLogFilter(hosts = setOf("api.example.com"))
        val event = ApiLog.event("purchase_completed", emptyMap(), "")

        assertNull(event.host)
        assertFalse(filter.matches(event))
    }

    /** Facets combine with AND: an entry has to satisfy every active one. */
    @Test
    fun `facets combine with AND`() {
        val filter = ApiLogFilter(
            statuses = setOf(StatusClass.CLIENT_ERROR),
            methods = setOf("POST"),
        )

        assertTrue(filter.matches(log(code = "404", method = "POST")))
        assertFalse("matching status alone is not enough", filter.matches(log(code = "404", method = "GET")))
        assertFalse("matching method alone is not enough", filter.matches(log(code = "200", method = "POST")))
    }

    /** The settled example: 4xx + 5xx × POST is "failed writes only". */
    @Test
    fun `OR within a facet combines with AND across facets`() {
        val filter = ApiLogFilter()
            .toggleStatus(StatusClass.CLIENT_ERROR)
            .toggleStatus(StatusClass.SERVER_ERROR)
            .toggleMethod("POST")

        assertTrue(filter.matches(log(code = "404", method = "POST")))
        assertTrue(filter.matches(log(code = "502", method = "POST")))
        assertFalse(filter.matches(log(code = "404", method = "GET")))
        assertFalse(filter.matches(log(code = "201", method = "POST")))
    }

    // MARK: - Toggling

    @Test
    fun `toggle adds then removes`() {
        var filter = ApiLogFilter()

        filter = filter.toggleStatus(StatusClass.SUCCESS)
        assertEquals(setOf(StatusClass.SUCCESS), filter.statuses)
        assertTrue(filter.isActive)

        filter = filter.toggleStatus(StatusClass.SUCCESS)
        assertTrue(filter.statuses.isEmpty())
        assertFalse(filter.isActive)
    }

    @Test
    fun `active count sums every facet`() {
        val filter = ApiLogFilter()
            .toggleStatus(StatusClass.SUCCESS)
            .toggleStatus(StatusClass.CLIENT_ERROR)
            .toggleMethod("GET")
            .toggleHost("api.example.com")

        assertEquals(4, filter.activeCount)
    }

    @Test
    fun `host is lowercased on both sides`() {
        val filter = ApiLogFilter().toggleHost("API.Example.COM")

        assertEquals(setOf("api.example.com"), filter.hosts)
        assertTrue(filter.matches(log(url = "https://API.Example.COM/v1/users")))
    }
}

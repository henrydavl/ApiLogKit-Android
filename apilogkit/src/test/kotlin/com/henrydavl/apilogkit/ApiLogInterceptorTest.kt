package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.interceptor.ApiLogInterceptor
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.ApiLogger
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The interceptor's pending → finished round trip.
 *
 * The failure worth pinning is a *duplicate*: recording the pending row and then
 * calling `addLog` on the way back out would leave two rows per request, one of
 * them stuck in flight forever. Both paths here therefore assert the count.
 */
class ApiLogInterceptorTest {

    private val interceptor = ApiLogInterceptor()

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

    private fun request(url: String = "https://api.example.com/v1/login") = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer xyz")
        .post("""{"username":"henry"}""".toRequestBody("application/json".toMediaType()))
        .build()

    @Test
    fun `a completed call leaves exactly one finished row`() {
        val response = interceptor.intercept(
            FakeChain(request()) { req ->
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .header("Content-Type", "application/json")
                    .body("""{"token":"abc"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        response.close()

        val log = ApiLogger.getLogs().single()
        assertEquals(ApiLogState.FINISHED, log.state)
        assertEquals("201", log.responseCode)
        assertEquals("the request side survives the completion", "henry", log.requestBody["username"])
    }

    @Test
    fun `a failing call completes the pending row rather than appending a second`() {
        val chain = FakeChain(request()) { throw IOException("connection reset") }

        try {
            interceptor.intercept(chain)
            throw AssertionError("the interceptor must rethrow")
        } catch (e: IOException) {
            assertEquals("connection reset", e.message)
        }

        val log = ApiLogger.getLogs().single()
        assertEquals(
            "a failure left pending would sit in flight forever",
            ApiLogState.FINISHED,
            log.state,
        )
        assertEquals("ERR", log.responseCode)
    }

    /**
     * The point of the whole feature: the row exists while the call is still out.
     * The fake chain inspects the logger from *inside* `proceed`.
     */
    @Test
    fun `the row is pending while the call is in flight`() {
        var stateDuringCall: ApiLogState? = null

        val response = interceptor.intercept(
            FakeChain(request()) { req ->
                stateDuringCall = ApiLogger.getLogs().single().state
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        response.close()

        assertEquals(ApiLogState.PENDING, stateDuringCall)
        assertEquals(ApiLogState.FINISHED, ApiLogger.getLogs().single().state)
    }

    @Test
    fun `a disabled logger records nothing`() {
        ApiLogger.isEnabled = false

        interceptor.intercept(
            FakeChain(request()) { req ->
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(null))
                    .build()
            },
        ).close()

        assertTrue(ApiLogger.getLogs().isEmpty())
    }

    /**
     * Stands in for OkHttp's real chain. Only `request()` and `proceed()` are
     * reachable from the interceptor; the rest exist to satisfy the interface.
     */
    private class FakeChain(
        private val request: Request,
        private val onProceed: (Request) -> Response,
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = onProceed(request)
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }
}

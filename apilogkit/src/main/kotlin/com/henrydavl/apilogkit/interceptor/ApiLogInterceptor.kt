package com.henrydavl.apilogkit.interceptor

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.util.Date

/**
 * Drop-in OkHttp interceptor that records every request/response into
 * [ApiLogger] — the idiomatic Android auto-capture path (a Chucker replacement),
 * complementing the manual [ApiLogger.addLog] API that mirrors iOS.
 *
 * Add it to your client and you're done:
 *
 *     val client = OkHttpClient.Builder()
 *         .addInterceptor(ApiLogInterceptor())
 *         .build()
 *
 * Capture is skipped entirely when [ApiLogger.isEnabled] is false, so it is safe
 * to leave installed and gate on build type. Requires the host app to provide
 * OkHttp (this library declares it `compileOnly`).
 *
 * @param maxContentLength response bodies larger than this (bytes) are truncated
 *                         when peeked, to bound memory use.
 */
class ApiLogInterceptor(
    private val maxContentLength: Long = DEFAULT_MAX_CONTENT_LENGTH,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!ApiLogger.isEnabled) {
            return chain.proceed(request)
        }

        val startNanos = System.nanoTime()
        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            // Record the failure as a synthetic entry, then rethrow.
            ApiLogger.addLog(failureLog(request, e, startNanos))
            throw e
        }
        val elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0

        // peekBody does not consume the real stream the caller will read.
        val peek = response.peekBody(maxContentLength)
        val responseBody = peek.string()

        ApiLogger.addLog(
            ApiLog(
                responseCode = response.code.toString(),
                method = request.method,
                url = request.url.toString(),
                responseTime = String.format("%.2f", elapsedSeconds),
                size = responseBody.toByteArray().size.toString(),
                date = Date(),
                responseHeader = response.headers.toApiMap(),
                responseBody = responseBody,
                requestHeader = request.headers.toApiMap(),
                requestBody = request.bodyToMap(),
            ),
        )

        return response
    }

    private fun failureLog(request: Request, error: Exception, startNanos: Long): ApiLog {
        val elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0
        return ApiLog(
            responseCode = "ERR",
            method = request.method,
            url = request.url.toString(),
            responseTime = String.format("%.2f", elapsedSeconds),
            size = "0",
            date = Date(),
            responseHeader = emptyMap(),
            responseBody = error.message ?: error.toString(),
            requestHeader = request.headers.toApiMap(),
            requestBody = request.bodyToMap(),
        )
    }

    private fun Headers.toApiMap(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }

    /**
     * Best-effort conversion of a request body to a key/value map so the detail
     * screen's JSON tree can render it: form fields map directly, a JSON object
     * body is parsed, and anything else lands under a single "body" key.
     */
    private fun Request.bodyToMap(): Map<String, Any?> {
        val body = body ?: return emptyMap()

        if (body is FormBody) {
            val map = LinkedHashMap<String, Any?>()
            for (i in 0 until body.size) {
                map[body.name(i)] = body.value(i)
            }
            return map
        }

        val raw = try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (_: Exception) {
            return emptyMap()
        }

        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val map = LinkedHashMap<String, Any?>()
                for (key in json.keys()) {
                    map[key] = json.get(key)
                }
                return map
            } catch (_: Exception) {
                // fall through to raw
            }
        }

        return if (raw.isEmpty()) emptyMap() else mapOf("body" to raw)
    }

    private companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH = 250_000L
    }
}

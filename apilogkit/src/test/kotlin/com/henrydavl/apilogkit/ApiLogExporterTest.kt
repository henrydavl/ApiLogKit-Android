package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.export.ApiLogExporter
import com.henrydavl.apilogkit.model.ApiLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ApiLogExporterTest {

    private fun sampleLog() = ApiLog(
        responseCode = "200",
        method = "POST",
        url = "https://api.example.com/v1/login",
        responseTime = "0.42",
        size = "2048",
        date = Date(0),
        responseHeader = mapOf("Content-Type" to "application/json"),
        responseBody = """{"token":"abc"}""",
        requestHeader = mapOf("Authorization" to "Bearer xyz"),
        requestBody = mapOf("username" to "henry"),
    )

    @Test
    fun `curl contains method url headers and data`() {
        val curl = ApiLogExporter.curl(sampleLog())
        assertTrue(curl.startsWith("curl -X POST"))
        assertTrue(curl.contains("'https://api.example.com/v1/login'"))
        assertTrue(curl.contains("-H 'Authorization: Bearer xyz'"))
        assertTrue(curl.contains("--data 'username=henry'"))
        assertTrue(!curl.endsWith(" \\"))
    }

    @Test
    fun `raw log contains section markers`() {
        val raw = ApiLogExporter.rawLog(sampleLog())
        assertTrue(raw.contains("---------- Request Header"))
        assertTrue(raw.contains("---------- Request Body"))
        assertTrue(raw.contains("---------- Response Header"))
        assertTrue(raw.contains("---------- Response Body"))
        assertTrue(raw.contains("======================>>>>>>>"))
    }
}

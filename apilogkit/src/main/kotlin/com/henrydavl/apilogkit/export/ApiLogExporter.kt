package com.henrydavl.apilogkit.export

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.util.jsonize

/**
 * Text exporters for log entries. Output formats match the iOS
 * `ApiLogExporter` byte-for-byte so a raw log or cURL produced on either
 * platform reads identically.
 */
object ApiLogExporter {

    /** Human-readable dump of a single log entry. */
    fun rawLog(log: ApiLog): String {
        val output = StringBuilder()
        output.append("${log.url}\n\n")

        output.append("---------- Request Header\n")
        for ((key, value) in log.requestHeader) {
            output.append("$key: ${value.toString().jsonize()}\n\n")
        }

        output.append("---------- Request Body\n")
        for ((_, value) in log.requestBody) {
            output.append("${value.toString().jsonize()}\n")
        }

        output.append("\n---------- Response Header\n")
        for ((key, value) in log.responseHeader) {
            output.append("$key: ${value.toString().jsonize()}\n\n")
        }

        output.append("---------- Response Body\n")
        output.append("${log.responseBody}\n\n")
        output.append("======================>>>>>>>\n\n\n")
        return output.toString()
    }

    /** `curl` command reproducing the request. */
    fun curl(log: ApiLog): String {
        val curl = StringBuilder()
        curl.append("curl -X ${log.method} \\\n")
        curl.append("  '${log.url}' \\\n")

        for ((key, value) in log.requestHeader) {
            val headerValue = value.toString().replace("'", "\\'")
            curl.append("  -H '$key: $headerValue' \\\n")
        }

        if (log.requestBody.isNotEmpty()) {
            for ((key, value) in log.requestBody) {
                val formValue = value.toString().replace("'", "\\'")
                curl.append("  --data '$key=$formValue' \\\n")
            }
        }

        var result = curl.toString()
        if (result.endsWith(" \\\n")) {
            result = result.dropLast(3)
        }
        return result
    }

    /** Raw textual dump of every supplied log (used by the list "Export" action). */
    fun rawLogs(logs: List<ApiLog>): String =
        logs.joinToString(separator = "") { rawLog(it) }
}

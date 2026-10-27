package com.henrydavl.apilogkit.export

import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.CrashReport
import com.henrydavl.apilogkit.util.apiLogFormatted
import com.henrydavl.apilogkit.util.jsonize

/**
 * Text exporters for log entries. Output formats match the iOS
 * `ApiLogExporter` byte-for-byte so a raw log or cURL produced on either
 * platform reads identically.
 *
 * The one addition is the crash block [rawLog] prepends for a crash-linked
 * entry. Parity is unaffected in practice: iOS has no crash capture, so no entry
 * it can produce carries a [CrashReport], and every entry that doesn't exports
 * exactly as before. Keeping the payload and the stack trace in one file is
 * worth it — that file is what gets pasted into the bug report.
 */
object ApiLogExporter {

    /** Human-readable dump of a single log entry. */
    fun rawLog(log: ApiLog): String {
        val output = StringBuilder()
        log.crash?.let { output.append(crashReport(it)) }
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

    /**
     * The crash block prepended to a crash-linked entry's raw log, and the text
     * the detail screen's **Crash** section copies.
     *
     * Front-loaded with the headline, thread and time so the first three lines
     * answer "what died, where, when"; the full trace, causes included, follows.
     */
    fun crashReport(crash: CrashReport): String {
        val output = StringBuilder()
        output.append("---------- Crash\n")
        output.append("${crash.headline}\n")
        output.append("Thread: ${crash.threadName}\n")
        output.append("Time: ${crash.date.apiLogFormatted()}\n\n")
        output.append(crash.stackTrace.trimEnd())
        output.append("\n\n")
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

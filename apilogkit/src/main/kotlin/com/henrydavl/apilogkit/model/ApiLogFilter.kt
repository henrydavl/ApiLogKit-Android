package com.henrydavl.apilogkit.model

import java.net.URI

/**
 * Status-code buckets offered by the status filter. Compose port of the iOS
 * `StatusClass`; the colours it mirrors live in
 * [com.henrydavl.apilogkit.ui.theme.ApiLogColors.statusColor].
 */
enum class StatusClass(val title: String) {
    PENDING("Pending"),
    SUCCESS("2xx Success"),
    REDIRECT("3xx Redirect"),
    CLIENT_ERROR("4xx Client Error"),
    SERVER_ERROR("5xx Server Error"),
    FAILED("Failed / No response"),
    ;

    companion object {

        /**
         * Classifies an entry. In-flight entries have no meaningful code yet, so
         * the state is checked before the code.
         */
        fun of(log: ApiLog): StatusClass =
            if (log.state == ApiLogState.PENDING) PENDING else of(log.responseCode)

        /**
         * Classifies a raw [ApiLog.responseCode].
         *
         * Anything unparseable or outside the HTTP range lands in [FAILED]:
         * [com.henrydavl.apilogkit.interceptor.ApiLogInterceptor] stores `"ERR"`
         * for calls that never reached the server, and EventTracker entries carry
         * the `"00"` sentinel — neither may read as a success.
         */
        fun of(code: String): StatusClass {
            val value = code.toIntOrNull() ?: return FAILED
            return when (value) {
                in 200..299 -> SUCCESS
                in 300..399 -> REDIRECT
                in 400..499 -> CLIENT_ERROR
                in 500..599 -> SERVER_ERROR
                else -> FAILED
            }
        }
    }
}

/**
 * Filtering state for the log list. Compose port of the iOS `ApiLogFilter`.
 *
 * Each facet is a set: empty means "don't filter on this", non-empty means
 * "match any of these" — so facets combine with AND between them and OR within
 * them. Selecting `4xx + 5xx` and `POST` therefore yields failed writes only.
 */
data class ApiLogFilter(
    val statuses: Set<StatusClass> = emptySet(),
    val methods: Set<String> = emptySet(),
    val hosts: Set<String> = emptySet(),
) {

    val isActive: Boolean
        get() = statuses.isNotEmpty() || methods.isNotEmpty() || hosts.isNotEmpty()

    /** Total number of selected facets, used for the "Clear" affordance. */
    val activeCount: Int
        get() = statuses.size + methods.size + hosts.size

    fun matches(log: ApiLog): Boolean {
        if (statuses.isNotEmpty() && StatusClass.of(log) !in statuses) return false
        if (methods.isNotEmpty() && log.method.uppercase() !in methods) return false
        // Checked last so the URL parse in [ApiLog.host] only runs when the host
        // facet is actually in use — `reload()` refilters the whole bucket on
        // every incoming log.
        if (hosts.isNotEmpty() && log.host !in hosts) return false
        return true
    }

    fun toggleStatus(status: StatusClass): ApiLogFilter = copy(statuses = statuses.toggled(status))

    fun toggleMethod(method: String): ApiLogFilter = copy(methods = methods.toggled(method.uppercase()))

    fun toggleHost(host: String): ApiLogFilter = copy(hosts = hosts.toggled(host.lowercase()))

    private fun <T> Set<T>.toggled(value: T): Set<T> =
        if (contains(value)) this - value else this + value
}

/**
 * Host component of the logged URL, lowercased.
 *
 * Null for EventTracker entries, whose [ApiLog.url] holds an event name rather
 * than a real URL. [URI] rather than `android.net.Uri` so this stays testable on
 * the JVM, and it throws on malformed input where `Uri` would silently return
 * something — hence the `runCatching`.
 */
val ApiLog.host: String?
    get() = runCatching { URI(url).host?.lowercase() }.getOrNull()

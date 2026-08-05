package com.henrydavl.apilogkit

import android.content.Context
import com.henrydavl.apilogkit.model.ApiLogger
import java.util.Locale

/**
 * Host-app integration points. Everything is optional; the library works out of
 * the box with defaults. Counterpart of the iOS `ApiLogKitConfig`.
 */
object ApiLogKitConfig {

    /** Locale used when formatting log dates (row timestamps). */
    @JvmField
    var dateLocale: Locale = Locale.getDefault()

    /**
     * Optional, XML-friendly "Developer Options" hook.
     *
     * When set, the log list's overflow menu shows a [DeveloperOptions.label]
     * entry. Selecting it invokes [DeveloperOptions.onSelected] with a [Context],
     * letting the host launch its own screen — typically a plain XML-based
     * Activity. This deliberately does NOT require Compose, so apps whose dev
     * options are still built in XML can plug straight in.
     *
     *     ApiLogKitConfig.developerOptions = ApiLogKitConfig.DeveloperOptions(
     *         label = "Developer Options",
     *         onSelected = { context ->
     *             context.startActivity(Intent(context, MyDevOptionsActivity::class.java))
     *         },
     *     )
     */
    @JvmField
    var developerOptions: DeveloperOptions? = null

    class DeveloperOptions(
        val label: String = "Developer Options",
        val onSelected: (Context) -> Unit,
    )

    /**
     * Opt-in disk persistence, so captured logs survive the host app being
     * killed and are still there to export — or to compare against a later call
     * to the same endpoint — on the next launch.
     *
     *     // in Application.onCreate()
     *     if (BuildConfig.DEBUG) {
     *         ApiLogKitConfig.persistence = ApiLogKitConfig.Persistence(maxEntries = 500)
     *     }
     *
     * Null (the default) keeps the original behaviour: logs live in memory only
     * and vanish with the process, matching iOS. **Leave it off in release
     * builds** — enabling it writes request and response bodies, and therefore
     * any auth tokens or personal data they contain, to app-private storage.
     *
     * Restored entries are flagged with [com.henrydavl.apilogkit.model.ApiLog.fromPreviousSession]
     * and labelled in the list so old runs are distinguishable from the current
     * one. `ApiLogger.clearLogs()` wipes memory and disk together.
     *
     * Safe to set at any point; it takes effect immediately. Setting it back to
     * null stops further writing but keeps what is already stored.
     */
    @JvmStatic
    var persistence: Persistence? = null
        set(value) {
            field = value
            ApiLogger.applyPersistence(value)
        }

    /**
     * @param maxEntries how many of the newest entries to keep on disk, counted
     *   separately for API logs and event-tracker logs. Older rows are pruned
     *   automatically. Restored entries are also held in memory, so a very large
     *   cap combined with large response bodies costs heap on the next launch.
     */
    class Persistence(
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ) {
        companion object {
            const val DEFAULT_MAX_ENTRIES = 500
        }
    }
}

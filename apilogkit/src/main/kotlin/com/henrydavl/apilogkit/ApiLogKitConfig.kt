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
     * killed — including by a crash, where the response that caused it is pinned
     * to the crash's stack trace and waiting in the inspector on the next launch
     * (see [Persistence.captureCrashes]).
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
     * @param captureCrashes whether an uncaught exception is recorded and pinned
     *   to the entries that were live when it happened — on by default, because
     *   it is what makes persistence useful for debugging a crash rather than
     *   only for reading old traffic.
     *
     *   Persistence alone is not enough to survive a crash. Writes are queued on
     *   a background thread, and the response that triggers a crash is usually
     *   still in that queue when the process dies. With this on, the handler
     *   blocks until the queue has reached disk, then records the crash and tags
     *   the suspect entries, so the next launch opens with the payload and the
     *   stack trace side by side.
     *
     *   The host's own crash reporter is unaffected: whatever handler was already
     *   installed is always called afterwards. Turn this off if you would rather
     *   nothing of ApiLogKit's ran on the crash path.
     */
    class Persistence @JvmOverloads constructor(
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        val captureCrashes: Boolean = true,
    ) {
        companion object {
            const val DEFAULT_MAX_ENTRIES = 500
        }
    }
}

package com.henrydavl.apilogkit

import android.content.Context
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
}

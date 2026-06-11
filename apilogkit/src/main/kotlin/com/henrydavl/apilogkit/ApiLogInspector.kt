package com.henrydavl.apilogkit

import android.content.Context
import android.content.Intent
import com.henrydavl.apilogkit.ui.ApiLogActivity

/**
 * Manual entry point for opening the inspector, e.g. from a debug button —
 * the counterpart of iOS's manual `ApiLogHostingController` presentation.
 * (Shake-to-open via [com.henrydavl.apilogkit.model.ApiLogger.enableShakeToOpen]
 * needs no call site.)
 */
object ApiLogInspector {

    /** Opens the log inspector. Safe to call from any [Context]. */
    fun launch(context: Context) {
        val intent = Intent(context, ApiLogActivity::class.java)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

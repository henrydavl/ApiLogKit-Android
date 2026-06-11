package com.henrydavl.apilogkit

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.henrydavl.apilogkit.model.ApiLogger

/**
 * Zero-config bootstrap. Android instantiates manifest-declared ContentProviders
 * before `Application.onCreate`, which lets the library capture the Application
 * context automatically — so the (default-on) log notification works without the
 * host calling any setup method. The same trick is used by Chucker, LeakCanary,
 * WorkManager, etc.
 *
 * It stores nothing and answers no queries; it exists only for [onCreate].
 */
internal class ApiLogInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { ApiLogger.attachApplication(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

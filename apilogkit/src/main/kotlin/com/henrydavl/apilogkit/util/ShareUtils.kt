package com.henrydavl.apilogkit.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** Android equivalents of the iOS share sheet and clipboard helpers. */
internal object ShareUtils {

    /** Opens the system share sheet (`Intent.ACTION_SEND`) with plain text. */
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share log"))
    }

    /** Copies text to the clipboard. */
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("ApiLogKit", text))
    }
}

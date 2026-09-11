package com.henrydavl.apilogkit.util

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible handler behind the share sheet's **Copy** and **Save as .txt**
 * actions on Android 14+ (see [ShareUtils.shareText]).
 *
 * It takes only the export's *file name*, never its contents: the action is
 * delivered through a `PendingIntent`, whose extras cross a Binder transaction
 * with the same ~1 MB ceiling that made file-based sharing necessary in the
 * first place. The file itself already sits in ApiLogKit's own cache, so the
 * name is enough to find it.
 *
 * Not exported. A `PendingIntent` runs with the creating app's identity, so the
 * system chooser can trigger this without the activity being open to other apps.
 */
internal class ShareActionActivity : ComponentActivity() {

    private val fileName: String? get() = intent?.getStringExtra(EXTRA_FILE_NAME)

    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_TYPE),
    ) { target ->
        if (target == null) {
            finish() // user backed out of the picker
            return@registerForActivityResult
        }
        val text = readExport()
        finishWith(
            if (text != null && ShareUtils.writeTo(this, target, text)) {
                "Saved"
            } else {
                "Couldn't save file"
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On recreation the result launcher restores itself and its callback
        // still arrives, so re-running the action here would double-fire it.
        if (savedInstanceState != null) return

        val name = fileName
        if (name == null) {
            finish()
            return
        }

        when (intent?.action) {
            ACTION_COPY -> {
                val text = readExport()
                finishWith(
                    when {
                        text == null -> "Couldn't read the export"
                        ShareUtils.copy(this, text) -> "Copied to clipboard"
                        else -> "Too large to copy — use Save as .txt"
                    },
                )
            }

            ACTION_SAVE -> saveLauncher.launch(name)

            else -> finish()
        }
    }

    private fun readExport(): String? = fileName?.let { name ->
        runCatching { ShareUtils.exportFile(this, name).readText() }.getOrNull()
    }

    private fun finishWith(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    internal companion object {
        const val ACTION_COPY = "com.henrydavl.apilogkit.action.COPY"
        const val ACTION_SAVE = "com.henrydavl.apilogkit.action.SAVE_AS_TXT"
        const val EXTRA_FILE_NAME = "com.henrydavl.apilogkit.extra.FILE_NAME"
        const val MIME_TYPE = "text/plain"
    }
}

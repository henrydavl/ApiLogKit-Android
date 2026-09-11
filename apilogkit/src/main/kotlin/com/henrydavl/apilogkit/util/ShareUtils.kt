package com.henrydavl.apilogkit.util

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.chooser.ChooserAction
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.henrydavl.apilogkit.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Android equivalents of the iOS share sheet and clipboard helpers. */
internal object ShareUtils {

    /**
     * Opens the system share sheet with the log attached as a `.txt` file.
     *
     * The payload deliberately travels as a file stream rather than as
     * [Intent.EXTRA_TEXT]. Intent extras cross a Binder transaction whose total
     * budget is roughly 1 MB, and a single base64 response body can exceed that
     * on its own — which fails with `TransactionTooLargeException` and takes the
     * host app down with it. A [FileProvider] URI has no such ceiling, and it
     * also makes the sheet offer file destinations (Files, Drive, mail
     * attachment) alongside the usual text targets.
     *
     * Text is still attached inline when it is small enough to be safe, so
     * plain-text targets — messengers, notes, and the system Copy action the
     * sheet shows on Android 13+ — keep behaving normally for ordinary logs.
     */
    fun shareText(context: Context, text: String, fileName: String = fileName()) {
        val uri = writeExport(context, text, fileName)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_SUBJECT, fileName)

            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                // Some targets read the grant from ClipData rather than the extra.
                clipData = ClipData.newUri(context.contentResolver, fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            when {
                // Small enough to ride along safely.
                text.length <= INLINE_TEXT_LIMIT -> putExtra(Intent.EXTRA_TEXT, text)
                // No file to fall back on (write failed): truncate rather than
                // hand the system a transaction it cannot deliver.
                uri == null -> putExtra(
                    Intent.EXTRA_TEXT,
                    text.take(INLINE_TEXT_LIMIT) + TRUNCATION_NOTE,
                )
            }
        }

        val chooser = Intent.createChooser(send, "Share log").apply {
            // Android 14+ can render Copy / Save as .txt as action chips inside the
            // native sheet — the same shape as iOS's UIActivityViewController, which
            // ships those as built-in system activities. Android has no such built-in
            // set (its sheet lists apps that registered an intent-filter), so before
            // API 34 there is no supported way to put them there; the inspector's
            // overflow menu carries both on every version.
            if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, customActions(context, fileName))
            }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    /**
     * Copies text to the clipboard, returning whether it worked.
     *
     * The clipboard is a Binder transaction too, so an oversized payload fails
     * here the same way an oversized [Intent.EXTRA_TEXT] does. Callers surface
     * the failure and point at "Save as .txt" instead; a debug overlay must
     * never crash its host over a copy.
     */
    fun copy(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
            true
        }.getOrDefault(false)
    }

    /**
     * Writes [text] to a document the user picked through the system file picker
     * (`ACTION_CREATE_DOCUMENT`). Returns whether it was written.
     */
    fun writeTo(context: Context, uri: Uri, text: String): Boolean = runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
        stream.bufferedWriter().use { it.write(text) }
        true
    }.getOrDefault(false)

    /** Timestamped export name, e.g. `apilog-20260911-143002.txt`. */
    fun fileName(prefix: String = "apilog"): String {
        // Deliberately Locale.US rather than ApiLogKitConfig.dateLocale: this is a
        // filename, and a localised one can carry characters some targets reject.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$stamp.txt"
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun customActions(context: Context, fileName: String): Array<ChooserAction> = arrayOf(
        chooserAction(
            context, R.drawable.ic_apilogkit_copy, "Copy",
            ShareActionActivity.ACTION_COPY, fileName, requestCode = 1,
        ),
        chooserAction(
            context, R.drawable.ic_apilogkit_save, "Save as .txt",
            ShareActionActivity.ACTION_SAVE, fileName, requestCode = 2,
        ),
    )

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun chooserAction(
        context: Context,
        @DrawableRes icon: Int,
        label: String,
        action: String,
        fileName: String,
        requestCode: Int,
    ): ChooserAction {
        val intent = Intent(context, ShareActionActivity::class.java)
            .setAction(action)
            // Only the name travels here: PendingIntent extras cross the same
            // Binder transaction that the log itself is too big for.
            .putExtra(ShareActionActivity.EXTRA_FILE_NAME, fileName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            // UPDATE_CURRENT so a later export replaces the file name in a
            // PendingIntent the system is still holding from a previous share.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return ChooserAction.Builder(
            Icon.createWithResource(context, icon),
            label,
            pending,
        ).build()
    }

    /** Resolves an export by name inside ApiLogKit's own cache directory. */
    internal fun exportFile(context: Context, fileName: String): File =
        File(exportDir(context), fileName)

    private fun exportDir(context: Context): File = File(context.cacheDir, EXPORT_DIR)

    /** Returns a shareable URI for [text], or null if the cache write failed. */
    private fun writeExport(context: Context, text: String, fileName: String): Uri? = runCatching {
        pruneStaleExports(exportDir(context).apply { mkdirs() })

        val file = exportFile(context, fileName)
        file.writeText(text)

        FileProvider.getUriForFile(context, "${context.packageName}.$AUTHORITY_SUFFIX", file)
    }.getOrNull()

    /**
     * Drops exports the share sheet is long done with. The system clears the
     * cache under pressure anyway, but a handful of multi-megabyte base64 dumps
     * is worth reclaiming sooner than that.
     */
    private fun pruneStaleExports(dir: File) {
        val cutoff = System.currentTimeMillis() - EXPORT_TTL_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    private const val MIME_TYPE = "text/plain"
    private const val CLIP_LABEL = "ApiLogKit"
    private const val EXPORT_DIR = "apilogkit-exports"
    /** Must match the `android:authorities` of `ApiLogFileProvider` in the manifest. */
    private const val AUTHORITY_SUFFIX = "apilogkit.fileprovider"

    /**
     * Well under the ~1 MB Binder ceiling, leaving room for the rest of the
     * transaction (the chooser's own extras, the ClipData grant, and so on).
     */
    private const val INLINE_TEXT_LIMIT = 96 * 1024

    private const val TRUNCATION_NOTE =
        "\n\n[truncated by ApiLogKit — the full log could not be attached as a file]"

    private const val EXPORT_TTL_MS = 60 * 60 * 1000L
}

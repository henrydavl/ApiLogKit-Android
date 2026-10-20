package com.henrydavl.apilogkit.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.henrydavl.apilogkit.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** A string value too long to show inline, opened from the JSON tree. */
data class LongStringItem(val title: String, val value: String)

/**
 * Full-screen viewer for string values too long to lay out inline in the JSON
 * tree — base64 images, tokens, encrypted payloads. Counterpart of the iOS
 * `LongStringViewer`.
 *
 * Built for low-memory devices: the text is split off the main thread into
 * chunks rendered lazily, so only what's on screen is laid out, and images are
 * decoded downsampled with an out-of-memory fallback to the text view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongStringViewer(item: LongStringItem, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val image by produceState<DecodedImage?>(null, item) {
        value = withContext(Dispatchers.Default) { LongStringText.decodeImage(item.value) }
    }
    var showsImage by remember { mutableStateOf(true) }

    // Feedback lives inside the dialog — the detail screen's toast sits behind it.
    var status by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            status = if (ShareUtils.writeTo(context, uri, item.value)) "Saved" else "Couldn't save file"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(item.title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // A base64-sized value can exceed the clipboard's
                            // Binder budget; point at the file route instead.
                            if (ShareUtils.copy(context, item.value)) {
                                copied = true
                                status = null
                            } else {
                                status = "Too large to copy — use Save as .txt"
                            }
                        }) {
                            Icon(
                                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                contentDescription = "Copy",
                            )
                        }
                        IconButton(onClick = { saveLauncher.launch(ShareUtils.fileName("value")) }) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = "Save as .txt")
                        }
                    },
                )

                InfoBar(
                    summary = status ?: summary(item.value, image),
                    hasImage = image != null,
                    showsImage = showsImage,
                    onShowImage = { showsImage = it },
                )
                HorizontalDivider()

                val decoded = image
                if (decoded != null && showsImage) {
                    ImagePreview(decoded)
                } else {
                    TextBody(item.value)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoBar(summary: String, hasImage: Boolean, showsImage: Boolean, onShowImage: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = summary,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (hasImage) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = showsImage,
                    onClick = { onShowImage(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) { Text("Image", fontSize = 12.sp) }
                SegmentedButton(
                    selected = !showsImage,
                    onClick = { onShowImage(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) { Text("Text", fontSize = 12.sp) }
            }
        }
    }
}

private fun summary(value: String, image: DecodedImage?): String {
    val size = formatSize(value)
    return if (image == null) size else "$size · base64 image ${image.width} × ${image.height}"
}

@Composable
private fun ImagePreview(image: DecodedImage) {
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val TextFontSize = 12.sp
private val TextPadding = 16.dp

@Composable
private fun TextBody(text: String) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = TextFontSize)
    val measurer = rememberTextMeasurer()
    // Average over a run of glyphs: a single glyph's width is rounded to whole
    // pixels, which would miscount the characters per line.
    val advance = remember(measurer) {
        measurer.measure("0".repeat(100), style).size.width / 100f
    }
    val padding = with(LocalDensity.current) { TextPadding.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val charsPerLine = LongStringText.charsPerLine(constraints.maxWidth - 2 * padding, advance)
        val chunks by produceState<List<String>?>(null, text, charsPerLine) {
            value = null
            value = withContext(Dispatchers.Default) { LongStringText.chunk(text, charsPerLine) }
        }

        val loaded = chunks
        if (loaded == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(TextPadding),
            ) {
                itemsIndexed(loaded) { _, chunk ->
                    SelectionContainer {
                        Text(text = chunk, style = style, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** A decoded base64 image, downsampled for display; [width]/[height] are the original's. */
internal class DecodedImage(val bitmap: Bitmap, val width: Int, val height: Int)

/** Formats a string's UTF-8 size, e.g. `617 KB`, without allocating its bytes. */
internal fun formatSize(value: String): String {
    var bytes = 0L
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        bytes += when {
            ch.code < 0x80 -> 1
            ch.code < 0x800 -> 2
            Character.isHighSurrogate(ch) -> { i++; 4 }
            else -> 3
        }
        i++
    }
    return when {
        bytes < 1_000 -> "$bytes bytes"
        bytes < 1_000_000 -> "${bytes / 1_000} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    }
}

/** Layout and decoding for [LongStringViewer], kept free of Compose so it can run off the main thread. */
internal object LongStringText {

    /** Lines per lazily rendered chunk — small enough that no item gets tall. */
    const val LINES_PER_CHUNK = 40

    /** Longest edge of a decoded preview; larger images are downsampled. */
    private const val MAX_IMAGE_EDGE = 2048

    /**
     * Characters that fit on one line, less one so a hard-wrapped line never
     * spills onto a second.
     */
    fun charsPerLine(widthPx: Float, advancePx: Float): Int {
        if (widthPx <= 0f || advancePx <= 0f) return 80
        return maxOf(20, (widthPx / advancePx).toInt() - 1)
    }

    /**
     * Splits [value] into chunks of [LINES_PER_CHUNK] lines.
     *
     * Values without whitespace (base64, tokens) are hard-wrapped to the line
     * width: left to itself, text layout breaks them at `/` and `+` and at every
     * chunk boundary, leaving ragged lines throughout. Display only — Copy and
     * Save always take the original value.
     */
    fun chunk(value: String, charsPerLine: Int): List<String> {
        val hardWrap = value.none { it.isWhitespace() }
        val lineLength = if (hardWrap) charsPerLine else charsPerLine * LINES_PER_CHUNK
        val linesPerPiece = if (hardWrap) LINES_PER_CHUNK else 1

        val result = ArrayList<String>()
        val piece = StringBuilder()
        var lines = 0
        var start = 0
        while (start < value.length) {
            val end = minOf(start + lineLength, value.length)
            if (lines > 0) piece.append('\n')
            piece.append(value, start, end)
            start = end
            if (++lines == linesPerPiece) {
                result += piece.toString()
                piece.setLength(0)
                lines = 0
            }
        }
        if (lines > 0) result += piece.toString()
        return result
    }

    /** Strips a `data:image/...;base64,` prefix, if present. */
    fun base64Payload(value: String): String =
        if (value.startsWith("data:")) value.substringAfter(',', "") else value

    /**
     * Decodes [value] as a base64 image, or returns null when it isn't one.
     *
     * Downsampled by powers of two to [MAX_IMAGE_EDGE], and an
     * [OutOfMemoryError] falls back to the text view rather than taking the
     * host app down — the failure this viewer exists to avoid.
     */
    fun decodeImage(value: String): DecodedImage? = try {
        val bytes = Base64.decode(base64Payload(value), Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?.let { DecodedImage(it, bounds.outWidth, bounds.outHeight) }
        }
    } catch (_: IllegalArgumentException) {
        null // not base64
    } catch (_: OutOfMemoryError) {
        null
    }
}

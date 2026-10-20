package com.henrydavl.apilogkit.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrydavl.apilogkit.json.JsonNode
import com.henrydavl.apilogkit.ui.theme.ApiLogColors

/**
 * One visible line of a JSON tree. The tree is rendered as a flat list of these
 * — one `LazyColumn` item each — rather than as a single nested composable.
 *
 * A single item holding the whole tree grows with the body: a few hundred KB of
 * base64, or "Expand all" on a large response, produced one item hundreds of
 * thousands of pixels tall, and the memory to lay it out got the process killed
 * by the low-memory killer on 2–3 GB devices. Flat rows keep every item one line
 * high and only compose what is on screen.
 */
sealed class JsonRow {
    abstract val depth: Int

    /** Opening line of an object/array, e.g. `"user": {`. Tapping toggles it. */
    data class Open(
        val path: String,
        val key: String?,
        val index: Int?,
        override val depth: Int,
        val node: JsonNode,
        val open: String,
        val close: String,
        val count: Int,
        val expanded: Boolean,
        /** Original text when [node] was unfolded from a JSON string value. */
        val embeddedRaw: String?,
    ) : JsonRow()

    /** Closing bracket of an expanded container. */
    data class Close(override val depth: Int, val text: String) : JsonRow()

    /** A scalar (or empty container) value. */
    data class Leaf(
        val path: String,
        val key: String?,
        val index: Int?,
        override val depth: Int,
        val node: JsonNode,
    ) : JsonRow()
}

/**
 * Expansion-state holder for a JSON tree. Compose port of the iOS
 * `JSONTreeModel`. Paths are stable string keys ("$", "$.a", "$[0]") so toggling
 * one branch leaves the rest untouched.
 */
class JsonTreeModel(val root: JsonNode) {

    /**
     * Strings longer than this are truncated in the tree (e.g. base64) and open
     * in [LongStringViewer] instead of expanding inline.
     */
    val stringLimit = 300

    private var expandedPaths by mutableStateOf(defaultExpanded(root))

    /** The currently visible lines, recomputed when a branch is toggled. */
    val rows: List<JsonRow> by derivedStateOf { flatten(root, expandedPaths) }

    fun isExpanded(path: String): Boolean = expandedPaths.contains(path)

    fun toggle(path: String) {
        expandedPaths = expandedPaths.toMutableSet().apply {
            if (contains(path)) remove(path) else add(path)
        }
    }

    fun expandAll() {
        expandedPaths = allContainerPaths(root)
    }

    fun collapseAll() {
        expandedPaths = setOf(ROOT_PATH)
    }

    companion object {
        const val ROOT_PATH = "$"

        private fun flatten(root: JsonNode, expanded: Set<String>): List<JsonRow> {
            val rows = ArrayList<JsonRow>()

            fun walk(node: JsonNode, key: String?, index: Int?, path: String, depth: Int, embeddedRaw: String?) {
                when {
                    // Unfolds in place, sharing its parent's path and depth.
                    node is JsonNode.Embedded ->
                        walk(node.parsed, key, index, path, depth, node.raw)

                    node is JsonNode.Obj && node.pairs.isNotEmpty() -> {
                        val isOpen = path in expanded
                        rows += JsonRow.Open(path, key, index, depth, node, "{", "}", node.pairs.size, isOpen, embeddedRaw)
                        if (isOpen) {
                            node.pairs.forEach { (childKey, child) ->
                                walk(child, childKey, null, "$path.$childKey", depth + 1, null)
                            }
                            rows += JsonRow.Close(depth, "}")
                        }
                    }

                    node is JsonNode.Arr && node.items.isNotEmpty() -> {
                        val isOpen = path in expanded
                        rows += JsonRow.Open(path, key, index, depth, node, "[", "]", node.items.size, isOpen, embeddedRaw)
                        if (isOpen) {
                            node.items.forEachIndexed { itemIndex, item ->
                                walk(item, null, itemIndex, "$path[$itemIndex]", depth + 1, null)
                            }
                            rows += JsonRow.Close(depth, "]")
                        }
                    }

                    else -> rows += JsonRow.Leaf(path, key, index, depth, node)
                }
            }

            walk(root, null, null, ROOT_PATH, 0, null)
            return rows
        }

        /** Default: top two levels expanded, large arrays start collapsed. */
        private fun defaultExpanded(root: JsonNode): Set<String> {
            val set = LinkedHashSet<String>()
            fun walk(node: JsonNode, path: String, depth: Int) {
                when (node) {
                    is JsonNode.Obj -> {
                        if (depth < 2) set.add(path)
                        node.pairs.forEach { walk(it.second, "$path.${it.first}", depth + 1) }
                    }
                    is JsonNode.Arr -> {
                        if (depth < 2 && node.items.size <= 100) set.add(path)
                        node.items.forEachIndexed { index, item -> walk(item, "$path[$index]", depth + 1) }
                    }
                    is JsonNode.Embedded -> walk(node.parsed, path, depth)
                    else -> Unit
                }
            }
            walk(root, ROOT_PATH, 0)
            return set
        }

        private fun allContainerPaths(root: JsonNode): Set<String> {
            val set = LinkedHashSet<String>()
            fun walk(node: JsonNode, path: String) {
                when (node) {
                    is JsonNode.Obj -> {
                        set.add(path)
                        node.pairs.forEach { walk(it.second, "$path.${it.first}") }
                    }
                    is JsonNode.Arr -> {
                        set.add(path)
                        node.items.forEachIndexed { index, item -> walk(item, "$path[$index]") }
                    }
                    is JsonNode.Embedded -> walk(node.parsed, path)
                    else -> Unit
                }
            }
            walk(root, ROOT_PATH)
            return set
        }
    }
}

private val MonoFont = FontFamily.Monospace
private val MonoSize = 13.sp
private val Indent = 16.dp

/** Leading inset for leaf/close rows so their text aligns under container text. */
private val TextInset = 18.dp

/** Renders one [JsonRow]; place each in its own `LazyColumn` item. */
@Composable
fun JsonRowView(
    row: JsonRow,
    model: JsonTreeModel,
    onCopy: (String) -> Unit,
    onOpenString: (LongStringItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indented = modifier.padding(start = Indent * row.depth)
    when (row) {
        is JsonRow.Open -> OpenRow(row, model, onCopy, indented)
        is JsonRow.Close -> Text(
            text = row.text,
            color = ApiLogColors.punctuation,
            fontFamily = MonoFont,
            fontSize = MonoSize,
            modifier = indented.padding(start = TextInset),
        )
        is JsonRow.Leaf -> LeafRow(row, model, onCopy, onOpenString, indented)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OpenRow(row: JsonRow.Open, model: JsonTreeModel, onCopy: (String) -> Unit, modifier: Modifier) {
    // Embedded JSON offers both forms on long-press; a plain container copies
    // its subtree straight away, as before.
    var menu by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { model.toggle(row.path) },
                    onLongClick = {
                        if (row.embeddedRaw != null) menu = true else onCopy(row.node.rawValue)
                    },
                ),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (row.expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (row.expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp),
            )
            Text(
                text = headerText(row),
                fontFamily = MonoFont,
                fontSize = MonoSize,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxWidth(),
            )
        }
        if (row.embeddedRaw != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy formatted JSON") },
                    onClick = { onCopy(row.node.prettyPrinted()); menu = false },
                )
                DropdownMenuItem(
                    text = { Text("Copy original string") },
                    onClick = { onCopy(row.embeddedRaw); menu = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeafRow(
    row: JsonRow.Leaf,
    model: JsonTreeModel,
    onCopy: (String) -> Unit,
    onOpenString: (LongStringItem) -> Unit,
    modifier: Modifier,
) {
    val longString = (row.node as? JsonNode.Str)?.value?.takeIf { it.length > model.stringLimit }
    Text(
        text = buildAnnotatedString {
            append(prefixText(row.key, row.index))
            append(valueText(model, row.node, longString != null))
        },
        fontFamily = MonoFont,
        fontSize = MonoSize,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = TextInset)
            .combinedClickable(
                onClick = {
                    // Long strings open the full-value viewer; everything else copies.
                    if (longString != null) {
                        onOpenString(LongStringItem(displayPath(row.path), longString))
                    } else {
                        onCopy(row.node.rawValue)
                    }
                },
                onLongClick = { onCopy(row.node.rawValue) },
            ),
    )
}

/** Readable location for the viewer's title, e.g. `request.photo_store`. */
private fun displayPath(path: String): String = when {
    path == JsonTreeModel.ROOT_PATH -> "Value"
    path.startsWith(JsonTreeModel.ROOT_PATH + ".") -> path.drop(2)
    else -> path.drop(1)
}

@Composable
private fun headerText(row: JsonRow.Open): AnnotatedString = buildAnnotatedString {
    append(prefixText(row.key, row.index))
    if (row.expanded) {
        withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append(row.open) }
    } else {
        withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append("${row.open} … ${row.close}") }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) { append("  ${row.count}") }
    }
    // Flags values that were a JSON string in the actual payload, so the
    // unfolded view isn't mistaken for the wire format.
    if (row.embeddedRaw != null) {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) { append("  JSON string") }
    }
}

@Composable
private fun prefixText(key: String?, index: Int?): AnnotatedString = buildAnnotatedString {
    when {
        key != null -> {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("\"$key\"") }
            withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append(": ") }
        }
        index != null -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) { append("$index: ") }
        else -> Unit
    }
}

@Composable
private fun valueText(model: JsonTreeModel, node: JsonNode, isLong: Boolean): AnnotatedString = buildAnnotatedString {
    when (node) {
        is JsonNode.Str -> {
            if (isLong) {
                val head = node.value.take(model.stringLimit)
                withStyle(SpanStyle(color = ApiLogColors.string)) { append("\"$head…\"") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) {
                    append("  tap to view · ${formatSize(node.value)}")
                }
            } else {
                withStyle(SpanStyle(color = ApiLogColors.string)) { append("\"${node.value}\"") }
            }
        }
        is JsonNode.Num -> withStyle(SpanStyle(color = ApiLogColors.number)) { append(node.value) }
        is JsonNode.Bool -> withStyle(SpanStyle(color = ApiLogColors.bool)) { append(if (node.value) "true" else "false") }
        JsonNode.Null -> withStyle(SpanStyle(color = ApiLogColors.nullValue)) { append("null") }
        is JsonNode.Obj -> withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append("{}") }
        is JsonNode.Arr -> withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append("[]") }
        // Unwrapped by the flattening before it reaches a leaf.
        is JsonNode.Embedded -> Unit
    }
}

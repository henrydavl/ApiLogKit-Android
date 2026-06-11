package com.henrydavl.apilogkit.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * Expansion-state holder for a JSON tree. Compose port of the iOS
 * `JSONTreeModel`. Paths are stable string keys ("$", "$.a", "$[0]") so toggling
 * one branch leaves the rest untouched.
 */
class JsonTreeModel(val root: JsonNode) {

    /** Strings longer than this are truncated in the tree (e.g. base64). */
    val stringLimit = 300

    private var expandedPaths by mutableStateOf(defaultExpanded(root))

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
                        node.pairs.forEach { walk(it.second, "$path.${it.first}", ) }
                    }
                    is JsonNode.Arr -> {
                        set.add(path)
                        node.items.forEachIndexed { index, item -> walk(item, "$path[$index]") }
                    }
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

@Composable
fun JsonTreeView(model: JsonTreeModel, onCopy: (String) -> Unit) {
    JsonNodeView(model, key = null, index = null, node = model.root, path = JsonTreeModel.ROOT_PATH, onCopy = onCopy)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonNodeView(
    model: JsonTreeModel,
    key: String?,
    index: Int?,
    node: JsonNode,
    path: String,
    onCopy: (String) -> Unit,
) {
    when {
        node is JsonNode.Obj && node.pairs.isNotEmpty() ->
            Container(model, key, index, path, open = "{", close = "}", count = node.pairs.size, node = node, onCopy = onCopy) {
                node.pairs.forEach { (childKey, childNode) ->
                    JsonNodeView(model, key = childKey, index = null, node = childNode, path = "$path.$childKey", onCopy = onCopy)
                }
            }

        node is JsonNode.Arr && node.items.isNotEmpty() ->
            Container(model, key, index, path, open = "[", close = "]", count = node.items.size, node = node, onCopy = onCopy) {
                node.items.forEachIndexed { itemIndex, item ->
                    JsonNodeView(model, key = null, index = itemIndex, node = item, path = "$path[$itemIndex]", onCopy = onCopy)
                }
            }

        else -> LeafRow(model, key, index, node, path, onCopy)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Container(
    model: JsonTreeModel,
    key: String?,
    index: Int?,
    path: String,
    open: String,
    close: String,
    count: Int,
    node: JsonNode,
    onCopy: (String) -> Unit,
    children: @Composable () -> Unit,
) {
    val expanded = model.isExpanded(path)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { model.toggle(path) },
                    onLongClick = { onCopy(node.rawValue) },
                ),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp),
            )
            Text(
                text = headerText(model, key, index, open, close, count, expanded),
                fontFamily = MonoFont,
                fontSize = MonoSize,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxWidth(),
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                children()
            }
            Text(
                text = close,
                color = ApiLogColors.punctuation,
                fontFamily = MonoFont,
                fontSize = MonoSize,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeafRow(
    model: JsonTreeModel,
    key: String?,
    index: Int?,
    node: JsonNode,
    path: String,
    onCopy: (String) -> Unit,
) {
    val isTruncatableString = node is JsonNode.Str && node.value.length > model.stringLimit
    Text(
        text = buildAnnotatedString {
            append(prefixText(key, index))
            append(valueText(model, node, path))
        },
        fontFamily = MonoFont,
        fontSize = MonoSize,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .combinedClickable(
                onClick = {
                    // Long strings expand/collapse on tap; everything else copies.
                    if (isTruncatableString) model.toggle(path) else onCopy(node.rawValue)
                },
                onLongClick = { onCopy(node.rawValue) },
            ),
    )
}

@Composable
private fun headerText(
    model: JsonTreeModel,
    key: String?,
    index: Int?,
    open: String,
    close: String,
    count: Int,
    expanded: Boolean,
): AnnotatedString = buildAnnotatedString {
    append(prefixText(key, index))
    if (expanded) {
        withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append(open) }
    } else {
        withStyle(SpanStyle(color = ApiLogColors.punctuation)) { append("$open … $close") }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) { append("  $count") }
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
private fun valueText(model: JsonTreeModel, node: JsonNode, path: String): AnnotatedString = buildAnnotatedString {
    when (node) {
        is JsonNode.Str -> {
            if (node.value.length > model.stringLimit && !model.isExpanded(path)) {
                val head = node.value.take(model.stringLimit)
                withStyle(SpanStyle(color = ApiLogColors.string)) { append("\"$head…\"") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) {
                    append("  tap to expand · ${node.value.length} chars")
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
    }
}

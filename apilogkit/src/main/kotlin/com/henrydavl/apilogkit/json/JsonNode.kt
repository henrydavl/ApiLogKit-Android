package com.henrydavl.apilogkit.json

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight JSON tree model used by the interactive JSON viewer in the detail
 * screen. Built on top of `org.json` for robustness. Kotlin port of the iOS
 * `JSONNode` recursive enum (`JSONValue.swift`).
 */
sealed class JsonNode {
    data class Obj(val pairs: List<Pair<String, JsonNode>>) : JsonNode()
    data class Arr(val items: List<JsonNode>) : JsonNode()
    data class Str(val value: String) : JsonNode()
    data class Num(val value: String) : JsonNode()
    data class Bool(val value: Boolean) : JsonNode()
    object Null : JsonNode()

    /**
     * A string value that itself holds a JSON object/array — e.g. a form field
     * sent as `request=<json>`. Display-only: the viewer unfolds [parsed], while
     * copy and export keep [raw] exactly as logged. Mirrors iOS
     * `JSONNode.embedded`.
     */
    data class Embedded(val raw: String, val parsed: JsonNode) : JsonNode()

    /** A container has collapsible children. */
    val isContainer: Boolean
        get() = this is Obj || this is Arr || this is Embedded

    /** Number of direct children (0 for leaves). */
    val childCount: Int
        get() = when (this) {
            is Obj -> pairs.size
            is Arr -> items.size
            is Embedded -> parsed.childCount
            else -> 0
        }

    // MARK: - Serializing (for copy)

    /** Pretty-printed JSON text for this node (used when copying a subtree). */
    fun prettyPrinted(indent: Int = 0): String {
        val pad = "  ".repeat(indent)
        val childPad = "  ".repeat(indent + 1)
        return when (this) {
            is Obj -> {
                if (pairs.isEmpty()) "{}" else {
                    val body = pairs.joinToString(",\n") { (key, value) ->
                        "$childPad${encode(key)}: ${value.prettyPrinted(indent + 1)}"
                    }
                    "{\n$body\n$pad}"
                }
            }
            is Arr -> {
                if (items.isEmpty()) "[]" else {
                    val body = items.joinToString(",\n") { "$childPad${it.prettyPrinted(indent + 1)}" }
                    "[\n$body\n$pad]"
                }
            }
            is Str -> encode(value)
            is Num -> value
            is Bool -> if (value) "true" else "false"
            Null -> "null"
            is Embedded -> encode(raw)
        }
    }

    /** Plain value used when copying a single leaf (no surrounding quotes). */
    val rawValue: String
        get() = when (this) {
            is Str -> value
            is Num -> value
            is Bool -> if (value) "true" else "false"
            Null -> "null"
            is Embedded -> raw
            is Obj, is Arr -> prettyPrinted()
        }

    companion object {

        /**
         * Parses a JSON string. Returns `null` if it isn't a JSON object/array
         * (so callers can fall back to plain text for HTML, fragments, etc.).
         */
        fun parse(string: String): JsonNode? {
            val trimmed = string.trim()
            val first = trimmed.firstOrNull() ?: return null
            if (first != '{' && first != '[') return null
            return try {
                when (first) {
                    '{' -> build(JSONObject(trimmed))
                    else -> build(JSONArray(trimmed))
                }
            } catch (_: Exception) {
                null
            }
        }

        /** Builds a node from a string-keyed map (e.g. request body). */
        fun fromMap(map: Map<String, Any?>): JsonNode? {
            if (map.isEmpty()) return null
            return build(JSONObject(map))
        }

        private fun build(any: Any?): JsonNode = when (any) {
            null, JSONObject.NULL -> Null
            is JSONObject -> {
                // org.json keys are unordered; sort for a stable, easy-to-scan display.
                val keys = any.keys().asSequence().toList().sorted()
                Obj(keys.map { it to build(any.get(it)) })
            }
            is JSONArray -> Arr((0 until any.length()).map { build(any.get(it)) })
            is Boolean -> Bool(any)
            is Number -> Num(numberString(any))
            is String -> parseEmbedded(any)?.let { Embedded(any, it) } ?: Str(any)
            else -> Str(any.toString())
        }

        /**
         * Parses a string value that holds a JSON object/array. The leading-brace
         * check keeps this cheap for the common case (plain text, base64, ids).
         */
        private fun parseEmbedded(string: String): JsonNode? {
            val first = string.firstOrNull { !it.isWhitespace() } ?: return null
            if (first != '{' && first != '[') return null
            return parse(string)?.takeIf { it.childCount > 0 }
        }

        private fun numberString(number: Number): String = when (number) {
            is Double -> if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
            is Float -> if (number == number.toLong().toFloat()) number.toLong().toString() else number.toString()
            else -> number.toString()
        }

        private fun encode(string: String): String {
            val result = StringBuilder("\"")
            for (ch in string) {
                when (ch) {
                    '"' -> result.append("\\\"")
                    '\\' -> result.append("\\\\")
                    '\n' -> result.append("\\n")
                    '\r' -> result.append("\\r")
                    '\t' -> result.append("\\t")
                    else -> result.append(ch)
                }
            }
            result.append("\"")
            return result.toString()
        }
    }
}

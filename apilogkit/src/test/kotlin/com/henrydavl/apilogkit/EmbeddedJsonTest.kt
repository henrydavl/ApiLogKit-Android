package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.json.JsonNode
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.model.LogSection
import com.henrydavl.apilogkit.ui.component.JsonRow
import com.henrydavl.apilogkit.ui.component.JsonTreeModel
import com.henrydavl.apilogkit.ui.detail.ApiLogDetailViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * A body logged as `mapOf("request" to jsonString)` should unfold in the tree
 * viewer, while copy and export keep the value exactly as it was sent.
 */
class EmbeddedJsonTest {

    private val payload = """{"id":"8e59","latitude":-6.21,"image":"\/9j\/4AAQ"}"""

    private fun embeddedNode(): JsonNode.Embedded {
        val root = JsonNode.fromMap(mapOf("request" to payload)) as JsonNode.Obj
        return root.pairs.single().second as JsonNode.Embedded
    }

    @Test
    fun `json string value unfolds into a tree`() {
        val node = embeddedNode()
        assertEquals(payload, node.raw)
        assertEquals(3, node.parsed.childCount)
    }

    @Test
    fun `embedded value serializes back as the original string`() {
        val root = JsonNode.fromMap(mapOf("request" to payload))!!
        assertEquals(payload, JSONObject(root.prettyPrinted()).getString("request"))
    }

    @Test
    fun `copying the embedded leaf gives the original string`() {
        assertEquals(payload, embeddedNode().rawValue)
    }

    @Test
    fun `plain strings stay strings`() {
        val root = JsonNode.fromMap(
            mapOf("image" to "/9j/4AAQSkZJRgABAQAASABIAAD", "note" to "[not json", "empty" to "{}"),
        ) as JsonNode.Obj
        root.pairs.forEach { (key, value) -> assertTrue("$key should stay a string", value is JsonNode.Str) }
    }

    @Test
    fun `detail copy value is unchanged by unfolding`() {
        val log = ApiLog(
            responseCode = "200", method = "POST", url = "https://example.com/outlet",
            responseTime = "0.4", size = "0", date = Date(),
            responseHeader = emptyMap(), responseBody = "{}",
            requestHeader = emptyMap(), requestBody = mapOf("request" to payload),
        )
        val copied = ApiLogDetailViewModel(log, LogEventType.API).copyValue(LogSection.REQUEST_BODY)
        assertEquals(payload, JSONObject(copied).getString("request"))
    }

    @Test
    fun `embedded container is expanded by default and tagged`() {
        val model = JsonTreeModel(JsonNode.fromMap(mapOf("request" to payload))!!)
        val open = model.rows.filterIsInstance<JsonRow.Open>().single { it.key == "request" }
        assertTrue(open.expanded)
        assertEquals(payload, open.embeddedRaw)
    }

    @Test
    fun `rows are one per visible line and follow toggling`() {
        val model = JsonTreeModel(JsonNode.parse("""{"a":{"b":1,"c":[true,null]},"d":"x"}""")!!)
        val expanded = model.rows
        assertTrue(expanded.first() is JsonRow.Open)
        assertTrue(expanded.last() is JsonRow.Close)

        model.toggle("$.a")
        val collapsed = model.rows
        val a = collapsed.filterIsInstance<JsonRow.Open>().single { it.key == "a" }
        assertTrue(!a.expanded)
        assertTrue("children of a collapsed node are not rows", collapsed.none { it is JsonRow.Leaf && it.key == "b" })
        assertEquals(listOf(1, 1, 0), collapsed.drop(1).map { it.depth })
    }

    @Test
    fun `expand all on a large array never yields a multi-line row`() {
        val big = (0 until 5_000).joinToString(",", "[", "]") { """{"i":$it}""" }
        val model = JsonTreeModel(JsonNode.parse(big)!!)
        model.expandAll()
        // root open + close, and per element: open, leaf, close.
        assertEquals(2 + 5_000 * 3, model.rows.size)
    }
}

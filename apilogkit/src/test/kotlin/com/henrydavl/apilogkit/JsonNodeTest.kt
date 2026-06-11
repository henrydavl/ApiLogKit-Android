package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.json.JsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonNodeTest {

    @Test
    fun `parse returns null for non-json`() {
        assertNull(JsonNode.parse("<html></html>"))
        assertNull(JsonNode.parse("plain text"))
        assertNull(JsonNode.parse(""))
    }

    @Test
    fun `parse object sorts keys and preserves types`() {
        val node = JsonNode.parse("""{"b":1,"a":"x","c":true,"d":null}""")
        assertTrue(node is JsonNode.Obj)
        val pairs = (node as JsonNode.Obj).pairs
        assertEquals(listOf("a", "b", "c", "d"), pairs.map { it.first })
        assertTrue(pairs[0].second is JsonNode.Str)
        assertTrue(pairs[1].second is JsonNode.Num)
        assertTrue(pairs[2].second is JsonNode.Bool)
        assertTrue(pairs[3].second is JsonNode.Null)
    }

    @Test
    fun `parse array`() {
        val node = JsonNode.parse("""[1,2,3]""")
        assertTrue(node is JsonNode.Arr)
        assertEquals(3, (node as JsonNode.Arr).items.size)
    }

    @Test
    fun `fromMap builds object and empty map is null`() {
        assertNull(JsonNode.fromMap(emptyMap()))
        val node = JsonNode.fromMap(mapOf("name" to "henry", "age" to 30))
        assertTrue(node is JsonNode.Obj)
    }

    @Test
    fun `prettyPrinted round-trips structure`() {
        val node = JsonNode.parse("""{"a":[1,2],"b":"x"}""")!!
        assertEquals(
            """
            {
              "a": [
                1,
                2
              ],
              "b": "x"
            }
            """.trimIndent(),
            node.prettyPrinted(),
        )
    }
}

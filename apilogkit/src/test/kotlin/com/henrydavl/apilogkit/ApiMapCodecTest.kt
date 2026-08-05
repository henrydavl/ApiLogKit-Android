package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.persistence.decodeApiMap
import com.henrydavl.apilogkit.persistence.encodeApiMap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the header/body serialisation used by `ApiLogStore`.
 *
 * The store itself needs a real SQLite database and so belongs in an
 * instrumented test, but the encoding is where the risk actually lives: a
 * persisted log is only useful if it comes back off disk looking exactly like
 * the one that went in.
 */
class ApiMapCodecTest {

    @Test
    fun `round trip preserves key order`() {
        val original = linkedMapOf<String, Any?>(
            "Zeta" to "1",
            "Alpha" to "2",
            "Mike" to "3",
            "Bravo" to "4",
        )
        val restored = original.encodeApiMap().decodeApiMap()
        assertEquals(original.keys.toList(), restored.keys.toList())
    }

    @Test
    fun `round trip preserves scalar types`() {
        val original = linkedMapOf<String, Any?>(
            "string" to "hello",
            "int" to 42,
            "long" to 9_000_000_000L,
            "double" to 1.5,
            "bool" to true,
            "null" to null,
        )
        val restored = original.encodeApiMap().decodeApiMap()

        assertEquals("hello", restored["string"])
        assertEquals(true, restored["bool"])

        // Numbers are compared by value, not by boxed type: the org.json on the
        // unit-test classpath decodes decimals as BigDecimal while Android's
        // returns Double. Either way the value survives, which is what the
        // detail screen renders.
        assertEquals(42L, (restored["int"] as Number).toLong())
        assertEquals(9_000_000_000L, (restored["long"] as Number).toLong())
        assertEquals(1.5, (restored["double"] as Number).toDouble(), 0.0)
        assertNull(restored["null"])
        assertTrue(restored.containsKey("null"))
    }

    @Test
    fun `round trip preserves nested json`() {
        val nested = JSONObject().put("id", 7).put("name", "widget")
        val restored = mapOf<String, Any?>("payload" to nested).encodeApiMap().decodeApiMap()

        val value = restored["payload"] as JSONObject
        assertEquals(7, value.getInt("id"))
        assertEquals("widget", value.getString("name"))
    }

    @Test
    fun `unsupported values fall back to their string form`() {
        class Opaque {
            override fun toString() = "opaque-value"
        }

        val restored = mapOf<String, Any?>("x" to Opaque()).encodeApiMap().decodeApiMap()
        assertEquals("opaque-value", restored["x"])
    }

    @Test
    fun `empty map round trips`() {
        assertTrue(emptyMap<String, Any?>().encodeApiMap().decodeApiMap().isEmpty())
    }

    @Test
    fun `malformed stored value decodes to an empty map instead of throwing`() {
        assertTrue("not json at all".decodeApiMap().isEmpty())
    }

    @Test
    fun `duplicate header keys collapse without losing the map`() {
        // ApiLog's maps already collapse repeated HTTP headers upstream; this
        // just pins that encoding does not make things worse.
        val restored = linkedMapOf<String, Any?>("Set-Cookie" to "a=1").encodeApiMap().decodeApiMap()
        assertEquals(1, restored.size)
        assertEquals("a=1", restored["Set-Cookie"])
    }
}

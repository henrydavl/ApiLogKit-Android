package com.henrydavl.apilogkit

import com.henrydavl.apilogkit.ui.component.LongStringText
import com.henrydavl.apilogkit.ui.component.formatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongStringTextTest {

    @Test
    fun `base64 is hard-wrapped into fixed-width lines and lossless`() {
        val value = "AB/+".repeat(5_000) // 20 000 chars, no whitespace
        val chunks = LongStringText.chunk(value, charsPerLine = 50)

        val lines = chunks.flatMap { it.split('\n') }
        assertTrue(lines.dropLast(1).all { it.length == 50 })
        assertTrue(chunks.all { it.count { ch -> ch == '\n' } < LongStringText.LINES_PER_CHUNK })
        assertEquals(value, lines.joinToString(""))
    }

    @Test
    fun `text with whitespace is chunked without inserting line breaks`() {
        val value = "word ".repeat(10_000)
        val chunks = LongStringText.chunk(value, charsPerLine = 50)
        assertEquals(value, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 50 * LongStringText.LINES_PER_CHUNK })
    }

    @Test
    fun `chars per line leaves a one-character margin`() {
        assertEquals(49, LongStringText.charsPerLine(widthPx = 500f, advancePx = 10f))
        assertEquals(80, LongStringText.charsPerLine(widthPx = 0f, advancePx = 10f))
    }

    @Test
    fun `data uri prefix is stripped`() {
        assertEquals("iVBORw0", LongStringText.base64Payload("data:image/png;base64,iVBORw0"))
        assertEquals("/9j/4AAQ", LongStringText.base64Payload("/9j/4AAQ"))
    }

    @Test
    fun `size is reported in utf8 bytes`() {
        assertEquals("999 bytes", formatSize("a".repeat(999)))
        assertEquals("617 KB", formatSize("a".repeat(617_368)))
        assertEquals("1.2 MB", formatSize("a".repeat(1_200_000)))
        assertEquals("1 KB", formatSize("é".repeat(500)))
    }
}

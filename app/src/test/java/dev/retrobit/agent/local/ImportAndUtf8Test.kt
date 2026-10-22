package dev.retrobit.agent.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ImportAndUtf8Test {
    private fun model(version: Int = 3) = byteArrayOf(0x47, 0x47, 0x55, 0x46, version.toByte(), 0, 0, 0) + ByteArray(64)

    @Test fun `GGUF v2 and v3 headers accepted`() {
        GgufImport.validateHeader(model(2).copyOf(8))
        GgufImport.validateHeader(model(3).copyOf(8))
    }
    @Test fun `APK ZIP and unknown version rejected`() {
        assertTrue(runCatching { GgufImport.validateHeader("PK123456".toByteArray()) }.isFailure)
        assertTrue(runCatching { GgufImport.validateHeader(model(9).copyOf(8)) }.isFailure)
    }
    @Test fun `short header rejected`() {
        assertTrue(runCatching { GgufImport.validateHeader(byteArrayOf(0x47)) }.isFailure)
    }
    @Test fun `stream copy preserves bytes`() = runBlocking {
        val output = ByteArrayOutputStream()
        assertEquals(72L, GgufImport.copy(ByteArrayInputStream(model()), output))
        assertArrayEquals(model(), output.toByteArray())
    }
    @Test fun `size limit enforced while streaming`() {
        val result = runCatching { runBlocking { GgufImport.copy(ByteArrayInputStream(model()), ByteArrayOutputStream(), maxBytes = 16) } }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun `truncated body rejected`() {
        val result = runCatching { runBlocking { GgufImport.copy(ByteArrayInputStream(model().copyOf(8)), ByteArrayOutputStream()) } }
        assertTrue(result.isFailure)
    }
    @Test fun `cancel callback interrupts copy`() {
        val result = runCatching { runBlocking { GgufImport.copy(ByteArrayInputStream(model()), ByteArrayOutputStream()) { throw CancellationException() } } }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
    @Test fun `header supports partial reads from a document provider`() = runBlocking {
        val input = object : ByteArrayInputStream(model()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1))
        }
        val output = ByteArrayOutputStream()
        GgufImport.copy(input, output)
        assertArrayEquals(model(), output.toByteArray())
    }
    @Test fun `emoji split across tokens is not corrupted`() {
        val decoder = Utf8Pieces()
        val emoji = "👋".toByteArray()
        assertEquals("", decoder.append(emoji.copyOfRange(0, 2)))
        assertEquals("👋", decoder.append(emoji.copyOfRange(2, 4)))
        assertEquals("", decoder.append(byteArrayOf(), end = true))
    }
    @Test fun `ASCII and non Latin scripts stream intact`() {
        val decoder = Utf8Pieces()
        val original = "Halo 世界 👋"
        val result = StringBuilder()
        original.toByteArray().forEach { result.append(decoder.append(byteArrayOf(it))) }
        result.append(decoder.append(byteArrayOf(), end = true))
        assertEquals(original, result.toString())
    }
    @Test fun `incomplete UTF8 at end is replaced not dropped`() {
        val decoder = Utf8Pieces()
        decoder.append(byteArrayOf(0xf0.toByte(), 0x9f.toByte()))
        assertEquals("\uFFFD", decoder.append(byteArrayOf(), end = true))
    }
}

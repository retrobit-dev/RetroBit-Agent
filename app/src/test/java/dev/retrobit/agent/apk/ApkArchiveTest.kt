package dev.retrobit.agent.apk

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val manifest = byteArrayOf(3, 0, 8, 0, 8, 0, 0, 0)
    private fun apk(vararg entries: Pair<String, ByteArray>): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    @Test fun `lists without extracting and counts resource dex assets ABI`() {
        val file = apk("AndroidManifest.xml" to manifest, "classes.dex" to byteArrayOf(1), "classes2.dex" to byteArrayOf(2),
            "res/layout/main.xml" to byteArrayOf(3), "resources.arsc" to byteArrayOf(4), "assets/data.json" to byteArrayOf(5), "lib/arm64-v8a/libtest.so" to byteArrayOf(6))
        val result = ApkArchiveReader.scan(file)
        assertEquals(7, result.totalEntries); assertEquals(2, result.dexCount); assertEquals(2, result.resourceCount)
        assertEquals(1, result.assetCount); assertEquals(listOf("arm64-v8a"), result.nativeAbis)
        assertFalse(result.signingBlockPresent)
        assertEquals(1, temp.root.listFiles()!!.size)
    }
    @Test fun `ordinary project ZIP is not an APK`() {
        assertTrue(runCatching { ApkArchiveReader.scan(apk("index.html" to "hello".toByteArray())) }.isFailure)
    }
    @Test fun `plain XML is not accepted as Android binary manifest`() {
        assertTrue(runCatching { ApkArchiveReader.scan(apk("AndroidManifest.xml" to "<manifest />".toByteArray())) }.isFailure)
    }
    @Test fun `unsafe path variants rejected even though extraction is never done`() {
        for (name in listOf("../escape", "/absolute", "a/../b", "a\\b", "a/./b", "C:/drive")) {
            assertTrue(name, runCatching { ApkArchiveReader.scan(apk("AndroidManifest.xml" to manifest, name to byteArrayOf(1))) }.isFailure)
        }
    }
    @Test fun `truncated archive rejected`() {
        val file = apk("AndroidManifest.xml" to manifest)
        RandomAccessFile(file, "rw").use { it.setLength(file.length() - 1) }
        assertTrue(runCatching { ApkArchiveReader.scan(file) }.isFailure)
    }
    @Test fun `large declared central directory rejected before allocation`() {
        val file = apk("AndroidManifest.xml" to manifest)
        RandomAccessFile(file, "rw").use { it.seek(file.length() - 10); it.write(byteArrayOf(-1, -1, -1, 0x7f)) }
        assertTrue(runCatching { ApkArchiveReader.scan(file) }.isFailure)
    }
    @Test fun `too many EOCD entries rejected before opening ZipFile`() {
        val file = apk("AndroidManifest.xml" to manifest)
        RandomAccessFile(file, "rw").use { it.seek(file.length() - 14); it.write(byteArrayOf(-1, -1, -1, -1)) }
        assertTrue(runCatching { ApkArchiveReader.scan(file) }.isFailure)
    }
    @Test fun `manifest actual inflate size is bounded`() {
        val big = ByteArray(8 * 1024 * 1024 + 1).also { manifest.copyInto(it) }
        assertTrue(runCatching { ApkArchiveReader.scan(apk("AndroidManifest.xml" to big)) }.isFailure)
    }
    @Test fun `file preview truncated but counts still cover every entry`() {
        val file = apk(*((listOf("AndroidManifest.xml" to manifest)) + (1..2001).map { "assets/$it" to byteArrayOf(1) }).toTypedArray())
        val report = ApkArchiveReader.scan(file)
        assertEquals(2002, report.totalEntries); assertEquals(2000, report.entries.size); assertEquals(2001, report.assetCount)
    }
    @Test fun `checkpoint permits cancellation during scan`() {
        val file = apk("AndroidManifest.xml" to manifest)
        val error = runCatching { ApkArchiveReader.scan(file) { throw kotlinx.coroutines.CancellationException("stop") } }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
    }
    @Test fun `duplicate names are rejected`() {
        val file = apk("AndroidManifest.xml" to manifest, "assets/a" to byteArrayOf(1), "assets/b" to byteArrayOf(2))
        val bytes = file.readBytes()
        val before = "assets/b".toByteArray(); val after = "assets/a".toByteArray()
        for (i in 0..bytes.size - before.size) {
            if (before.indices.all { bytes[i + it] == before[it] }) after.copyInto(bytes, i)
        }
        file.writeBytes(bytes)
        assertTrue(runCatching { ApkArchiveReader.scan(file) }.isFailure)
    }
    @Test fun `overlong paths rejected`() {
        assertTrue(runCatching { ApkArchiveReader.scan(apk("AndroidManifest.xml" to manifest, "a".repeat(513) to byteArrayOf(1))) }.isFailure)
    }
    private fun signedEnvelope(badHeader: Boolean): File {
        val file = apk("AndroidManifest.xml" to manifest)
        val original = file.readBytes()
        val eocd = original.size - 22
        val offset = (0..3).fold(0) { n, i -> n or ((original[eocd + 16 + i].toInt() and 255) shl (8 * i)) }
        val block = ByteArray(32)
        block[0] = if (badHeader) 23 else 24; block[8] = 24
        "APK Sig Block 42".toByteArray().copyInto(block, 16)
        val bytes = original.copyOfRange(0, offset) + block + original.copyOfRange(offset, original.size)
        val shifted = eocd + block.size
        for (i in 0..3) bytes[shifted + 16 + i] = ((offset + block.size) ushr (8 * i)).toByte()
        file.writeBytes(bytes)
        return file
    }
    @Test fun `signing block presence is metadata only not signature verification`() {
        assertTrue(ApkArchiveReader.scan(signedEnvelope(false)).signingBlockPresent)
    }
    @Test fun `mismatching signing block lengths rejected`() {
        assertTrue(runCatching { ApkArchiveReader.scan(signedEnvelope(true)) }.isFailure)
    }

}

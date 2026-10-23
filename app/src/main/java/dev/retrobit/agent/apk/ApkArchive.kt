package dev.retrobit.agent.apk

import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

@Serializable
data class ApkEntry(val path: String, val bytes: Long, val compressedBytes: Long)

internal data class ApkArchive(
    val entries: List<ApkEntry>, val totalEntries: Int, val declaredBytes: Long,
    val dexCount: Int, val resourceCount: Int, val assetCount: Int,
    val nativeAbis: List<String>, val signingBlockPresent: Boolean,
)

/** Bounded metadata inspection only. No extraction, DEX loading or resource inflation into memory. */
internal object ApkArchiveReader {
    const val MAX_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRIES = 20_000
    const val PREVIEW_ENTRIES = 2_000
    private const val MAX_CRITICAL_TOTAL = 64L * 1024 * 1024

    fun scan(file: File, checkpoint: () -> Unit = {}): ApkArchive {
        require(file.length() in 22..MAX_BYTES) { "Ukuran APK tidak valid; maksimum 256 MiB." }
        val (expectedCount, signingBlock) = envelope(file)
        val preview = mutableListOf<ApkEntry>()
        val names = hashSetOf<String>()
        val abis = sortedSetOf<String>()
        var total = 0L
        var criticalTotal = 0L
        var dex = 0
        var resources = 0
        var assets = 0
        var count = 0
        var manifest = false
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                checkpoint()
                val entry = entries.nextElement()
                require(++count <= MAX_ENTRIES) { "APK berisi terlalu banyak entry." }
                val name = entry.name
                require(name.isNotEmpty() && name.length <= 512 && !name.startsWith('/') && '\\' !in name && ':' !in name &&
                    name.none { it.code < 32 } && name.split('/').none { it == ".." || it == "." }) { "Path ZIP tidak aman/tidak didukung." }
                require(names.add(name)) { "APK memiliki nama entry duplikat." }
                require(entry.size in 0..MAX_BYTES && entry.compressedSize in 0..MAX_BYTES) { "Ukuran entry ZIP tidak valid/terlalu besar." }
                total += entry.size
                require(total <= 2L * 1024 * 1024 * 1024) { "Ukuran deklarasi ZIP melebihi 2 GiB." }
                if (name.matches(Regex("classes[0-9]*\\.dex"))) dex++
                if (name.startsWith("res/") || name == "resources.arsc") resources++
                if (name.startsWith("assets/")) assets++
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    val parts = name.split('/')
                    if (parts.size == 3) abis += parts[1].take(64)
                }
                val meta = name.startsWith("META-INF/", ignoreCase = true)
                val limit = when {
                    name == "AndroidManifest.xml" -> 8L * 1024 * 1024
                    name == "resources.arsc" -> 32L * 1024 * 1024
                    meta -> 4L * 1024 * 1024
                    else -> null
                }
                if (limit != null && !entry.isDirectory) {
                    require(entry.size <= limit) { "Manifest/resource/signature ZIP terlalu besar." }
                    criticalTotal += entry.size
                    require(criticalTotal <= MAX_CRITICAL_TOTAL) { "Metadata APK terlalu besar." }
                    // Check actual decompressed size too; central-directory declarations are untrusted.
                    zip.getInputStream(entry).use { input ->
                        var actual = 0L
                        val crc = java.util.zip.CRC32()
                        val buffer = ByteArray(64 * 1024)
                        if (name == "AndroidManifest.xml") {
                            val prefix = ByteArray(4)
                            java.io.DataInputStream(input).readFully(prefix)
                            require(prefix.contentEquals(byteArrayOf(3, 0, 8, 0))) { "Manifest bukan binary Android XML. ZIP proyek bukan APK." }
                            actual = 4; crc.update(prefix)
                        }
                        while (true) {
                            checkpoint()
                            val n = input.read(buffer)
                            if (n == -1) break
                            if (n == 0) continue
                            crc.update(buffer, 0, n)
                            actual += n
                            require(actual <= limit) { "Data ZIP mengembang melewati batas metadata." }
                        }
                        require(actual == entry.size && crc.value == entry.crc) { "Entry ZIP terpotong/ukuran atau CRC metadata tidak cocok." }
                    }
                }
                if (name == "AndroidManifest.xml") { require(!entry.isDirectory && entry.size >= 8); manifest = true }
                if (preview.size < PREVIEW_ENTRIES) preview += ApkEntry(name, entry.size, entry.compressedSize)
            }
        }
        require(manifest) { "Tidak ada AndroidManifest.xml; berkas bukan APK tunggal." }
        require(count == expectedCount) { "Jumlah entry ZIP tidak cocok." }
        return ApkArchive(preview, count, total, dex, resources, assets, abis.toList(), signingBlock)
    }

    /** Reject ZIP64/multi-disk and huge central/signing blocks BEFORE ZipFile/PackageManager allocation. */
    private fun envelope(file: File): Pair<Int, Boolean> = RandomAccessFile(file, "r").use { input ->
        val tailSize = minOf(file.length(), 65_557L).toInt()
        val tail = ByteArray(tailSize)
        val base = file.length() - tailSize
        input.seek(base); input.readFully(tail)
        fun u16(i: Int) = (tail[i].toInt() and 255) or ((tail[i + 1].toInt() and 255) shl 8)
        fun u32(i: Int): Long = (0..3).fold(0L) { n, b -> n or ((tail[i + b].toLong() and 255) shl (8 * b)) }
        val end = (tailSize - 22 downTo 0).firstOrNull { i ->
            u32(i) == 0x06054b50L && i + 22 + u16(i + 20) == tailSize
        } ?: error("Direktori ZIP tidak ditemukan/berkas terpotong.")
        require(u16(end + 4) == 0 && u16(end + 6) == 0 && u16(end + 8) == u16(end + 10)) { "ZIP multi-disk tidak didukung." }
        val count = u16(end + 10)
        val centralSize = u32(end + 12)
        val offset = u32(end + 16)
        require(count in 1..MAX_ENTRIES && centralSize in 1..16L * 1024 * 1024 && offset + centralSize == base + end) {
            "ZIP64, direktori ZIP besar, atau offset ZIP tidak didukung."
        }
        var signed = false
        if (offset >= 24) {
            input.seek(offset - 24)
            val footer = ByteArray(24); input.readFully(footer)
            if (footer.copyOfRange(8, 24).contentEquals("APK Sig Block 42".toByteArray(Charsets.US_ASCII))) {
                val size = (0..7).fold(0L) { n, i -> n or ((footer[i].toLong() and 255) shl (8 * i)) }
                require(size in 24..8L * 1024 * 1024 && size + 8 <= offset) { "Blok signing APK terlalu besar/tidak valid." }
                input.seek(offset - size - 8)
                val header = ByteArray(8); input.readFully(header)
                require(header.contentEquals(footer.copyOfRange(0, 8))) { "Ukuran blok signing tidak cocok." }
                signed = true
            }
        }
        count to signed
    }
}

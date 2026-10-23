package dev.retrobit.agent.local

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded streaming copy: never hold a GB-sized model in a ByteArray. */
internal object GgufImport {
    const val MAX_BYTES = 3L * 1024 * 1024 * 1024

    fun validateHeader(bytes: ByteArray) {
        require(bytes.size == 8 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
            "Bukan berkas GGUF. Pilih model Instruct .gguf, bukan APK, ZIP, atau safetensors."
        }
        val version = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(version == 2 || version == 3) { "Versi GGUF belum didukung. Gunakan GGUF v2/v3." }
    }

    suspend fun copy(
        input: InputStream, output: OutputStream, maxBytes: Long = MAX_BYTES,
        check: suspend (Long) -> Unit = {},
    ): Long {
        val header = ByteArray(8)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            require(n > 0) { "Berkas GGUF terpotong." }
            read += n
        }
        validateHeader(header)
        require(maxBytes >= 8) { "Batas ukuran terlalu kecil." }
        output.write(header)
        var total = 8L
        val buffer = ByteArray(256 * 1024)
        check(total)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            require(total + n <= maxBytes) { "Model melebihi batas impor 3 GiB. Pilih model 1.5B atau 3B Q4 satu berkas." }
            check(total + n)
            output.write(buffer, 0, n)
            total += n
        }
        // Real GGUF has counts/metadata after the first 8 bytes.
        require(total >= 24) { "Berkas GGUF tidak lengkap." }
        return total
    }
}

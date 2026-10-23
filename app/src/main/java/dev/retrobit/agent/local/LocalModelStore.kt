package dev.retrobit.agent.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

/** Private model storage. Download/import never replaces a live mmap or exports weights. */
class LocalModelStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "local-models").apply { mkdirs() }
    private val file = File(directory, "model.gguf")
    private val preferences = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private val transfers = Mutex()

    init { File(directory, "import.partial").delete(); LocalInference.modelFile = file }
    val ready: Boolean get() = file.isFile
    val label: String get() = if (ready) preferences.getString("name", "Model GGUF").orEmpty() else "Belum ada model"
    val sizeBytes: Long get() = if (ready) file.length() else 0L
    val sha256: String get() = if (ready) preferences.getString("sha256", "").orEmpty() else ""

    suspend fun import(uri: Uri, expectedSha256: String = "", progress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        val checksum = ModelDownloadPolicy.checksum(expectedSha256)
        transfers.withLock {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.take(120) ?: "Model GGUF"
            context.contentResolver.openInputStream(uri)?.use { input ->
                install(input, name, -1, checksum, progress)
            } ?: error("Tidak bisa membaca berkas yang dipilih.")
        }
    }

    suspend fun download(url: String, expectedSha256: String, progress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val checksum = ModelDownloadPolicy.checksum(expectedSha256)
        transfers.withLock {
            ModelDownloader().download(url) { input, total, name ->
                progress(0, total)
                install(input, name.take(120), total, checksum) { copied -> progress(copied, total) }
            }
        }
    }

    /** Shared transaction for imports and downloads; tests inject streams and expected lengths. */
    internal suspend fun install(input: InputStream, name: String, expectedSize: Long, expectedHash: String?, progress: suspend (Long) -> Unit) {
        val partial = File(directory, "import.partial")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileOutputStream(partial).use { output ->
                var last = 0L
                copied = GgufImport.copy(DigestInputStream(input, digest), output) { bytes ->
                    currentCoroutineContext().ensureActive()
                    require(directory.usableSpace > 64L * 1024 * 1024 + 256 * 1024) { "Penyimpanan hampir penuh. Kosongkan ruang sebelum impor/download." }
                    if (bytes - last >= 1024 * 1024) { progress(bytes); last = bytes }
                }
                output.fd.sync()
            }
            require(expectedSize < 0 || copied == expectedSize) { "Download terpotong: ukuran tidak cocok. Model lama tetap disimpan." }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(expectedHash == null || hash.equals(expectedHash, ignoreCase = true)) { "SHA-256 tidak cocok. Model lama tetap disimpan." }
            progress(copied) // last cancellable callback before the atomic commit
            LocalInference.gate.withLock {
                currentCoroutineContext().ensureActive()
                // Once committed, finish metadata without reporting a false cancellation.
                withContext(NonCancellable) {
                    Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    preferences.edit().putString("name", name).putString("sha256", hash).commit()
                }
            }
        } finally { partial.delete() }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        transfers.withLock {
            LocalInference.gate.withLock {
                check(!file.exists() || file.delete()) { "Berkas model tidak bisa dihapus." }
                preferences.edit().clear().apply()
            }
        }
    }
}
